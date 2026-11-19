package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How the bus of a node's edges is drawn, given by a [ChildLayoutAlgorithm] that knows its node placement
 * (see [ChildLayoutResult.busHintsByNodeId]). Used only with [EdgeRouting.BUS].
 */
data class BusHint(
    /** The side of the node the trunk leaves from. */
    val side: BorderSide,
    /**
     * False: the bus runs across the trunk (a T shape), midway between the node and its nearest branch node.
     * True: the bus continues straight on from the trunk and the branches leave it sideways, as in an indented tree.
     */
    val busAlongTrunk: Boolean = false,
    /**
     * Where the trunk leaves the side, in dp from the side's start (its left end for TOP and BOTTOM, its top end
     * for LEFT and RIGHT). Null uses the middle of the side.
     */
    val trunkOffset: Double? = null,
    /** True lets a single edge use the bus shape too, so a tree looks the same whatever the number of children. */
    val allowSingleEdge: Boolean = false
)

/**
 * A set of edges drawn with one shared trunk and bus at [nodeId], and a branch to each of their other ends.
 * [edgeIds] are sorted; the view draws the symbol at the shared end only for the first one.
 */
data class EdgeBus(
    val nodeId: String,
    val side: BorderSide,
    val edgeIds: List<String>
)

/**
 * An edge as seen by [BusEdgeRouter]. Edges only share a bus at a node when their keys at that end are equal
 * (for example the same bus group and the same end symbol).
 */
data class BusRouterEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val sourceKey: Any? = null,
    val targetKey: Any? = null
)

data class BusRoutingResult(
    /** Routes of the edges put on a bus, from source to target, in global coordinates. */
    val routesByEdgeId: Map<String, List<Pair<Double, Double>>>,
    val buses: List<EdgeBus>,
    /** Edges not put on a bus; route them some other way. */
    val unroutedEdgeIds: List<String>
)

/**
 * Merges edges that meet a node on the same side into one trunk, a bus and a branch per edge,
 * like the generalisation trees of UML class diagrams.
 *
 * An edge joins at most one bus: at the end with the bigger group, preferring the target end on a tie.
 * The direction of an edge does not matter. Edges whose bus would run through another node are left out
 * (see [BusRoutingResult.unroutedEdgeIds]). The result is deterministic for identical input.
 */
class BusEdgeRouter(
    /** Smallest gap kept between a bus and the nodes it connects. */
    val margin: Double = 12.0,
    /** Distance between buses that would otherwise lie on the same line. */
    val busSpacing: Double = 8.0
) {
    private val eps = 1e-6

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val cx get() = (left + right) / 2.0
        val cy get() = (top + bottom) / 2.0
    }

    /**
     * Coordinates turned so that [side] faces down: u grows away from the node through the side, v runs along it.
     */
    private class Frame(val side: BorderSide) {
        fun u(x: Double, y: Double) = when (side) {
            BorderSide.BOTTOM -> y
            BorderSide.TOP -> -y
            BorderSide.RIGHT -> x
            BorderSide.LEFT -> -x
        }

        fun v(x: Double, y: Double) = when (side) {
            BorderSide.BOTTOM, BorderSide.TOP -> x
            BorderSide.RIGHT, BorderSide.LEFT -> y
        }

        fun xy(u: Double, v: Double): Pair<Double, Double> = when (side) {
            BorderSide.BOTTOM -> v to u
            BorderSide.TOP -> v to -u
            BorderSide.RIGHT -> u to v
            BorderSide.LEFT -> -u to v
        }

        /** (uMin, uMax, vMin, vMax) of a rectangle. */
        fun box(r: Rect): List<Double> {
            val us = listOf(u(r.left, r.top), u(r.right, r.bottom))
            val vs = listOf(v(r.left, r.top), v(r.right, r.bottom))
            return listOf(us.min(), us.max(), vs.min(), vs.max())
        }
    }

    private data class Candidate(
        val nodeId: String,
        val side: BorderSide,
        val key: Any?,
        /** Edge id to the id of its other end. */
        val others: Map<String, String>,
        /** Edge ids whose target is the shared node. */
        val targetEnded: Set<String>
    )

    private data class Group(val nodeId: String, val side: BorderSide, val hint: BusHint?, val members: Map<String, String>)

    /** Where a line meets a node side: the bus or edge it belongs to and its position along the side. */
    private data class Attachment(val nodeId: String, val side: BorderSide, val owner: String, val order: Double)

    fun route(
        nodes: List<RouterNode>,
        edges: List<BusRouterEdge>,
        hintsByNodeId: Map<String, BusHint> = emptyMap()
    ): BusRoutingResult {
        val nodesById = nodes.associateBy { it.id }
        val rectById = nodes.associate { it.id to Rect(it.x, it.y, it.x + it.width, it.y + it.height) }

        fun ancestors(id: String): Set<String> {
            val result = mutableSetOf<String>()
            var current = nodesById[id]?.containerId
            while (current != null && result.add(current)) {
                current = nodesById[current]?.containerId
            }
            return result
        }

        val sortedEdges = edges.sortedBy { it.id }
        // Self loops, ports, and edges between a container and something inside it keep their usual routes.
        val eligible = sortedEdges.filter { e ->
            e.sourceId != e.targetId &&
                e.sourceId in rectById && e.targetId in rectById &&
                nodesById.getValue(e.sourceId).borderHostId == null && nodesById.getValue(e.targetId).borderHostId == null &&
                e.sourceId !in ancestors(e.targetId) && e.targetId !in ancestors(e.sourceId)
        }

        // Can the edge from [nodeId] to [otherId] leave [nodeId] through [side] and use a bus there?
        fun admissible(nodeId: String, otherId: String, side: BorderSide, hint: BusHint?): Boolean {
            val f = Frame(side)
            val n = f.box(rectById.getValue(nodeId))
            val o = f.box(rectById.getValue(otherId))
            return if (hint?.busAlongTrunk == true) {
                val trunkV = trunkV(n, hint)
                val oCentreU = (o[0] + o[1]) / 2.0
                oCentreU > n[1] + 2 * margin && (o[2] > trunkV + margin || o[3] < trunkV - margin)
            } else {
                o[0] - n[1] >= 2 * margin
            }
        }

        // Candidate buses: a node, a side, and the key of the edges' ends at that node.
        val candidates = mutableListOf<Candidate>()
        val candidateMembers = mutableMapOf<Triple<String, BorderSide, Any?>, Pair<MutableMap<String, String>, MutableSet<String>>>()
        eligible.forEach { e ->
            listOf(Triple(e.sourceId, e.targetId, e.sourceKey), Triple(e.targetId, e.sourceId, e.targetKey)).forEach { (nodeId, otherId, key) ->
                val hint = hintsByNodeId[nodeId]
                val sides = hint?.let { listOf(it.side) } ?: BorderSide.entries
                sides.filter { admissible(nodeId, otherId, it, hint) }.forEach { side ->
                    val entry = candidateMembers.getOrPut(Triple(nodeId, side, key)) { mutableMapOf<String, String>() to mutableSetOf() }
                    // Parallel edges to the same other node would draw on top of each other: keep the first.
                    if (otherId !in entry.first.values) {
                        entry.first[e.id] = otherId
                        if (nodeId == e.targetId) entry.second.add(e.id)
                    }
                }
            }
        }
        candidateMembers.forEach { (k, v) -> candidates.add(Candidate(k.first, k.second, k.third, v.first, v.second)) }

        // Greedy: the biggest group first; an edge joins at most one group.
        val assigned = mutableSetOf<String>()
        val groups = mutableListOf<Group>()
        val remaining = candidates.toMutableList()
        while (true) {
            val scored = remaining.mapNotNull { c ->
                val members = c.others.filterKeys { it !in assigned }
                val minSize = if (hintsByNodeId[c.nodeId]?.allowSingleEdge == true) 1 else 2
                if (members.size < minSize) null else Triple(c, members, members.keys.count { it in c.targetEnded })
            }
            val best = scored.maxWithOrNull(
                compareBy<Triple<Candidate, Map<String, String>, Int>>({ it.second.size }, { it.third })
                    .thenByDescending { it.first.nodeId }
                    .thenByDescending { it.first.side.ordinal }
            ) ?: break
            remaining.remove(best.first)
            groups.add(Group(best.first.nodeId, best.first.side, hintsByNodeId[best.first.nodeId], best.second))
            assigned.addAll(best.second.keys)
        }

        // Spread the lines that meet the same side of a node, so they do not overlap.
        val attachments = mutableListOf<Attachment>()
        groups.forEach { g ->
            val f = Frame(g.side)
            val othersV = g.members.values.map { val b = f.box(rectById.getValue(it)); (b[2] + b[3]) / 2.0 }
            if (g.hint?.trunkOffset == null) {
                attachments.add(Attachment(g.nodeId, g.side, "bus:${g.nodeId}:${g.side}:${groups.indexOf(g)}", othersV.average()))
            }
            val n = f.box(rectById.getValue(g.nodeId))
            g.members.forEach { (edgeId, otherId) ->
                val o = f.box(rectById.getValue(otherId))
                val arrivalSide = if (g.hint?.busAlongTrunk == true) {
                    // Branches leave the trunk sideways.
                    val trunkV = trunkV(n, g.hint)
                    sideOf(g.side, toLarger = o[2] > trunkV, across = true)
                } else {
                    opposite(g.side)
                }
                val sharedCentre = Frame(arrivalSide).let { af -> val b = af.box(rectById.getValue(g.nodeId)); (b[2] + b[3]) / 2.0 }
                attachments.add(Attachment(otherId, arrivalSide, "edge:$edgeId", sharedCentre))
            }
        }
        val slotByOwnerAndNode = mutableMapOf<Pair<String, String>, Double>()
        attachments.groupBy { it.nodeId to it.side }.forEach { (key, list) ->
            val (nodeId, side) = key
            val f = Frame(side)
            val b = f.box(rectById.getValue(nodeId))
            val sorted = list.sortedWith(compareBy({ it.order }, { it.owner }))
            sorted.forEachIndexed { i, a ->
                val v = if (sorted.size == 1) (b[2] + b[3]) / 2.0 else b[2] + (b[3] - b[2]) * (i + 1) / (sorted.size + 1)
                slotByOwnerAndNode[a.owner to nodeId] = v
            }
        }

        // Obstacles for a group: every node that is not one of its ends, or a container of / inside one of them.
        fun obstaclesFor(endIds: Set<String>): List<Rect> {
            val related = endIds.flatMap { ancestors(it) }.toSet() + endIds
            return nodes.filter { n -> n.id !in related && ancestors(n.id).none { it in endIds } }
                .sortedBy { it.id }
                .map { rectById.getValue(it.id) }
        }

        fun hits(a: Pair<Double, Double>, b: Pair<Double, Double>, obstacles: List<Rect>): Boolean {
            val l = min(a.first, b.first); val r = max(a.first, b.first)
            val t = min(a.second, b.second); val btm = max(a.second, b.second)
            return obstacles.any { o -> l < o.right - eps && r > o.left + eps && t < o.bottom - eps && btm > o.top + eps }
        }

        data class Built(val busCoord: Double, val busLo: Double, val busHi: Double, val routes: Map<String, List<Pair<Double, Double>>>)

        val routes = mutableMapOf<String, List<Pair<Double, Double>>>()
        val buses = mutableListOf<EdgeBus>()
        // Per group, after filtering: (group, frame, bus line in u/v) for spacing buses apart.
        val builtGroups = mutableListOf<Triple<Group, Built, MutableMap<String, String>>>()

        groups.forEachIndexed { gi, g ->
            val f = Frame(g.side)
            val n = f.box(rectById.getValue(g.nodeId))
            val busOwner = "bus:${g.nodeId}:${g.side}:$gi"
            val trunkV = if (g.hint?.trunkOffset != null) trunkV(n, g.hint) else slotByOwnerAndNode[busOwner to g.nodeId] ?: ((n[2] + n[3]) / 2.0)
            val members = g.members.toMutableMap()
            val minSize = if (g.hint?.allowSingleEdge == true) 1 else 2
            val obstacles = obstaclesFor(members.values.toSet() + g.nodeId)

            fun build(): Built {
                val list = members.entries.sortedBy { it.key }
                val built = mutableMapOf<String, List<Pair<Double, Double>>>()
                if (g.hint?.busAlongTrunk == true) {
                    var far = n[1]
                    list.forEach { (edgeId, otherId) ->
                        val o = f.box(rectById.getValue(otherId))
                        val toLarger = o[2] > trunkV
                        val arrival = sideOf(g.side, toLarger, across = true)
                        val uSlot = slotByOwnerAndNode["edge:$edgeId" to otherId]?.let { uOfSlot(arrival, g.side, it) } ?: ((o[0] + o[1]) / 2.0)
                        far = max(far, uSlot)
                        val oFace = if (toLarger) o[2] else o[3]
                        built[edgeId] = listOf(f.xy(n[1], trunkV), f.xy(uSlot, trunkV), f.xy(uSlot, oFace))
                    }
                    return Built(trunkV, n[1], far, built)
                }
                val nearest = list.minOf { f.box(rectById.getValue(it.value))[0] }
                val c = (n[1] + nearest) / 2.0
                var lo = trunkV; var hi = trunkV
                list.forEach { (edgeId, otherId) ->
                    val o = f.box(rectById.getValue(otherId))
                    val v = slotByOwnerAndNode["edge:$edgeId" to otherId] ?: ((o[2] + o[3]) / 2.0)
                    lo = min(lo, v); hi = max(hi, v)
                    built[edgeId] = listOf(f.xy(n[1], trunkV), f.xy(c, trunkV), f.xy(c, v), f.xy(o[0], v))
                }
                return Built(c, lo, hi, built)
            }

            // Leave out members whose branch hits a node, then members that make the bus hit one.
            var built = build()
            built.routes.forEach { (edgeId, pts) ->
                val branchHits = if (g.hint?.busAlongTrunk == true) hits(pts[1], pts[2], obstacles) else hits(pts[2], pts[3], obstacles)
                if (branchHits) members.remove(edgeId)
            }
            while (members.size >= minSize) {
                built = build()
                val trunkAndBus = built.routes.values.flatMap { pts ->
                    if (g.hint?.busAlongTrunk == true) listOf(pts[0] to pts[1]) else listOf(pts[0] to pts[1], pts[1] to pts[2])
                }
                if (trunkAndBus.none { (a, b) -> hits(a, b, obstacles) }) break
                // Drop the member furthest along the bus from the trunk.
                val furthest = built.routes.entries.maxWithOrNull(compareBy<Map.Entry<String, List<Pair<Double, Double>>>> { (_, pts) ->
                    abs(f.v(pts.last().first, pts.last().second) - trunkV) + abs(f.u(pts.last().first, pts.last().second) - n[1])
                }.thenBy { it.key }) ?: break
                members.remove(furthest.key)
            }
            if (members.size < minSize) return@forEachIndexed
            builtGroups.add(Triple(g, build(), members))
        }

        // Buses across their trunk that lie on the same line and overlap are moved apart.
        val spacedCoordByGroup = mutableMapOf<Group, Double>()
        builtGroups.filter { it.first.hint?.busAlongTrunk != true }
            .groupBy { (g, b, _) ->
                val horizontal = g.side == BorderSide.TOP || g.side == BorderSide.BOTTOM
                val coord = Frame(g.side).xy(b.busCoord, 0.0).let { if (horizontal) it.second else it.first }
                horizontal to (coord / busSpacing).toLong()
            }
            .values.forEach { list ->
                if (list.size < 2) return@forEach
                val levels = mutableListOf<MutableList<Pair<Double, Double>>>()
                val levelOf = mutableMapOf<Group, Int>()
                list.sortedWith(compareBy({ it.second.busLo }, { it.first.nodeId })).forEach { (g, b, _) ->
                    val span = b.busLo to b.busHi
                    val level = levels.indexOfFirst { spans -> spans.none { it.first <= span.second + busSpacing && span.first <= it.second + busSpacing } }
                        .let { if (it < 0) { levels.add(mutableListOf()); levels.lastIndex } else it }
                    levels[level].add(span)
                    levelOf[g] = level
                }
                if (levels.size < 2) return@forEach
                list.forEach { (g, b, _) ->
                    spacedCoordByGroup[g] = b.busCoord + (levelOf.getValue(g) - (levels.size - 1) / 2.0) * busSpacing
                }
            }

        builtGroups.forEach { (g, b, members) ->
            val f = Frame(g.side)
            val newCoord = spacedCoordByGroup[g]
            b.routes.forEach { (edgeId, pts) ->
                val moved = if (newCoord == null) pts else pts.mapIndexed { i, p ->
                    if (i == 1 || i == 2) f.xy(newCoord, f.v(p.first, p.second)) else p
                }
                val edge = sortedEdges.first { it.id == edgeId }
                val fromShared = simplify(moved)
                routes[edgeId] = if (edge.sourceId == g.nodeId) fromShared else fromShared.reversed()
            }
            buses.add(EdgeBus(g.nodeId, g.side, members.keys.sorted()))
        }

        return BusRoutingResult(
            routesByEdgeId = routes,
            buses = buses.sortedWith(compareBy({ it.nodeId }, { it.side.ordinal })),
            unroutedEdgeIds = sortedEdges.map { it.id }.filter { it !in routes }
        )
    }

    private fun trunkV(n: List<Double>, hint: BusHint?): Double {
        val offset = hint?.trunkOffset ?: return (n[2] + n[3]) / 2.0
        return (n[2] + offset).coerceIn(n[2], n[3])
    }

    /** The side a branch arrives at when it leaves an along-trunk bus sideways. */
    private fun sideOf(trunkSide: BorderSide, toLarger: Boolean, across: Boolean): BorderSide {
        check(across)
        return when (trunkSide) {
            // v is x: a node at larger x is entered on its left.
            BorderSide.BOTTOM, BorderSide.TOP -> if (toLarger) BorderSide.LEFT else BorderSide.RIGHT
            // v is y: a node at larger y is entered on its top.
            BorderSide.RIGHT, BorderSide.LEFT -> if (toLarger) BorderSide.TOP else BorderSide.BOTTOM
        }
    }

    /** A slot position along [arrivalSide] (its v) as a u coordinate of the trunk's frame. */
    private fun uOfSlot(arrivalSide: BorderSide, trunkSide: BorderSide, slotV: Double): Double {
        // The slot's v runs along the arrival side, which is the trunk frame's u axis (up to sign).
        val (x, y) = Frame(arrivalSide).xy(0.0, slotV)
        val along = when (arrivalSide) {
            BorderSide.LEFT, BorderSide.RIGHT -> y
            BorderSide.TOP, BorderSide.BOTTOM -> x
        }
        return when (trunkSide) {
            BorderSide.BOTTOM, BorderSide.RIGHT -> along
            BorderSide.TOP, BorderSide.LEFT -> -along
        }
    }

    private fun opposite(side: BorderSide) = when (side) {
        BorderSide.TOP -> BorderSide.BOTTOM
        BorderSide.BOTTOM -> BorderSide.TOP
        BorderSide.LEFT -> BorderSide.RIGHT
        BorderSide.RIGHT -> BorderSide.LEFT
    }

    /** Removes repeated and collinear points. */
    private fun simplify(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val dedup = mutableListOf<Pair<Double, Double>>()
        points.forEach { p -> if (dedup.isEmpty() || abs(dedup.last().first - p.first) > eps || abs(dedup.last().second - p.second) > eps) dedup.add(p) }
        if (dedup.size <= 2) return dedup
        val result = mutableListOf(dedup.first())
        for (i in 1 until dedup.size - 1) {
            val a = result.last(); val b = dedup[i]; val c = dedup[i + 1]
            val collinear = (abs(a.first - b.first) < eps && abs(b.first - c.first) < eps) ||
                (abs(a.second - b.second) < eps && abs(b.second - c.second) < eps)
            if (!collinear) result.add(b)
        }
        result.add(dedup.last())
        return result
    }
}
