package net.akehurst.kotlin.components.layout.graph

import kotlin.math.abs
import kotlin.math.ceil
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
    val defaultProfile: CompoundLayoutProfile = CompoundLayoutProfile.DEFAULT,
    val defaultChildLayout: ChildLayout = ChildLayout.GRAPH,
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
    val profile: CompoundLayoutProfile
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

private enum class DetourSide {
    RIGHT,
    BOTTOM,
    LEFT,
    TOP
}

private data class PlannedGraphLayout(
    val graphId: String,
    val profile: CompoundLayoutProfile,
    val routeBoundary: Boolean,
    val childOriginByContainerNodeId: Map<String, Pair<Double, Double>>,
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

private data class ProfileSettings(
    val layerSpacing: Double,
    val nodeSpacing: Double,
    val edgeRouting: EdgeRouting
)

private data class TiledLayout(
    val nodeSizesById: Map<String, Pair<Double, Double>>,
    val nodeTopLeftById: Map<String, Pair<Double, Double>>,
    val contentWidth: Double,
    val contentHeight: Double
)

class CompoundLayoutEngine(
    private val config: CompoundLayoutConfig = CompoundLayoutConfig()
) {

    private val boundaryEpsilon = 1e-6

    fun layout(
        state: GraphLayoutCompoundGraphState,
        containerMetricsByNodeId: Map<String, ContainerChildHostMetrics> = emptyMap()
    ): CompoundLayoutResult {
        val validation = state.validateInvariants()
        require(validation.isValid) { "Invalid compound graph state: ${validation.errors.joinToString()}" }

        val index = buildGraphIndex(state.root)
        val rootProfile = state.root.layoutProfile ?: config.defaultProfile
        val initialChildLayout = state.root.childLayout ?: config.defaultChildLayout
        val rootPlan = planGraph(
            graph = state.root,
            index = index,
            inheritedProfile = rootProfile,
            inheritedChildLayout = initialChildLayout,
            containerMetricsByNodeId = containerMetricsByNodeId
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
        val parallelSlotByEdgeId = computeParallelEdgeSlotsByResolvedEndpoints(edgeDescriptorsById, collapsedRedirectMap)
        val resolvedEdgeEndpointsById = mutableMapOf<String, Pair<String, String>>()

        edgeDescriptorsById.keys.sorted().forEach { edgeId ->
            val descriptor = edgeDescriptorsById.getValue(edgeId)
            val sourceId = collapsedRedirectMap[descriptor.sourceId] ?: descriptor.sourceId
            val targetId = collapsedRedirectMap[descriptor.targetId] ?: descriptor.targetId
            if (sourceId == targetId) return@forEach
            val source = nodeLayouts[sourceId]
            val target = nodeLayouts[targetId]
            if (source != null && target != null) {
                resolvedEdgeEndpointsById[edgeId] = sourceId to targetId
                val isCrossBoundary =
                    source.ownerGraphId != descriptor.ownerGraphId ||
                        target.ownerGraphId != descriptor.ownerGraphId

                if (isCrossBoundary || edgeId !in edgeRoutesByEdgeId) {
                    edgeRoutesByEdgeId[edgeId] = routeCrossBoundaryEdge(
                        sourceNode = source,
                        targetNode = target,
                        nodeLayouts = nodeLayouts,
                        graphLayouts = graphLayouts,
                        routing = state.routing,
                        parallelSlot = parallelSlotByEdgeId[edgeId] ?: 0.0
                    )
                }
            }
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
        inheritedProfile: CompoundLayoutProfile,
        inheritedChildLayout: ChildLayout,
        containerMetricsByNodeId: Map<String, ContainerChildHostMetrics>
    ): PlannedGraphLayout {
        val effectiveChildLayout = graph.childLayout ?: inheritedChildLayout
        val effectiveProfile = graph.layoutProfile ?: inheritedProfile
        val profile = profileSettings(effectiveProfile)
        // Only recurse into non-collapsed children; collapsed containers are treated as plain nodes.
        val childPlansById = graph.children.keys.sorted()
            .filter { childId -> !graph.children.getValue(childId).isCollapsed }
            .associateWith { childId ->
                planGraph(
                    graph = graph.children.getValue(childId),
                    index = index,
                    inheritedProfile = effectiveProfile,
                    inheritedChildLayout = effectiveChildLayout,
                    containerMetricsByNodeId = containerMetricsByNodeId
                )
            }

        // All container node IDs regardless of collapse state (used for isContainer flag).
        val containerNodeIds = graph.nodes.keys.filter { it in graph.children }.toSet()

        val nodes = graph.nodes.values.sortedBy { it.id }
        val nodeSizesById = nodes.associate { node ->
            val childPlan = childPlansById[node.id]
            val metrics = containerMetricsByNodeId[node.id] ?: fallbackContainerMetrics(childPlan)
            val paddingHintWidth = node.containerPaddingHint?.let { it.left+it.right } ?: 0.0
            val paddingHintHeight = node.containerPaddingHint?.let { it.bottom+it.top+it.bottom } ?: 0.0
            val widthHint = (node.widthHint ?:config.defaultNodeWidth)
            val heightHint = (node.heightHint ?:config.defaultNodeHeight)
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

        when (effectiveChildLayout) {
            ChildLayout.TESSELLATE -> {
                val tiled = tessellateNodes(nodes, nodeSizesById)
                return PlannedGraphLayout(
                    graphId = graph.id,
                    profile = effectiveProfile,
                    routeBoundary = graph.routeBoundary,
                    childOriginByContainerNodeId = childOriginByContainerNodeId,
                    nodeOrder = nodes,
                    nodeSizesById = tiled.nodeSizesById,
                    nodeTopLeftById = tiled.nodeTopLeftById,
                    edgeRoutesByEdgeId = emptyMap(),
                    childPlansById = childPlansById,
                    containerNodeIds = containerNodeIds,
                    contentWidth = tiled.contentWidth,
                    contentHeight = tiled.contentHeight
                )
            }

            ChildLayout.GRAPH -> Unit
        }

        val edges = graph.edges.values
            .sortedBy { it.id }
            .mapNotNull { edge ->
                val sourceNodeId = projectToLevelNode(graph.id, edge.sourceId, index)
                val targetNodeId = projectToLevelNode(graph.id, edge.targetId, index)
                val source = sourceNodeId?.let { graph.nodes[it] }
                val target = targetNodeId?.let { graph.nodes[it] }
                if (source != null && target != null) SugiyamaEdge(edge.id, source, target) else null
            }

        val layoutData: SugiyamaLayoutData<GraphLayoutCompoundNode> = if (nodes.isEmpty()) {
            SugiyamaLayoutData<GraphLayoutCompoundNode>()
        } else {
            SugiyamaLayout<GraphLayoutCompoundNode>(
                nodeWidth = { node -> nodeSizesById.getValue(node.id).first },
                nodeHeight = { node -> nodeSizesById.getValue(node.id).second },
                layerSpacing = profile.layerSpacing,
                nodeSpacing = profile.nodeSpacing,
                edgeRouting = profile.edgeRouting
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
            profile = effectiveProfile,
            routeBoundary = graph.routeBoundary,
            childOriginByContainerNodeId = childOriginByContainerNodeId,
            nodeOrder = nodes,
            nodeSizesById = nodeSizesById,
            nodeTopLeftById = nodeTopLeftById,
            edgeRoutesByEdgeId = localEdgeRoutes,
            childPlansById = childPlansById,
            containerNodeIds = containerNodeIds,
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
            routeBoundary = plan.routeBoundary,
            profile = plan.profile
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
            val childOrigin = plan.childOriginByContainerNodeId[childId] ?: (0.0 to 0.0)
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
                graphLayouts = graphLayouts,
                edgeRoutesByEdgeId = edgeRoutesByEdgeId
            )
        }
    }

    // ── Boundary-port routing helpers ───────────────────────────────────────

    /**
     * Returns the point where the line from [from] to [to] first crosses the boundary of the
     * rectangle defined by ([rectX], [rectY], [rectW], [rectH]).
     *
     * Works whether [from] is inside or outside the rectangle:
     * - inside → outside: returns the exit point.
     * - outside → inside: returns the entry point.
     *
     * Falls back to [from] if [from] == [to] or no intersection is found within the segment.
     */
    private fun lineRectIntersection(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        rectX: Double,
        rectY: Double,
        rectW: Double,
        rectH: Double
    ): Pair<Double, Double> {
        val rect = RectBounds(rectX, rectY, rectW, rectH)
        return clipRayToRectBoundary(from, to, rect)
    }

    /**
     * Finds the lowest common ancestor graph of [graphA] and [graphB] by walking [graphLayouts].
     * Returns [graphA] as a safe fallback if the LCA cannot be determined.
     */
    private fun findLca(
        graphA: String,
        graphB: String,
        graphLayouts: Map<String, CompoundGraphLayout>
    ): String {
        val ancestorsOfA = mutableSetOf<String>()
        var current: String? = graphA
        while (current != null) {
            ancestorsOfA.add(current)
            current = graphLayouts[current]?.parentGraphId
        }
        var currentB: String? = graphB
        while (currentB != null) {
            if (currentB in ancestorsOfA) return currentB
            currentB = graphLayouts[currentB]?.parentGraphId
        }
        return graphA
    }

    /**
     * Collects the ordered list of container [CompoundNodeLayout]s that must be crossed going
     * from [startGraphId] outward until [lcaGraphId] is reached (exclusive).
     * The list is innermost → outermost, matching the traversal order for source-side exits.
     */
    private fun containerChainToLca(
        startGraphId: String,
        lcaGraphId: String,
        graphLayouts: Map<String, CompoundGraphLayout>,
        nodeLayouts: Map<String, CompoundNodeLayout>
    ): List<CompoundNodeLayout> {
        val chain = mutableListOf<CompoundNodeLayout>()
        var currentGraphId = startGraphId
        while (currentGraphId != lcaGraphId) {
            val graphLayout = graphLayouts[currentGraphId] ?: break
            val containerNodeId = graphLayout.containerNodeId ?: break
            val containerNode = nodeLayouts[containerNodeId] ?: break
            if (graphLayout.routeBoundary) {
                chain.add(containerNode)
            }
            currentGraphId = containerNode.ownerGraphId
        }
        return chain
    }

    /**
     * Builds a route for a cross-container edge.
     *
     * The route is: `sourceCenter → (exit each source container) → (enter each target container) → targetCenter`.
     * Boundary attachment points are computed via [lineRectIntersection] so each waypoint
     * lies exactly on the wall of the container being crossed.
     */
    private fun routeCrossBoundaryEdge(
        sourceNode: CompoundNodeLayout,
        targetNode: CompoundNodeLayout,
        nodeLayouts: Map<String, CompoundNodeLayout>,
        graphLayouts: Map<String, CompoundGraphLayout>,
        routing: EdgeRouting,
        parallelSlot: Double
    ): List<Pair<Double, Double>> {
        val sourceCenter = sourceNode.globalX + sourceNode.width / 2.0 to sourceNode.globalY + sourceNode.height / 2.0
        val targetCenter = targetNode.globalX + targetNode.width / 2.0 to targetNode.globalY + targetNode.height / 2.0

        val sourceRect = RectBounds(sourceNode.globalX, sourceNode.globalY, sourceNode.width, sourceNode.height)
        val targetRect = RectBounds(targetNode.globalX, targetNode.globalY, targetNode.width, targetNode.height)

        val sourceEndpoint = when (routing) {
            EdgeRouting.DIRECT -> clipRayToRectBoundary(sourceCenter, targetCenter, sourceRect)
            EdgeRouting.RECTILINEAR -> rectilinearAnchor(sourceRect, sourceCenter, targetCenter, parallelSlot)
        }
        val targetEndpoint = when (routing) {
            EdgeRouting.DIRECT -> clipRayToRectBoundary(targetCenter, sourceCenter, targetRect)
            EdgeRouting.RECTILINEAR -> rectilinearAnchor(targetRect, targetCenter, sourceCenter, parallelSlot)
        }

        if (routing == EdgeRouting.DIRECT) {
            return collapseConsecutiveDuplicates(listOf(sourceEndpoint, targetEndpoint)).map(::normalizePoint)
        }

        val lcaGraphId = findLca(sourceNode.ownerGraphId, targetNode.ownerGraphId, graphLayouts)

        // Container nodes the edge must EXIT (innermost → outermost, going from source to LCA)
        val sourceChain = containerChainToLca(sourceNode.ownerGraphId, lcaGraphId, graphLayouts, nodeLayouts)
        // Container nodes the edge must ENTER (innermost → outermost, reversed = outermost first for entry)
        val targetChain = containerChainToLca(targetNode.ownerGraphId, lcaGraphId, graphLayouts, nodeLayouts)

        val route = mutableListOf(sourceEndpoint)
        var currentPoint = sourceEndpoint

        // Exit source containers (innermost first, heading toward targetCenter)
        for (container in sourceChain) {
            val exitPoint = lineRectIntersection(
                currentPoint, targetCenter,
                container.globalX, container.globalY, container.width, container.height
            )
            route.add(exitPoint)
            currentPoint = exitPoint
        }

        // Enter target containers (outermost first, still heading toward targetCenter)
        for (container in targetChain.reversed()) {
            val entryPoint = lineRectIntersection(
                currentPoint, targetCenter,
                container.globalX, container.globalY, container.width, container.height
            )
            route.add(entryPoint)
            currentPoint = entryPoint
        }

        if (routing == EdgeRouting.RECTILINEAR) {
            val bend = targetEndpoint.first to currentPoint.second
            if (!samePoint(currentPoint, bend) && !samePoint(bend, targetEndpoint)) {
                route.add(normalizePoint(bend))
            }
        }
        route.add(targetEndpoint)

        val relatedContainerIds = (sourceChain + targetChain)
            .map { it.nodeId }
            .toMutableSet()
            .also {
                graphLayouts[lcaGraphId]?.containerNodeId?.let { lcaContainerNodeId ->
                    it.add(lcaContainerNodeId)
                }
                it.add(sourceNode.nodeId)
                it.add(targetNode.nodeId)
            }

        val unrelatedContainerObstacles = nodeLayouts.values
            .filter { layout ->
                layout.isContainer &&
                    layout.nodeId !in relatedContainerIds &&
                    (graphLayouts[layout.nodeId]?.routeBoundary != false)
            }
            .sortedBy { it.nodeId }
            .map { RectBounds(it.globalX, it.globalY, it.width, it.height) }

        val normalized = collapseConsecutiveDuplicates(route).map(::normalizePoint)
        return applyObstacleAvoidanceToPolyline(normalized, unrelatedContainerObstacles)
    }

    private fun applyObstacleAvoidanceToPolyline(
        points: List<Pair<Double, Double>>,
        obstacles: List<RectBounds>
    ): List<Pair<Double, Double>> {
        if (points.size <= 1 || obstacles.isEmpty()) {
            return points
        }

        val result = mutableListOf(points.first())
        for (index in 0 until points.size - 1) {
            val from = result.last()
            val to = points[index + 1]
            val detour = detourSegmentAvoidingObstacles(from, to, obstacles)
            detour.drop(1).forEach { point ->
                if (!samePoint(result.last(), point)) {
                    result.add(normalizePoint(point))
                }
            }
        }
        return collapseConsecutiveDuplicates(result).map(::normalizePoint)
    }

    private fun detourSegmentAvoidingObstacles(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        obstacles: List<RectBounds>
    ): List<Pair<Double, Double>> {
        val blocking = obstacles.filter { segmentIntersectsRectInterior(from, to, it) }
        if (blocking.isEmpty()) {
            return listOf(from, to)
        }

        val margin = 16.0
        val minLeft = blocking.minOf { it.x } - margin
        val maxRight = blocking.maxOf { it.x + it.width } + margin
        val minTop = blocking.minOf { it.y } - margin
        val maxBottom = blocking.maxOf { it.y + it.height } + margin

        val candidates = listOf(
            DetourSide.RIGHT to listOf(from, maxRight to from.second, maxRight to to.second, to),
            DetourSide.BOTTOM to listOf(from, from.first to maxBottom, to.first to maxBottom, to),
            DetourSide.LEFT to listOf(from, minLeft to from.second, minLeft to to.second, to),
            DetourSide.TOP to listOf(from, from.first to minTop, to.first to minTop, to)
        )

        val valid = candidates
            .map { (side, path) -> side to collapseConsecutiveDuplicates(path).map(::normalizePoint) }
            .filter { (_, path) ->
                path.zipWithNext().none { (a, b) -> obstacles.any { obstacle -> segmentIntersectsRectInterior(a, b, obstacle) } }
            }

        if (valid.isEmpty()) {
            return listOf(from, to)
        }

        val best = valid.minWithOrNull(
            compareBy<Pair<DetourSide, List<Pair<Double, Double>>>>(
                { (_, path) -> pathLength(path) },
                { (side, _) -> detourSidePriority(side) }
            )
        ) ?: return listOf(from, to)

        return best.second
    }

    private fun pathLength(path: List<Pair<Double, Double>>): Double {
        return path.zipWithNext().sumOf { (a, b) ->
            val dx = b.first - a.first
            val dy = b.second - a.second
            sqrt(dx * dx + dy * dy)
        }
    }

    private fun detourSidePriority(side: DetourSide): Int = when (side) {
        DetourSide.RIGHT -> 0
        DetourSide.BOTTOM -> 1
        DetourSide.LEFT -> 2
        DetourSide.TOP -> 3
    }

    private fun segmentIntersectsRectInterior(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        rect: RectBounds
    ): Boolean {
        val left = rect.x + boundaryEpsilon
        val right = rect.x + rect.width - boundaryEpsilon
        val top = rect.y + boundaryEpsilon
        val bottom = rect.y + rect.height - boundaryEpsilon
        if (left >= right || top >= bottom) {
            return false
        }

        val dx = b.first - a.first
        val dy = b.second - a.second
        var t0 = 0.0
        var t1 = 1.0

        fun clip(p: Double, q: Double): Boolean {
            if (abs(p) < boundaryEpsilon) {
                return q >= 0.0
            }
            val r = q / p
            return if (p < 0.0) {
                if (r > t1) {
                    false
                } else {
                    if (r > t0) t0 = r
                    true
                }
            } else {
                if (r < t0) {
                    false
                } else {
                    if (r < t1) t1 = r
                    true
                }
            }
        }

        if (!clip(-dx, a.first - left)) return false
        if (!clip(dx, right - a.first)) return false
        if (!clip(-dy, a.second - top)) return false
        if (!clip(dy, bottom - a.second)) return false

        return t0 < t1 && t1 > 0.0 && t0 < 1.0
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

    private fun rectilinearAnchor(
        rect: RectBounds,
        center: Pair<Double, Double>,
        otherCenter: Pair<Double, Double>,
        slot: Double
    ): Pair<Double, Double> {
        val preferred = preferredSideForDirection(center, otherCenter)
        val baseCenter = sideCenter(rect, preferred)
        val offset = slot * 10.0
        return when (preferred) {
            BoundarySide.RIGHT, BoundarySide.LEFT -> {
                val yMin = rect.y + boundaryEpsilon
                val yMax = rect.y + rect.height - boundaryEpsilon
                normalizePoint(baseCenter.first to min(yMax, max(yMin, baseCenter.second + offset)))
            }

            BoundarySide.TOP, BoundarySide.BOTTOM -> {
                val xMin = rect.x + boundaryEpsilon
                val xMax = rect.x + rect.width - boundaryEpsilon
                normalizePoint(min(xMax, max(xMin, baseCenter.first + offset)) to baseCenter.second)
            }
        }
    }

    private fun preferredSideForDirection(
        center: Pair<Double, Double>,
        otherCenter: Pair<Double, Double>
    ): BoundarySide {
        val dx = otherCenter.first - center.first
        val dy = otherCenter.second - center.second
        if (abs(dx) < boundaryEpsilon && abs(dy) < boundaryEpsilon) {
            return BoundarySide.RIGHT
        }
        return if (abs(dx) > abs(dy)) {
            if (dx >= 0.0) BoundarySide.RIGHT else BoundarySide.LEFT
        } else if (abs(dy) > abs(dx)) {
            if (dy >= 0.0) BoundarySide.BOTTOM else BoundarySide.TOP
        } else {
            if (dx >= 0.0) BoundarySide.RIGHT else if (dy >= 0.0) BoundarySide.BOTTOM else BoundarySide.LEFT
        }
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

    /**
     * Groups edges by their *resolved* endpoints (after applying [redirectMap]
     * for hidden nodes inside collapsed containers) and assigns deterministic slots.
     */
    private fun computeParallelEdgeSlotsByResolvedEndpoints(
        edgeDescriptorsById: Map<String, EdgeDescriptor>,
        redirectMap: Map<String, String>
    ): Map<String, Double> {
        val grouped = edgeDescriptorsById.values.groupBy {
            val src = redirectMap[it.sourceId] ?: it.sourceId
            val tgt = redirectMap[it.targetId] ?: it.targetId
            "$src->$tgt"
        }
        val result = mutableMapOf<String, Double>()
        grouped.values.forEach { descriptors ->
            val sorted = descriptors.sortedBy { it.edgeId }
            val midpoint = (sorted.size - 1) / 2.0
            sorted.forEachIndexed { idx, descriptor ->
                result[descriptor.edgeId] = idx - midpoint
            }
        }
        return result
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

    private fun profileSettings(profile: CompoundLayoutProfile): ProfileSettings {
        return when (profile) {
            CompoundLayoutProfile.DEFAULT -> ProfileSettings(
                layerSpacing = 100.0,
                nodeSpacing = 100.0,
                edgeRouting = EdgeRouting.DIRECT
            )

            CompoundLayoutProfile.HIERARCHY_BIASED -> ProfileSettings(
                layerSpacing = 130.0,
                nodeSpacing = 110.0,
                edgeRouting = EdgeRouting.DIRECT
            )

            CompoundLayoutProfile.ORTHOGONAL_BIASED -> ProfileSettings(
                layerSpacing = 110.0,
                nodeSpacing = 110.0,
                edgeRouting = EdgeRouting.RECTILINEAR
            )

            CompoundLayoutProfile.COMPACT -> ProfileSettings(
                layerSpacing = 70.0,
                nodeSpacing = 70.0,
                edgeRouting = EdgeRouting.DIRECT
            )

            CompoundLayoutProfile.CHANNEL_BIASED -> ProfileSettings(
                layerSpacing = 95.0,
                nodeSpacing = 130.0,
                edgeRouting = EdgeRouting.DIRECT
            )
        }
    }

    private fun fallbackContainerMetrics(childPlan: PlannedGraphLayout?): ContainerChildHostMetrics {
        return if (childPlan == null) {
            ContainerChildHostMetrics()
        } else {
            config.defaultContainerChildHostMetrics
        }
    }

    private fun tessellateNodes(
        nodes: List<GraphLayoutCompoundNode>,
        nodeSizesById: Map<String, Pair<Double, Double>>
    ): TiledLayout {
        val count = nodes.size
        val cols = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toDouble() / cols.toDouble()).toInt().coerceAtLeast(1)

        val cellWidth = nodes.maxOf { node -> nodeSizesById.getValue(node.id).first }
        val cellHeight = nodes.maxOf { node -> nodeSizesById.getValue(node.id).second }

        val tiledNodeSizes = nodes.associate { node ->
            node.id to (cellWidth to cellHeight)
        }

        val positions = nodes.mapIndexed { index, node ->
            val row = index / cols
            val col = index % cols
            node.id to (col * cellWidth to row * cellHeight)
        }.toMap()

        return TiledLayout(
            nodeSizesById = tiledNodeSizes,
            nodeTopLeftById = positions,
            contentWidth = cols * cellWidth,
            contentHeight = rows * cellHeight
        )
    }
}

