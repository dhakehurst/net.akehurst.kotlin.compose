package net.akehurst.kotlin.compose.layout.ladder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.EdgeContentPosition
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutEdgeContent
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import net.akehurst.kotlin.compose.layout.graph.InitialViewPlacement
import net.akehurst.kotlin.compose.layout.graph.applyPanAndZoomGesture
import net.akehurst.kotlin.compose.layout.graph.drawEdgeSymbol
import net.akehurst.kotlin.compose.layout.graph.initialViewState
import net.akehurst.kotlin.compose.layout.graph.routeAnchor
import net.akehurst.kotlin.compose.layout.graph.zoomAboutPointer
import net.akehurst.kotlin.compose.layout.graph.zoomFactorFromScrollDelta
import kotlin.math.roundToInt

/**
 * Colours used by [LadderLayoutView] for the things it draws itself.
 * Rail headers, rung texts and group labels are drawn by the caller and keep their own colours.
 */
data class LadderLayoutColors(
    /** Rung line colour, used when [GraphLayoutEdgeContent.lineColor] is null. */
    val rungLine: Color = Color(0xFF444444),
    /** The dashed line below each rail header. */
    val railLine: Color = Color(0xFF444444),
    val barFill: Color = Color.White,
    val barBorder: Color = Color(0xFF444444),
    /** Group box, tab outline and the dashed lines between group parts. */
    val groupBorder: Color = Color(0xFF444444),
    /** Background of a group's label tab, so rail lines do not run through the label. */
    val groupTabFill: Color = Color.White,
    /** Text and border of the placeholder shown for a rail, rung or group with no content. */
    val error: Color = Color(0xFFD32F2F),
    /** Background of the placeholder shown for a rail, rung or group with no content. */
    val errorBackground: Color = Color(0x22D32F2F),
)

/** Line widths and dash patterns used by [LadderLayoutView] for the things it draws itself. All in dp. */
data class LadderLayoutStyle(
    val railLineWidth: Float = 1f,
    val railLineDash: List<Float> = listOf(6f, 4f),
    val barBorderWidth: Float = 1f,
    val groupBorderWidth: Float = 1f,
    val groupPartDividerDash: List<Float> = listOf(6f, 4f),
)

/**
 * Draws a [LadderLayoutState]: rails with their headers, rungs, bars and groups, laid out by [LadderLayoutEngine].
 * Pan and zoom work as in `CompoundGraphLayoutView`: the caller holds [viewState] and [updateView] changes it.
 *
 * Content that is missing from [LadderLayoutState] is shown as a red placeholder, except group part labels.
 * A rung whose [GraphLayoutEdgeContent] has no texts or symbols is an intentionally plain line.
 */
@Composable
fun LadderLayoutView(
    state: LadderLayoutState,
    viewState: GraphLayoutViewState,
    updateView: (offset: Offset, zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: LadderLayoutColors = LadderLayoutColors(),
    style: LadderLayoutStyle = LadderLayoutStyle(),
    config: LadderLayoutConfig = LadderLayoutConfig(),
    /**
     * Where to put the diagram when it is first laid out. Other than [InitialViewPlacement.AS_GIVEN],
     * the view calls [updateView] once the diagram is measured, and again when its size changes,
     * until the user pans or zooms. It is done again for a new [state] id.
     */
    initialView: InitialViewPlacement = InitialViewPlacement.AS_GIVEN,
) {
    val density = LocalDensity.current.density
    val engine = remember(config) { LadderLayoutEngine(config) }
    // The engine works in dp; this holds its result in pixels, written while measuring and read while drawing.
    var layoutPx by remember(state.id) { mutableStateOf<LadderLayoutResult?>(null) }

    val stateForGestures by rememberUpdatedState(viewState)
    val currentUpdateView by rememberUpdatedState(updateView)
    var movedStateId by remember { mutableStateOf<String?>(null) }
    val currentStateId by rememberUpdatedState(state.id)
    val userMovedView = movedStateId == state.id
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val diagramWidth = layoutPx?.width?.toFloat() ?: 0f
    val diagramHeight = layoutPx?.height?.toFloat() ?: 0f
    LaunchedEffect(state.id, initialView, diagramWidth, diagramHeight, viewportSize, userMovedView) {
        if (userMovedView || layoutPx == null || viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        val placed = initialViewState(
            initialView, diagramWidth, diagramHeight,
            viewportSize.width.toFloat(), viewportSize.height.toFloat()
        ) ?: return@LaunchedEffect
        if (placed != stateForGestures) currentUpdateView(placed.offset, placed.zoom)
    }

    val model = state.model
    val railIds = model.rails.map { it.id }.distinct()
    val rungs = remember(model) { model.rungs() }
    val groups = remember(model) { model.groups() }
    val parts = groups.flatMap { it.parts }.filter { it.id in state.groupPartLabelById }
    val rungContent = state.rungContentById.toMap()
    val rungLabelSlots = rungs.flatMap { rung ->
        val content = rungContent[rung.id]
        if (content == null) {
            listOf(RungLabelSlot(rung.id, EdgeContentPosition.MIDDLE, null))
        } else {
            EdgeContentPosition.entries.mapNotNull { p ->
                val texts = content.texts.filter { it.position == p }
                if (texts.isEmpty()) null else RungLabelSlot(rung.id, p, content)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val sg = stateForGestures
                    val next = applyPanAndZoomGesture(state = sg, centroid = centroid, pan = pan, zoomChange = zoom)
                    if (next != sg) {
                        movedStateId = currentStateId
                        currentUpdateView(next.offset, next.zoom)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            val scale = zoomFactorFromScrollDelta(deltaY = change.scrollDelta.y, density = density)
                            val next = zoomAboutPointer(state = stateForGestures, pointer = change.position, targetZoom = stateForGestures.zoom * scale)
                            if (next != stateForGestures) {
                                movedStateId = currentStateId
                                currentUpdateView(next.offset, next.zoom)
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    translationX = viewState.offset.x,
                    translationY = viewState.offset.y,
                    scaleX = viewState.zoom,
                    scaleY = viewState.zoom,
                    transformOrigin = TransformOrigin(0f, 0f),
                )
        ) {
            Layout(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .drawBehind { layoutPx?.let { drawLadder(it, rungContent, colors, style, config.tabCorner.toFloat()) } },
                content = {
                    railIds.forEach { id ->
                        val header = state.railHeaderById[id]
                        Box { if (header != null) header() else MissingContent("rail $id", colors) }
                    }
                    rungLabelSlots.forEach { slot ->
                        if (slot.content == null) {
                            MissingContent(slot.rungId, colors)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                slot.content.texts.filter { it.position == slot.position }.forEach { it.content() }
                            }
                        }
                    }
                    groups.forEach { g ->
                        val label = state.groupLabelById[g.id]
                        Box { if (label != null) label() else MissingContent(g.id, colors) }
                    }
                    parts.forEach { p ->
                        Box { state.groupPartLabelById[p.id]?.invoke() }
                    }
                }
            ) { measurables, _ ->
                val placeables = measurables.map { it.measure(Constraints()) }
                fun sizeDp(p: Placeable) = LadderSize(p.width / density.toDouble(), p.height / density.toDouble())
                var i = 0
                val headerPlaceables = railIds.map { placeables[i++] }
                val rungPlaceables = rungLabelSlots.map { placeables[i++] }
                val groupPlaceables = groups.map { placeables[i++] }
                val partPlaceables = parts.map { placeables[i++] }

                val sizes = LadderMeasuredSizes(
                    railHeaders = railIds.zip(headerPlaceables).associate { (id, p) -> id to sizeDp(p).ceilToWholeDp() },
                    rungLabels = rungLabelSlots.zip(rungPlaceables)
                        .groupBy({ it.first.rungId }, { it.first.position to sizeDp(it.second).ceilToWholeDp() })
                        .mapValues { it.value.toMap() },
                    groupLabels = groups.zip(groupPlaceables).associate { (g, p) -> g.id to sizeDp(p).ceilToWholeDp() },
                    groupPartLabels = parts.zip(partPlaceables)
                        .filter { (_, p) -> p.width > 0 || p.height > 0 }
                        .associate { (part, p) -> part.id to sizeDp(p).ceilToWholeDp() },
                )
                val result = engine.layout(model, sizes).scaledBy(density.toDouble())
                layoutPx = result

                val railsById = result.rails.associateBy { it.railId }
                val rungsById = result.rungs.associateBy { it.rungId }
                val groupsById = result.groups.associateBy { it.groupId }
                val partsById = result.groups.flatMap { it.parts }.associateBy { it.partId }
                layout(result.width.roundToInt(), result.height.roundToInt()) {
                    railIds.forEachIndexed { k, id ->
                        railsById[id]?.let { headerPlaceables[k].place(it.header.left.roundToInt(), it.header.top.roundToInt()) }
                    }
                    rungLabelSlots.forEachIndexed { k, slot ->
                        val rect = rungsById[slot.rungId]?.labels?.get(slot.position) ?: return@forEachIndexed
                        rungPlaceables[k].place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                    groups.forEachIndexed { k, g ->
                        groupsById[g.id]?.let { groupPlaceables[k].place(it.label.left.roundToInt(), it.label.top.roundToInt()) }
                    }
                    parts.forEachIndexed { k, p ->
                        partsById[p.id]?.label?.let { partPlaceables[k].place(it.left.roundToInt(), it.top.roundToInt()) }
                    }
                }
            }
        }
    }
}

private data class RungLabelSlot(val rungId: String, val position: EdgeContentPosition, val content: GraphLayoutEdgeContent?)

/** Rounds up to a whole dp, so content always fits, ignoring floating-point noise. */
private fun LadderSize.ceilToWholeDp() = LadderSize(kotlin.math.ceil(width - 1e-6), kotlin.math.ceil(height - 1e-6))

@Composable
private fun MissingContent(label: String, colors: LadderLayoutColors) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(colors.errorBackground)
            .border(1.dp, colors.error)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = "⚠ $label", color = colors.error, style = MaterialTheme.typography.labelSmall)
    }
}

private fun LadderModel.rungs(): List<LadderRung> = allEvents(events).filterIsInstance<LadderRung>()

private fun LadderModel.groups(): List<LadderGroup> = allEvents(events).filterIsInstance<LadderGroup>()

private fun allEvents(events: List<LadderEvent>): List<LadderEvent> = events.flatMap { e ->
    if (e is LadderGroup) listOf(e) + e.parts.flatMap { allEvents(it.events) } else listOf(e)
}

private fun DrawScope.dash(pattern: List<Float>): PathEffect? =
    if (pattern.isEmpty()) null else PathEffect.dashPathEffect(FloatArray(pattern.size) { pattern[it].dp.toPx() }, phase = 0f)

private fun DrawScope.drawLadder(
    layout: LadderLayoutResult,
    rungContent: Map<String, GraphLayoutEdgeContent>,
    colors: LadderLayoutColors,
    style: LadderLayoutStyle,
    /** Size of the tab's cut corner, in dp. */
    tabCorner: Float,
) {
    // Groups first, so everything else is drawn on top of them.
    layout.groups.forEach { g ->
        val r = g.rect
        val groupStroke = Stroke(width = style.groupBorderWidth.dp.toPx())
        drawRect(colors.groupBorder, Offset(r.left.toFloat(), r.top.toFloat()), Size(r.width.toFloat(), r.height.toFloat()), style = groupStroke)
        val t = g.tab
        val corner = minOf(tabCorner.dp.toPx(), t.height.toFloat() / 2, t.width.toFloat() / 2)
        drawPath(
            Path().apply {
                moveTo(t.left.toFloat(), t.top.toFloat())
                lineTo(t.right.toFloat(), t.top.toFloat())
                lineTo(t.right.toFloat(), t.bottom.toFloat() - corner)
                lineTo(t.right.toFloat() - corner, t.bottom.toFloat())
                lineTo(t.left.toFloat(), t.bottom.toFloat())
                close()
            },
            colors.groupTabFill
        )
        drawPath(
            Path().apply {
                moveTo(t.left.toFloat(), t.bottom.toFloat())
                lineTo(t.right.toFloat() - corner, t.bottom.toFloat())
                lineTo(t.right.toFloat(), t.bottom.toFloat() - corner)
                lineTo(t.right.toFloat(), t.top.toFloat())
            },
            colors.groupBorder,
            style = groupStroke
        )
        g.parts.drop(1).forEach { p ->
            drawLine(
                colors.groupBorder,
                Offset(p.rect.left.toFloat(), p.rect.top.toFloat()),
                Offset(p.rect.right.toFloat(), p.rect.top.toFloat()),
                strokeWidth = style.groupBorderWidth.dp.toPx(),
                pathEffect = dash(style.groupPartDividerDash)
            )
        }
    }

    layout.rails.forEach { rail ->
        drawLine(
            colors.railLine,
            Offset(rail.x.toFloat(), rail.lineTop.toFloat()),
            Offset(rail.x.toFloat(), rail.lineBottom.toFloat()),
            strokeWidth = style.railLineWidth.dp.toPx(),
            pathEffect = dash(style.railLineDash)
        )
    }

    layout.bars.sortedBy { it.depth }.forEach { bar ->
        val r = bar.rect
        val topLeft = Offset(r.left.toFloat(), r.top.toFloat())
        val size = Size(r.width.toFloat(), r.height.toFloat())
        drawRect(colors.barFill, topLeft, size)
        drawRect(colors.barBorder, topLeft, size, style = Stroke(width = style.barBorderWidth.dp.toPx()))
    }

    layout.rungs.forEach { rung ->
        if (rung.points.size < 2) return@forEach
        val content = rungContent[rung.rungId]
        drawPath(
            Path().apply {
                rung.points.forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat()) }
            },
            content?.lineColor ?: colors.rungLine,
            style = Stroke(
                width = (content?.lineWidth ?: GraphLayoutEdgeContent.DEFAULT_LINE_WIDTH).dp.toPx(),
                pathEffect = content?.lineDash?.let { dash(it) }
            )
        )
        val drawSymbolPath: (Path, Color?, Stroke?) -> Unit = { path, color, stroke ->
            if (stroke == null) drawPath(path, color ?: Color.Transparent) else drawPath(path, color ?: Color.Transparent, style = stroke)
        }
        content?.startSymbol?.let { drawEdgeSymbol(it, routeAnchor(rung.points, EdgeContentPosition.START), 0.0, 0.0, density, drawSymbolPath) }
        content?.endSymbol?.let { drawEdgeSymbol(it, routeAnchor(rung.points, EdgeContentPosition.END), 0.0, 0.0, density, drawSymbolPath) }
    }
}
