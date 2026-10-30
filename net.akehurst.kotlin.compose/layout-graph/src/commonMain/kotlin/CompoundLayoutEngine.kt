package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Configures recursive compound layout sizing defaults.
 *
 * Container child-origin/inset geometry is provided as Compose-measured metrics at layout call sites.
 */
data class CompoundLayoutConfig(
    val defaultNodeWidth: Double = 100.0,
    val defaultNodeHeight: Double = 56.0,
    val defaultChildLayout: ChildLayoutAlgorithm = SugiyamaChildLayout.DEFAULT,
    /** Used for every edge when the state's routing is [EdgeRouting.RECTILINEAR]. */
    val edgeRouter: OrthogonalEdgeRouter = OrthogonalEdgeRouter(),
    val defaultContainerChildHostMetrics: ContainerChildHostMetrics = ContainerChildHostMetrics(
        originX = 12.0,
        originY = 24.0,
        insetRight = 12.0,
        insetBottom = 12.0
    )
)

data class ContainerChildHostMetrics(
    val originX: Double = 0.0,
    val originY: Double = 0.0,
    val insetRight: Double = 0.0,
    val insetBottom: Double = 0.0,
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
    val routeBoundary: Boolean,
    val childLayout: ChildLayoutAlgorithm,
    /**
     * How far the children are shifted right inside the container's child area, to centre them
     * when the container is wider than they need. The child area starts at globalOffsetX - centringX.
     */
    val centringX: Double = 0.0
)

data class CompoundRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
)

data class CompoundLayoutResult(
    val graphId: String = "",
    val localBounds: CompoundRect = CompoundRect(0.0, 0.0, 0.0, 0.0),
    val globalBounds: CompoundRect = CompoundRect(0.0, 0.0, 0.0, 0.0),
    val localNodePositions: Map<String, Pair<Double, Double>> = emptyMap(),
    val globalNodePositions: Map<String, Pair<Double, Double>> = emptyMap(),
    val childResults: Map<String, CompoundLayoutResult> = emptyMap(),
    val nodeLayoutsById: Map<String, CompoundNodeLayout>,
    val graphLayoutsById: Map<String, CompoundGraphLayout>,
    val edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>,
    val edgeEndpointsByEdgeId: Map<String, Pair<String, String>> = emptyMap()
)

private enum class BoundarySide {
    RIGHT,
    BOTTOM,
    LEFT,
    TOP
}

private data class RectBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)


private data class PlannedGraphLayout(
    val graphId: String,
    val childLayout: ChildLayoutAlgorithm,
    val routeBoundary: Boolean,
    val childOriginByContainerNodeId: Map<String, Pair<Double, Double>>,
    val childTrailingXByContainerNodeId: Map<String, Double>,
    val nodeOrder: List<GraphLayoutCompoundNode>,
    val nodeSizesById: Map<String, Pair<Double, Double>>,
    val nodeTopLeftById: Map<String, Pair<Double, Double>>,
    val edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>,
    val childPlansById: Map<String, PlannedGraphLayout>,
    /** All node IDs that have a child graph, regardless of collapse state. */
    val containerNodeIds: Set<String>,
    val contentWidth: Double,
    val contentHeight: Double
)

private data class EdgeDescriptor(
    val edgeId: String,
    val ownerGraphId: String,
    val sourceId: String,
    val targetId: String
)

private data class CompoundGraphIndex(
    val nodeOwnerByNodeId: Map<String, String>,
    val parentGraphByGraphId: Map<String, String?>
)

class CompoundLayoutEngine(
    private val config: CompoundLayoutConfig = CompoundLayoutConfig()
) {

    private val boundaryEpsilon = 1e-6

    fun layout(
        state: GraphLayoutCompoundGraphState,
        containerMetricsByNodeId: Map<String, ContainerChildHostMetrics> = emptyMap(),
        /** Measured content size of leaf nodes (width to height). Hints act as minimums. */
        leafSizesByNodeId: Map<String, Pair<Double, Double>> = emptyMap()
    ): CompoundLayoutResult {
        val validation = state.validateInvariants()
        require(validation.isValid) { "Invalid compound graph state: ${validation.errors.joinToString()}" }

        val index = buildGraphIndex(state.root)
        val initialChildLayout = state.root.childLayout ?: config.defaultChildLayout
        val rootPlan = planGraph(
            graph = state.root,
            index = index,
            inheritedChildLayout = initialChildLayout,
            containerMetricsByNodeId = containerMetricsByNodeId,
            leafSizesByNodeId = leafSizesByNodeId
        )
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

        val edgeDescriptorsById = collectEdgeDescriptors(state.root)
        val collapsedRedirectMap = buildCollapsedNodeRedirectMap(state.root)
        val resolvedEdgeEndpointsById = mutableMapOf<String, Pair<String, String>>()

        val routerEdges = mutableListOf<RouterEdge>()
        edgeDescriptorsById.keys.sorted().forEach { edgeId ->
            val descriptor = edgeDescriptorsById.getValue(edgeId)
            val sourceId = collapsedRedirectMap[descriptor.sourceId] ?: descriptor.sourceId
            val targetId = collapsedRedirectMap[descriptor.targetId] ?: descriptor.targetId
            if (sourceId == targetId) return@forEach
            val source = nodeLayouts[sourceId]
            val target = nodeLayouts[targetId]
            if (source != null && target != null) {
                resolvedEdgeEndpointsById[edgeId] = sourceId to targetId
                if (state.routing == EdgeRouting.RECTILINEAR) {
                    // All edges are routed together below, around unrelated nodes.
                    routerEdges.add(RouterEdge(edgeId, sourceId, targetId))
                    return@forEach
                }
                val isCrossBoundary =
                    source.ownerGraphId != descriptor.ownerGraphId ||
                        target.ownerGraphId != descriptor.ownerGraphId

                if (isCrossBoundary || edgeId !in edgeRoutesByEdgeId) {
                    edgeRoutesByEdgeId[edgeId] = routeDirectEdge(sourceNode = source, targetNode = target)
                }
            }
        }

        if (routerEdges.isNotEmpty()) {
            val routerNodes = nodeLayouts.values.sortedBy { it.nodeId }.map { n ->
                RouterNode(
                    id = n.nodeId,
                    x = n.globalX,
                    y = n.globalY,
                    width = n.width,
                    height = n.height,
                    containerId = graphLayouts[n.ownerGraphId]?.containerNodeId,
                    childAreaInsets = graphLayouts[n.nodeId]?.let { g ->
                        val left = g.globalOffsetX - g.centringX - n.globalX
                        val top = g.globalOffsetY - n.globalY
                        val m = containerMetricsByNodeId[n.nodeId]
                        listOf(left, top, m?.insetRight ?: left, m?.insetBottom ?: left)
                    } ?: listOf(0.0, 0.0, 0.0, 0.0)
                )
            }
            edgeRoutesByEdgeId.keys.removeAll(routerEdges.map { it.id }.toSet())
            edgeRoutesByEdgeId.putAll(config.edgeRouter.route(routerNodes, routerEdges))
        }

        val resultByGraphId = buildResultsByGraphId(
            graph = state.root,
            graphLayouts = graphLayouts,
            nodeLayouts = nodeLayouts,
            edgeRoutesByEdgeId = edgeRoutesByEdgeId,
            edgeDescriptorsById = edgeDescriptorsById,
            edgeEndpointsByEdgeId = resolvedEdgeEndpointsById
        )
        val rootResult = resultByGraphId.getValue(state.root.id)

        return rootResult.copy(
            nodeLayoutsById = nodeLayouts,
            graphLayoutsById = graphLayouts,
            edgeRoutesByEdgeId = edgeRoutesByEdgeId,
            edgeEndpointsByEdgeId = resolvedEdgeEndpointsById
        )
    }

    private fun planGraph(
        graph: GraphLayoutCompoundGraph,
        index: CompoundGraphIndex,
        inheritedChildLayout: ChildLayoutAlgorithm,
        containerMetricsByNodeId: Map<String, ContainerChildHostMetrics>,
        leafSizesByNodeId: Map<String, Pair<Double, Double>>
    ): PlannedGraphLayout {
        val effectiveChildLayout = graph.childLayout ?: inheritedChildLayout
        // Only recurse into non-collapsed children; collapsed containers are treated as plain nodes.
        val childPlansById = graph.children.keys.sorted()
            .filter { childId -> !graph.children.getValue(childId).isCollapsed }
            .associateWith { childId ->
                planGraph(
                    graph = graph.children.getValue(childId),
                    index = index,
                    inheritedChildLayout = effectiveChildLayout,
                    containerMetricsByNodeId = containerMetricsByNodeId,
                    leafSizesByNodeId = leafSizesByNodeId
                )
            }

        // All container node IDs regardless of collapse state (used for isContainer flag).
        val containerNodeIds = graph.nodes.keys.filter { it in graph.children }.toSet()

        val nodes = graph.nodes.values.sortedBy { it.id }
        val nodeSizesById = nodes.associate { node ->
            val childPlan = childPlansById[node.id]
            val metrics = containerMetricsByNodeId[node.id] ?: fallbackContainerMetrics(childPlan)
            val paddingHintWidth = node.containerPaddingHint?.let { it.left+it.right } ?: 0.0
            val paddingHintHeight = node.containerPaddingHint?.let { it.top+it.bottom } ?: 0.0
            // A measured leaf takes its content size, never smaller than its hints.
            // Until it is measured (and for expanded containers) the default size is used when there is no hint.
            val measured = if (childPlan == null) leafSizesByNodeId[node.id] else null
            val widthHint = if (measured != null) max(node.widthHint ?: 0.0, measured.first) else (node.widthHint ?: config.defaultNodeWidth)
            val heightHint = if (measured != null) max(node.heightHint ?: 0.0, measured.second) else (node.heightHint ?: config.defaultNodeHeight)
            val width = max(
                widthHint,
                (childPlan?.contentWidth ?: 0.0) + metrics.originX + metrics.insetRight
            ) + paddingHintWidth
            val height = max(
                heightHint,
                (childPlan?.contentHeight ?: 0.0) + metrics.originY + metrics.insetBottom
            ) + paddingHintHeight
            node.id to (width to height)
        }

        val childOriginByContainerNodeId = childPlansById.keys.associateWith { childId ->
            val childPlan = childPlansById[childId]
            val metrics = containerMetricsByNodeId[childId] ?: fallbackContainerMetrics(childPlan)
            metrics.originX to metrics.originY
        }
        // Space a container keeps to the right of its children (measured inset plus padding hint).
        val childTrailingXByContainerNodeId = childPlansById.keys.associateWith { childId ->
            val metrics = containerMetricsByNodeId[childId] ?: fallbackContainerMetrics(childPlansById[childId])
            val paddingHintWidth = graph.nodes[childId]?.containerPaddingHint?.let { it.left + it.right } ?: 0.0
            metrics.insetRight + paddingHintWidth
        }

        val edges = graph.edges.values
            .sortedBy { it.id }
            .mapNotNull { edge ->
                val sourceNodeId = projectToLevelNode(graph.id, edge.sourceId, index)
                val targetNodeId = projectToLevelNode(graph.id, edge.targetId, index)
                if (sourceNodeId != null && targetNodeId != null && sourceNodeId in graph.nodes && targetNodeId in graph.nodes) {
                    ChildLayoutEdge(edge.id, sourceNodeId, targetNodeId)
                } else {
                    null
                }
            }

        val laidOut = effectiveChildLayout.layout(
            ChildLayoutInput(
                graphId = graph.id,
                nodes = nodes,
                nodeSizesById = nodeSizesById,
                edges = edges
            )
        )

        return PlannedGraphLayout(
            graphId = graph.id,
            childLayout = effectiveChildLayout,
            routeBoundary = graph.routeBoundary,
            childOriginByContainerNodeId = childOriginByContainerNodeId,
            childTrailingXByContainerNodeId = childTrailingXByContainerNodeId,
            nodeOrder = nodes,
            nodeSizesById = laidOut.nodeSizesById,
            nodeTopLeftById = laidOut.nodeTopLeftById,
            edgeRoutesByEdgeId = laidOut.edgeRoutesByEdgeId,
            childPlansById = childPlansById,
            containerNodeIds = containerNodeIds,
            contentWidth = laidOut.contentWidth,
            contentHeight = laidOut.contentHeight
        )
    }

    private fun materialize(
        plan: PlannedGraphLayout,
        parentGraphId: String?,
        containerNodeId: String?,
        localOffset: Pair<Double, Double>,
        globalOffset: Pair<Double, Double>,
        nodeLayouts: MutableMap<String, CompoundNodeLayout>,
        /** Horizontal shift of the children inside the container's child area (included in the offsets). */
        centring: Double = 0.0,
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
            routeBoundary = plan.routeBoundary,
            childLayout = plan.childLayout,
            centringX = centring
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
                isContainer = node.id in plan.containerNodeIds
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
            val origin = plan.childOriginByContainerNodeId[childId] ?: (0.0 to 0.0)
            // When the container is wider than its children (e.g. a long name), centre them horizontally.
            val available = childContainer.width - origin.first - (plan.childTrailingXByContainerNodeId[childId] ?: 0.0)
            // Tessellated children fill the area instead (spec 6.6), so they are not centred.
            val centring = if (childPlan.childLayout == TessellateChildLayout) 0.0 else max(0.0, (available - childPlan.contentWidth) / 2.0)
            val childOrigin = (origin.first + centring) to origin.second
            val childLocalOffset =
                (childContainer.localX + childOrigin.first) to
                    (childContainer.localY + childOrigin.second)
            val childGlobalOffset =
                (childContainer.globalX + childOrigin.first) to
                    (childContainer.globalY + childOrigin.second)

            materialize(
                plan = childPlan,
                parentGraphId = plan.graphId,
                containerNodeId = childId,
                localOffset = childLocalOffset,
                globalOffset = childGlobalOffset,
                nodeLayouts = nodeLayouts,
                centring = centring,
                graphLayouts = graphLayouts,
                edgeRoutesByEdgeId = edgeRoutesByEdgeId
            )
        }
    }

    // ── Boundary-port routing helpers ───────────────────────────────────────

    /**
     * Straight route for an edge when routing is [EdgeRouting.DIRECT]: from border to border on the
     * line between the node centres. (Orthogonal routes come from [OrthogonalEdgeRouter].)
     */
    private fun routeDirectEdge(
        sourceNode: CompoundNodeLayout,
        targetNode: CompoundNodeLayout
    ): List<Pair<Double, Double>> {
        val sourceCenter = sourceNode.globalX + sourceNode.width / 2.0 to sourceNode.globalY + sourceNode.height / 2.0
        val targetCenter = targetNode.globalX + targetNode.width / 2.0 to targetNode.globalY + targetNode.height / 2.0
        val sourceRect = RectBounds(sourceNode.globalX, sourceNode.globalY, sourceNode.width, sourceNode.height)
        val targetRect = RectBounds(targetNode.globalX, targetNode.globalY, targetNode.width, targetNode.height)
        val sourceEndpoint = clipRayToRectBoundary(sourceCenter, targetCenter, sourceRect)
        val targetEndpoint = clipRayToRectBoundary(targetCenter, sourceCenter, targetRect)
        return collapseConsecutiveDuplicates(listOf(sourceEndpoint, targetEndpoint)).map(::normalizePoint)
    }

    private fun buildResultsByGraphId(
        graph: GraphLayoutCompoundGraph,
        graphLayouts: Map<String, CompoundGraphLayout>,
        nodeLayouts: Map<String, CompoundNodeLayout>,
        edgeRoutesByEdgeId: Map<String, List<Pair<Double, Double>>>,
        edgeDescriptorsById: Map<String, EdgeDescriptor>,
        edgeEndpointsByEdgeId: Map<String, Pair<String, String>>
    ): Map<String, CompoundLayoutResult> {
        val resultByGraphId = mutableMapOf<String, CompoundLayoutResult>()

        fun build(graphNode: GraphLayoutCompoundGraph): CompoundLayoutResult {
            val graphLayout = graphLayouts.getValue(graphNode.id)
            val localNodePositions = nodeLayouts.values
                .filter { it.ownerGraphId == graphNode.id }
                .associate { it.nodeId to (it.localX to it.localY) }
            val globalNodePositions = nodeLayouts.values
                .filter { it.ownerGraphId == graphNode.id }
                .associate { it.nodeId to (it.globalX to it.globalY) }
            val childResults = graphNode.children.keys.sorted()
                .filter { childId -> !graphNode.children.getValue(childId).isCollapsed }
                .associateWith { childId ->
                    build(graphNode.children.getValue(childId))
                }

            val edgeRoutesForGraph = edgeDescriptorsById.values
                .filter { it.ownerGraphId == graphNode.id }
                .sortedBy { it.edgeId }
                .associate { descriptor ->
                    descriptor.edgeId to (edgeRoutesByEdgeId[descriptor.edgeId] ?: emptyList())
                }

            val result = CompoundLayoutResult(
                graphId = graphNode.id,
                localBounds = CompoundRect(
                    left = 0.0,
                    top = 0.0,
                    right = graphLayout.contentWidth,
                    bottom = graphLayout.contentHeight
                ),
                globalBounds = CompoundRect(
                    left = graphLayout.globalOffsetX,
                    top = graphLayout.globalOffsetY,
                    right = graphLayout.globalOffsetX + graphLayout.contentWidth,
                    bottom = graphLayout.globalOffsetY + graphLayout.contentHeight
                ),
                localNodePositions = localNodePositions,
                globalNodePositions = globalNodePositions,
                childResults = childResults,
                nodeLayoutsById = emptyMap(),
                graphLayoutsById = emptyMap(),
                edgeRoutesByEdgeId = edgeRoutesForGraph,
                edgeEndpointsByEdgeId = edgeEndpointsByEdgeId.filterKeys { it in edgeRoutesForGraph.keys }
            )
            resultByGraphId[graphNode.id] = result
            return result
        }

        build(graph)
        return resultByGraphId
    }

    private fun clipRayToRectBoundary(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        rect: RectBounds
    ): Pair<Double, Double> {
        val dx = to.first - from.first
        val dy = to.second - from.second
        if (abs(dx) < boundaryEpsilon && abs(dy) < boundaryEpsilon) {
            return normalizePoint(sideCenter(rect, BoundarySide.RIGHT))
        }

        val candidates = mutableListOf<Triple<Double, BoundarySide, Pair<Double, Double>>>()

        if (abs(dx) >= boundaryEpsilon) {
            val tRight = (rect.x + rect.width - from.first) / dx
            if (tRight > boundaryEpsilon) {
                val y = from.second + tRight * dy
                if (y >= rect.y - boundaryEpsilon && y <= rect.y + rect.height + boundaryEpsilon) {
                    candidates.add(Triple(tRight, BoundarySide.RIGHT, rect.x + rect.width to y))
                }
            }

            val tLeft = (rect.x - from.first) / dx
            if (tLeft > boundaryEpsilon) {
                val y = from.second + tLeft * dy
                if (y >= rect.y - boundaryEpsilon && y <= rect.y + rect.height + boundaryEpsilon) {
                    candidates.add(Triple(tLeft, BoundarySide.LEFT, rect.x to y))
                }
            }
        }

        if (abs(dy) >= boundaryEpsilon) {
            val tBottom = (rect.y + rect.height - from.second) / dy
            if (tBottom > boundaryEpsilon) {
                val x = from.first + tBottom * dx
                if (x >= rect.x - boundaryEpsilon && x <= rect.x + rect.width + boundaryEpsilon) {
                    candidates.add(Triple(tBottom, BoundarySide.BOTTOM, x to rect.y + rect.height))
                }
            }

            val tTop = (rect.y - from.second) / dy
            if (tTop > boundaryEpsilon) {
                val x = from.first + tTop * dx
                if (x >= rect.x - boundaryEpsilon && x <= rect.x + rect.width + boundaryEpsilon) {
                    candidates.add(Triple(tTop, BoundarySide.TOP, x to rect.y))
                }
            }
        }

        if (candidates.isEmpty()) {
            return normalizePoint(from)
        }

        val minT = candidates.minOf { it.first }
        val best = candidates
            .filter { abs(it.first - minT) <= boundaryEpsilon }
            .minByOrNull { sidePriority(it.second) }
            ?: candidates.minByOrNull { it.first }!!

        return normalizePoint(best.third)
    }

    private fun sideCenter(rect: RectBounds, side: BoundarySide): Pair<Double, Double> = when (side) {
        BoundarySide.RIGHT -> rect.x + rect.width to rect.y + rect.height / 2.0
        BoundarySide.BOTTOM -> rect.x + rect.width / 2.0 to rect.y + rect.height
        BoundarySide.LEFT -> rect.x to rect.y + rect.height / 2.0
        BoundarySide.TOP -> rect.x + rect.width / 2.0 to rect.y
    }

    private fun sidePriority(side: BoundarySide): Int = when (side) {
        BoundarySide.RIGHT -> 0
        BoundarySide.BOTTOM -> 1
        BoundarySide.LEFT -> 2
        BoundarySide.TOP -> 3
    }

    private fun normalizePoint(point: Pair<Double, Double>): Pair<Double, Double> {
        fun normalizeValue(value: Double): Double = if (abs(value) < boundaryEpsilon) 0.0 else value
        return normalizeValue(point.first) to normalizeValue(point.second)
    }

    private fun samePoint(a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
        return abs(a.first - b.first) <= boundaryEpsilon && abs(a.second - b.second) <= boundaryEpsilon
    }

    private fun collapseConsecutiveDuplicates(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.isEmpty()) return points
        val deduped = mutableListOf(points.first())
        points.drop(1).forEach { point ->
            if (!samePoint(deduped.last(), point)) {
                deduped.add(point)
            }
        }
        return deduped
    }

    /**
     * Returns a map from nodeId → containerNodeId for every node that is hidden inside a
     * collapsed container.  Nodes in non-collapsed containers are not in the map.
     */
    private fun buildCollapsedNodeRedirectMap(root: GraphLayoutCompoundGraph): Map<String, String> {
        val redirect = mutableMapOf<String, String>()

        fun walkCollapsed(graph: GraphLayoutCompoundGraph, collapsedContainerNodeId: String?) {
            if (collapsedContainerNodeId != null) {
                // Everything inside a collapsed ancestor → redirect to that ancestor container node.
                graph.nodes.keys.forEach { nodeId -> redirect[nodeId] = collapsedContainerNodeId }
                graph.children.values.forEach { child -> walkCollapsed(child, collapsedContainerNodeId) }
            } else {
                graph.children.entries.forEach { (childId, childGraph) ->
                    if (childGraph.isCollapsed) {
                        // Direct children of this collapsed graph redirect to the container node.
                        childGraph.nodes.keys.forEach { nodeId -> redirect[nodeId] = childId }
                        childGraph.children.values.forEach { sub -> walkCollapsed(sub, childId) }
                    } else {
                        walkCollapsed(childGraph, null)
                    }
                }
            }
        }

        walkCollapsed(root, null)
        return redirect
    }

    private fun collectEdgeDescriptors(graph: GraphLayoutCompoundGraph): Map<String, EdgeDescriptor> {
        val current = graph.edges.values
            .sortedBy { it.id }
            .associate { edge ->
                edge.id to EdgeDescriptor(
                    edgeId = edge.id,
                    ownerGraphId = graph.id,
                    sourceId = edge.sourceId,
                    targetId = edge.targetId
                )
            }
        val child = graph.children.keys.sorted()
            .filter { childId -> !graph.children.getValue(childId).isCollapsed }
            .flatMap { childId ->
                collectEdgeDescriptors(graph.children.getValue(childId)).entries
            }
        return (current.entries + child).associate { it.toPair() }
    }

    private fun buildGraphIndex(root: GraphLayoutCompoundGraph): CompoundGraphIndex {
        val nodeOwnerByNodeId = mutableMapOf<String, String>()
        val parentGraphByGraphId = mutableMapOf<String, String?>()

        fun walk(graph: GraphLayoutCompoundGraph, parentGraphId: String?) {
            parentGraphByGraphId[graph.id] = parentGraphId
            graph.nodes.values.forEach { node ->
                nodeOwnerByNodeId[node.id] = graph.id
            }
            graph.children.keys.sorted().forEach { childId ->
                walk(graph.children.getValue(childId), graph.id)
            }
        }

        walk(root, null)
        return CompoundGraphIndex(
            nodeOwnerByNodeId = nodeOwnerByNodeId,
            parentGraphByGraphId = parentGraphByGraphId
        )
    }

    private fun projectToLevelNode(graphId: String, nodeId: String, index: CompoundGraphIndex): String? {
        val ownerGraphId = index.nodeOwnerByNodeId[nodeId] ?: return null
        if (ownerGraphId == graphId) {
            return nodeId
        }

        var currentGraphId = ownerGraphId
        while (true) {
            val parentGraphId = index.parentGraphByGraphId[currentGraphId] ?: return null
            if (parentGraphId == graphId) {
                return currentGraphId
            }
            currentGraphId = parentGraphId
        }
    }

    private fun fallbackContainerMetrics(childPlan: PlannedGraphLayout?): ContainerChildHostMetrics {
        return if (childPlan == null) {
            ContainerChildHostMetrics()
        } else {
            config.defaultContainerChildHostMetrics
        }
    }
}
