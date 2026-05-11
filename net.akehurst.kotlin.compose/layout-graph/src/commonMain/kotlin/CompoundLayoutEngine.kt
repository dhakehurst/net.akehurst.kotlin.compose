package net.akehurst.kotlin.components.layout.graph

import kotlin.math.max

/**
 * Configures recursive compound layout sizing and spacing.
 */
data class CompoundLayoutConfig(
    val defaultNodeWidth: Double = 100.0,
    val defaultNodeHeight: Double = 56.0,
    val containerHeaderHeight: Double = 28.0
)

data class CompoundNodeLayout(
    val nodeId: String,
    val ownerGraphId: String,
    val localX: Double,
    val localY: Double,
    val globalX: Double,
    val globalY: Double,
    val width: Double,
    val height: Double,
    val isContainer: Boolean
)

data class CompoundGraphLayout(
    val graphId: String,
    val parentGraphId: String?,
    val containerNodeId: String?,
    val localOffsetX: Double,
    val localOffsetY: Double,
    val globalOffsetX: Double,
    val globalOffsetY: Double,
    val contentWidth: Double,
    val contentHeight: Double,
    val padding: Double,
    val headerHeight: Double
)

data class CompoundLayoutResult(
    val nodeLayoutsById: Map<String, CompoundNodeLayout>,
    val graphLayoutsById: Map<String, CompoundGraphLayout>,
    val edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>
)

private data class PlannedGraphLayout(
    val graphId: String,
    val padding: Double,
    val nodeOrder: List<GraphLayoutCompoundNode>,
    val nodeSizesById: Map<String, Pair<Double, Double>>,
    val nodeTopLeftById: Map<String, Pair<Double, Double>>,
    val edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>,
    val childPlansById: Map<String, PlannedGraphLayout>,
    val contentWidth: Double,
    val contentHeight: Double
)

private data class EdgeEndpoints(val sourceId: String, val targetId: String)

class CompoundLayoutEngine(
    private val config: CompoundLayoutConfig = CompoundLayoutConfig()
) {

    fun layout(state: GraphLayoutCompoundGraphState): CompoundLayoutResult {
        val validation = state.validateInvariants()
        require(validation.isValid) { "Invalid compound graph state: ${validation.errors.joinToString()}" }

        val rootPlan = planGraph(state.root)
        val nodeLayouts = mutableMapOf<String, CompoundNodeLayout>()
        val graphLayouts = mutableMapOf<String, CompoundGraphLayout>()
        val edgeRoutesByEdgeId = mutableMapOf<String, List<Pair<Double, Double>>>()

        materialize(
            plan = rootPlan,
            parentGraphId = null,
            containerNodeId = null,
            localOffset = 0.0 to 0.0,
            globalOffset = 0.0 to 0.0,
            nodeLayouts = nodeLayouts,
            graphLayouts = graphLayouts,
            edgeRoutesByEdgeId = edgeRoutesByEdgeId
        )

        val edgeEndpointsById = collectEdgeEndpoints(state.root)
        edgeEndpointsById.keys.sorted().forEach { edgeId ->
            if (edgeId !in edgeRoutesByEdgeId) {
                val endpoints = edgeEndpointsById.getValue(edgeId)
                val source = nodeLayouts[endpoints.sourceId]
                val target = nodeLayouts[endpoints.targetId]
                if (source != null && target != null) {
                    val sourceCenter = source.globalX + source.width / 2.0 to source.globalY + source.height / 2.0
                    val targetCenter = target.globalX + target.width / 2.0 to target.globalY + target.height / 2.0
                    edgeRoutesByEdgeId[edgeId] = listOf(sourceCenter, targetCenter)
                }
            }
        }

        return CompoundLayoutResult(
            nodeLayoutsById = nodeLayouts,
            graphLayoutsById = graphLayouts,
            edgeRoutesByEdgeId = edgeRoutesByEdgeId
        )
    }

    private fun planGraph(graph: GraphLayoutCompoundGraph): PlannedGraphLayout {
        val childPlansById = graph.children.keys.sorted().associateWith { childId ->
            planGraph(graph.children.getValue(childId))
        }

        val nodes = graph.nodes.values.sortedBy { it.id }
        val nodeSizesById = nodes.associate { node ->
            val childPlan = childPlansById[node.id]
            val width = max(
                node.widthHint ?: config.defaultNodeWidth,
                (childPlan?.contentWidth ?: 0.0) + (2.0 * graph.padding)
            )
            val height = max(
                node.heightHint ?: config.defaultNodeHeight,
                (childPlan?.contentHeight ?: 0.0) + (2.0 * graph.padding) + if (childPlan != null) config.containerHeaderHeight else 0.0
            )
            node.id to (width to height)
        }

        val edges = graph.edges.values
            .sortedBy { it.id }
            .mapNotNull { edge ->
                val source = graph.nodes[edge.sourceId]
                val target = graph.nodes[edge.targetId]
                if (source != null && target != null) SugiyamaEdge(edge.id, source, target) else null
            }

        val layoutData: SugiyamaLayoutData<GraphLayoutCompoundNode> = if (nodes.isEmpty()) {
            SugiyamaLayoutData<GraphLayoutCompoundNode>()
        } else {
            SugiyamaLayout<GraphLayoutCompoundNode>(
                nodeWidth = { node -> nodeSizesById.getValue(node.id).first },
                nodeHeight = { node -> nodeSizesById.getValue(node.id).second },
                edgeRouting = EdgeRouting.DIRECT
            ).layoutGraph(nodes = nodes, edges = edges)
        }

        val nodeTopLeftById = nodes.associate { node ->
            val position = layoutData.nodePositions[node] ?: (0.0 to 0.0)
            node.id to position
        }

        val maxRight = nodes.maxOfOrNull { node ->
            val topLeft = nodeTopLeftById.getValue(node.id)
            topLeft.first + nodeSizesById.getValue(node.id).first
        } ?: 0.0
        val maxBottom = nodes.maxOfOrNull { node ->
            val topLeft = nodeTopLeftById.getValue(node.id)
            topLeft.second + nodeSizesById.getValue(node.id).second
        } ?: 0.0

        val contentWidth = max(layoutData.totalWidth, maxRight)
        val contentHeight = max(layoutData.totalHeight, maxBottom)

        val localEdgeRoutes = layoutData.edgeRoutes.entries
            .sortedBy { it.key.id }
            .associate { (edge, route) -> edge.id to route }

        return PlannedGraphLayout(
            graphId = graph.id,
            padding = graph.padding,
            nodeOrder = nodes,
            nodeSizesById = nodeSizesById,
            nodeTopLeftById = nodeTopLeftById,
            edgeRoutesByEdgeId = localEdgeRoutes,
            childPlansById = childPlansById,
            contentWidth = contentWidth,
            contentHeight = contentHeight
        )
    }

    private fun materialize(
        plan: PlannedGraphLayout,
        parentGraphId: String?,
        containerNodeId: String?,
        localOffset: Pair<Double, Double>,
        globalOffset: Pair<Double, Double>,
        nodeLayouts: MutableMap<String, CompoundNodeLayout>,
        graphLayouts: MutableMap<String, CompoundGraphLayout>,
        edgeRoutesByEdgeId: MutableMap<String, List<Pair<Double, Double>>>
    ) {
        graphLayouts[plan.graphId] = CompoundGraphLayout(
            graphId = plan.graphId,
            parentGraphId = parentGraphId,
            containerNodeId = containerNodeId,
            localOffsetX = localOffset.first,
            localOffsetY = localOffset.second,
            globalOffsetX = globalOffset.first,
            globalOffsetY = globalOffset.second,
            contentWidth = plan.contentWidth,
            contentHeight = plan.contentHeight,
            padding = plan.padding,
            headerHeight = config.containerHeaderHeight
        )

        plan.nodeOrder.forEach { node ->
            val localTopLeft = plan.nodeTopLeftById.getValue(node.id)
            val globalTopLeft = (globalOffset.first + localTopLeft.first) to (globalOffset.second + localTopLeft.second)
            val size = plan.nodeSizesById.getValue(node.id)

            nodeLayouts[node.id] = CompoundNodeLayout(
                nodeId = node.id,
                ownerGraphId = plan.graphId,
                localX = localTopLeft.first,
                localY = localTopLeft.second,
                globalX = globalTopLeft.first,
                globalY = globalTopLeft.second,
                width = size.first,
                height = size.second,
                isContainer = node.id in plan.childPlansById
            )
        }

        plan.edgeRoutesByEdgeId.forEach { (edgeId, route) ->
            edgeRoutesByEdgeId[edgeId] = route.map { point ->
                point.first + globalOffset.first to point.second + globalOffset.second
            }
        }

        plan.childPlansById.keys.sorted().forEach { childId ->
            val childPlan = plan.childPlansById.getValue(childId)
            val childContainer = nodeLayouts[childId] ?: return@forEach
            val childLocalOffset =
                (childContainer.localX + plan.padding) to
                    (childContainer.localY + config.containerHeaderHeight + plan.padding)
            val childGlobalOffset =
                (childContainer.globalX + plan.padding) to
                    (childContainer.globalY + config.containerHeaderHeight + plan.padding)

            materialize(
                plan = childPlan,
                parentGraphId = plan.graphId,
                containerNodeId = childId,
                localOffset = childLocalOffset,
                globalOffset = childGlobalOffset,
                nodeLayouts = nodeLayouts,
                graphLayouts = graphLayouts,
                edgeRoutesByEdgeId = edgeRoutesByEdgeId
            )
        }
    }

    private fun collectEdgeEndpoints(graph: GraphLayoutCompoundGraph): Map<String, EdgeEndpoints> {
        val current = graph.edges.values
            .sortedBy { it.id }
            .associate { edge -> edge.id to EdgeEndpoints(edge.sourceId, edge.targetId) }
        val children = graph.children.keys.sorted().flatMap { childId ->
            collectEdgeEndpoints(graph.children.getValue(childId)).entries
        }
        return (current.entries + children).associate { it.toPair() }
    }
}

