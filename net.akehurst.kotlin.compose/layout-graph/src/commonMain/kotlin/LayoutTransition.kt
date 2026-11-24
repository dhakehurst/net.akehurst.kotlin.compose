package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.math.max

/**
 * What is drawn part way through the transition from one layout to the next: [layout] holds every node,
 * graph and edge of both layouts at its drawn place, and [nodeAlpha] / [edgeAlpha] say how opaque each is
 * (1 when missing from the map).
 */
internal data class DrawnLayout(
    val layout: CompoundLayoutResult,
    val nodeAlpha: Map<String, Float> = emptyMap(),
    val edgeAlpha: Map<String, Float> = emptyMap(),
)

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

/**
 * The layout drawn at [progress] (0 to 1, already eased) of the transition from [from] to [to].
 *
 * - Nodes in both move and resize from their old bounds to their new bounds.
 * - New nodes fade in, moving out from the old centre of the node they grow out of: their container,
 *   else a node they are connected to, else [focusNodeId]; with none of those they fade in at their place.
 * - Removed nodes fade out at their old place.
 * - Edges in both: the routes are resampled to the same number of points along their length and
 *   interpolated. New edges fade in and removed edges fade out, their ends following their nodes.
 *
 * At progress 0 the nodes and edges of [from] are drawn where they were; at progress 1 the result is [to].
 */
internal fun interpolateLayouts(
    from: CompoundLayoutResult,
    to: CompoundLayoutResult,
    progress: Float,
    focusNodeId: String? = null,
): DrawnLayout {
    if (progress >= 1f) return DrawnLayout(to)
    val t = progress.coerceAtLeast(0f).toDouble()

    // Where each node starts (in the 'from' space) and ends (in the 'to' space).
    val nodeAlpha = mutableMapOf<String, Float>()
    val startById = mutableMapOf<String, CompoundNodeLayout>()
    val endById = mutableMapOf<String, CompoundNodeLayout>()
    to.nodeLayoutsById.forEach { (id, end) ->
        val start = from.nodeLayoutsById[id] ?: run {
            nodeAlpha[id] = progress
            val anchor = anchorFor(id, end, from, to, focusNodeId)
            if (anchor == null) end else {
                // Centred on the anchor's old centre.
                val cx = anchor.globalX + anchor.width / 2.0
                val cy = anchor.globalY + anchor.height / 2.0
                val gx = cx - end.width / 2.0
                val gy = cy - end.height / 2.0
                end.copy(globalX = gx, globalY = gy, localX = end.localX + (gx - end.globalX), localY = end.localY + (gy - end.globalY))
            }
        }
        startById[id] = start
        endById[id] = end
    }
    from.nodeLayoutsById.forEach { (id, start) ->
        if (id !in to.nodeLayoutsById) {
            nodeAlpha[id] = 1f - progress
            startById[id] = start
            endById[id] = start
        }
    }
    val nodes = LinkedHashMap<String, CompoundNodeLayout>()
    endById.forEach { (id, end) ->
        val start = startById.getValue(id)
        nodes[id] = end.copy(
            localX = lerp(start.localX, end.localX, t),
            localY = lerp(start.localY, end.localY, t),
            globalX = lerp(start.globalX, end.globalX, t),
            globalY = lerp(start.globalY, end.globalY, t),
            width = lerp(start.width, end.width, t),
            height = lerp(start.height, end.height, t),
        )
    }

    // Graphs: their content origin moves with their container node, so children are placed consistently.
    val graphs = LinkedHashMap<String, CompoundGraphLayout>()
    fun containerShift(graph: CompoundGraphLayout, inSpace: Map<String, CompoundNodeLayout>): Pair<Double, Double> {
        val container = graph.containerNodeId ?: return 0.0 to 0.0
        val drawn = nodes[container] ?: return 0.0 to 0.0
        val ref = inSpace[container] ?: return 0.0 to 0.0
        return (drawn.globalX - ref.globalX) to (drawn.globalY - ref.globalY)
    }
    // A graph's content keeps its place inside its container as in the new layout (a collapsed graph's
    // offsets in the old layout mean nothing), so it moves with the drawn container.
    to.graphLayoutsById.forEach { (id, end) ->
        val start = from.graphLayoutsById[id]
        val (dx, dy) = containerShift(end, endById)
        graphs[id] = end.copy(
            globalOffsetX = end.globalOffsetX + dx,
            globalOffsetY = end.globalOffsetY + dy,
            contentWidth = if (start != null) lerp(start.contentWidth, end.contentWidth, t) else end.contentWidth,
            contentHeight = if (start != null) lerp(start.contentHeight, end.contentHeight, t) else end.contentHeight,
        )
    }
    from.graphLayoutsById.forEach { (id, start) ->
        if (id !in graphs) {
            val (dx, dy) = containerShift(start, startById)
            graphs[id] = start.copy(globalOffsetX = start.globalOffsetX + dx, globalOffsetY = start.globalOffsetY + dy)
        }
    }

    // Edges.
    val edgeAlpha = mutableMapOf<String, Float>()
    val routes = LinkedHashMap<String, List<Pair<Double, Double>>>()
    val endpoints = LinkedHashMap<String, Pair<String, String>>()
    endpoints.putAll(from.edgeEndpointsByEdgeId)
    endpoints.putAll(to.edgeEndpointsByEdgeId)
    fun centre(n: CompoundNodeLayout) = (n.globalX + n.width / 2.0) to (n.globalY + n.height / 2.0)
    /**
     * A new edge grows from (a removed edge shrinks to) the straight line between where its end nodes
     * start (end): each point moves between its place on the route and its place on that line.
     */
    fun grow(route: List<Pair<Double, Double>>, a: String?, b: String?, line: Map<String, CompoundNodeLayout>, towardsRoute: Double): List<Pair<Double, Double>> {
        val na = a?.let { line[it] }
        val nb = b?.let { line[it] }
        if (route.size < 2 || na == null || nb == null) return route
        val (ax, ay) = centre(na)
        val (bx, by) = centre(nb)
        val fractions = fractionsAlong(route)
        return route.mapIndexed { i, (x, y) ->
            val f = fractions[i]
            lerp(lerp(ax, bx, f), x, towardsRoute) to lerp(lerp(ay, by, f), y, towardsRoute)
        }
    }
    val refRouteById = mutableMapOf<String, Pair<List<Pair<Double, Double>>, Map<String, CompoundNodeLayout>>>()
    // For edges in both layouts, the route they move from (their ends slide round the node border).
    val fromRouteById = mutableMapOf<String, List<Pair<Double, Double>>>()
    to.edgeRoutesByEdgeId.forEach { (id, end) ->
        val start = from.edgeRoutesByEdgeId[id]
        val ends = to.edgeEndpointsByEdgeId[id]
        refRouteById[id] = end to to.nodeLayoutsById
        routes[id] = if (start != null && start.size >= 2 && end.size >= 2) {
            fromRouteById[id] = start
            interpolateRoutes(start, end, t)
        } else {
            if (start == null) edgeAlpha[id] = progress
            grow(end, ends?.first, ends?.second, startById, t)
        }
    }
    from.edgeRoutesByEdgeId.forEach { (id, start) ->
        if (id !in routes) {
            edgeAlpha[id] = 1f - progress
            val ends = from.edgeEndpointsByEdgeId[id]
            refRouteById[id] = start to from.nodeLayoutsById
            routes[id] = grow(start, ends?.first, ends?.second, endById, 1.0 - t)
        }
    }
    // The ends of every drawn route sit on the border of their drawn nodes, on the same side and at the
    // same place along it as on the route they are moving to (or, when fading out, from).
    routes.keys.toList().forEach { id ->
        val route = routes.getValue(id)
        val ends = endpoints[id] ?: return@forEach
        val (refRoute, refNodes) = refRouteById[id] ?: return@forEach
        if (route.size < 2 || refRoute.size < 2) return@forEach
        val snapped = route.toMutableList()
        val fromRoute = fromRouteById[id]
        fun place(nodeId: String, refPoint: Pair<Double, Double>, fromPoint: Pair<Double, Double>?): Pair<Double, Double>? {
            val drawnNode = nodes[nodeId] ?: return null
            val ref = refNodes[nodeId] ?: return null
            val toS = perimeterPosition(refPoint, ref)
            val fromNode = from.nodeLayoutsById[nodeId]
            val s = if (fromPoint != null && fromNode != null) {
                // Round the border the short way, from where the end was to where it goes.
                val fromS = perimeterPosition(fromPoint, fromNode)
                var d = toS - fromS
                if (d > 2.0) d -= 4.0
                if (d < -2.0) d += 4.0
                ((fromS + d * t) % 4.0 + 4.0) % 4.0
            } else toS
            return pointAtPerimeter(s, drawnNode)
        }
        place(ends.first, refRoute.first(), fromRoute?.first())?.let { snapped[0] = it }
        place(ends.second, refRoute.last(), fromRoute?.last())?.let { snapped[snapped.lastIndex] = it }
        routes[id] = snapped
    }

    val buses = to.edgeBuses + from.edgeBuses.filter { bus -> bus.edgeIds.none { it in to.edgeRoutesByEdgeId } }
    return DrawnLayout(
        layout = to.copy(
            nodeLayoutsById = nodes,
            graphLayoutsById = graphs,
            edgeRoutesByEdgeId = routes,
            edgeEndpointsByEdgeId = endpoints,
            edgeBuses = buses,
        ),
        nodeAlpha = nodeAlpha,
        edgeAlpha = edgeAlpha,
    )
}

/**
 * Where [point] is round the border of [node], from 0 to 4: top 0..1 (left to right), right 1..2,
 * bottom 2..3 (right to left), left 3..4. A point not on the border is taken to the nearest side.
 */
private fun perimeterPosition(point: Pair<Double, Double>, node: CompoundNodeLayout): Double {
    val (x, y) = point
    val fx = if (node.width > 0.0) ((x - node.globalX) / node.width).coerceIn(0.0, 1.0) else 0.5
    val fy = if (node.height > 0.0) ((y - node.globalY) / node.height).coerceIn(0.0, 1.0) else 0.5
    val dLeft = abs(x - node.globalX)
    val dRight = abs(x - (node.globalX + node.width))
    val dTop = abs(y - node.globalY)
    val dBottom = abs(y - (node.globalY + node.height))
    return when (minOf(dLeft, dRight, dTop, dBottom)) {
        dTop -> fx
        dRight -> 1.0 + fy
        dBottom -> 2.0 + (1.0 - fx)
        else -> (3.0 + (1.0 - fy)) % 4.0
    }
}

/** The point at [s] (see [perimeterPosition]) round the border of [node]. */
private fun pointAtPerimeter(s: Double, node: CompoundNodeLayout): Pair<Double, Double> {
    val x = node.globalX; val y = node.globalY; val w = node.width; val h = node.height
    return when {
        s < 1.0 -> x + s * w to y
        s < 2.0 -> x + w to y + (s - 1.0) * h
        s < 3.0 -> x + (1.0 - (s - 2.0)) * w to y + h
        else -> x to y + (1.0 - (s - 3.0)) * h
    }
}

/** The node in [from] that new node [id] grows out of, or null. */
private fun anchorFor(
    id: String,
    node: CompoundNodeLayout,
    from: CompoundLayoutResult,
    to: CompoundLayoutResult,
    focusNodeId: String?,
): CompoundNodeLayout? {
    // Its container (or the container's container, ...), for example the node that was expanded.
    var graphId: String? = node.ownerGraphId
    while (graphId != null) {
        val graph = to.graphLayoutsById[graphId] ?: break
        val container = graph.containerNodeId ?: break
        from.nodeLayoutsById[container]?.let { return it }
        graphId = graph.parentGraphId
    }
    node.borderHostId?.let { host -> from.nodeLayoutsById[host]?.let { return it } }
    // A node it is connected to.
    to.edgeEndpointsByEdgeId.entries.sortedBy { it.key }.forEach { (_, ends) ->
        val other = when (id) {
            ends.first -> ends.second
            ends.second -> ends.first
            else -> null
        }
        other?.let { from.nodeLayoutsById[it] }?.let { return it }
    }
    return focusNodeId?.let { from.nodeLayoutsById[it] }
}

/** For each point of [route], how far along the route it is (0 at the start, 1 at the end). */
private fun fractionsAlong(route: List<Pair<Double, Double>>): DoubleArray {
    val result = DoubleArray(route.size)
    var total = 0.0
    for (i in 1 until route.size) {
        total += abs(route[i].first - route[i - 1].first) + abs(route[i].second - route[i - 1].second)
        result[i] = total
    }
    if (total <= 0.0) {
        for (i in result.indices) result[i] = if (route.size > 1) i.toDouble() / (route.size - 1) else 0.0
    } else {
        for (i in result.indices) result[i] /= total
    }
    return result
}

/** [route] resampled to [count] points spread evenly along its length (both ends kept). */
internal fun resampleRoute(route: List<Pair<Double, Double>>, count: Int): List<Pair<Double, Double>> {
    if (route.size < 2 || count < 2) return route
    val fractions = fractionsAlong(route)
    var seg = 1
    return List(count) { k ->
        val f = k.toDouble() / (count - 1)
        while (seg < route.lastIndex && fractions[seg] < f) seg++
        val f0 = fractions[seg - 1]
        val f1 = fractions[seg]
        val u = if (f1 - f0 <= 0.0) 0.0 else ((f - f0) / (f1 - f0)).coerceIn(0.0, 1.0)
        val a = route[seg - 1]
        val b = route[seg]
        lerp(a.first, b.first, u) to lerp(a.second, b.second, u)
    }
}

/**
 * The route part way between [start] and [end]. When both have the same number of points, each point moves
 * to its counterpart, so orthogonal routes stay orthogonal where they bend alike; otherwise both are resampled.
 */
internal fun interpolateRoutes(start: List<Pair<Double, Double>>, end: List<Pair<Double, Double>>, t: Double): List<Pair<Double, Double>> {
    val (a, b) = if (start.size == end.size) start to end else {
        val n = max(16, max(start.size, end.size) * 4)
        resampleRoute(start, n) to resampleRoute(end, n)
    }
    return a.indices.map { i -> lerp(a[i].first, b[i].first, t) to lerp(a[i].second, b[i].second, t) }
}
