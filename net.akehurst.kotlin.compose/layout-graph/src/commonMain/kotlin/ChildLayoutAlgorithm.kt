package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * An edge between two nodes of the same graph level, as seen by a [ChildLayoutAlgorithm].
 * Edges that cross container boundaries are already projected onto the nodes of this level.
 */
data class ChildLayoutEdge(
    val id: String,
    val sourceId: String,
    val targetId: String
)

/**
 * The nodes and edges of one graph level, with the size each node needs.
 */
data class ChildLayoutInput(
    val graphId: String,
    val nodes: List<GraphLayoutCompoundNode>,
    val nodeSizesById: Map<String, Pair<Double, Double>>,
    val edges: List<ChildLayoutEdge>,
    /**
     * How strongly a node should be placed to the left (negative) or right (positive) of its siblings,
     * for example because it connects to a port on that side of the container. Algorithms may ignore it.
     */
    val horizontalPullById: Map<String, Double> = emptyMap()
)

/**
 * Where a [ChildLayoutAlgorithm] placed the nodes of one graph level.
 *
 * [nodeSizesById] may differ from the input sizes (for example, tessellation makes all cells the same size).
 * [edgeRoutesByEdgeId] may leave out edges; the engine routes any missing edge itself.
 * All coordinates are local to the graph level.
 */
data class ChildLayoutResult(
    val nodeSizesById: Map<String, Pair<Double, Double>>,
    val nodeTopLeftById: Map<String, Pair<Double, Double>>,
    val edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>,
    val contentWidth: Double,
    val contentHeight: Double
)

/**
 * Lays out the direct children of one graph level.
 * Set it on [GraphLayoutCompoundGraph.childLayout], or as [CompoundLayoutConfig.defaultChildLayout].
 * Child graphs that do not set one inherit the algorithm of their parent.
 */
fun interface ChildLayoutAlgorithm {
    fun layout(input: ChildLayoutInput): ChildLayoutResult
}

/**
 * Layered (Sugiyama) layout of a graph level.
 * The companion object holds the named presets that used to be `CompoundLayoutProfile`.
 */
data class SugiyamaChildLayout(
    val layerSpacing: Double = 100.0,
    val nodeSpacing: Double = 100.0,
    val edgeRouting: EdgeRouting = EdgeRouting.DIRECT
) : ChildLayoutAlgorithm {

    companion object {
        val DEFAULT = SugiyamaChildLayout(layerSpacing = 100.0, nodeSpacing = 100.0, edgeRouting = EdgeRouting.DIRECT)
        val HIERARCHY_BIASED = SugiyamaChildLayout(layerSpacing = 130.0, nodeSpacing = 110.0, edgeRouting = EdgeRouting.DIRECT)
        val ORTHOGONAL_BIASED = SugiyamaChildLayout(layerSpacing = 110.0, nodeSpacing = 110.0, edgeRouting = EdgeRouting.RECTILINEAR)
        val COMPACT = SugiyamaChildLayout(layerSpacing = 70.0, nodeSpacing = 70.0, edgeRouting = EdgeRouting.DIRECT)
        val CHANNEL_BIASED = SugiyamaChildLayout(layerSpacing = 95.0, nodeSpacing = 130.0, edgeRouting = EdgeRouting.DIRECT)
    }

    override fun layout(input: ChildLayoutInput): ChildLayoutResult {
        val nodes = input.nodes
        val nodeSizesById = input.nodeSizesById
        val nodesById = nodes.associateBy { it.id }
        val edges = input.edges.mapNotNull { edge ->
            val source = nodesById[edge.sourceId]
            val target = nodesById[edge.targetId]
            if (source != null && target != null) SugiyamaEdge(edge.id, source, target) else null
        }

        val layoutData: SugiyamaLayoutData<GraphLayoutCompoundNode> = if (nodes.isEmpty()) {
            SugiyamaLayoutData()
        } else {
            SugiyamaLayout<GraphLayoutCompoundNode>(
                nodeWidth = { node -> nodeSizesById.getValue(node.id).first },
                nodeHeight = { node -> nodeSizesById.getValue(node.id).second },
                layerSpacing = layerSpacing,
                nodeSpacing = nodeSpacing,
                edgeRouting = edgeRouting,
                horizontalPull = { node -> input.horizontalPullById[node.id] ?: 0.0 }
            ).layoutGraph(nodes = nodes, edges = edges)
        }

        val nodeTopLeftById = nodes.associate { node ->
            node.id to (layoutData.nodePositions[node] ?: (0.0 to 0.0))
        }

        val maxRight = nodes.maxOfOrNull { node ->
            nodeTopLeftById.getValue(node.id).first + nodeSizesById.getValue(node.id).first
        } ?: 0.0
        val maxBottom = nodes.maxOfOrNull { node ->
            nodeTopLeftById.getValue(node.id).second + nodeSizesById.getValue(node.id).second
        } ?: 0.0

        val edgeRoutesByEdgeId = layoutData.edgeRoutes.entries
            .sortedBy { it.key.id }
            .associate { (edge, route) -> edge.id to route }

        return ChildLayoutResult(
            nodeSizesById = nodeSizesById,
            nodeTopLeftById = nodeTopLeftById,
            edgeRoutesByEdgeId = edgeRoutesByEdgeId,
            contentWidth = max(layoutData.totalWidth, maxRight),
            contentHeight = max(layoutData.totalHeight, maxBottom)
        )
    }
}

/**
 * Places the children in a near-square grid of equal cells, ignoring edges.
 * Every cell takes the size of the largest child.
 */
object TessellateChildLayout : ChildLayoutAlgorithm {
    override fun layout(input: ChildLayoutInput): ChildLayoutResult {
        val nodes = input.nodes
        val nodeSizesById = input.nodeSizesById
        val count = nodes.size
        val cols = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toDouble() / cols.toDouble()).toInt().coerceAtLeast(1)

        val cellWidth = nodes.maxOf { node -> nodeSizesById.getValue(node.id).first }
        val cellHeight = nodes.maxOf { node -> nodeSizesById.getValue(node.id).second }

        val positions = nodes.mapIndexed { index, node ->
            val row = index / cols
            val col = index % cols
            node.id to (col * cellWidth to row * cellHeight)
        }.toMap()

        return ChildLayoutResult(
            nodeSizesById = nodes.associate { node -> node.id to (cellWidth to cellHeight) },
            nodeTopLeftById = positions,
            edgeRoutesByEdgeId = emptyMap(),
            contentWidth = cols * cellWidth,
            contentHeight = rows * cellHeight
        )
    }
}
