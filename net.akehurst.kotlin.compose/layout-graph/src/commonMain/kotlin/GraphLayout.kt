package net.akehurst.kotlin.components.layout.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * change the id if you want the graph to update
 */
data class GraphLayoutGraphState(
    /** identifies the graph, change it to indicate a state change */
    val id: String,
    val routing: EdgeRouting = EdgeRouting.DIRECT
) {
    val nodesById = mutableStateMapOf<String, GraphLayoutNode>()
    val edgesById = mutableStateMapOf<String, GraphLayoutEdge>()
    val nodes get() = nodesById.values.toList()
    val edges get() = edgesById.values.toList()

    fun addNode(nodeId: String, content: @Composable () -> Unit = {}) {
        nodesById[nodeId] = GraphLayoutNode(nodeId, content)
    }

    fun addEdge(edgeId: String, sourceId: String, targetId: String, content: List<@Composable () -> Unit> = emptyList()) {
        edgesById[edgeId] = GraphLayoutEdge(edgeId, sourceId, targetId, content)
    }
}

data class GraphLayoutNode(val id: String) {
    constructor(id: String, content: @Composable () -> Unit) : this(id) {
        this.content = content
    }

    var content: @Composable () -> Unit = {}; private set
}

data class GraphLayoutEdge(
    val id: String,
    val sourceId: String,
    val targetId: String
) {
    constructor(id: String, sourceId: String, targetId: String, content: List<@Composable () -> Unit>) : this(id, sourceId, targetId) {
        this.content = content
    }

    var colour: Color = Color.Black
    var width: Float = 5f

    var content: List<@Composable () -> Unit> = emptyList(); private set
}

data class GraphLayoutViewState(
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero
)

object GraphLayoutIcons {

    private var _Rectilinear: ImageVector? = null
    val Rectilinear: ImageVector
        get() {
            if (_Rectilinear != null) return _Rectilinear!!

            _Rectilinear = ImageVector.Builder(
                name = "Rectilinear",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                ) {//TODO: optimise this
                    moveTo(600f, 680f)
                    horizontalLineToRelative(240f)
                    verticalLineToRelative(240f)
                    horizontalLineToRelative(-240f)
                    verticalLineToRelative(-60f)
                    horizontalLineToRelative(180f)
                    verticalLineToRelative(-120f)
                    horizontalLineToRelative(-120f)
                    verticalLineToRelative(180f)
                    horizontalLineToRelative(-60f)
                    close()
                    moveTo(120f, 420f)
                    horizontalLineToRelative(240f)
                    verticalLineToRelative(240f)
                    horizontalLineToRelative(-240f)
                    verticalLineToRelative(-60f)
                    horizontalLineToRelative(180f)
                    verticalLineToRelative(-120f)
                    horizontalLineToRelative(-120f)
                    verticalLineToRelative(180f)
                    horizontalLineToRelative(-60f)
                    close()
                    moveTo(400f, 100f)
                    horizontalLineToRelative(240f)
                    verticalLineToRelative(240f)
                    horizontalLineToRelative(-240f)
                    verticalLineToRelative(-60f)
                    horizontalLineToRelative(180f)
                    verticalLineToRelative(-120f)
                    horizontalLineToRelative(-120f)
                    verticalLineToRelative(180f)
                    horizontalLineToRelative(-60f)
                    close()
                    moveTo(750f, 680f)
                    lineTo(750f, 510f)
                    lineTo(360f, 510f)
                    verticalLineToRelative(60f)
                    lineTo(690f, 570f)
                    lineTo(690f, 680f)
                    close()
                    moveTo(490f, 340f)
                    lineTo(490f, 510f)
                    lineTo(550f, 510f)
                    lineTo(550f, 340f)
                    close()
                }
            }.build()

            return _Rectilinear!!
        }

    private var _Direct: ImageVector? = null
    val Direct: ImageVector
        get() {
            if (_Direct != null) return _Direct!!

            _Direct = ImageVector.Builder(
                name = "Direct",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(600f, 880f)
                    verticalLineToRelative(-100f)
                    lineTo(320f, 640f)
                    horizontalLineTo(120f)
                    verticalLineToRelative(-240f)
                    horizontalLineToRelative(172f)
                    lineToRelative(108f, -124f)
                    verticalLineToRelative(-196f)
                    horizontalLineToRelative(240f)
                    verticalLineToRelative(240f)
                    horizontalLineTo(468f)
                    lineTo(360f, 444f)
                    verticalLineToRelative(126f)
                    lineToRelative(240f, 120f)
                    verticalLineToRelative(-50f)
                    horizontalLineToRelative(240f)
                    verticalLineToRelative(240f)
                    close()
                    moveTo(480f, 240f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(-80f)
                    horizontalLineToRelative(-80f)
                    close()
                    moveTo(200f, 560f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(-80f)
                    horizontalLineToRelative(-80f)
                    close()
                    moveToRelative(480f, 240f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(-80f)
                    horizontalLineToRelative(-80f)
                    close()
                    moveToRelative(40f, -40f)
                }
            }.build()

            return _Direct!!
        }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphLayoutView(
    graphState: GraphLayoutGraphState,
    viewState: GraphLayoutViewState,
    routing: EdgeRouting,
    updateRouting: (EdgeRouting) -> Unit = {},
    updateView: (offset: Offset, zoom: Float) -> Unit,
    routingSelectorsColor: Color? = Color.LightGray,
    modifier: Modifier = Modifier,
) {
    val stateForGestures by rememberUpdatedState(viewState)
    val layoutData by remember { derivedStateOf {

    } }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            //.fillMaxSize()
            .clip(RectangleShape) // Clip the content to the bounds of the Box
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = viewState.zoom,
                    scaleY = viewState.zoom,
                    translationX = viewState.offset.x,
                    translationY = viewState.offset.y,
                    // rotationZ = rotation
                )
                .pointerInput(Unit) {
                    // Detect pinch-to-zoom, drag, and rotation gestures.
                    detectTransformGestures { centroid, pan, zoom, gestureRotation ->
                        val sg = stateForGestures
                        val panPixels = pan * density.density
                        // Calculate the new scale.
                        // Keep the scale within a reasonable range (e.g., 0.5f to 5f).
                        val newZoom = (sg.zoom * zoom).coerceIn(0.5f, 5f)
                        val newOffset = sg.offset + (panPixels * newZoom)
                        //rotation += gestureRotation

                        updateView.invoke(newOffset, newZoom)
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    // Handle mouse scroll for zooming
                    val delta = event.changes.first().scrollDelta.y
                    val newZoom = (viewState.zoom - delta * 0.1f).coerceIn(0.5f, 5f) // 0.1f is a sensitivity factor
                    updateView.invoke(viewState.offset, newZoom)
                },
        ) {
            var graphLayoutData by remember { mutableStateOf(SugiyamaLayoutData<GraphLayoutNode>()) }
            var actualNodePositions by remember { mutableStateOf<Map<GraphLayoutNode, Pair<Int, Int>>>(emptyMap()) }

            //layout the nodes
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    graphState.nodes.map { node -> node.content() }
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { measurable -> measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

                //TODO: make this somehow derived state
                val nodes = graphState.nodes
                val nodesById = graphState.nodesById
                val sgl = SugiyamaLayout<GraphLayoutNode>(
                    nodeWidth = { node -> placeables[nodes.indexOf(node)].width.toDouble() },
                    nodeHeight = { node -> placeables[nodes.indexOf(node)].height.toDouble() },
                    edgeRouting = routing
                )
                val edges = graphState.edges.mapNotNull {
                    val src = nodesById[it.sourceId]
                    val tgt = nodesById[it.targetId]
                    src?.let{ tgt?.let {
                        SugiyamaEdge(it.id,src, tgt)
                    } }
                }
                graphLayoutData = sgl.layoutGraph(nodes, edges) // cache for use laying out edges

                layout(width = graphLayoutData.totalWidth.roundToInt(), height = graphLayoutData.totalHeight.roundToInt()) {
                    placeables.forEachIndexed { index, placeable ->
                        val node = graphState.nodes[index]
                        val position = graphLayoutData.nodePositions[node]
                        println("** layout ${node.id} at $position")
                        if (null != position) {
                            placeable.placeRelative(x = position.first.roundToInt(), y = position.second.roundToInt())
                        }
                    }
                }
            }

            //layout the edges
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(width = graphLayoutData.totalWidth.roundToInt(), height = graphLayoutData.totalHeight.roundToInt()) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) {
                graphLayoutData.edgeRoutes.forEach { (edge, route) ->
                    val oe = graphState.edgesById[edge.id]
                    drawPath(
                        path = Path().apply {
                            route.forEachIndexed { idx, pt ->
                                if (0 == idx) {
                                    moveTo(pt.first.toFloat(), pt.second.toFloat())
                                } else {
                                    lineTo(pt.first.toFloat(), pt.second.toFloat())
                                }
                            }
                        },
                        color = oe?.colour ?: Color.Black,
                        style = Stroke(oe?.width ?: 5f)
                    )
                }
            }
        }
        if (null != routingSelectorsColor) {
            // after the graph so it is drawn on top
            Row {
                IconButton(onClick = { updateRouting.invoke(EdgeRouting.RECTILINEAR) }) {
                    Icon(imageVector = GraphLayoutIcons.Rectilinear, contentDescription = "Rectilinear", tint = routingSelectorsColor)
                }
                IconButton(onClick = { updateRouting.invoke(EdgeRouting.DIRECT) }) {
                    Icon(imageVector = GraphLayoutIcons.Direct, contentDescription = "Direct", tint = routingSelectorsColor)
                }
            }
        }

    }

}

@Composable
fun ScalableBox(
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) = Box(
    modifier = modifier
        .layout { measurable, constraints ->
            // 1. Measure the inner content with unbounded constraints.
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0
                )
            )

            // 2. Calculate the scaled size based on the content's measured size.
            val newWidth = (placeable.width * scale).roundToInt()
            val newHeight = (placeable.height * scale).roundToInt()

            // 3. Report the new, scaled size to the parent.
            layout(newWidth, newHeight) {
                // 4. Place the inner content, applying the graphicsLayer scale.
                //    We use placeWithLayer to apply the scale and other effects.
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        },
    content = content
)

