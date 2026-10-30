package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A node as seen by [OrthogonalEdgeRouter], in global coordinates.
 *
 * [containerId] is the node that directly contains this one (null at the top level).
 */
data class RouterNode(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val containerId: String?,
    /**
     * For a container: where its child area starts and ends, as insets from its border
     * (left, top, right, bottom). Edges avoid running inside the band between border and child area,
     * for example along a header.
     */
    val childAreaInsets: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    /**
     * For a node on another node's border (e.g. a port): the host's id and the side the node is on.
     * Its edges leave it straight out through that side, or straight in towards the host's inside
     * when the other end is the host or inside it.
     */
    val borderHostId: String? = null,
    val borderSide: BorderSide? = null
)

data class RouterEdge(
    val id: String,
    val sourceId: String,
    val targetId: String
)

/**
 * Routes edges with horizontal and vertical segments only, going around every node
 * that is not the source, the target, or a container of either.
 *
 * The search runs over a sparse grid made of the obstacle borders (plus [margin]) and the
 * node centre lines, and minimises length plus a cost per bend. Edges that share a segment
 * are then pushed apart by [edgeSpacing]. The result is deterministic for identical input.
 */
class OrthogonalEdgeRouter(
    val margin: Double = 12.0,
    val bendCost: Double = 40.0,
    /** Cost multiplier for running inside a container's border band or along its border. */
    val bandCostFactor: Double = 6.0,
    val edgeSpacing: Double = 8.0
) {

    private val eps = 1e-6

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val cx get() = (left + right) / 2.0
        val cy get() = (top + bottom) / 2.0
        fun inflate(d: Double) = Rect(left - d, top - d, right + d, bottom + d)
        fun containsStrict(x: Double, y: Double, e: Double) = x > left + e && x < right - e && y > top + e && y < bottom - e
    }

    fun route(nodes: List<RouterNode>, edges: List<RouterEdge>): Map<String, List<Pair<Double, Double>>> {
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

        // The direction an edge leaves a border node in, or null for an ordinary node.
        fun exitDirection(id: String, otherId: String): Int? {
            val n = nodesById[id] ?: return null
            val host = n.borderHostId ?: return null
            val outward = when (n.borderSide ?: return null) {
                BorderSide.RIGHT -> 0
                BorderSide.BOTTOM -> 1
                BorderSide.LEFT -> 2
                BorderSide.TOP -> 3
            }
            val inward = otherId == host || host in ancestors(otherId)
            return if (inward) (outward + 2) % 4 else outward
        }

        val routes = mutableMapOf<String, List<Pair<Double, Double>>>()
        edges.sortedBy { it.id }.forEach { edge ->
            val source = rectById[edge.sourceId] ?: return@forEach
            val target = rectById[edge.targetId] ?: return@forEach
            if (edge.sourceId == edge.targetId) {
                routes[edge.id] = selfLoop(source)
                return@forEach
            }
            val sourceAncestors = ancestors(edge.sourceId)
            val targetAncestors = ancestors(edge.targetId)
            val sourceNested = sourceAncestors + edge.sourceId
            val targetNested = targetAncestors + edge.targetId
            // A node inside the source or target is not an obstacle either (edge to/from a container).
            val obstacles = nodes
                .filter { n ->
                    n.id != edge.sourceId && n.id != edge.targetId &&
                        n.id !in sourceAncestors && n.id !in targetAncestors &&
                        ancestors(n.id).none { it == edge.sourceId || it == edge.targetId }
                }
                .sortedBy { it.id }
                .map { rectById.getValue(it.id) }
            // Containers the edge is inside of: it may cross their band, but should not run along it.
            val bands = (sourceAncestors + targetAncestors).sorted().mapNotNull { id ->
                val n = nodesById[id] ?: return@mapNotNull null
                val outer = rectById.getValue(id)
                val i = n.childAreaInsets
                outer to Rect(outer.left + i[0], outer.top + i[1], outer.right - i[2], outer.bottom - i[3])
            }
            // The route stays inside the innermost container holding both ends. A border node whose
            // edge goes inwards counts as inside its host.
            fun inside(id: String, otherId: String): Set<String> {
                val host = nodesById[id]?.borderHostId
                val inward = host != null && (otherId == host || host in ancestors(otherId))
                return if (inward) ancestors(id) + host!! else ancestors(id)
            }
            val confine = (inside(edge.sourceId, edge.targetId) intersect inside(edge.targetId, edge.sourceId))
                .mapNotNull { rectById[it] }
                .minByOrNull { it.width() * it.height() }
            val path = findPath(
                source, target, obstacles, bands,
                sourceNested.contains(edge.targetId) || targetNested.contains(edge.sourceId),
                exitDirection(edge.sourceId, edge.targetId), exitDirection(edge.targetId, edge.sourceId),
                confine
            )
            routes[edge.id] = path
        }
        return nudge(routes)
    }

    private fun selfLoop(r: Rect): List<Pair<Double, Double>> {
        val d = margin * 2
        val x1 = r.right - r.width() / 4
        return listOf(x1 to r.top, x1 to r.top - d, r.right + d to r.top - d, r.right + d to r.top + r.height() / 4, r.right to r.top + r.height() / 4)
    }

    private fun Rect.width() = right - left
    private fun Rect.height() = bottom - top

    // direction: 0 = +x, 1 = +y, 2 = -x, 3 = -y
    private data class State(val xi: Int, val yi: Int, val dir: Int)

    private val dxs = intArrayOf(1, 0, -1, 0)
    private val dys = intArrayOf(0, 1, 0, -1)

    /** The middle of the side of [r] that faces [dir]. */
    private fun sideMiddle(r: Rect, dir: Int): Pair<Double, Double> = when (dir) {
        0 -> r.right to r.cy
        1 -> r.cx to r.bottom
        2 -> r.left to r.cy
        else -> r.cx to r.top
    }

    /** A point [distance] beyond the side of [r] that faces [dir]. */
    private fun stub(r: Rect, dir: Int, distance: Double): Pair<Double, Double> =
        sideMiddle(r, dir).let { (x, y) -> (x + dxs[dir] * distance) to (y + dys[dir] * distance) }

    /**
     * [sourceDir] and [targetDir], when given, fix the direction the route leaves the source in and
     * the direction it leaves the target in (so it arrives the opposite way). The route then starts
     * and ends with a straight piece out of that side.
     */
    private fun findPath(
        source: Rect, target: Rect, obstacles: List<Rect>, bands: List<Pair<Rect, Rect>>, nested: Boolean,
        sourceDir: Int? = null, targetDir: Int? = null,
        /** When given, the route may not leave this rectangle. */
        confine: Rect? = null
    ): List<Pair<Double, Double>> {
        val inflated = obstacles.map { it.inflate(margin) }
        val xs = mutableSetOf<Double>()
        val ys = mutableSetOf<Double>()
        listOf(source, target).forEach { r ->
            xs.add(r.cx); ys.add(r.cy)
            xs.add(r.left - margin); xs.add(r.right + margin)
            ys.add(r.top - margin); ys.add(r.bottom + margin)
        }
        val startPoint = sourceDir?.let { stub(source, it, margin) } ?: (source.cx to source.cy)
        val endPoint = targetDir?.let { stub(target, it, margin) } ?: (target.cx to target.cy)
        listOfNotNull(sourceDir?.let { stub(source, it, 2 * margin) }, targetDir?.let { stub(target, it, 2 * margin) }, startPoint, endPoint)
            .forEach { (x, y) -> xs.add(x); ys.add(y) }
        inflated.forEach { r -> xs.add(r.left); xs.add(r.right); ys.add(r.top); ys.add(r.bottom) }
        bands.forEach { (outer, inner) ->
            // Lines just inside the child area and just outside the border, so routes can keep clear of both.
            xs.add(inner.left + margin); xs.add(inner.right - margin); ys.add(inner.top + margin); ys.add(inner.bottom - margin)
            xs.add(outer.left - margin); xs.add(outer.right + margin); ys.add(outer.top - margin); ys.add(outer.bottom + margin)
        }
        // Length of a segment that runs along a container's border band (header, frame, or just outside the border).
        fun lengthAlongBands(x1: Double, y1: Double, x2: Double, y2: Double): Double = bands.sumOf { (outer, inner) ->
            val half = margin / 2.0
            if (abs(y1 - y2) < eps) {
                val inTop = y1 > outer.top - half && y1 < inner.top + half
                val inBottom = y1 > inner.bottom - half && y1 < outer.bottom + half
                if (!(inTop || inBottom)) 0.0 else max(0.0, min(max(x1, x2), outer.right) - max(min(x1, x2), outer.left))
            } else {
                val inLeft = x1 > outer.left - half && x1 < inner.left + half
                val inRight = x1 > inner.right - half && x1 < outer.right + half
                if (!(inLeft || inRight)) 0.0 else max(0.0, min(max(y1, y2), outer.bottom) - max(min(y1, y2), outer.top))
            }
        }

        // Lines midway between neighbouring coordinates give routes room in narrow channels.
        val xList = withMidpoints(xs.sorted())
        val yList = withMidpoints(ys.sorted())

        fun outsideConfine(x: Double, y: Double) =
            confine != null && (x < confine.left - eps || x > confine.right + eps || y < confine.top - eps || y > confine.bottom + eps)
        fun blockedPoint(x: Double, y: Double) = outsideConfine(x, y) || inflated.any { it.containsStrict(x, y, eps) }
        fun blockedSegment(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
            val mx = (x1 + x2) / 2.0
            val my = (y1 + y2) / 2.0
            return blockedPoint(mx, my) || blockedPoint(x1, y1) || blockedPoint(x2, y2)
        }

        val sx = xList.indexOf(startPoint.first)
        val sy = yList.indexOf(startPoint.second)
        val tx = xList.indexOf(endPoint.first)
        val ty = yList.indexOf(endPoint.second)
        // Coming into the end point from outside the target, opposite to the way it would leave.
        val arrivalDir = targetDir?.let { (it + 2) % 4 }

        // Dijkstra over (grid point, direction); cost = length + bendCost per turn.
        val dist = HashMap<State, Double>()
        val prev = HashMap<State, State>()
        val queue = MinQueue<State>()
        for (d in 0..3) {
            if (sourceDir != null && d != sourceDir) continue
            val s = State(sx, sy, d)
            dist[s] = 0.0
            queue.add(0.0, s)
        }
        var end: State? = null
        while (queue.isNotEmpty()) {
            val (cost, state) = queue.poll()
            if (cost > (dist[state] ?: Double.MAX_VALUE) + eps) continue
            if (state.xi == tx && state.yi == ty && (arrivalDir == null || state.dir == arrivalDir)) {
                end = state; break
            }
            for (nd in 0..3) {
                if (nd == (state.dir + 2) % 4) continue
                val nxi = state.xi + if (nd == 0) 1 else if (nd == 2) -1 else 0
                val nyi = state.yi + if (nd == 1) 1 else if (nd == 3) -1 else 0
                if (nxi !in xList.indices || nyi !in yList.indices) continue
                val x1 = xList[state.xi]; val y1 = yList[state.yi]
                val x2 = xList[nxi]; val y2 = yList[nyi]
                if (blockedSegment(x1, y1, x2, y2)) continue
                // Only cross the source on the way out, and the target on the way in.
                if (!nested) {
                    val reentersSource = source.containsStrict(x2, y2, eps) && !source.containsStrict(x1, y1, eps)
                    val leavesTarget = target.containsStrict(x1, y1, eps) && !target.containsStrict(x2, y2, eps)
                    if (reentersSource || leavesTarget) continue
                    // Inside the source or target, stay on its centre lines so the edge meets the border at a side centre.
                    if (offCentreInside(source, x1, y1, x2, y2) || offCentreInside(target, x1, y1, x2, y2)) continue
                }
                val length = abs(x2 - x1) + abs(y2 - y1)
                // Crossing a band is fine; running along it is expensive.
                val step = length + lengthAlongBands(x1, y1, x2, y2) * (bandCostFactor - 1.0)
                val turn = if (nd != state.dir) bendCost else 0.0
                val next = State(nxi, nyi, nd)
                val nc = cost + step + turn
                if (nc + eps < (dist[next] ?: Double.MAX_VALUE)) {
                    dist[next] = nc
                    prev[next] = state
                    queue.add(nc, next)
                }
            }
        }
        if (end == null) {
            // No free path: fall back to a single bend.
            val fallback = listOf(startPoint, endPoint.first to startPoint.second, endPoint)
            return clipEnds(fallback, source, target, sourceDir, targetDir)
        }
        val points = mutableListOf<Pair<Double, Double>>()
        var cur: State? = end
        while (cur != null) {
            points.add(xList[cur.xi] to yList[cur.yi])
            cur = prev[cur]
        }
        points.reverse()
        return clipEnds(simplify(points), source, target, sourceDir, targetDir)
    }

    private fun offCentreInside(r: Rect, x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val touchesInside = r.containsStrict(x1, y1, eps) || r.containsStrict(x2, y2, eps) ||
            r.containsStrict((x1 + x2) / 2.0, (y1 + y2) / 2.0, eps)
        if (!touchesInside) return false
        val onCentreColumn = abs(x1 - r.cx) < eps && abs(x2 - r.cx) < eps
        val onCentreRow = abs(y1 - r.cy) < eps && abs(y2 - r.cy) < eps
        return !(onCentreColumn || onCentreRow)
    }

    private fun withMidpoints(values: List<Double>): List<Double> {
        val result = mutableListOf<Double>()
        values.forEachIndexed { i, v ->
            if (i > 0) {
                val p = values[i - 1]
                if (v - p > 2 * margin) result.add((p + v) / 2.0)
            }
            result.add(v)
        }
        return result
    }

    /** Removes repeated and collinear points. */
    private fun simplify(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val dedup = mutableListOf<Pair<Double, Double>>()
        points.forEach { p -> if (dedup.isEmpty() || !same(dedup.last(), p)) dedup.add(p) }
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

    private fun same(a: Pair<Double, Double>, b: Pair<Double, Double>) = abs(a.first - b.first) < eps && abs(a.second - b.second) < eps

    /**
     * Moves the first and last point from the node centres to the node borders. An end with a fixed
     * direction already starts outside the node, so the middle of that side is added in front of it.
     */
    private fun clipEnds(
        points: List<Pair<Double, Double>>, source: Rect, target: Rect,
        sourceDir: Int? = null, targetDir: Int? = null
    ): List<Pair<Double, Double>> {
        if (points.size < 2) return points
        val result = points.toMutableList()
        if (sourceDir == null) result[0] = exitPoint(points[0], points[1], source) else result.add(0, sideMiddle(source, sourceDir))
        if (targetDir == null) {
            result[result.lastIndex] = exitPoint(points[points.lastIndex], points[points.lastIndex - 1], target)
        } else {
            result.add(sideMiddle(target, targetDir))
        }
        return simplify(result)
    }

    private fun exitPoint(inside: Pair<Double, Double>, toward: Pair<Double, Double>, r: Rect): Pair<Double, Double> {
        val dx = toward.first - inside.first
        val dy = toward.second - inside.second
        return when {
            abs(dx) > abs(dy) -> (if (dx > 0) r.right else r.left) to inside.second
            else -> inside.first to (if (dy > 0) r.bottom else r.top)
        }
    }

    /**
     * Pushes apart segments of different edges that lie on the same line and overlap,
     * so they can be told apart. End segments move their end point along the node border.
     */
    private fun nudge(routes: Map<String, List<Pair<Double, Double>>>): Map<String, List<Pair<Double, Double>>> {
        val mutable = routes.entries.sortedBy { it.key }.associate { it.key to it.value.toMutableList() }
        // Collect segments: (edgeId, index, horizontal, coordinate, from, to)
        data class Seg(val edgeId: String, val index: Int, val horizontal: Boolean, val coord: Double, val lo: Double, val hi: Double)

        fun segments(): List<Seg> = mutable.flatMap { (id, pts) ->
            (0 until pts.size - 1).mapNotNull { i ->
                val a = pts[i]; val b = pts[i + 1]
                when {
                    abs(a.second - b.second) < eps && abs(a.first - b.first) > eps ->
                        Seg(id, i, true, a.second, min(a.first, b.first), max(a.first, b.first))
                    abs(a.first - b.first) < eps && abs(a.second - b.second) > eps ->
                        Seg(id, i, false, a.first, min(a.second, b.second), max(a.second, b.second))
                    else -> null
                }
            }
        }

        val groups = segments()
            .groupBy { (if (it.horizontal) "h" else "v") + ":" + ((it.coord * 1000).toLong()) }
            .entries.sortedBy { it.key }.associate { it.key to it.value }
        groups.values.forEach { segs ->
            // Group overlapping segments of different edges into clusters.
            val sorted = segs.sortedWith(compareBy({ it.lo }, { it.edgeId }))
            val clusters = mutableListOf<MutableList<Seg>>()
            sorted.forEach { s ->
                val c = clusters.lastOrNull()
                if (c != null && c.any { it.hi > s.lo + eps }) c.add(s) else clusters.add(mutableListOf(s))
            }
            clusters.filter { c -> c.map { it.edgeId }.distinct().size > 1 }.forEach { c ->
                val byEdge = c.groupBy { it.edgeId }.entries.sortedBy { it.key }.associate { it.key to it.value }
                val n = byEdge.size
                byEdge.entries.forEachIndexed { k, (edgeId, list) ->
                    val offset = (k - (n - 1) / 2.0) * edgeSpacing
                    if (abs(offset) < eps) return@forEachIndexed
                    val pts = mutable.getValue(edgeId)
                    list.forEach { s ->
                        val i = s.index
                        if (s.horizontal) {
                            pts[i] = pts[i].first to pts[i].second + offset
                            pts[i + 1] = pts[i + 1].first to pts[i + 1].second + offset
                        } else {
                            pts[i] = pts[i].first + offset to pts[i].second
                            pts[i + 1] = pts[i + 1].first + offset to pts[i + 1].second
                        }
                    }
                }
            }
        }
        return mutable.mapValues { it.value.toList() }
    }
}

/** Small deterministic priority queue (cost, then insertion order). */
private class MinQueue<T : Any> {
    private data class Entry<T>(val cost: Double, val seq: Long, val state: T)

    private val heap = ArrayList<Entry<T>>()
    private var seq = 0L

    fun isNotEmpty() = heap.isNotEmpty()

    fun add(cost: Double, state: T) {
        heap.add(Entry(cost, seq++, state))
        var i = heap.lastIndex
        while (i > 0) {
            val p = (i - 1) / 2
            if (less(heap[i], heap[p])) {
                val t = heap[i]; heap[i] = heap[p]; heap[p] = t; i = p
            } else break
        }
    }

    fun poll(): Pair<Double, T> {
        val top = heap[0]
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            var i = 0
            while (true) {
                val l = 2 * i + 1; val r = l + 1
                var m = i
                if (l < heap.size && less(heap[l], heap[m])) m = l
                if (r < heap.size && less(heap[r], heap[m])) m = r
                if (m == i) break
                val t = heap[i]; heap[i] = heap[m]; heap[m] = t; i = m
            }
        }
        return top.cost to top.state
    }

    private fun less(a: Entry<T>, b: Entry<T>) = a.cost < b.cost || (a.cost == b.cost && a.seq < b.seq)
}
