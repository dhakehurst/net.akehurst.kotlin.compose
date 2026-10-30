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
    val containerId: String?
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
            val path = findPath(source, target, obstacles, sourceNested.contains(edge.targetId) || targetNested.contains(edge.sourceId))
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

    private fun findPath(source: Rect, target: Rect, obstacles: List<Rect>, nested: Boolean): List<Pair<Double, Double>> {
        val inflated = obstacles.map { it.inflate(margin) }
        val xs = mutableSetOf<Double>()
        val ys = mutableSetOf<Double>()
        listOf(source, target).forEach { r ->
            xs.add(r.cx); ys.add(r.cy)
            xs.add(r.left - margin); xs.add(r.right + margin)
            ys.add(r.top - margin); ys.add(r.bottom + margin)
        }
        inflated.forEach { r -> xs.add(r.left); xs.add(r.right); ys.add(r.top); ys.add(r.bottom) }
        // Lines midway between neighbouring coordinates give routes room in narrow channels.
        val xList = withMidpoints(xs.sorted())
        val yList = withMidpoints(ys.sorted())

        fun blockedPoint(x: Double, y: Double) = inflated.any { it.containsStrict(x, y, eps) }
        fun blockedSegment(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
            val mx = (x1 + x2) / 2.0
            val my = (y1 + y2) / 2.0
            return blockedPoint(mx, my) || blockedPoint(x1, y1) || blockedPoint(x2, y2)
        }

        val sx = xList.indexOf(source.cx)
        val sy = yList.indexOf(source.cy)
        val tx = xList.indexOf(target.cx)
        val ty = yList.indexOf(target.cy)

        // Dijkstra over (grid point, direction); cost = length + bendCost per turn.
        val dist = HashMap<State, Double>()
        val prev = HashMap<State, State>()
        val queue = MinQueue<State>()
        for (d in 0..3) {
            val s = State(sx, sy, d)
            dist[s] = 0.0
            queue.add(0.0, s)
        }
        var end: State? = null
        while (queue.isNotEmpty()) {
            val (cost, state) = queue.poll()
            if (cost > (dist[state] ?: Double.MAX_VALUE) + eps) continue
            if (state.xi == tx && state.yi == ty) {
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
                val step = abs(x2 - x1) + abs(y2 - y1)
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
            return clipEnds(listOf(source.cx to source.cy, target.cx to source.cy, target.cx to target.cy), source, target)
        }
        val points = mutableListOf<Pair<Double, Double>>()
        var cur: State? = end
        while (cur != null) {
            points.add(xList[cur.xi] to yList[cur.yi])
            cur = prev[cur]
        }
        points.reverse()
        return clipEnds(simplify(points), source, target)
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

    /** Moves the first and last point from the node centres to the node borders. */
    private fun clipEnds(points: List<Pair<Double, Double>>, source: Rect, target: Rect): List<Pair<Double, Double>> {
        if (points.size < 2) return points
        val result = points.toMutableList()
        result[0] = exitPoint(points[0], points[1], source)
        result[result.lastIndex] = exitPoint(points[points.lastIndex], points[points.lastIndex - 1], target)
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
        val mutable = routes.mapValues { it.value.toMutableList() }.toSortedMap()
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
            .toSortedMap()
        groups.values.forEach { segs ->
            // Group overlapping segments of different edges into clusters.
            val sorted = segs.sortedWith(compareBy({ it.lo }, { it.edgeId }))
            val clusters = mutableListOf<MutableList<Seg>>()
            sorted.forEach { s ->
                val c = clusters.lastOrNull()
                if (c != null && c.any { it.hi > s.lo + eps }) c.add(s) else clusters.add(mutableListOf(s))
            }
            clusters.filter { c -> c.map { it.edgeId }.distinct().size > 1 }.forEach { c ->
                val byEdge = c.groupBy { it.edgeId }.toSortedMap()
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
