package net.akehurst.kotlin.components.layout.graph

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.absoluteValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.Placeable
import kotlin.math.min

// ── Error indicator defaults ────────────────────────────────────────────────

private val errorRed = Color(0xFFD32F2F)
private val errorRedBg = Color(0x22D32F2F)
private val tessellationBorderColor = Color(0xFF3F7ACC)

/**
 * Shown when a node has no entry in [GraphLayoutCompoundGraphState.nodeContentById].
 * Fills the node's allocated bounds so the missing content is immediately visible.
 */
@Composable
private fun MissingNodeContent(nodeId: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(errorRedBg)
            .border(2.dp, errorRed)
            .padding(4.dp)
    ) {
        Text(
            text = "⚠ no content\n$nodeId",
            color = errorRed,
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(errorRedBg)
            .border(1.dp, errorRed)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "⚠ $edgeId",
            color = errorRed,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EdgeTextBubble(text: GraphLayoutEdgeText) {
    text.content()
}

private data class EdgeAnchor(
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

private fun routeAnchor(route: List<Pair<Double, Double>>, position: EdgeContentPosition): EdgeAnchor {
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

private fun edgeTextOffset(position: EdgeContentPosition, angleRadians: Float): Offset {
    val distance = 18f
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

private fun drawEdgeSymbol(
    symbol: GraphLayoutEdgeSymbol,
    anchor: EdgeAnchor,
    globalToViewportOffsetX: Double,
    globalToViewportOffsetY: Double,
    drawPathOnCanvas: (Path, Color?, Stroke?) -> Unit
) {
    if (symbol.pathPoints.isEmpty()) return

    val cosTheta = cos(anchor.angleRadians)
    val sinTheta = sin(anchor.angleRadians)
    val anchorX = (anchor.point.first + globalToViewportOffsetX).toFloat()
    val anchorY = (anchor.point.second + globalToViewportOffsetY).toFloat()
    val transformedPath = Path().apply {
        symbol.pathPoints.forEachIndexed { index, point ->
            val x = anchorX + (point.x * cosTheta) - (point.y * sinTheta)
            val y = anchorY + (point.x * sinTheta) + (point.y * cosTheta)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        if (symbol.isClosed) close()
    }

    if (symbol.isClosed && symbol.fillColor.alpha > 0f) {
        drawPathOnCanvas(transformedPath, symbol.fillColor, null)
    }
    if (symbol.strokeWidth > 0f && symbol.strokeColor.alpha > 0f) {
        drawPathOnCanvas(transformedPath, symbol.strokeColor, Stroke(width = symbol.strokeWidth))
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
    contentOriginY: Double
): MeasurePolicy = MeasurePolicy { measurables, constraints ->
    // println("composeLayout: $nodeId")
    val placeables = childNodes.mapIndexed { i, childNode ->
        val measurable = measurables[i]
        val contentWidth = measurable.maxIntrinsicWidth(constraints.maxWidth)
        val contentHeight = measurable.maxIntrinsicHeight(constraints.maxHeight)
       // println("measure child ${childNode.nodeId}: alg-w=${childNode.width}, alg-h=${childNode.height}")
       // println("measure child ${childNode.nodeId}: nat-w=${contentWidth}, nat-h=${contentHeight}")
        measurable.measure(
            Constraints(
                minWidth = max(contentWidth,childNode.width.roundToInt()),
                maxWidth = max(contentWidth,childNode.width.roundToInt()),
                minHeight = max(contentHeight,childNode.height.roundToInt()),
                maxHeight = max(contentHeight,childNode.height.roundToInt()),
            )
        )
    }
    val requiredChildWidth = requiredChildHostWidth(placeables, childNodes, contentOriginX)
    val requiredChildHeight = requiredChildHostHeight(placeables,childNodes, contentOriginY)

    val layoutWidth = when {
        constraints.hasBoundedWidth -> max(requiredChildWidth.roundToInt(), constraints.maxWidth)
         else -> requiredChildWidth.roundToInt()
    }
    val layoutHeight = when {
        constraints.hasBoundedHeight -> max(requiredChildHeight.roundToInt(), constraints.maxHeight)
        else -> requiredChildHeight.roundToInt()
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
) {
    val content = state.nodeContentById[node.nodeId]
    if (node.isContainer) {
        val containerGraphLayout = layoutResult.graphLayoutsById[node.nodeId]
        val contentOriginX = containerGraphLayout?.globalOffsetX ?: node.globalX
        val contentOriginY = containerGraphLayout?.globalOffsetY ?: node.globalY
        val childNodes = layoutResult.nodeLayoutsById.values
            .filter { it.ownerGraphId == node.nodeId }
            .sortedWith(compareBy({ if (it.isContainer) 0 else 1 }, { it.nodeId }))
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
        LaunchedEffect(containerCoordinates, childHostCoordinates) {
            val containerCoords = containerCoordinates
            val hostCoords = childHostCoordinates
            if (containerCoords != null && hostCoords != null) {
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
                                    onChildHostMeasured = onChildHostMeasured
                                )
                            }
                        },
                        measurePolicy = composeLayout(node.nodeId,childNodes, contentOriginX, contentOriginY)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
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

internal fun requiredChildHostWidth(
    placeables:List<Placeable>,
    childNodes: List<CompoundNodeLayout>,
    contentOriginX: Double
): Double = childNodes.mapIndexed { index, childNode ->
    max(0.0, childNode.globalX - contentOriginX) + placeables[index].width.toDouble()
}.maxOrNull() ?: 0.0

internal fun requiredChildHostHeight(
    placeables:List<Placeable>,
    childNodes: List<CompoundNodeLayout>,
    contentOriginY: Double
): Double = childNodes.mapIndexed { index, childNode ->
    max(0.0, childNode.globalY - contentOriginY) + placeables[index].height.toDouble()
}.maxOrNull() ?: 0.0

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
) {
    var measuredContainerMetrics by remember(state.id, layoutKey) {
        mutableStateOf<Map<String, ContainerChildHostMetrics>>(emptyMap())
    }
    var graphLayerCoordinates by remember(state.id, layoutKey) { mutableStateOf<LayoutCoordinates?>(null) }
    val nodeCoordinatesById = remember(state.id, layoutKey) { mutableStateMapOf<String, LayoutCoordinates>() }
    val layoutResult = remember(state.id, layoutKey, measuredContainerMetrics) {
        CompoundLayoutEngine().layout(state, measuredContainerMetrics)
    }

    // Containers first so their composable content sits behind leaf-node content in z-order.
    // Also paint shallower containers first so parent backgrounds do not cover child containers.
    val rootNodes = remember(layoutResult) {
        layoutResult.nodeLayoutsById.values
            .filter { it.ownerGraphId == state.root.id }
            .sortedWith(
                compareBy<CompoundNodeLayout>(
                    { if (it.isContainer) 0 else 1 },
                    { it.nodeId }
                )
            )
    }

    val sortedEdgeIds = remember(layoutResult) {
        layoutResult.edgeRoutesByEdgeId.keys.sorted()
    }
    val tessellatedGraphIds = remember(state.id, layoutKey) {
        fun collect(graph: GraphLayoutCompoundGraph): List<String> {
            val include = if (graph.childLayout == ChildLayout.TESSELLATE && !graph.isCollapsed) {
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

    val minX = remember(layoutResult) {
        val nodeMin = layoutResult.nodeLayoutsById.values.minOfOrNull { it.globalX }
        val edgeMin = layoutResult.edgeRoutesByEdgeId.values
            .flatten()
            .minOfOrNull { it.first }
        listOfNotNull(nodeMin, edgeMin).minOrNull() ?: 0.0
    }
    val minY = remember(layoutResult) {
        val nodeMin = layoutResult.nodeLayoutsById.values.minOfOrNull { it.globalY }
        val edgeMin = layoutResult.edgeRoutesByEdgeId.values
            .flatten()
            .minOfOrNull { it.second }
        listOfNotNull(nodeMin, edgeMin).minOrNull() ?: 0.0
    }
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

    val onChildHostMeasured: (String, ContainerChildHostMetrics) -> Unit = remember {
        { nodeId, metrics ->
            if (measuredContainerMetrics[nodeId] != metrics) {
                measuredContainerMetrics = measuredContainerMetrics + (nodeId to metrics)
            }
        }
    }

    val density = LocalDensity.current
    val stateForGestures by rememberUpdatedState(viewState)

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

    Box(modifier = modifier.clip(RectangleShape)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    graphLayerCoordinates = coordinates
                }
                .graphicsLayer(
                    scaleX = viewState.zoom,
                    scaleY = viewState.zoom,
                    translationX = viewState.offset.x,
                    translationY = viewState.offset.y,
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val sg = stateForGestures
                        val panPixels = pan * density.density
                        val newZoom = (sg.zoom * zoom).coerceIn(0.5f, 5f)
                        val newOffset = sg.offset + (panPixels * newZoom)
                        updateView(newOffset, newZoom)
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.first().scrollDelta.y
                    val newZoom = (viewState.zoom - delta * 0.1f).coerceIn(0.5f, 5f)
                    updateView(viewState.offset, newZoom)
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
                            onChildHostMeasured = onChildHostMeasured
                        )
                    }
                },
                measurePolicy = composeLayout("root",rootNodes, globalToViewportOffsetX, globalToViewportOffsetY)
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
                                color = tessellationBorderColor,
                                start = Offset(px, (minGraphY + globalToViewportOffsetY).toFloat()),
                                end = Offset(px, (maxGraphY + globalToViewportOffsetY).toFloat()),
                                strokeWidth = 1.5f
                            )
                        }
                        yDividers.forEach { y ->
                            val py = (y + globalToViewportOffsetY).toFloat()
                            drawLine(
                                color = tessellationBorderColor,
                                start = Offset((minGraphX + globalToViewportOffsetX).toFloat(), py),
                                end = Offset((maxGraphX + globalToViewportOffsetX).toFloat(), py),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }

                sortedEdgeIds.forEach { edgeId ->
                    val route = layoutResult.edgeRoutesByEdgeId[edgeId] ?: return@forEach

                    // Get the actual rendered node positions to properly attach edge endpoints
                    val endpoints = layoutResult.edgeEndpointsByEdgeId[edgeId]
                    val drawRoute = if (endpoints != null && graphLayerCoordinates != null) {
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
                                    val srcLeft   = (sourceBoundsViewport.left   - globalToViewportOffsetX).toDouble()
                                    val srcTop    = (sourceBoundsViewport.top    - globalToViewportOffsetY).toDouble()
                                    val srcRight  = (sourceBoundsViewport.right  - globalToViewportOffsetX).toDouble()
                                    val srcBottom = (sourceBoundsViewport.bottom - globalToViewportOffsetY).toDouble()

                                    val dstLeft   = (targetBoundsViewport.left   - globalToViewportOffsetX).toDouble()
                                    val dstTop    = (targetBoundsViewport.top    - globalToViewportOffsetY).toDouble()
                                    val dstRight  = (targetBoundsViewport.right  - globalToViewportOffsetX).toDouble()
                                    val dstBottom = (targetBoundsViewport.bottom - globalToViewportOffsetY).toDouble()

                                    // Use the actual measured node centers as the origin of each endpoint ray.
                                    // This ensures the edge exits/enters the correct boundary regardless of
                                    // any difference between layout-predicted and Compose-rendered positions.
                                    val srcCenter = (srcLeft + srcRight) / 2.0 to (srcTop + srcBottom) / 2.0
                                    val dstCenter = (dstLeft + dstRight) / 2.0 to (dstTop + dstBottom) / 2.0

                                    // Direction for start: from source center toward first waypoint (or target)
                                    val startToward = if (route.size > 2) route[1] else dstCenter
                                    // Direction for end: from target center toward last waypoint (or source)
                                    val endToward   = if (route.size > 2) route[route.size - 2] else srcCenter

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

                    if (drawRoute.size >= 2) {
                        drawPath(
                            path = Path().apply {
                                drawRoute.forEachIndexed { index, point ->
                                    val x = (point.first + globalToViewportOffsetX).toFloat()
                                    val y = (point.second + globalToViewportOffsetY).toFloat()
                                    if (0 == index) moveTo(x, y) else lineTo(x, y)
                                }
                            },
                            color = Color(0xFF444444),
                            style = Stroke(width = 2f)
                        )
                    }

                    // Draw debug endpoint circles if enabled
                    if (state.showDebugOverlay.value && drawRoute.size >= 2) {
                        val debugBlue = Color(0xFF2196F3)
                        val debugRed = Color(0xFFD32F2F)
                        // Draw circle at start endpoint
                        val startX = (drawRoute[0].first + globalToViewportOffsetX).toFloat()
                        val startY = (drawRoute[0].second + globalToViewportOffsetY).toFloat()
                        drawCircle(color = debugBlue, radius = 4f, center = Offset(startX, startY))
                        // Draw circle at end endpoint
                        val endX = (drawRoute[drawRoute.lastIndex].first + globalToViewportOffsetX).toFloat()
                        val endY = (drawRoute[drawRoute.lastIndex].second + globalToViewportOffsetY).toFloat()
                        drawCircle(color = debugRed, radius = 4f, center = Offset(endX, endY))
                    }

                    val edgeContent = edgeContentById[edgeId]
                    if (edgeContent != null) {
                        edgeContent.startSymbol?.let { symbol ->
                            drawEdgeSymbol(
                                symbol = symbol,
                                anchor = routeAnchor(drawRoute, EdgeContentPosition.START),
                                globalToViewportOffsetX = globalToViewportOffsetX,
                                globalToViewportOffsetY = globalToViewportOffsetY
                            ) { path, color, stroke ->
                                if (stroke == null) {
                                    drawPath(path = path, color = color ?: Color.Transparent)
                                } else {
                                    drawPath(path = path, color = color ?: Color.Transparent, style = stroke)
                                }
                            }
                        }
                        edgeContent.endSymbol?.let { symbol ->
                            drawEdgeSymbol(
                                symbol = symbol,
                                anchor = routeAnchor(drawRoute, EdgeContentPosition.END),
                                globalToViewportOffsetX = globalToViewportOffsetX,
                                globalToViewportOffsetY = globalToViewportOffsetY
                            ) { path, color, stroke ->
                                if (stroke == null) {
                                    drawPath(path = path, color = color ?: Color.Transparent)
                                } else {
                                    drawPath(path = path, color = color ?: Color.Transparent, style = stroke)
                                }
                            }
                        }
                    }
                }

                if (state.showContentOrigins.value) {
                    contentOrigins.forEach { origin ->
                        val ox = (origin.first + globalToViewportOffsetX).toFloat()
                        val oy = (origin.second + globalToViewportOffsetY).toFloat()
                        val r = 6f
                        drawLine(
                            color = Color(0xFFD32F2F),
                            start = Offset(ox - r, oy),
                            end = Offset(ox + r, oy),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = Color(0xFFD32F2F),
                            start = Offset(ox, oy - r),
                            end = Offset(ox, oy + r),
                            strokeWidth = 1.5f
                        )
                    }
                }

                if (state.showDebugOverlay.value) {
                    // ── DEBUG: Draw measured node bounds ────────────────────────
                    val debugColors = listOf(
                        Color(0xB36200EE),
                        Color(0xB303DAC6),
                        Color(0xB3FF6D00),
                        Color(0xB32196F3),
                        Color(0xB3D32F2F),
                        Color(0xB34CAF50),
                    )
                    nodeCoordinatesById.entries.sortedBy { it.key }.forEachIndexed { index, (_, coordinates) ->
                        val rect = nodeBoundsInLayer(graphLayerCoordinates, coordinates) ?: return@forEachIndexed
                        val color = debugColors[index % debugColors.size]
                        drawRect(
                            color = color,
                            topLeft = Offset(rect.left, rect.top),
                            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // ── Layer 3: Edge text overlays ───────────────────────────────────
            if (edgeTextPlacements.isNotEmpty()) {
                Layout(
                    content = {
                        edgeTextPlacements.forEach { placement ->
                            if (placement.isMissing) {
                                MissingEdgeContent(placement.edgeId)
                            } else {
                                Column {
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
                    layout(viewportWidth, viewportHeight) {
                        edgeTextPlacements.forEachIndexed { index, placement ->
                            val route = layoutResult.edgeRoutesByEdgeId[placement.edgeId].orEmpty()
                            val anchor = routeAnchor(route, placement.position)
                            val textOffset = edgeTextOffset(placement.position, anchor.angleRadians)
                            val anchorX = (anchor.point.first + globalToViewportOffsetX).toFloat() + textOffset.x
                            val anchorY = (anchor.point.second + globalToViewportOffsetY).toFloat() + textOffset.y
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
}

