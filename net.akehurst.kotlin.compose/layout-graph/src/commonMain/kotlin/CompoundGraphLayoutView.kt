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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas

// ── Error indicator defaults ────────────────────────────────────────────────

private val errorRed = Color(0xFFD32F2F)
private val errorRedBg = Color(0x22D32F2F)

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
 * An edge with an *empty list* is treated as intentionally unlabelled; only a *missing key* triggers this.
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

// ── Compound layout view ────────────────────────────────────────────────────

/**
 * Renders a [GraphLayoutCompoundGraphState] using the [CompoundLayoutEngine].
 *
 * Node content is resolved from [GraphLayoutCompoundGraphState.nodeContentById] by stable node ID.
 * Edge labels are resolved from [GraphLayoutCompoundGraphState.edgeContentById] by stable edge ID.
 *
 * **Missing content contract:**
 * - If a node ID has no entry in [GraphLayoutCompoundGraphState.nodeContentById], a red
 *   [MissingNodeContent] placeholder is rendered in the node's bounds. Content must be provided.
 * - If an edge ID has no entry in [GraphLayoutCompoundGraphState.edgeContentById], a red
 *   [MissingEdgeContent] label is rendered at the edge midpoint. Content must be provided.
 * - If an edge ID maps to an *empty list* that is an intentional "no label" — only the route
 *   line is drawn, with no error.
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
    val layoutResult = remember(state.id, layoutKey) {
        CompoundLayoutEngine().layout(state)
    }

    // Containers first so their composable content sits behind leaf-node content in z-order.
    val sortedNodes = remember(layoutResult) {
        layoutResult.nodeLayoutsById.values
            .sortedWith(compareByDescending<CompoundNodeLayout> { it.isContainer }.thenBy { it.nodeId })
    }

    val sortedEdgeIds = remember(layoutResult) {
        layoutResult.edgeRoutesByEdgeId.keys.sorted()
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

    val density = LocalDensity.current
    val stateForGestures by rememberUpdatedState(viewState)

    // Edges that need a composable label slot: null entry = missing (error), non-empty list = labels.
    // Empty list = intentionally unlabelled, no slot needed.
    val edgesNeedingLabelSlot = sortedEdgeIds.filter { edgeId ->
        val content = state.edgeContentById[edgeId]
        content == null || content.isNotEmpty()
    }

    Box(modifier = modifier.clip(RectangleShape)) {
        Box(
            modifier = Modifier
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
            // ── Layer 1: Node composable content ────────────────────────────
            // Each node is measured to its exact layout-computed bounds and placed at its
            // global position. Missing content shows the error placeholder.
            Layout(
                content = {
                    sortedNodes.forEach { node ->
                        val content = state.nodeContentById[node.nodeId]
                        if (null != content) {
                            content()
                        } else {
                            MissingNodeContent(node.nodeId)
                        }
                    }
                }
            ) { measurables, _ ->
                val placeables = sortedNodes.mapIndexed { index, node ->
                    measurables[index].measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = node.width.roundToInt().coerceAtLeast(1),
                            minHeight = 0,
                            maxHeight = node.height.roundToInt().coerceAtLeast(1)
                        )
                    )
                }
                layout(totalWidth.roundToInt(), totalHeight.roundToInt()) {
                    sortedNodes.forEachIndexed { index, node ->
                        placeables[index].placeRelative(
                            x = (node.globalX + globalToViewportOffsetX).roundToInt(),
                            y = (node.globalY + globalToViewportOffsetY).roundToInt()
                        )
                    }
                }
            }

            // ── Layer 2: Edge route lines ────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(totalWidth.roundToInt(), totalHeight.roundToInt()) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) {
                sortedEdgeIds.forEach { edgeId ->
                    val route = layoutResult.edgeRoutesByEdgeId[edgeId] ?: return@forEach
                    if (route.size >= 2) {
                        drawPath(
                            path = Path().apply {
                                route.forEachIndexed { index, point ->
                                    val x = (point.first + globalToViewportOffsetX).toFloat()
                                    val y = (point.second + globalToViewportOffsetY).toFloat()
                                    if (0 == index) moveTo(x, y) else lineTo(x, y)
                                }
                            },
                            color = Color(0xFF444444),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // ── Layer 3: Edge label composables ──────────────────────────────
            // One composable slot per edge that has labels or a missing-content indicator.
            // Positioned at the midpoint of the edge route.
            if (edgesNeedingLabelSlot.isNotEmpty()) {
                Layout(
                    content = {
                        edgesNeedingLabelSlot.forEach { edgeId ->
                            val content = state.edgeContentById[edgeId]
                            if (null == content) {
                                // Key is absent — must be provided
                                MissingEdgeContent(edgeId)
                            } else {
                                // Non-empty list: wrap slots in a Column so it is one measurable
                                Column {
                                    content.forEach { slot -> slot() }
                                }
                            }
                        }
                    }
                ) { measurables, _ ->
                    val placeables = measurables.map { m ->
                        m.measure(Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = Constraints.Infinity))
                    }
                    layout(totalWidth.roundToInt(), totalHeight.roundToInt()) {
                        edgesNeedingLabelSlot.forEachIndexed { index, edgeId ->
                            val route = layoutResult.edgeRoutesByEdgeId[edgeId]
                            val midPoint = if (route != null && route.isNotEmpty()) {
                                route[route.size / 2]
                            } else {
                                0.0 to 0.0
                            }
                            placeables[index].placeRelative(
                                x = (midPoint.first + globalToViewportOffsetX).roundToInt(),
                                y = (midPoint.second + globalToViewportOffsetY).roundToInt()
                            )
                        }
                    }
                }
            }
        }
    }
}

