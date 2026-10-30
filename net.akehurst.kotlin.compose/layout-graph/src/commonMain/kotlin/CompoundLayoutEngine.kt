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
    /** Size of a port before its content is measured, when it has no hints. */
    val defaultPortWidth: Double = 20.0,
    val defaultPortHeight: Double = 20.0,
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
    val isContainer: Boolean,
    /** For a port: the id of the node that owns it, and the side of that node it is on. */
    val borderHostId: String? = null,
    val borderSide: BorderSide? = null
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
     * when the container is wider than they need, or to keep them clear of border nodes on the left side.
     * The child area starts at globalOffsetX - centringX.
     */
    val centringX: Double = 0.0,
    /**
     * How far the children are shifted down inside the container's child area, to keep them clear of
     * border nodes on the top side. The child area starts at globalOffsetY - shiftY.
     */
    val shiftY: Double = 0.0
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
    val contentHeight: Double,
    /**
     * Space reserved around a host for the border nodes that stick out of it (left, top, right, bottom).
     * The laid-out size includes it; the host itself is inset by it.
     */
    val hostInsetsById: Map<String, List<Double>> = emptyMap(),
    /** Room kept inside a container host for the part of its border nodes that reaches in (left, top, right, bottom). */
    val hostInnerInsetsById: Map<String, List<Double>> = emptyMap()
)

private data class EdgeDescriptor(
    val edgeId: String,
    val ownerGraphId: String,
    val sourceId: String,
    val targetId: String
)

private data class CompoundGraphIndex(
    val nodeOwnerByNodeId: Map<String, String>,
    val parentGraphByGraphId: Map<String, String?>,
    /** Port id to the id of the node that owns it. */
    val borderHostByNodeId: Map<String, String> = emptyMap(),
    val portsById: Map<String, GraphLayoutCompoundPort> = emptyMap()
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
        if (index.borderHostByNodeId.isEmpty()) {
            return layoutOnce(state, index, containerMetricsByNodeId, leafSizesByNodeId, emptyMap())
        }
        // Border nodes: the first pass chooses the free sides from where the connected nodes are;
        // the second pass makes room for the border nodes on those sides.
        val first = layoutOnce(state, index, containerMetricsByNodeId, leafSizesByNodeId, emptyMap())
        val sides = first.nodeLayoutsById.values
            .filter { it.borderHostId != null && it.borderSide != null }
            .associate { it.nodeId to it.borderSide!! }
        return layoutOnce(state, index, containerMetricsByNodeId, leafSizesByNodeId, sides)
    }

    private fun layoutOnce(
        state: GraphLayoutCompoundGraphState,
        index: CompoundGraphIndex,
        containerMetricsByNodeId: Map<String, ContainerChildHostMetrics>,
        leafSizesByNodeId: Map<String, Pair<Double, Double>>,
        /** Sides of border nodes known before this pass (chosen in an earlier pass). Pinned sides are always known. */
        knownBorderSides: Map<String, BorderSide>
    ): CompoundLayoutResult {
        val initialChildLayout = state.root.childLayout ?: config.defaultChildLayout
        val borderSizesById = mutableMapOf<String, Pair<Double, Double>>()
        val borderFootprintsById = mutableMapOf<String, List<Double>>()
        val allEdges = mutableListOf<GraphLayoutCompoundEdge>()
        fun collectEdges(g: GraphLayoutCompoundGraph) {
            allEdges.addAll(g.edges.values)
            g.children.values.forEach(::collectEdges)
        }
        collectEdges(state.root)
        allEdges.sortBy { it.id }
        val rootPlan = planGraph(
            graph = state.root,
            index = index,
            inheritedChildLayout = initialChildLayout,
            containerMetricsByNodeId = containerMetricsByNodeId,
            leafSizesByNodeId = leafSizesByNodeId,
            knownBorderSides = knownBorderSides,
            borderSizesById = borderSizesById,
            allEdges = allEdges,
            borderFootprintsById = borderFootprintsById
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
        placeBorderNodes(
            root = state.root,
            index = index,
            knownBorderSides = knownBorderSides,
            borderSizesById = borderSizesById,
            borderFootprintsById = borderFootprintsById,
            edgeDescriptors = edgeDescriptorsById.values,
            collapsedRedirectMap = collapsedRedirectMap,
            nodeLayouts = nodeLayouts,
            graphLayouts = graphLayouts
        )
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
                        val top = g.globalOffsetY - g.shiftY - n.globalY
                        val m = containerMetricsByNodeId[n.nodeId]
                        listOf(left, top, m?.insetRight ?: left, m?.insetBottom ?: left)
                    } ?: listOf(0.0, 0.0, 0.0, 0.0),
                    borderHostId = n.borderHostId,
                    borderSide = n.borderSide
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
        leafSizesByNodeId: Map<String, Pair<Double, Double>>,
        knownBorderSides: Map<String, BorderSide>,
        /** Collects the size of every port, for placing them after the layout. */
        borderSizesById: MutableMap<String, Pair<Double, Double>>,
        allEdges: List<GraphLayoutCompoundEdge>,
        /** Collects how far each port's own ports stick out of it (left, top, right, bottom). */
        borderFootprintsById: MutableMap<String, List<Double>>
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
                    leafSizesByNodeId = leafSizesByNodeId,
                    knownBorderSides = knownBorderSides,
                    borderSizesById = borderSizesById,
                    allEdges = allEdges,
                    borderFootprintsById = borderFootprintsById
                )
            }

        // All container node IDs regardless of collapse state (used for isContainer flag).
        val containerNodeIds = graph.nodes.keys.filter { it in graph.children }.toSet()

        val nodes = graph.nodes.values.sortedBy { it.id }
        // Ports are not laid out with the nodes; they are placed on their owner's border afterwards.
        val ownSizesById = nodes.associate { node ->
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
        // A port takes its measured content size, never smaller than its hints.
        val allPorts = mutableListOf<GraphLayoutCompoundPort>()
        fun collectNested(list: List<GraphLayoutCompoundPort>) {
            list.forEach { allPorts.add(it); collectNested(it.ports) }
        }
        nodes.forEach { collectNested(it.ports) }
        val baseSizeById = allPorts.associate { port ->
            val measured = leafSizesByNodeId[port.id]
            val w = if (measured != null) max(port.widthHint ?: 0.0, measured.first) else (port.widthHint ?: config.defaultPortWidth)
            val h = if (measured != null) max(port.heightHint ?: 0.0, measured.second) else (port.heightHint ?: config.defaultPortHeight)
            port.id to (w to h)
        }
        // A port with ports of its own grows to fit them, like any host; its footprint is how far they stick out.
        val portSizesById = mutableMapOf<String, Pair<Double, Double>>()
        fun sizeOf(port: GraphLayoutCompoundPort): Pair<Double, Double> = portSizesById[port.id] ?: run {
            val base = baseSizeById.getValue(port.id)
            val size = if (port.ports.isEmpty()) {
                borderFootprintsById[port.id] = listOf(0.0, 0.0, 0.0, 0.0)
                base
            } else {
                val req = sideRequirements(port.ports, knownBorderSides, ::sizeOf, borderFootprintsById)
                borderFootprintsById[port.id] = req.outside
                max(base.first, max(req.needed(1), req.needed(3))) to max(base.second, max(req.needed(0), req.needed(2)))
            }
            portSizesById[port.id] = size
            size
        }
        allPorts.forEach { sizeOf(it) }
        borderSizesById.putAll(portSizesById)

        // An owner must be long enough on each side for its ports there, and the layout reserves
        // room around it for the part of them that sticks out.
        val hostInsetsById = mutableMapOf<String, List<Double>>()
        val hostInnerInsetsById = mutableMapOf<String, List<Double>>()
        val hostMinSizeById = mutableMapOf<String, Pair<Double, Double>>()
        nodes.filter { it.ports.isNotEmpty() }.forEach { owner ->
            val hostId = owner.id
            val req = sideRequirements(owner.ports, knownBorderSides, ::sizeOf, borderFootprintsById)
            hostInsetsById[hostId] = req.outside
            // In a container, children keep clear of the ports, with a channel for edges between them
            // (wider than the router keeps clear of both), and left and right ports stay beside the child
            // area (not beside a header).
            val hostIsContainer = hostId in childPlansById
            val headerHeight = if (hostIsContainer) (containerMetricsByNodeId[hostId] ?: fallbackContainerMetrics(childPlansById[hostId])).originY else 0.0
            if (hostIsContainer) {
                hostInnerInsetsById[hostId] = req.inside.mapIndexed { i, d -> if (req.counts[i] == 0) 0.0 else d + 2 * config.edgeRouter.margin + borderNodeGap }
            }
            hostMinSizeById[hostId] = max(req.needed(1), req.needed(3)) to (max(req.needed(0), req.needed(2)) + headerHeight)
        }
        val nodeSizesById = nodes.associate { node ->
            val inner = hostInnerInsetsById[node.id] ?: listOf(0.0, 0.0, 0.0, 0.0)
            val (w, h) = ownSizesById.getValue(node.id).let { (w, h) -> (w + inner[0] + inner[2]) to (h + inner[1] + inner[3]) }
            val min = hostMinSizeById[node.id] ?: (0.0 to 0.0)
            val insets = hostInsetsById[node.id] ?: listOf(0.0, 0.0, 0.0, 0.0)
            node.id to ((max(w, min.first) + insets[0] + insets[2]) to (max(h, min.second) + insets[1] + insets[3]))
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
                if (sourceNodeId != null && targetNodeId != null && sourceNodeId != targetNodeId &&
                    sourceNodeId in graph.nodes && targetNodeId in graph.nodes
                ) {
                    ChildLayoutEdge(edge.id, sourceNodeId, targetNodeId)
                } else {
                    null
                }
            }

        // Ports of the container whose child graph this is are visible inside it too: a child connected to
        // a port on the left side is pulled to the left, and to the right for a port on the right.
        val pullById = mutableMapOf<String, Double>()
        allEdges.forEach { edge ->
            listOf(edge.sourceId to edge.targetId, edge.targetId to edge.sourceId).forEach { (endId, otherId) ->
                // The port on the container itself (for a port on a port, the outer one).
                var portId = endId
                while (index.borderHostByNodeId[portId] in index.portsById) portId = index.borderHostByNodeId.getValue(portId)
                if (index.borderHostByNodeId[portId] != graph.id) return@forEach
                val port = index.portsById.getValue(portId)
                val sign = when (port.side ?: knownBorderSides[portId]) {
                    BorderSide.LEFT -> -1.0
                    BorderSide.RIGHT -> 1.0
                    else -> return@forEach
                }
                val child = projectToLevelNode(graph.id, otherId, index) ?: return@forEach
                if (child in graph.nodes) pullById[child] = (pullById[child] ?: 0.0) + sign
            }
        }

        val laidOut = effectiveChildLayout.layout(
            ChildLayoutInput(
                graphId = graph.id,
                nodes = nodes,
                nodeSizesById = nodeSizesById,
                edges = edges,
                horizontalPullById = pullById
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
            contentHeight = laidOut.contentHeight,
            hostInsetsById = hostInsetsById,
            hostInnerInsetsById = hostInnerInsetsById
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
        /** Vertical shift of the children inside the container's child area (included in the offsets). */
        shiftY: Double = 0.0,
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
            centringX = centring,
            shiftY = shiftY
        )

        plan.nodeOrder.forEach { node ->
            // A host is inset by the room reserved for its border nodes.
            val insets = plan.hostInsetsById[node.id] ?: listOf(0.0, 0.0, 0.0, 0.0)
            val laidOutTopLeft = plan.nodeTopLeftById.getValue(node.id)
            val localTopLeft = (laidOutTopLeft.first + insets[0]) to (laidOutTopLeft.second + insets[1])
            val globalTopLeft = (globalOffset.first + localTopLeft.first) to (globalOffset.second + localTopLeft.second)
            val laidOutSize = plan.nodeSizesById.getValue(node.id)
            val size = (laidOutSize.first - insets[0] - insets[2]) to (laidOutSize.second - insets[1] - insets[3])

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
            // Room kept for border nodes that reach into the container.
            val inner = plan.hostInnerInsetsById[childId] ?: listOf(0.0, 0.0, 0.0, 0.0)
            val available = childContainer.width - origin.first - (plan.childTrailingXByContainerNodeId[childId] ?: 0.0) - inner[0] - inner[2]
            // Tessellated children fill the area instead (spec 6.6), so they are not centred.
            val centring = inner[0] + if (childPlan.childLayout == TessellateChildLayout) 0.0 else max(0.0, (available - childPlan.contentWidth) / 2.0)
            val shiftY = inner[1]
            val childOrigin = (origin.first + centring) to (origin.second + shiftY)
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
                shiftY = shiftY,
                graphLayouts = graphLayouts,
                edgeRoutesByEdgeId = edgeRoutesByEdgeId
            )
        }
    }

    // ── Border nodes ────────────────────────────────────────────────────────

    /** Space between border nodes on the same side, and between them and the corners. */
    private val borderNodeGap = 8.0

    /** Index into (left, top, right, bottom) lists. */
    private fun sideIndex(side: BorderSide): Int = when (side) {
        BorderSide.LEFT -> 0
        BorderSide.TOP -> 1
        BorderSide.RIGHT -> 2
        BorderSide.BOTTOM -> 3
    }

    /** What a set of ports needs from the node they are on, per side (left, top, right, bottom). */
    private class SideRequirements(
        /** How far they stick out of the node. */
        val outside: List<Double>,
        /** How far they reach into the node. */
        val inside: List<Double>,
        val lengths: List<Double>,
        val counts: List<Int>,
        private val gap: Double
    ) {
        /** The length a side needs for its ports, with gaps between them and at the corners. */
        fun needed(i: Int) = if (counts[i] == 0) 0.0 else lengths[i] + gap * (counts[i] + 1)
    }

    /** Ports whose side is not known yet (free, before the first pass chooses it) are left out. */
    private fun sideRequirements(
        ports: List<GraphLayoutCompoundPort>,
        knownBorderSides: Map<String, BorderSide>,
        sizeOf: (GraphLayoutCompoundPort) -> Pair<Double, Double>,
        footprints: Map<String, List<Double>>
    ): SideRequirements {
        val outside = DoubleArray(4)
        val inside = DoubleArray(4)
        val lengths = DoubleArray(4)
        val counts = IntArray(4)
        ports.forEach { port ->
            val side = port.side ?: knownBorderSides[port.id] ?: return@forEach
            val (w, h) = sizeOf(port)
            val fp = footprints[port.id] ?: listOf(0.0, 0.0, 0.0, 0.0)
            val i = sideIndex(side)
            val horizontal = side == BorderSide.TOP || side == BorderSide.BOTTOM
            val depth = if (horizontal) h else w
            // Along the side, the port's own ports on its ends take room too.
            val length = if (horizontal) w + fp[0] + fp[2] else h + fp[1] + fp[3]
            val out = outsideDepth(depth, port.placement)
            outside[i] = max(outside[i], out + fp[i])
            inside[i] = max(inside[i], depth - out + fp[(i + 2) % 4])
            lengths[i] += length
            counts[i]++
        }
        return SideRequirements(outside.toList(), inside.toList(), lengths.toList(), counts.toList(), borderNodeGap)
    }

    /** How far a border node of the given depth sticks out of its host. */
    private fun outsideDepth(depth: Double, placement: BorderPlacement): Double = when (placement) {
        BorderPlacement.STRADDLE -> depth / 2.0
        BorderPlacement.INSIDE -> 0.0
        BorderPlacement.OUTSIDE -> depth
    }

    /**
     * Places every border node on its host's border, once the hosts and the nodes they connect to are placed.
     *
     * A free side faces the nodes the border node connects to (on average). Along the side, border nodes
     * are ordered by, and placed as close as they can be to, the position of those nodes.
     */
    private fun placeBorderNodes(
        root: GraphLayoutCompoundGraph,
        index: CompoundGraphIndex,
        knownBorderSides: Map<String, BorderSide>,
        borderSizesById: Map<String, Pair<Double, Double>>,
        borderFootprintsById: Map<String, List<Double>>,
        edgeDescriptors: Collection<EdgeDescriptor>,
        collapsedRedirectMap: Map<String, String>,
        nodeLayouts: MutableMap<String, CompoundNodeLayout>,
        graphLayouts: Map<String, CompoundGraphLayout>
    ) {
        val borderNodes = mutableListOf<Triple<GraphLayoutCompoundPort, String, String>>() // port, owner id, owner's graph id
        fun walk(graph: GraphLayoutCompoundGraph) {
            graph.nodes.values.sortedBy { it.id }.forEach { owner -> owner.ports.forEach { borderNodes.add(Triple(it, owner.id, graph.id)) } }
            graph.children.values.filter { !it.isCollapsed }.forEach(::walk)
        }
        walk(root)
        if (borderNodes.isEmpty()) return

        fun centreOf(id: String): Pair<Double, Double>? {
            val resolved = collapsedRedirectMap[id] ?: id
            // A port that is not placed yet: the nearest placed node it is on stands in for it.
            var id2: String? = resolved
            var n: CompoundNodeLayout? = null
            while (id2 != null && n == null) {
                n = nodeLayouts[id2]
                id2 = index.borderHostByNodeId[id2]
            }
            if (n == null) return null
            return (n.globalX + n.width / 2.0) to (n.globalY + n.height / 2.0)
        }

        // Centres of the nodes at the other end of each border node's edges.
        val otherEnds = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
        edgeDescriptors.sortedBy { it.edgeId }.forEach { e ->
            if (e.sourceId in index.borderHostByNodeId) centreOf(e.targetId)?.let { otherEnds.getOrPut(e.sourceId) { mutableListOf() }.add(it) }
            if (e.targetId in index.borderHostByNodeId) centreOf(e.sourceId)?.let { otherEnds.getOrPut(e.targetId) { mutableListOf() }.add(it) }
        }

        // A port's own ports connect through it, so their other ends count for it too.
        fun endsWithNested(id: String): List<Pair<Double, Double>> =
            otherEnds[id].orEmpty() + index.portsById[id]?.ports.orEmpty().flatMap { endsWithNested(it.id) }

        data class Item(val node: GraphLayoutCompoundPort, val ownerGraphId: String, val host: CompoundNodeLayout, val side: BorderSide, val ideal: Double)

        fun placeLevel(levelNodes: List<Triple<GraphLayoutCompoundPort, String, String>>) {
            val items = mutableListOf<Item>()
            val countBySide = mutableMapOf<Pair<String, BorderSide>, Int>()
            // Connected border nodes first, so unconnected ones can go to the least used side.
            val ordered = levelNodes.sortedWith(compareBy({ if (endsWithNested(it.first.id).isEmpty()) 1 else 0 }, { it.first.id }))
            ordered.forEach { (node, hostId, ownerGraphId) ->
                val host = nodeLayouts[hostId] ?: return@forEach
                val hostCx = host.globalX + host.width / 2.0
                val hostCy = host.globalY + host.height / 2.0
                val ends = endsWithNested(node.id)
                val meanX = if (ends.isEmpty()) hostCx else ends.sumOf { it.first } / ends.size
                val meanY = if (ends.isEmpty()) hostCy else ends.sumOf { it.second } / ends.size
                val chosen = node.side ?: knownBorderSides[node.id] ?: if (ends.isEmpty()) {
                    listOf(BorderSide.RIGHT, BorderSide.LEFT, BorderSide.BOTTOM, BorderSide.TOP)
                        .minBy { countBySide[hostId to it] ?: 0 }
                } else {
                    // Compare directions relative to the host's shape, so a wide host prefers top and bottom less.
                    val dx = (meanX - hostCx) / max(host.width / 2.0, 1.0)
                    val dy = (meanY - hostCy) / max(host.height / 2.0, 1.0)
                    if (abs(dx) >= abs(dy)) {
                        if (dx >= 0) BorderSide.RIGHT else BorderSide.LEFT
                    } else {
                        if (dy >= 0) BorderSide.BOTTOM else BorderSide.TOP
                    }
                }
                // A port on a port is not put on the side facing into the outer port's owner unless pinned there.
                val hostSide = host.borderSide
                val side = if (node.side == null && hostSide != null && sideIndex(chosen) == (sideIndex(hostSide) + 2) % 4) hostSide else chosen
                countBySide[hostId to side] = (countBySide[hostId to side] ?: 0) + 1
                val ideal = if (side == BorderSide.TOP || side == BorderSide.BOTTOM) meanX else meanY
                items.add(Item(node, ownerGraphId, host, side, ideal))
            }

            items.groupBy { it.host.nodeId to it.side }.entries.sortedBy { "${it.key.first}:${it.key.second}" }.forEach { (key, group) ->
                val (_, side) = key
                val host = group.first().host
                val horizontal = side == BorderSide.TOP || side == BorderSide.BOTTOM
                // Left and right border nodes of a container stay beside its child area, below any header.
                val header = graphLayouts[host.nodeId]?.let { g -> g.globalOffsetY - g.shiftY - host.globalY } ?: 0.0
                val lo = (if (horizontal) host.globalX else host.globalY + header) + borderNodeGap
                val hi = (if (horizontal) host.globalX + host.width else host.globalY + host.height) - borderNodeGap
                val sorted = group.sortedWith(compareBy({ it.ideal }, { it.node.id }))
                fun footprint(item: Item) = borderFootprintsById[item.node.id] ?: listOf(0.0, 0.0, 0.0, 0.0)
                // Along the side, a port's own ports on its ends take room too.
                fun length(item: Item): Double = borderSizesById.getValue(item.node.id).let { if (horizontal) it.first else it.second } +
                    footprint(item).let { if (horizontal) it[0] + it[2] else it[1] + it[3] }
                // Start of each node along the side: as near its ideal centre as possible, without overlaps, within the side.
                val starts = DoubleArray(sorted.size)
                sorted.forEachIndexed { i, item ->
                    val wanted = item.ideal - length(item) / 2.0
                    starts[i] = if (i == 0) max(lo, wanted) else max(wanted, starts[i - 1] + length(sorted[i - 1]) + borderNodeGap)
                }
                for (i in sorted.indices.reversed()) {
                    val limit = if (i == sorted.lastIndex) hi else starts[i + 1] - borderNodeGap
                    starts[i] = min(starts[i], limit - length(sorted[i]))
                }
                for (i in sorted.indices) {
                    // When there is not enough room (e.g. before the host was enlarged), keep the order from the start.
                    starts[i] = if (i == 0) max(starts[i], lo) else max(starts[i], starts[i - 1] + length(sorted[i - 1]) + borderNodeGap)
                }
                sorted.forEachIndexed { i, item ->
                    val (w, h) = borderSizesById.getValue(item.node.id)
                    val depth = if (horizontal) h else w
                    val out = outsideDepth(depth, item.node.placement)
                    val x: Double
                    val y: Double
                    when (side) {
                        BorderSide.TOP -> { x = starts[i] + footprint(item)[0]; y = host.globalY - out }
                        BorderSide.BOTTOM -> { x = starts[i] + footprint(item)[0]; y = host.globalY + host.height + out - depth }
                        BorderSide.LEFT -> { x = host.globalX - out; y = starts[i] + footprint(item)[1] }
                        BorderSide.RIGHT -> { x = host.globalX + host.width + out - depth; y = starts[i] + footprint(item)[1] }
                    }
                    val graph = graphLayouts[item.ownerGraphId]
                    nodeLayouts[item.node.id] = CompoundNodeLayout(
                        nodeId = item.node.id,
                        ownerGraphId = item.ownerGraphId,
                        localX = x - (graph?.globalOffsetX ?: 0.0),
                        localY = y - (graph?.globalOffsetY ?: 0.0),
                        globalX = x,
                        globalY = y,
                        width = w,
                        height = h,
                        isContainer = false,
                        borderHostId = item.host.nodeId,
                        borderSide = side
                    )
                }
            }
        }

        // Ports first, then ports on those ports, and so on.
        var level = borderNodes.toList()
        while (level.isNotEmpty()) {
            placeLevel(level)
            level = level.flatMap { (port, _, graphId) -> port.ports.map { Triple(it, port.id, graphId) } }
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
    private fun allPortIds(ports: List<GraphLayoutCompoundPort>): List<String> =
        ports.flatMap { listOf(it.id) + allPortIds(it.ports) }

    private fun buildCollapsedNodeRedirectMap(root: GraphLayoutCompoundGraph): Map<String, String> {
        val redirect = mutableMapOf<String, String>()

        fun walkCollapsed(graph: GraphLayoutCompoundGraph, collapsedContainerNodeId: String?) {
            if (collapsedContainerNodeId != null) {
                // Everything inside a collapsed ancestor → redirect to that ancestor container node.
                graph.nodes.values.forEach { node ->
                    redirect[node.id] = collapsedContainerNodeId
                    allPortIds(node.ports).forEach { redirect[it] = collapsedContainerNodeId }
                }
                graph.children.values.forEach { child -> walkCollapsed(child, collapsedContainerNodeId) }
            } else {
                graph.children.entries.forEach { (childId, childGraph) ->
                    if (childGraph.isCollapsed) {
                        // Direct children of this collapsed graph redirect to the container node.
                        childGraph.nodes.values.forEach { node ->
                            redirect[node.id] = childId
                            allPortIds(node.ports).forEach { redirect[it] = childId }
                        }
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
        val borderHostByNodeId = mutableMapOf<String, String>()
        val portsById = mutableMapOf<String, GraphLayoutCompoundPort>()

        fun walk(graph: GraphLayoutCompoundGraph, parentGraphId: String?) {
            parentGraphByGraphId[graph.id] = parentGraphId
            graph.nodes.values.forEach { node ->
                nodeOwnerByNodeId[node.id] = graph.id
                fun addPorts(hostId: String, ports: List<GraphLayoutCompoundPort>) {
                    ports.forEach { port ->
                        // A port belongs to the same graph as its owner (it is drawn there).
                        nodeOwnerByNodeId[port.id] = graph.id
                        borderHostByNodeId[port.id] = hostId
                        portsById[port.id] = port
                        addPorts(port.id, port.ports)
                    }
                }
                addPorts(node.id, node.ports)
            }
            graph.children.keys.sorted().forEach { childId ->
                walk(graph.children.getValue(childId), graph.id)
            }
        }

        walk(root, null)
        return CompoundGraphIndex(
            nodeOwnerByNodeId = nodeOwnerByNodeId,
            parentGraphByGraphId = parentGraphByGraphId,
            borderHostByNodeId = borderHostByNodeId,
            portsById = portsById
        )
    }

    private fun projectToLevelNode(graphId: String, borderOrNodeId: String, index: CompoundGraphIndex): String? {
        // For layout, an edge to a port counts as an edge to the node that owns it.
        var nodeId = borderOrNodeId
        while (true) nodeId = index.borderHostByNodeId[nodeId] ?: break
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
