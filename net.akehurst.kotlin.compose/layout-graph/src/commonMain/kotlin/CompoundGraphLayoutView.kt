package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.absoluteValue
import kotlin.math.exp
import androidx.compose.foundation.Canvas
import kotlin.math.min

// ── Colours ─────────────────────────────────────────────────────────────────

/**
 * Colours used by [CompoundGraphLayoutView] for the things it draws itself.
 * Node content and edge symbols/labels are drawn by the caller and keep their own colours.
 *
 * The defaults are the colours the view has always used, so a light theme looks as before.
 */
data class CompoundGraphLayoutColors(
    /** Edge line colour, used when [GraphLayoutEdgeContent.lineColor] is null. */
    val edgeLine: Color = Color(0xFF444444),
    /** Text and border of the placeholder shown for a node or edge with no content. */
    val error: Color = Color(0xFFD32F2F),
    /** Background of the placeholder shown for a node or edge with no content. */
    val errorBackground: Color = Color(0x22D32F2F),
    /** Dividing lines between the regions of a tessellated container. */
    val tessellationBorder: Color = Color(0xFF3F7ACC),
    /** Debug overlay: marker at the start of each edge. */
    val debugEdgeStart: Color = Color(0xFF2196F3),
    /** Debug overlay: marker at the end of each edge. */
    val debugEdgeEnd: Color = Color(0xFFD32F2F),
    /** Debug overlay: outlines of the measured node bounds, used in turn. Must not be empty. */
    val debugNodeBounds: List<Color> = listOf(
        Color(0xB36200EE),
        Color(0xB303DAC6),
        Color(0xB3FF6D00),
        Color(0xB32196F3),
        Color(0xB3D32F2F),
        Color(0xB34CAF50),
    ),
    /** Content-origin overlay: cross at the content origin of each container. */
    val contentOrigin: Color = Color(0xFFD32F2F),
) {
    init {
        require(debugNodeBounds.isNotEmpty()) { "debugNodeBounds must not be empty" }
    }
}

/** Where [CompoundGraphLayoutView] puts the graph when it is first laid out. */
enum class InitialViewPlacement {
    /** The view state is left as given (by default the graph's top left corner is at the view's top left). */
    AS_GIVEN,
    /** Centred in the view, at zoom 1. */
    CENTRE,
    /** Zoomed out so the whole graph fits the view (never zoomed in beyond 1), and centred. */
    FIT_AND_CENTRE
}

/**
 * The view state that puts a graph of [graphWidth] x [graphHeight] in a view of [viewWidth] x [viewHeight]
 * (all in pixels) as [placement] says, or null for [InitialViewPlacement.AS_GIVEN].
 */
internal fun initialViewState(
    placement: InitialViewPlacement,
    graphWidth: Float, graphHeight: Float,
    viewWidth: Float, viewHeight: Float
): GraphLayoutViewState? {
    val zoom = when (placement) {
        InitialViewPlacement.AS_GIVEN -> return null
        InitialViewPlacement.CENTRE -> 1f
        InitialViewPlacement.FIT_AND_CENTRE -> {
            val fit = min(viewWidth / graphWidth.coerceAtLeast(1f), viewHeight / graphHeight.coerceAtLeast(1f))
            fit.coerceIn(minViewZoom, 1f)
        }
    }
    return GraphLayoutViewState(
        zoom = zoom,
        offset = Offset((viewWidth - graphWidth * zoom) / 2f, (viewHeight - graphHeight * zoom) / 2f)
    )
}

private val LocalCompoundGraphLayoutColors = staticCompositionLocalOf { CompoundGraphLayoutColors() }

/**
 * Shown when a node has no entry in [GraphLayoutCompoundGraphState.nodeContentById].
 * Fills the node's allocated bounds so the missing content is immediately visible.
 */
@Composable
private fun MissingNodeContent(nodeId: String) {
    val colors = LocalCompoundGraphLayoutColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.errorBackground)
            .border(2.dp, colors.error)
            .padding(4.dp)
    ) {
        Text(
            text = "⚠ no content\n$nodeId",
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
/**
 * Shown when an edge has no entry in [GraphLayoutCompoundGraphState.edgeContentById].
 * Appears as a small label at the edge route midpoint.
 * An edge with [GraphLayoutEdgeContent] but no texts/symbols is treated as intentionally plain.
 */
@Composable
private fun MissingEdgeContent(edgeId: String) {
    val colors = LocalCompoundGraphLayoutColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(colors.errorBackground)
            .border(1.dp, colors.error)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "⚠ $edgeId",
            color = colors.error,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** The default `layoutErrorContent` of [CompoundGraphLayoutView]: the exception's message. */
@Composable
private fun DefaultLayoutErrorContent(error: Throwable) {
    val colors = LocalCompoundGraphLayoutColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.errorBackground)
            .border(2.dp, colors.error)
            .padding(8.dp)
    ) {
        Text(
            text = "⚠ layout failed\n${error.message ?: error.toString()}",
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EdgeTextBubble(text: GraphLayoutEdgeText) {
    text.content()
}

internal data class EdgeAnchor(
    val point: Pair<Double, Double>,
    val angleRadians: Float
)

private data class EdgeTextPlacement(
    val edgeId: String,
    val position: EdgeContentPosition,
    val texts: List<GraphLayoutEdgeText> = emptyList(),
    val isMissing: Boolean = false
)

private const val boundaryEpsilon = 1e-6
private const val minViewZoom = 0.5f
private const val maxViewZoom = 5f
private const val scrollZoomSensitivity = 0.06f

private fun clipRayToRectBoundary(from: Offset, to: Offset, rect: Rect): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (kotlin.math.abs(dx) < boundaryEpsilon && kotlin.math.abs(dy) < boundaryEpsilon) {
        return Offset(rect.right, rect.center.y)
    }

    val candidates = mutableListOf<Triple<Float, Int, Offset>>()

    if (kotlin.math.abs(dx) >= boundaryEpsilon) {
        val tRight = (rect.right - from.x) / dx
        if (tRight > boundaryEpsilon) {
            val y = from.y + tRight * dy
            if (y >= rect.top - boundaryEpsilon && y <= rect.bottom + boundaryEpsilon) {
                candidates.add(Triple(tRight, 0, Offset(rect.right, y)))
            }
        }
        val tLeft = (rect.left - from.x) / dx
        if (tLeft > boundaryEpsilon) {
            val y = from.y + tLeft * dy
            if (y >= rect.top - boundaryEpsilon && y <= rect.bottom + boundaryEpsilon) {
                candidates.add(Triple(tLeft, 2, Offset(rect.left, y)))
            }
        }
    }
    if (kotlin.math.abs(dy) >= boundaryEpsilon) {
        val tBottom = (rect.bottom - from.y) / dy
        if (tBottom > boundaryEpsilon) {
            val x = from.x + tBottom * dx
            if (x >= rect.left - boundaryEpsilon && x <= rect.right + boundaryEpsilon) {
                candidates.add(Triple(tBottom, 1, Offset(x, rect.bottom)))
            }
        }
        val tTop = (rect.top - from.y) / dy
        if (tTop > boundaryEpsilon) {
            val x = from.x + tTop * dx
            if (x >= rect.left - boundaryEpsilon && x <= rect.right + boundaryEpsilon) {
                candidates.add(Triple(tTop, 3, Offset(x, rect.top)))
            }
        }
    }

    if (candidates.isEmpty()) {
        return from
    }

    val minT = candidates.minOf { it.first }
    return candidates
        .filter { kotlin.math.abs(it.first - minT) <= boundaryEpsilon }
        .minBy { it.second }
        .third
}

private fun nodeBoundsInLayer(
    graphLayerCoordinates: LayoutCoordinates?,
    nodeCoordinates: LayoutCoordinates?
): Rect? {
    if (graphLayerCoordinates == null || nodeCoordinates == null) {
        return null
    }
    return graphLayerCoordinates.localBoundingBoxOf(nodeCoordinates, clipBounds = false)
}

internal fun routeAnchor(route: List<Pair<Double, Double>>, position: EdgeContentPosition): EdgeAnchor {
    if (route.isEmpty()) return EdgeAnchor(0.0 to 0.0, 0f)
    if (route.size == 1) return EdgeAnchor(route.first(), 0f)

    fun angle(from: Pair<Double, Double>, to: Pair<Double, Double>) = atan2(
        (to.second - from.second).toFloat(),
        (to.first - from.first).toFloat()
    )

    return when (position) {
        EdgeContentPosition.START -> EdgeAnchor(route.first(), angle(route[0], route[1]))
        EdgeContentPosition.END -> EdgeAnchor(route.last(), angle(route[route.lastIndex - 1], route.last()))
        EdgeContentPosition.MIDDLE -> {
            val segmentLengths = route.zipWithNext { start, end ->
                val dx = end.first - start.first
                val dy = end.second - start.second
                sqrt((dx * dx) + (dy * dy))
            }
            val totalLength = segmentLengths.sum()
            if (totalLength <= 0.0) {
                EdgeAnchor(route[route.size / 2], angle(route[0], route[1]))
            } else {
                val targetLength = totalLength / 2.0
                var traversed = 0.0
                var segmentIndex = 0
                while (segmentIndex < segmentLengths.lastIndex && traversed + segmentLengths[segmentIndex] < targetLength) {
                    traversed += segmentLengths[segmentIndex]
                    segmentIndex += 1
                }
                val start = route[segmentIndex]
                val end = route[segmentIndex + 1]
                val segmentLength = segmentLengths[segmentIndex].coerceAtLeast(1e-6)
                val ratio = ((targetLength - traversed) / segmentLength).coerceIn(0.0, 1.0)
                EdgeAnchor(
                    point = (
                        start.first + ((end.first - start.first) * ratio)
                    ) to (
                        start.second + ((end.second - start.second) * ratio)
                    ),
                    angleRadians = angle(start, end)
                )
            }
        }
    }
}
/** @param distance how far START and END texts sit from the route end, in pixels */
/** The point [distance] along [route] from its start, with the direction of the segment it is on. */
internal fun pointAlongRoute(route: List<Pair<Double, Double>>, distance: Double): Pair<Pair<Double, Double>, Float> {
    if (route.isEmpty()) return (0.0 to 0.0) to 0f
    if (route.size == 1) return route.first() to 0f
    var remaining = distance.coerceAtLeast(0.0)
    for (i in 0 until route.lastIndex) {
        val a = route[i]
        val b = route[i + 1]
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = sqrt(dx * dx + dy * dy)
        if (remaining <= len || i == route.lastIndex - 1) {
            val r = if (len <= 1e-9) 0.0 else (remaining / len).coerceIn(0.0, 1.0)
            return (a.first + dx * r to a.second + dy * r) to atan2(dy.toFloat(), dx.toFloat())
        }
        remaining -= len
    }
    return route.last() to 0f
}

private fun routeLength(route: List<Pair<Double, Double>>): Double = route.zipWithNext { a, b ->
    val dx = b.first - a.first
    val dy = b.second - a.second
    sqrt(dx * dx + dy * dy)
}.sum()

/** An edge label to place: its route, where on the route it goes, its size, and its usual centre. */
internal data class EdgeLabelToPlace(
    val route: List<Pair<Double, Double>>,
    val position: EdgeContentPosition,
    val width: Float,
    val height: Float,
    val defaultCentre: Offset
)

/**
 * Centres for edge labels, in the coordinates of the routes.
 *
 * Each label goes at its usual centre unless that overlaps a label placed before it. Then it slides along
 * its own route, in steps of [step], to the nearest centre where it overlaps neither a placed label nor one
 * of [obstacles] (the nodes). If there is no such place it keeps its usual centre.
 * With [avoidOverlap] false every label keeps its usual centre.
 */
internal fun placeEdgeLabels(
    labels: List<EdgeLabelToPlace>,
    obstacles: List<Rect>,
    step: Float,
    edgeTextDistance: Float,
    avoidOverlap: Boolean
): List<Offset> {
    if (!avoidOverlap) return labels.map { it.defaultCentre }
    val placed = mutableListOf<Rect>()
    fun box(centre: Offset, l: EdgeLabelToPlace) =
        Rect(centre.x - l.width / 2f, centre.y - l.height / 2f, centre.x + l.width / 2f, centre.y + l.height / 2f)
    // Touching edges are not an overlap.
    fun Rect.hits(o: Rect) = left < o.right - 0.5f && o.left < right - 0.5f && top < o.bottom - 0.5f && o.top < bottom - 0.5f
    return labels.map { l ->
        val default = box(l.defaultCentre, l)
        val centre = if (placed.none { it.hits(default) }) {
            l.defaultCentre
        } else {
            val length = routeLength(l.route)
            val d0 = when (l.position) {
                EdgeContentPosition.START -> min(edgeTextDistance.toDouble(), length)
                EdgeContentPosition.MIDDLE -> length / 2.0
                EdgeContentPosition.END -> max(length - edgeTextDistance, 0.0)
            }
            val safeStep = step.coerceAtLeast(1f).toDouble()
            val maxK = (length / safeStep).toInt() + 1
            var found: Offset? = null
            for (k in 1..maxK) {
                for (sign in intArrayOf(1, -1)) {
                    val d = d0 + sign * k * safeStep
                    if (d < 0.0 || d > length) continue
                    val (p, _) = pointAlongRoute(l.route, d)
                    val c = Offset(p.first.toFloat(), p.second.toFloat())
                    val b = box(c, l)
                    if (placed.none { it.hits(b) } && obstacles.none { it.hits(b) }) {
                        found = c
                        break
                    }
                }
                if (found != null) break
            }
            found ?: l.defaultCentre
        }
        placed += box(centre, l)
        centre
    }
}

private fun edgeTextOffset(position: EdgeContentPosition, angleRadians: Float, distance: Float): Offset {
    return when (position) {
        EdgeContentPosition.START -> Offset(
            x = cos(angleRadians) * distance,
            y = sin(angleRadians) * distance
        )

        EdgeContentPosition.END -> Offset(
            x = -cos(angleRadians) * distance,
            y = -sin(angleRadians) * distance
        )

        EdgeContentPosition.MIDDLE -> Offset.Zero
    }
}

private fun lineRectIntersection(
    from: Pair<Double, Double>,
    to: Pair<Double, Double>,
    rectLeft: Double,
    rectTop: Double,
    rectRight: Double,
    rectBottom: Double
): Pair<Double, Double> {
    // Clip a line segment to a rectangle boundary.
    // Returns the first boundary intersection point going from 'from' towards 'to'.
    if (from == to) return from

    val dx = to.first - from.first
    val dy = to.second - from.second

    // Parametric line: p(t) = from + t * (to - from), where t in [0, 1]
    var tEnter = 0.0

    // Check intersection with vertical lines (left, right)
    if (dx.absoluteValue > 1e-9) {
        val tLeft = (rectLeft - from.first) / dx
        val tRight = (rectRight - from.first) / dx
        val tMin = minOf(tLeft, tRight)
        tEnter = maxOf(tEnter, tMin)
    }

    // Check intersection with horizontal lines (top, bottom)
    if (dy.absoluteValue > 1e-9) {
        val tTop = (rectTop - from.second) / dy
        val tBottom = (rectBottom - from.second) / dy
        val tMin = minOf(tTop, tBottom)
        tEnter = maxOf(tEnter, tMin)
    }

    // Use tEnter to find boundary intersection in direction of 'to'
    val t = tEnter.coerceIn(0.0, 1.0)
    return (from.first + t * dx) to (from.second + t * dy)
}

/** Default for the `directEdgeEndGap` of [CompoundGraphLayoutView]. */
val DEFAULT_DIRECT_EDGE_END_GAP: Dp = 2.dp

/**
 * Moves the first point of [route] towards the second, and the last point towards the one before it,
 * by [gap] each, so the route stops short of the nodes at its ends.
 * An end segment is shortened by at most a third of its length, so short segments keep their direction.
 */
internal fun withEndGaps(route: List<Pair<Double, Double>>, gap: Double): List<Pair<Double, Double>> {
    if (route.size < 2 || gap <= 0.0) return route
    fun moveToward(from: Pair<Double, Double>, to: Pair<Double, Double>): Pair<Double, Double> {
        val dx = to.first - from.first
        val dy = to.second - from.second
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 1e-9) return from
        val d = minOf(gap, length / 3.0)
        return (from.first + dx / length * d) to (from.second + dy / length * d)
    }
    val result = route.toMutableList()
    result[0] = moveToward(route[0], route[1])
    result[result.lastIndex] = moveToward(route[route.lastIndex], route[route.lastIndex - 1])
    return result
}

/**
 * Finds the point where a ray from [center] (which is inside the rectangle) exits
 * the rectangle, going in the direction of [toward].
 *
 * Returns [center] if no exit is found (degenerate case).
 */
private fun rectExitPoint(
    center: Pair<Double, Double>,
    toward: Pair<Double, Double>,
    rectLeft: Double,
    rectTop: Double,
    rectRight: Double,
    rectBottom: Double
): Pair<Double, Double> {
    val dx = toward.first - center.first
    val dy = toward.second - center.second
    if (dx.absoluteValue < 1e-9 && dy.absoluteValue < 1e-9) return center

    var tExit = Double.MAX_VALUE

    // For each boundary side, find t where the ray crosses it (must be positive = forward)
    if (dx.absoluteValue > 1e-9) {
        val t = if (dx > 0) (rectRight - center.first) / dx else (rectLeft - center.first) / dx
        if (t > 0) tExit = minOf(tExit, t)
    }
    if (dy.absoluteValue > 1e-9) {
        val t = if (dy > 0) (rectBottom - center.second) / dy else (rectTop - center.second) / dy
        if (t > 0) tExit = minOf(tExit, t)
    }

    if (tExit == Double.MAX_VALUE) return center
    return (center.first + tExit * dx) to (center.second + tExit * dy)
}

internal fun drawEdgeSymbol(
    symbol: GraphLayoutEdgeSymbol,
    anchor: EdgeAnchor,
    globalToViewportOffsetX: Double,
    globalToViewportOffsetY: Double,
    /** Pixels per dp: the symbol's points and stroke width are in dp. */
    scale: Float,
    drawPathOnCanvas: (Path, Color?, Stroke?) -> Unit
) {
    if (symbol.pathPoints.isEmpty()) return

    val cosTheta = cos(anchor.angleRadians)
    val sinTheta = sin(anchor.angleRadians)
    val anchorX = (anchor.point.first + globalToViewportOffsetX).toFloat()
    val anchorY = (anchor.point.second + globalToViewportOffsetY).toFloat()
    val transformedPath = Path().apply {
        symbol.pathPoints.forEachIndexed { index, point ->
            val x = anchorX + (point.x * scale * cosTheta) - (point.y * scale * sinTheta)
            val y = anchorY + (point.x * scale * sinTheta) + (point.y * scale * cosTheta)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        if (symbol.isClosed) close()
    }

    if (symbol.isClosed && symbol.fillColor.alpha > 0f) {
        drawPathOnCanvas(transformedPath, symbol.fillColor, null)
    }
    if (symbol.strokeWidth > 0f && symbol.strokeColor.alpha > 0f) {
        drawPathOnCanvas(transformedPath, symbol.strokeColor, Stroke(width = symbol.strokeWidth * scale))
    }
}

// ── Compound layout view ────────────────────────────────────────────────────

/**
 * This function is a custom compose-layout for the childnodes.
 * children size should be constrained to the max of the layed out or measures size
 *
 * this node size should be big enough for measured size/position of all children
 */
private fun composeLayout(
    nodeId:String,
    childNodes: List<CompoundNodeLayout>,
    contentOriginX: Double,
    contentOriginY: Double,
    onContentMeasured: (String, Pair<Double, Double>) -> Unit
): MeasurePolicy = MeasurePolicy { measurables, constraints ->
    // println("composeLayout: $nodeId")
    val placeables = childNodes.mapIndexed { i, childNode ->
        val measurable = measurables[i]
        // The content's own size, reported so the next layout pass can size the node to fit it.
        // For a container this leaves out its children: the children host has no intrinsic size.
        val naturalWidth = measurable.maxIntrinsicWidth(Constraints.Infinity)
        val naturalHeight = measurable.maxIntrinsicHeight(naturalWidth)
        onContentMeasured(childNode.nodeId, naturalWidth.toDouble() to naturalHeight.toDouble())
        // maxIntrinsicWidth takes the available height, and maxIntrinsicHeight the chosen width.
        val width = max(measurable.maxIntrinsicWidth(childNode.height.roundToInt()), childNode.width.roundToInt())
        val height = max(measurable.maxIntrinsicHeight(width), childNode.height.roundToInt())
        measurable.measure(Constraints.fixed(width, height))
    }
    val requiredChildWidth = requiredChildHostWidth(placeables.map { it.width.toDouble() }, childNodes, contentOriginX)
    val requiredChildHeight = requiredChildHostHeight(placeables.map { it.height.toDouble() }, childNodes, contentOriginY)

    // Never report more than the constraints allow: Compose centres a layout that is larger than its
    // constraints, which shifted every node away from the edges (drawn unshifted) when the diagram was
    // bigger than the view. Children outside the reported size are still placed and drawn.
    val layoutWidth = when {
        constraints.hasBoundedWidth -> constraints.maxWidth
        else -> requiredChildWidth.roundToInt().coerceAtLeast(constraints.minWidth)
    }
    val layoutHeight = when {
        constraints.hasBoundedHeight -> constraints.maxHeight
        else -> requiredChildHeight.roundToInt().coerceAtLeast(constraints.minHeight)
    }
   // println("${nodeId}: constraints = $constraints")
   // println("${nodeId}: requiredChildWidth = $requiredChildWidth, requiredChildHeight = $requiredChildHeight")
   // println("${nodeId}: layoutWidth = $layoutWidth, layoutHeight = $layoutHeight")

    layout(layoutWidth, layoutHeight) {
        childNodes.forEachIndexed { i, childNode ->
            placeables[i].placeRelative(
                (childNode.globalX - contentOriginX).roundToInt(),
                (childNode.globalY - contentOriginY).roundToInt()
            )
        }
    }
}

/**
 * Measures and places like [policy], but has no intrinsic size. Used for a container's children host,
 * so the intrinsic size of a container's content is the size of what the content itself draws,
 * not of its children (whose size comes from the layout).
 */
private fun withoutIntrinsicSize(policy: MeasurePolicy): MeasurePolicy = object : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult =
        with(policy) { measure(measurables, constraints) }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0
}

/**
 * Renders one node recursively. If the node is a container, its node-content lambda receives
 * a children composable that places direct child nodes (containers first, then leaves)
 * at their layout-computed local positions.
 *
 * The container composable decides where in its visual tree to call children, which defines
 * the child-host origin in Compose and gives full z-order control over overlays (dividers, etc.).
 */
@Composable
private fun NodeContent(
    node: CompoundNodeLayout,
    state: GraphLayoutCompoundGraphState,
    layoutResult: CompoundLayoutResult,
    nodeCoordinatesById: MutableMap<String, LayoutCoordinates>,
    onChildHostMeasured: (String, ContainerChildHostMetrics) -> Unit,
    onContentMeasured: (String, Pair<Double, Double>) -> Unit,
    /** Opacity of nodes that fade in or out during a transition (1 when missing). */
    nodeAlpha: Map<String, Float>,
    /** The layout measurements are reported against; null while a transition is drawn (nothing is reported). */
    reportMetricsFor: CompoundLayoutResult?,
) {
    val content = state.nodeContentById[node.nodeId]
    val alpha = nodeAlpha[node.nodeId] ?: 1f
    val alphaModifier = if (alpha < 1f) Modifier.graphicsLayer(alpha = alpha) else Modifier
    if (node.isContainer) {
        val containerGraphLayout = layoutResult.graphLayoutsById[node.nodeId]
        // The child area's origin; children may be shifted right of it to centre them.
        val contentOriginX = containerGraphLayout?.let { it.globalOffsetX - it.centringX } ?: node.globalX
        val contentOriginY = containerGraphLayout?.let { it.globalOffsetY - it.shiftY } ?: node.globalY
        val childNodes = layoutResult.nodeLayoutsById.values
            .filter { it.ownerGraphId == node.nodeId }
            // Border nodes last, so they are drawn on top of their host.
            .sortedWith(compareBy({ drawOrder(it, layoutResult) }, { it.nodeId }))
        // Store the container's LayoutCoordinates (not boundsInRoot) so child-host
        // insets are computed in zoom-independent layout pixels.
        var containerCoordinates by remember(node.nodeId) { mutableStateOf<LayoutCoordinates?>(null) }
        var childHostCoordinates by remember(node.nodeId) { mutableStateOf<LayoutCoordinates?>(null) }
        val reportContainerMetrics: () -> Unit = {
            val containerCoords = containerCoordinates
            val hostCoords = childHostCoordinates
            if (containerCoords != null && hostCoords != null) {
                try {
                    // Use localBoundingBoxOf so that values are in container-local layout
                    // pixels, completely independent of graphicsLayer zoom / translation.
                    val hostInContainer = containerCoords.localBoundingBoxOf(hostCoords, clipBounds = false)
                    onChildHostMeasured(
                        node.nodeId,
                        resolveContainerChildHostMetrics(
                            containerWidth = containerCoords.size.width.toDouble(),
                            containerHeight = containerCoords.size.height.toDouble(),
                            measuredHostLeft = hostInContainer.left.toDouble(),
                            measuredHostTop = hostInContainer.top.toDouble(),
                            measuredHostRight = hostInContainer.right.toDouble(),
                            measuredHostBottom = hostInContainer.bottom.toDouble(),
                            childNodes = childNodes,
                            contentOriginX = contentOriginX,
                            contentOriginY = contentOriginY
                        )
                    )
                } catch (_: IllegalStateException) {
                    // Coordinates may be detached if composition order causes callbacks to fire
                    // out of sync; silently defer until both are stable.
                }
            }
        }
        // Sample on the next frame so the coordinate pair is attached before measuring.
        // A new onChildHostMeasured (new state id or layout key) starts with no metrics, so they are
        // reported again, although the coordinates are the same objects as before. They are also reported
        // again for each new layout: the layout runs in the background, so a new layout key can be reported
        // from the previous layout (for example of the collapsed container) while the new one is computed.
        LaunchedEffect(containerCoordinates, childHostCoordinates, onChildHostMeasured, reportMetricsFor) {
            val containerCoords = containerCoordinates
            val hostCoords = childHostCoordinates
            if (reportMetricsFor != null && containerCoords != null && hostCoords != null) {
                withFrameNanos { }
                reportContainerMetrics()
            }
        }
        val children: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        childHostCoordinates = coordinates
                    }
            ) {
                if (childNodes.isNotEmpty()) {
                    Layout(
                        content = {
                            childNodes.forEach { childNode ->
                                NodeContent(
                                    node = childNode,
                                    state = state,
                                    layoutResult = layoutResult,
                                    nodeCoordinatesById = nodeCoordinatesById,
                                    onChildHostMeasured = onChildHostMeasured,
                                    onContentMeasured = onContentMeasured,
                                    nodeAlpha = nodeAlpha,
                                    reportMetricsFor = reportMetricsFor,
                                )
                            }
                        },
                        measurePolicy = withoutIntrinsicSize(composeLayout(node.nodeId, childNodes, contentOriginX, contentOriginY, onContentMeasured))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(alphaModifier)
                .onGloballyPositioned { coordinates ->
                    containerCoordinates = coordinates
                    nodeCoordinatesById[node.nodeId] = coordinates
                }
        ) {
            if (content != null) content(children) else MissingNodeContent(node.nodeId)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(alphaModifier)
                .onGloballyPositioned { coordinates ->
                    nodeCoordinatesById[node.nodeId] = coordinates
                }
        ) {
            if (content != null) content {} else MissingNodeContent(node.nodeId)
        }
    }
}

internal fun resolveContainerChildHostMetrics(
    containerWidth: Double,
    containerHeight: Double,
    measuredHostLeft: Double,
    measuredHostTop: Double,
    measuredHostRight: Double,
    measuredHostBottom: Double,
    childNodes: List<CompoundNodeLayout>,
    contentOriginX: Double,
    contentOriginY: Double
): ContainerChildHostMetrics {
    val originX = max(0.0, measuredHostLeft)
    val originY = max(0.0, measuredHostTop)
    val measuredRight = max(originX, measuredHostRight)
    val measuredBottom = max(originY, measuredHostBottom)

    // Keep measured outer insets (for example container padding) stable.
    // Child overflow must expand container size, not erase measured padding.
    val measuredInsetRight = max(0.0, containerWidth - measuredRight)
    val measuredInsetBottom = max(0.0, containerHeight - measuredBottom)

    return ContainerChildHostMetrics(
        originX = originX,
        originY = originY,
        insetRight = measuredInsetRight,
        insetBottom = measuredInsetBottom
    )
}

/**
 * @param childWidths measured width of each child, in the same order as [childNodes]
 */
/**
 * Containers first, then leaves, then ports; a port on a port after the port it is on,
 * so each is drawn on top of what it sits on.
 */
private fun drawOrder(node: CompoundNodeLayout, layoutResult: CompoundLayoutResult): Int {
    if (node.borderHostId == null) return if (node.isContainer) 0 else 1
    var depth = 0
    var host: String? = node.borderHostId
    while (host != null && depth < 64) {
        depth++
        host = layoutResult.nodeLayoutsById[host]?.borderHostId
    }
    return 1 + depth
}

internal fun requiredChildHostWidth(
    childWidths: List<Double>,
    childNodes: List<CompoundNodeLayout>,
    contentOriginX: Double
): Double = childNodes.mapIndexed { index, childNode ->
    max(0.0, childNode.globalX - contentOriginX) + childWidths[index]
}.maxOrNull() ?: 0.0

/**
 * @param childHeights measured height of each child, in the same order as [childNodes]
 */
internal fun requiredChildHostHeight(
    childHeights: List<Double>,
    childNodes: List<CompoundNodeLayout>,
    contentOriginY: Double
): Double = childNodes.mapIndexed { index, childNode ->
    max(0.0, childNode.globalY - contentOriginY) + childHeights[index]
}.maxOrNull() ?: 0.0

internal fun zoomAboutPointer(
    state: GraphLayoutViewState,
    pointer: Offset,
    targetZoom: Float
): GraphLayoutViewState {
    val clampedZoom = targetZoom.coerceIn(minViewZoom, maxViewZoom)
    if (clampedZoom == state.zoom) return state

    val zoomFactor = clampedZoom / state.zoom
    val offsetAfterZoom = pointer - ((pointer - state.offset) * zoomFactor)
    return GraphLayoutViewState(zoom = clampedZoom, offset = offsetAfterZoom)
}

internal fun applyPanAndZoomGesture(
    state: GraphLayoutViewState,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float
): GraphLayoutViewState {
    val zoomed = zoomAboutPointer(state, centroid, state.zoom * zoomChange)
    return zoomed.copy(offset = zoomed.offset + pan)
}

internal fun zoomFactorFromScrollDelta(deltaY: Float, density: Float): Float {
    val safeDensity = density.coerceAtLeast(0.0001f)
    val boundedDelta = (deltaY / safeDensity).coerceIn(-10f, 10f)
    return exp(-boundedDelta * scrollZoomSensitivity)
}

/** A layout as it was shown: the layout, its top left corner (in the layout's pixels) and the view state. */
internal data class ShownPlacement(
    val layout: CompoundLayoutResult,
    val minX: Double,
    val minY: Double,
    val viewState: GraphLayoutViewState,
    val nodeAlpha: Map<String, Float> = emptyMap(),
    val edgeAlpha: Map<String, Float> = emptyMap(),
)

private class ShownPlacementHolder {
    var last: ShownPlacement? = null
}

/**
 * For a new layout, the view state [to] that keeps the focus node still, replacing [from];
 * [screen] is where the focus node's centre stays on the screen.
 */
internal data class FocusPlacement(val from: GraphLayoutViewState, val to: GraphLayoutViewState, val screen: Offset)

/** Longest a transition waits for the nodes of the new layout to be measured. */
private const val MEASURE_WAIT_MILLIS = 1000L

/** A transition from the layout drawn when a new one arrived. */
private class RelayoutTransition(val from: CompoundLayoutResult) {
    val progress = Animatable(0f)
}

/** The top left corner of everything a layout draws (nodes and edge routes). */
internal fun layoutTopLeft(layout: CompoundLayoutResult): Pair<Double, Double> {
    val nodeMinX = layout.nodeLayoutsById.values.minOfOrNull { it.globalX }
    val edgeMinX = layout.edgeRoutesByEdgeId.values.flatten().minOfOrNull { it.first }
    val nodeMinY = layout.nodeLayoutsById.values.minOfOrNull { it.globalY }
    val edgeMinY = layout.edgeRoutesByEdgeId.values.flatten().minOfOrNull { it.second }
    return (listOfNotNull(nodeMinX, edgeMinX).minOrNull() ?: 0.0) to (listOfNotNull(nodeMinY, edgeMinY).minOrNull() ?: 0.0)
}

/**
 * The view state that keeps [focusNodeId]'s centre at the screen position it had in [previous], when
 * [layout] replaces it; null when there is no focus node, no previous layout, the layout is the same, or the
 * node is not in both. A point p of a layout is drawn at offset + zoom * (p - (minX, minY)).
 */
internal fun focusPlacementFor(
    previous: ShownPlacement?,
    layout: CompoundLayoutResult,
    minX: Double,
    minY: Double,
    focusNodeId: String?,
    viewState: GraphLayoutViewState,
): FocusPlacement? {
    if (focusNodeId == null || previous == null || previous.layout === layout) return null
    val old = previous.layout.nodeLayoutsById[focusNodeId] ?: return null
    val new = layout.nodeLayoutsById[focusNodeId] ?: return null
    val pv = previous.viewState
    val screenX = pv.offset.x + pv.zoom * (old.globalX + old.width / 2.0 - previous.minX).toFloat()
    val screenY = pv.offset.y + pv.zoom * (old.globalY + old.height / 2.0 - previous.minY).toFloat()
    val zoom = viewState.zoom
    val offset = Offset(
        screenX - zoom * (new.globalX + new.width / 2.0 - minX).toFloat(),
        screenY - zoom * (new.globalY + new.height / 2.0 - minY).toFloat(),
    )
    return FocusPlacement(viewState, GraphLayoutViewState(zoom = zoom, offset = offset), Offset(screenX, screenY))
}

/** Test tags set by [CompoundGraphLayoutView]. */
object CompoundGraphLayoutViewTags {
    /**
     * On the view while a layout is being computed (the first one, or one after new measurements),
     * and while a transition to a new layout is drawn.
     */
    const val LAYING_OUT = "CompoundGraphLayoutView.layingOut"

    /** On the view while [CompoundGraphLayoutView]'s `layoutErrorContent` is shown, because the layout failed. */
    const val LAYOUT_ERROR = "CompoundGraphLayoutView.layoutError"
}

/** What a layout of one graph state is computed from; a change starts a new layout. */
private data class LayoutInputs(
    val layoutKey: Any,
    val containerMetrics: Map<String, ContainerChildHostMetrics>,
    val contentSizes: Map<String, Pair<Double, Double>>,
    val density: Float,
)

/** A copy of what the layout engine reads, so the layout can run while the caller changes the graph. */
private fun GraphLayoutCompoundGraphState.copyForLayout(): GraphLayoutCompoundGraphState =
    GraphLayoutCompoundGraphState(id = id, routing = routing, root = root.copyForLayout())

private fun GraphLayoutCompoundGraph.copyForLayout(): GraphLayoutCompoundGraph = copy(
    nodes = nodes.toMutableMap(),
    edges = edges.toMutableMap(),
    children = children.mapValuesTo(mutableMapOf()) { (_, child) -> child.copyForLayout() },
)

/**
 * Renders a [GraphLayoutCompoundGraphState] using the [CompoundLayoutEngine].
 *
 * Node content is resolved from [GraphLayoutCompoundGraphState.nodeContentById] by stable node ID.
 * Edge rendering is resolved from [GraphLayoutCompoundGraphState.edgeContentById] by stable edge ID.
 *
 * **Missing content contract:**
 * - If a node ID has no entry in [GraphLayoutCompoundGraphState.nodeContentById], a red
 *   [MissingNodeContent] placeholder is rendered in the node's bounds. Content must be provided.
 * - If an edge ID has no entry in [GraphLayoutCompoundGraphState.edgeContentById], a red
 *   [MissingEdgeContent] label is rendered at the edge midpoint. Content must be provided.
 * - If an edge ID maps to [GraphLayoutEdgeContent] with no symbols/texts, that is an intentional
 *   plain line and no error is shown.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CompoundGraphLayoutView(
    state: GraphLayoutCompoundGraphState,
    viewState: GraphLayoutViewState,
    updateView: (offset: Offset, zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * An arbitrary key that, when changed, forces the layout to be recomputed.
     * Use this to invalidate the cached layout after toggling collapse state.
     */
    layoutKey: Any = Unit,
    /** Colours for the edge lines, placeholders and overlays the view draws itself. */
    colors: CompoundGraphLayoutColors = CompoundGraphLayoutColors(),
    /**
     * Where to put the graph when it is first laid out. Other than [InitialViewPlacement.AS_GIVEN],
     * the view calls [updateView] once the graph is measured, and again while measurement changes the
     * layout, until the user pans or zooms. It is done again for a new [state] id.
     */
    initialView: InitialViewPlacement = InitialViewPlacement.AS_GIVEN,
    /**
     * When true, an edge label that would overlap another edge label slides along its own edge to the
     * nearest place where it overlaps no other label and no node. Labels that do not overlap stay put.
     */
    avoidLabelOverlap: Boolean = true,
    /**
     * For [EdgeRouting.DIRECT]: space left between each end of an edge and the drawn border of its node,
     * so lines and end symbols stop just short of the node.
     */
    directEdgeEndGap: Dp = DEFAULT_DIRECT_EDGE_END_GAP,
    /**
     * Shown, centred in the view, while the first layout of [state] is computed. The layout runs off the
     * UI thread; later layouts (after measuring, or for a new [layoutKey]) keep the previous one on screen.
     */
    layingOutPlaceholder: @Composable () -> Unit = {},
    /**
     * A node that stays where it is on the screen when a new layout replaces the one shown, for example the
     * node the user just expanded or collapsed. When it is in both layouts the view changes the offset (not the
     * zoom) so that its centre stays put, and calls [updateView] with it; this takes the place of [initialView]
     * for that layout. Otherwise, and for the first layout, [initialView] applies as usual.
     */
    focusNodeId: String? = null,
    /**
     * Length of the animated transition when a new layout replaces the one shown (for the same [state] id):
     * nodes move and resize to their new places, new nodes fade in growing out of the node they belong to
     * or are connected to, removed nodes and edges fade out, and with [focusNodeId] the view moves with them
     * so the focus node stays still. Clicks are ignored while it runs. 0 (the default) switches at once.
     */
    relayoutAnimationMillis: Int = 0,
    /**
     * When true, a new layout of the same [state] id starts from the one shown: nodes keep their layers and
     * their order within a layer, unless reordering gives fewer edge crossings (for layered layouts, see
     * [SugiyamaChildLayout]). The layout of a graph then depends on what was shown before. False (the default)
     * lays out each time from scratch.
     */
    stableRelayout: Boolean = false,
    /**
     * Shown, filling the view, instead of the graph when the layout fails, for example because the graph is
     * invalid (see [validateInvariants]). It receives the exception. It is replaced by the graph as soon as a
     * later layout of [state] succeeds. The default shows the message in the view's [CompoundGraphLayoutColors.error] colours.
     */
    layoutErrorContent: @Composable (Throwable) -> Unit = { DefaultLayoutErrorContent(it) },
    /** Called with the exception each time a layout fails, for example to log it; [layoutErrorContent] is shown as well. */
    onLayoutError: (Throwable) -> Unit = {},
) {
    CompositionLocalProvider(LocalCompoundGraphLayoutColors provides colors) {
        CompoundGraphLayoutViewContent(state, viewState, updateView, modifier, layoutKey, colors, initialView, avoidLabelOverlap, directEdgeEndGap, layingOutPlaceholder, focusNodeId, relayoutAnimationMillis, stableRelayout, layoutErrorContent, onLayoutError)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompoundGraphLayoutViewContent(
    state: GraphLayoutCompoundGraphState,
    viewState: GraphLayoutViewState,
    updateView: (offset: Offset, zoom: Float) -> Unit,
    modifier: Modifier,
    layoutKey: Any,
    colors: CompoundGraphLayoutColors,
    initialView: InitialViewPlacement,
    avoidLabelOverlap: Boolean,
    directEdgeEndGap: Dp,
    layingOutPlaceholder: @Composable () -> Unit,
    focusNodeId: String?,
    relayoutAnimationMillis: Int,
    stableRelayout: Boolean,
    layoutErrorContent: @Composable (Throwable) -> Unit,
    onLayoutError: (Throwable) -> Unit,
) {
    var measuredContainerMetrics by remember(state.id, layoutKey) {
        mutableStateOf<Map<String, ContainerChildHostMetrics>>(emptyMap())
    }
    var graphLayerCoordinates by remember(state.id, layoutKey) { mutableStateOf<LayoutCoordinates?>(null) }
    val nodeCoordinatesById = remember(state.id, layoutKey) { mutableStateMapOf<String, LayoutCoordinates>() }
    var measuredContentSizes by remember(state.id, layoutKey) {
        mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap())
    }
    // The engine works in dp. Everything measured here is in pixels, so convert on the way in,
    // and convert the result back to pixels for placing and drawing.
    val density = LocalDensity.current.density
    // The layout is computed off the UI thread (it can take a while for large graphs). Until a new one is
    // ready, the previous layout of this graph stays on screen; before the first one, [layingOutPlaceholder].
    val layoutInputs = LayoutInputs(layoutKey, measuredContainerMetrics, measuredContentSizes, density)
    var computedLayout by remember(state.id) { mutableStateOf<Pair<LayoutInputs, CompoundLayoutResult>?>(null) }
    // The exception of the latest layout, when it failed; null once a layout succeeds.
    var layoutError by remember(state.id) { mutableStateOf<Throwable?>(null) }
    val currentOnLayoutError by rememberUpdatedState(onLayoutError)
    LaunchedEffect(state.id, layoutInputs) {
        // Copied here, on the UI thread, so the caller can change the graph while the layout runs.
        val snapshot = state.copyForLayout()
        val previous = if (stableRelayout) computedLayout?.second else null
        val result = try {
            withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                val pxToDp = 1.0 / density
                // Whole dp: at a fractional density (1.5x) measured sizes come out as fractions of a dp, and
                // tiny differences can tip layout choices (for example which side an edge is routed on).
                // Leaf sizes round up so content always fits.
                CompoundLayoutEngine().also { engine ->
                    // A layout superseded by newer measurements (or a new layout key) stops early.
                    engine.checkCancelled = { job.ensureActive() }
                }.layout(
                    snapshot,
                    layoutInputs.containerMetrics.mapValues { (_, metrics) -> metrics.scaledBy(pxToDp).roundedToWholeDp() },
                    layoutInputs.contentSizes.mapValues { (_, size) -> ceilDp(size.first * pxToDp) to ceilDp(size.second * pxToDp) },
                    previous?.scaledBy(pxToDp)
                ).scaledBy(density.toDouble())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An exception here would end the composition (and the app); show it in the view instead.
            layoutError = e
            currentOnLayoutError(e)
            return@LaunchedEffect
        }
        layoutError = null
        computedLayout = layoutInputs to result
    }
    layoutError?.let { error ->
        Box(modifier = modifier.testTag(CompoundGraphLayoutViewTags.LAYOUT_ERROR)) {
            layoutErrorContent(error)
        }
        return
    }
    val targetLayout = computedLayout?.second ?: run {
        Box(modifier = modifier.testTag(CompoundGraphLayoutViewTags.LAYING_OUT), contentAlignment = Alignment.Center) {
            layingOutPlaceholder()
        }
        return
    }

    // What was drawn last (layout and view state), for the focus node and the start of a transition.
    val shownPlacement = remember(state.id) { ShownPlacementHolder() }
    // Transition from what is drawn to a new layout. A layout that arrives during a transition starts
    // from the positions drawn at that moment.
    val transition = remember(targetLayout) {
        // Nodes and edges that were (nearly) invisible, such as new ones waiting to be measured, are not
        // carried into the next transition: there they fade in again instead of popping up.
        val previous = shownPlacement.last?.let { last ->
            val hiddenNodes = last.nodeAlpha.filterValues { it < 0.5f }.keys
            val hiddenEdges = last.edgeAlpha.filterValues { it < 0.5f }.keys
            if (hiddenNodes.isEmpty() && hiddenEdges.isEmpty()) last.layout else last.layout.copy(
                nodeLayoutsById = last.layout.nodeLayoutsById - hiddenNodes,
                edgeRoutesByEdgeId = last.layout.edgeRoutesByEdgeId - hiddenEdges,
                edgeEndpointsByEdgeId = last.layout.edgeEndpointsByEdgeId - hiddenEdges,
            )
        }
        if (relayoutAnimationMillis > 0 && previous != null && previous !== targetLayout) RelayoutTransition(previous) else null
    }
    // A layout made before its new nodes are measured is only a first guess (the measured one follows), so
    // the transition waits, drawing where it starts, until every node of the new layout has been measured.
    // The new nodes are composed meanwhile (invisible), which is what measures them.
    val newNodesMeasured by rememberUpdatedState(targetLayout.nodeLayoutsById.keys.all { it in measuredContentSizes })
    LaunchedEffect(transition) {
        if (transition == null) return@LaunchedEffect
        withTimeoutOrNull(MEASURE_WAIT_MILLIS) { snapshotFlow { newNodesMeasured }.first { it } }
        transition.progress.animateTo(1f, tween(durationMillis = relayoutAnimationMillis, easing = FastOutSlowInEasing))
    }
    val drawn = transition?.let { interpolateLayouts(it.from, targetLayout, it.progress.value, focusNodeId) } ?: DrawnLayout(targetLayout)
    val animating = drawn.layout !== targetLayout
    val layoutPending = computedLayout?.first != layoutInputs || animating
    // Everything below draws this; measurements are reported against the target layout only.
    val layoutResult = drawn.layout

    // Containers first so their composable content sits behind leaf-node content in z-order.
    // Also paint shallower containers first so parent backgrounds do not cover child containers.
    val rootNodes = remember(layoutResult) {
        layoutResult.nodeLayoutsById.values
            .filter { it.ownerGraphId == state.root.id }
            .sortedWith(
                compareBy<CompoundNodeLayout>(
                    { drawOrder(it, layoutResult) },
                    { it.nodeId }
                )
            )
    }

    val sortedEdgeIds = remember(layoutResult) {
        layoutResult.edgeRoutesByEdgeId.keys.sorted()
    }
    // On a bus the symbol at the shared node is drawn once, by the bus's first edge.
    // Edge id to true when its start symbol is hidden, false when its end symbol is.
    val hiddenBusSymbolEnds = remember(layoutResult) {
        layoutResult.edgeBuses.flatMap { bus ->
            bus.edgeIds.drop(1).mapNotNull { edgeId ->
                val ends = layoutResult.edgeEndpointsByEdgeId[edgeId] ?: return@mapNotNull null
                edgeId to (ends.first == bus.nodeId)
            }
        }.toMap()
    }
    val tessellatedGraphIds = remember(state.id, layoutKey) {
        fun collect(graph: GraphLayoutCompoundGraph): List<String> {
            val include = if (graph.childLayout == TessellateChildLayout && !graph.isCollapsed) {
                listOf(graph.id)
            } else {
                emptyList()
            }
            val children = graph.children.values
                .filter { !it.isCollapsed }
                .sortedBy { it.id }
                .flatMap { collect(it) }
            return include + children
        }
        collect(state.root)
    }
    val contentOrigins = remember(layoutResult) {
        layoutResult.graphLayoutsById.values
            .filter { it.containerNodeId != null }
            .sortedBy { it.graphId }
            .map { it.globalOffsetX to it.globalOffsetY }
    }

    val minX = remember(layoutResult) { layoutTopLeft(layoutResult).first }
    val minY = remember(layoutResult) { layoutTopLeft(layoutResult).second }
    val maxX = remember(layoutResult) {
        val nodeMax = layoutResult.nodeLayoutsById.values.maxOfOrNull { it.globalX + it.width }
        val edgeMax = layoutResult.edgeRoutesByEdgeId.values
            .flatten()
            .maxOfOrNull { it.first }
        listOfNotNull(nodeMax, edgeMax).maxOrNull() ?: 100.0
    }
    val maxY = remember(layoutResult) {
        val nodeMax = layoutResult.nodeLayoutsById.values.maxOfOrNull { it.globalY + it.height }
        val edgeMax = layoutResult.edgeRoutesByEdgeId.values
            .flatten()
            .maxOfOrNull { it.second }
        listOfNotNull(nodeMax, edgeMax).maxOrNull() ?: 100.0
    }
    val totalWidth = (maxX - minX).coerceAtLeast(100.0)
    val totalHeight = (maxY - minY).coerceAtLeast(100.0)
    val globalToViewportOffsetX = -minX
    val globalToViewportOffsetY = -minY

    // Keyed like measuredContainerMetrics, so a new layout key writes to the new state, not the first one.
    val onChildHostMeasured: (String, ContainerChildHostMetrics) -> Unit = remember(state.id, layoutKey) {
        { nodeId, metrics ->
            if (measuredContainerMetrics[nodeId] != metrics) {
                measuredContainerMetrics = measuredContainerMetrics + (nodeId to metrics)
            }
        }
    }

    val currentTargetLayout by rememberUpdatedState(targetLayout)
    val onContentMeasured: (String, Pair<Double, Double>) -> Unit = remember(state.id, layoutKey) {
        { nodeId, size ->
            // Nodes still drawn while they fade out are not in the graph any more.
            if (nodeId in currentTargetLayout.nodeLayoutsById && measuredContentSizes[nodeId] != size) {
                measuredContentSizes = measuredContentSizes + (nodeId to size)
            }
        }
    }

    // Focus node: when a new layout replaces the one shown, the offset that keeps the focus node's centre
    // where it was on the screen. It is drawn with at once, and passed to updateView; it is used until the
    // caller's view state changes.
    val focusPlacement: FocusPlacement? = remember(targetLayout) {
        val (targetMinX, targetMinY) = layoutTopLeft(targetLayout)
        focusPlacementFor(shownPlacement.last, targetLayout, targetMinX, targetMinY, focusNodeId, viewState)
    }
    val drawnViewState = when {
        // Used until the caller's view state changes, other than to the offset passed to updateView.
        focusPlacement == null || (viewState != focusPlacement.from && viewState != focusPlacement.to) -> viewState
        !animating -> focusPlacement.to
        // During the transition the offset moves with the drawn layout, so the focus node stays still.
        else -> layoutResult.nodeLayoutsById[focusNodeId]?.let { f ->
            val zoom = focusPlacement.to.zoom
            GraphLayoutViewState(
                zoom = zoom,
                offset = Offset(
                    focusPlacement.screen.x - zoom * (f.globalX + f.width / 2.0 - minX).toFloat(),
                    focusPlacement.screen.y - zoom * (f.globalY + f.height / 2.0 - minY).toFloat(),
                )
            )
        } ?: focusPlacement.to
    }
    SideEffect { shownPlacement.last = ShownPlacement(layoutResult, minX, minY, drawnViewState, drawn.nodeAlpha, drawn.edgeAlpha) }
    LaunchedEffect(focusPlacement) {
        if (focusPlacement != null && focusPlacement.to != focusPlacement.from) updateView(focusPlacement.to.offset, focusPlacement.to.zoom)
    }

    val stateForGestures by rememberUpdatedState(drawnViewState)

    // Initial placement: follows the layout until the user moves the view.
    // The gesture handlers are not restarted for a new state, so they record which graph was moved.
    var movedStateId by remember { mutableStateOf<String?>(null) }
    val currentStateId by rememberUpdatedState(state.id)
    val userMovedView = movedStateId == state.id
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val currentUpdateView by rememberUpdatedState(updateView)
    // Placed for the layout being shown or moved to, not for each drawn step of a transition.
    val (graphWidth, graphHeight) = remember(targetLayout) {
        val (tMinX, tMinY) = layoutTopLeft(targetLayout)
        val tMaxX = listOfNotNull(
            targetLayout.nodeLayoutsById.values.maxOfOrNull { it.globalX + it.width },
            targetLayout.edgeRoutesByEdgeId.values.flatten().maxOfOrNull { it.first }
        ).maxOrNull() ?: 100.0
        val tMaxY = listOfNotNull(
            targetLayout.nodeLayoutsById.values.maxOfOrNull { it.globalY + it.height },
            targetLayout.edgeRoutesByEdgeId.values.flatten().maxOfOrNull { it.second }
        ).maxOrNull() ?: 100.0
        (tMaxX - tMinX).toFloat() to (tMaxY - tMinY).toFloat()
    }
    LaunchedEffect(state.id, initialView, targetLayout, viewportSize, userMovedView) {
        // The focus node decides where this layout goes.
        if (focusPlacement != null) return@LaunchedEffect
        if (userMovedView || viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        val placed = initialViewState(
            initialView, graphWidth, graphHeight,
            viewportSize.width.toFloat(), viewportSize.height.toFloat()
        ) ?: return@LaunchedEffect
        if (placed != stateForGestures) currentUpdateView(placed.offset, placed.zoom)
    }

    val edgeContentById = state.edgeContentById.toMap()
    val edgeTextPlacements = sortedEdgeIds.flatMap { edgeId ->
        val content = edgeContentById[edgeId]
        when {
            content == null -> listOf(EdgeTextPlacement(edgeId = edgeId, position = EdgeContentPosition.MIDDLE, isMissing = true))
            else -> EdgeContentPosition.entries.mapNotNull { position ->
                val textsAtPosition = content.texts.filter { it.position == position }
                if (textsAtPosition.isEmpty()) null else EdgeTextPlacement(edgeId = edgeId, position = position, texts = textsAtPosition)
            }
        }
    }

    Box(
        modifier = modifier
            .then(if (layoutPending) Modifier.testTag(CompoundGraphLayoutViewTags.LAYING_OUT) else Modifier)
            .clip(RectangleShape)
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val sg = stateForGestures
                    val next = applyPanAndZoomGesture(
                        state = sg,
                        centroid = centroid,
                        pan = pan,
                        zoomChange = zoom
                    )
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
                            // The scroll zooms this view, so a scrolling parent must not scroll as well.
                            event.changes.forEach { it.consume() }
                            val scale = zoomFactorFromScrollDelta(
                                deltaY = change.scrollDelta.y,
                                // this pointer input's density, which follows the current one
                                density = this.density
                            )
                            val next = zoomAboutPointer(
                                state = stateForGestures,
                                pointer = change.position,
                                targetZoom = stateForGestures.zoom * scale
                            )
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
                    translationX = drawnViewState.offset.x,
                    translationY = drawnViewState.offset.y,
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        transformOrigin = TransformOrigin(0f, 0f),
                        scaleX = drawnViewState.zoom,
                        scaleY = drawnViewState.zoom,
                    )
                    // Taken inside the pan and zoom layers, so node bounds measured against it are in the
                    // same space the edge canvas draws in. Outside them, the view offset and zoom would be
                    // applied to DIRECT edge ends twice.
                    .onGloballyPositioned { coordinates ->
                        graphLayerCoordinates = coordinates
                    }
            ) {
                // ── Layer 1: Node content (recursive) ───────────────────────────
                // Container composables receive their children as a composable argument,
                // giving them full z-order control (e.g. overlays drawn after children).
                Layout(
                    content = {
                        rootNodes.forEach { node ->
                            NodeContent(
                                node = node,
                                state = state,
                                layoutResult = layoutResult,
                                nodeCoordinatesById = nodeCoordinatesById,
                                onChildHostMeasured = onChildHostMeasured,
                                onContentMeasured = onContentMeasured,
                                nodeAlpha = drawn.nodeAlpha,
                                reportMetricsFor = if (animating) null else targetLayout,
                            )
                        }
                    },
                    measurePolicy = composeLayout("root", rootNodes, -globalToViewportOffsetX, -globalToViewportOffsetY, onContentMeasured)
                )

                // ── Layer 2: Edge route lines ────────────────────────────────────
                Canvas(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val viewportWidth = if (constraints.hasBoundedWidth) {
                                constraints.maxWidth
                            } else {
                                totalWidth.roundToInt().coerceAtLeast(constraints.minWidth)
                            }
                            val viewportHeight = if (constraints.hasBoundedHeight) {
                                constraints.maxHeight
                            } else {
                                totalHeight.roundToInt().coerceAtLeast(constraints.minHeight)
                            }
                            val placeable = measurable.measure(constraints)
                            layout(viewportWidth, viewportHeight) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) {
                    // Draw tessellation separators once per tessellated graph to avoid doubled region borders.
                    tessellatedGraphIds.forEach { graphId ->
                        val nodesInGraph = layoutResult.nodeLayoutsById.values
                            .filter { it.ownerGraphId == graphId }
                        if (nodesInGraph.size >= 2) {
                            val minGraphX = nodesInGraph.minOf { it.globalX }
                            val maxGraphX = nodesInGraph.maxOf { it.globalX + it.width }
                            val minGraphY = nodesInGraph.minOf { it.globalY }
                            val maxGraphY = nodesInGraph.maxOf { it.globalY + it.height }

                            val xDividers = nodesInGraph
                                .map { it.globalX + it.width }
                                .distinct()
                                .sorted()
                                .filter { it > minGraphX && it < maxGraphX }
                            val yDividers = nodesInGraph
                                .map { it.globalY + it.height }
                                .distinct()
                                .sorted()
                                .filter { it > minGraphY && it < maxGraphY }

                            xDividers.forEach { x ->
                                val px = (x + globalToViewportOffsetX).toFloat()
                                drawLine(
                                    color = colors.tessellationBorder,
                                    start = Offset(px, (minGraphY + globalToViewportOffsetY).toFloat()),
                                    end = Offset(px, (maxGraphY + globalToViewportOffsetY).toFloat()),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                            yDividers.forEach { y ->
                                val py = (y + globalToViewportOffsetY).toFloat()
                                drawLine(
                                    color = colors.tessellationBorder,
                                    start = Offset((minGraphX + globalToViewportOffsetX).toFloat(), py),
                                    end = Offset((maxGraphX + globalToViewportOffsetX).toFloat(), py),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }

                    sortedEdgeIds.forEach { edgeId ->
                        val route = layoutResult.edgeRoutesByEdgeId[edgeId] ?: return@forEach

                        // Get the actual rendered node positions to properly attach edge endpoints
                        val endpoints = layoutResult.edgeEndpointsByEdgeId[edgeId]
                        // Orthogonal routes already end on the node borders; moving their end points
                        // to the rendered bounds would add diagonal segments.
                        val clippedRoute = if (state.routing == EdgeRouting.DIRECT && endpoints != null && graphLayerCoordinates != null) {
                            val (sourceNodeId, targetNodeId) = endpoints
                            val sourceCoords = nodeCoordinatesById[sourceNodeId]
                            val targetCoords = nodeCoordinatesById[targetNodeId]

                            if (sourceCoords != null && targetCoords != null) {
                                // nodeBoundsInLayer returns bounds in canvas/viewport space.
                                // Route points are in layout global space.
                                // viewport = global + globalToViewportOffset  =>  global = viewport - globalToViewportOffset
                                val sourceBoundsViewport = nodeBoundsInLayer(graphLayerCoordinates, sourceCoords)
                                val targetBoundsViewport = nodeBoundsInLayer(graphLayerCoordinates, targetCoords)

                                when {
                                    sourceBoundsViewport == null || targetBoundsViewport == null -> route
                                    route.size < 2 -> route
                                    else -> {
                                        // Convert measured viewport bounds to layout global space so they
                                        // are in the same coordinate space as the stored route points.
                                        val srcLeft = (sourceBoundsViewport.left - globalToViewportOffsetX).toDouble()
                                        val srcTop = (sourceBoundsViewport.top - globalToViewportOffsetY).toDouble()
                                        val srcRight = (sourceBoundsViewport.right - globalToViewportOffsetX).toDouble()
                                        val srcBottom = (sourceBoundsViewport.bottom - globalToViewportOffsetY).toDouble()

                                        val dstLeft = (targetBoundsViewport.left - globalToViewportOffsetX).toDouble()
                                        val dstTop = (targetBoundsViewport.top - globalToViewportOffsetY).toDouble()
                                        val dstRight = (targetBoundsViewport.right - globalToViewportOffsetX).toDouble()
                                        val dstBottom = (targetBoundsViewport.bottom - globalToViewportOffsetY).toDouble()

                                        // Use the actual measured node centers as the origin of each endpoint ray.
                                        // This ensures the edge exits/enters the correct boundary regardless of
                                        // any difference between layout-predicted and Compose-rendered positions.
                                        val srcCenter = (srcLeft + srcRight) / 2.0 to (srcTop + srcBottom) / 2.0
                                        val dstCenter = (dstLeft + dstRight) / 2.0 to (dstTop + dstBottom) / 2.0

                                        // Direction for start: from source center toward first waypoint (or target)
                                        val startToward = if (route.size > 2) route[1] else dstCenter
                                        // Direction for end: from target center toward last waypoint (or source)
                                        val endToward = if (route.size > 2) route[route.size - 2] else srcCenter

                                        val adjustedStart = rectExitPoint(
                                            center = srcCenter, toward = startToward,
                                            rectLeft = srcLeft, rectTop = srcTop,
                                            rectRight = srcRight, rectBottom = srcBottom
                                        )
                                        val adjustedEnd = rectExitPoint(
                                            center = dstCenter, toward = endToward,
                                            rectLeft = dstLeft, rectTop = dstTop,
                                            rectRight = dstRight, rectBottom = dstBottom
                                        )

                                        // Preserve intermediate waypoints; only replace endpoints
                                        when {
                                            route.size == 2 -> listOf(adjustedStart, adjustedEnd)
                                            else -> listOf(adjustedStart) + route.drop(1).dropLast(1) + listOf(adjustedEnd)
                                        }
                                    }
                                }
                            } else {
                                route
                            }
                        } else {
                            route
                        }

                        val drawRoute = if (state.routing == EdgeRouting.DIRECT) {
                            withEndGaps(clippedRoute, directEdgeEndGap.toPx().toDouble())
                        } else {
                            clippedRoute
                        }

                        val edgeContent = edgeContentById[edgeId]
                        val edgeAlpha = drawn.edgeAlpha[edgeId] ?: 1f
                        if (drawRoute.size >= 2) {
                            drawPath(
                                alpha = edgeAlpha,
                                path = Path().apply {
                                    drawRoute.forEachIndexed { index, point ->
                                        val x = (point.first + globalToViewportOffsetX).toFloat()
                                        val y = (point.second + globalToViewportOffsetY).toFloat()
                                        if (0 == index) moveTo(x, y) else lineTo(x, y)
                                    }
                                },
                                color = edgeContent?.lineColor ?: colors.edgeLine,
                                style = Stroke(
                                    width = (edgeContent?.lineWidth ?: GraphLayoutEdgeContent.DEFAULT_LINE_WIDTH).dp.toPx(),
                                    pathEffect = edgeContent?.lineDash?.let { dash ->
                                        PathEffect.dashPathEffect(FloatArray(dash.size) { dash[it].dp.toPx() }, phase = 0f)
                                    }
                                )
                            )
                        }

                        // Draw debug endpoint circles if enabled
                        if (state.showDebugOverlay.value && drawRoute.size >= 2) {
                            // Draw circle at start endpoint
                            val startX = (drawRoute[0].first + globalToViewportOffsetX).toFloat()
                            val startY = (drawRoute[0].second + globalToViewportOffsetY).toFloat()
                            drawCircle(color = colors.debugEdgeStart, radius = 4.dp.toPx(), center = Offset(startX, startY))
                            // Draw circle at end endpoint
                            val endX = (drawRoute[drawRoute.lastIndex].first + globalToViewportOffsetX).toFloat()
                            val endY = (drawRoute[drawRoute.lastIndex].second + globalToViewportOffsetY).toFloat()
                            drawCircle(color = colors.debugEdgeEnd, radius = 4.dp.toPx(), center = Offset(endX, endY))
                        }

                        if (edgeContent != null) {
                            edgeContent.startSymbol?.takeIf { hiddenBusSymbolEnds[edgeId] != true }?.let { symbol ->
                                drawEdgeSymbol(
                                    symbol = symbol,
                                    anchor = routeAnchor(drawRoute, EdgeContentPosition.START),
                                    globalToViewportOffsetX = globalToViewportOffsetX,
                                    globalToViewportOffsetY = globalToViewportOffsetY,
                                    scale = density
                                ) { path, color, stroke ->
                                    if (stroke == null) {
                                        drawPath(path = path, color = color ?: Color.Transparent, alpha = edgeAlpha)
                                    } else {
                                        drawPath(path = path, color = color ?: Color.Transparent, style = stroke, alpha = edgeAlpha)
                                    }
                                }
                            }
                            edgeContent.endSymbol?.takeIf { hiddenBusSymbolEnds[edgeId] != false }?.let { symbol ->
                                drawEdgeSymbol(
                                    symbol = symbol,
                                    anchor = routeAnchor(drawRoute, EdgeContentPosition.END),
                                    globalToViewportOffsetX = globalToViewportOffsetX,
                                    globalToViewportOffsetY = globalToViewportOffsetY,
                                    scale = density
                                ) { path, color, stroke ->
                                    if (stroke == null) {
                                        drawPath(path = path, color = color ?: Color.Transparent, alpha = edgeAlpha)
                                    } else {
                                        drawPath(path = path, color = color ?: Color.Transparent, style = stroke, alpha = edgeAlpha)
                                    }
                                }
                            }
                        }
                    }

                    // Overlays: drawn once, after all the edges.
                    if (state.showContentOrigins.value) {
                        contentOrigins.forEach { origin ->
                            val ox = (origin.first + globalToViewportOffsetX).toFloat()
                            val oy = (origin.second + globalToViewportOffsetY).toFloat()
                            val r = 6.dp.toPx()
                            drawLine(
                                color = colors.contentOrigin,
                                start = Offset(ox - r, oy),
                                end = Offset(ox + r, oy),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = colors.contentOrigin,
                                start = Offset(ox, oy - r),
                                end = Offset(ox, oy + r),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }

                    if (state.showDebugOverlay.value) {
                        // ── DEBUG: Draw measured node bounds ────────────────────────
                        val debugColors = colors.debugNodeBounds
                        nodeCoordinatesById.entries.sortedBy { it.key }.forEachIndexed { index, (_, coordinates) ->
                            val rect = nodeBoundsInLayer(graphLayerCoordinates, coordinates) ?: return@forEachIndexed
                            val color = debugColors[index % debugColors.size]
                            drawRect(
                                color = color,
                                topLeft = Offset(rect.left, rect.top),
                                size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
                // ── Layer 3: Edge text overlays ───────────────────────────────────
                if (edgeTextPlacements.isNotEmpty()) {
                    Layout(
                        content = {
                            edgeTextPlacements.forEach { placement ->
                                val labelAlpha = drawn.edgeAlpha[placement.edgeId] ?: 1f
                                if (placement.isMissing) {
                                    MissingEdgeContent(placement.edgeId)
                                } else {
                                    Column(if (labelAlpha < 1f) Modifier.graphicsLayer(alpha = labelAlpha) else Modifier) {
                                        placement.texts.forEach { text ->
                                            EdgeTextBubble(text)
                                        }
                                    }
                                }
                            }
                        }
                    ) { measurables, constraints ->
                        val viewportWidth = if (constraints.hasBoundedWidth) {
                            constraints.maxWidth
                        } else {
                            totalWidth.roundToInt().coerceAtLeast(constraints.minWidth)
                        }
                        val viewportHeight = if (constraints.hasBoundedHeight) {
                            constraints.maxHeight
                        } else {
                            totalHeight.roundToInt().coerceAtLeast(constraints.minHeight)
                        }
                        val placeables = measurables.map { m ->
                            m.measure(Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = Constraints.Infinity))
                        }
                        val edgeTextDistance = 18.dp.toPx()
                        val labels = edgeTextPlacements.mapIndexed { index, placement ->
                            val route = layoutResult.edgeRoutesByEdgeId[placement.edgeId].orEmpty()
                            val anchor = routeAnchor(route, placement.position)
                            val textOffset = edgeTextOffset(placement.position, anchor.angleRadians, edgeTextDistance)
                            EdgeLabelToPlace(
                                route = route,
                                position = placement.position,
                                width = placeables[index].width.toFloat(),
                                height = placeables[index].height.toFloat(),
                                defaultCentre = Offset(
                                    anchor.point.first.toFloat() + textOffset.x,
                                    anchor.point.second.toFloat() + textOffset.y
                                )
                            )
                        }
                        val nodeRects = layoutResult.nodeLayoutsById.values
                            .filter { !it.isContainer }
                            .sortedBy { it.nodeId }
                            .map { Rect(it.globalX.toFloat(), it.globalY.toFloat(), (it.globalX + it.width).toFloat(), (it.globalY + it.height).toFloat()) }
                        val centres = placeEdgeLabels(labels, nodeRects, 4.dp.toPx(), edgeTextDistance, avoidLabelOverlap)
                        layout(viewportWidth, viewportHeight) {
                            centres.forEachIndexed { index, centre ->
                                val anchorX = centre.x + globalToViewportOffsetX.toFloat()
                                val anchorY = centre.y + globalToViewportOffsetY.toFloat()
                                placeables[index].placeRelative(
                                    x = (anchorX - (placeables[index].width / 2f)).roundToInt(),
                                    y = (anchorY - (placeables[index].height / 2f)).roundToInt()
                                )
                            }
                        }
                    }
                }
            }
        }
        // While a transition is drawn the nodes are not where the new layout puts them, so clicks are ignored.
        if (animating) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
            )
        }
    }
}

// ── Unit conversion (engine works in dp, the view in pixels) ────────────────

internal fun ContainerChildHostMetrics.scaledBy(f: Double) = ContainerChildHostMetrics(
    originX = originX * f,
    originY = originY * f,
    insetRight = insetRight * f,
    insetBottom = insetBottom * f
)

private fun ContainerChildHostMetrics.roundedToWholeDp() = ContainerChildHostMetrics(
    originX = kotlin.math.round(originX),
    originY = kotlin.math.round(originY),
    insetRight = kotlin.math.round(insetRight),
    insetBottom = kotlin.math.round(insetBottom)
)

/** Rounds up to a whole dp, ignoring floating-point noise (100.0000001 stays 100). */
private fun ceilDp(v: Double): Double = kotlin.math.ceil(v - 1e-6)

private fun CompoundRect.scaledBy(f: Double) = CompoundRect(left * f, top * f, right * f, bottom * f)

private fun Pair<Double, Double>.scaledBy(f: Double) = (first * f) to (second * f)

/** Every length and position in the result multiplied by [f]; ids and structure unchanged. */
internal fun CompoundLayoutResult.scaledBy(f: Double): CompoundLayoutResult = copy(
    localBounds = localBounds.scaledBy(f),
    globalBounds = globalBounds.scaledBy(f),
    localNodePositions = localNodePositions.mapValues { (_, p) -> p.scaledBy(f) },
    globalNodePositions = globalNodePositions.mapValues { (_, p) -> p.scaledBy(f) },
    childResults = childResults.mapValues { (_, r) -> r.scaledBy(f) },
    nodeLayoutsById = nodeLayoutsById.mapValues { (_, n) ->
        n.copy(
            localX = n.localX * f, localY = n.localY * f,
            globalX = n.globalX * f, globalY = n.globalY * f,
            width = n.width * f, height = n.height * f
        )
    },
    graphLayoutsById = graphLayoutsById.mapValues { (_, g) ->
        g.copy(
            localOffsetX = g.localOffsetX * f, localOffsetY = g.localOffsetY * f,
            globalOffsetX = g.globalOffsetX * f, globalOffsetY = g.globalOffsetY * f,
            contentWidth = g.contentWidth * f, contentHeight = g.contentHeight * f,
            centringX = g.centringX * f, shiftY = g.shiftY * f
        )
    },
    edgeRoutesByEdgeId = edgeRoutesByEdgeId.mapValues { (_, route) -> route.map { it.scaledBy(f) } }
)
