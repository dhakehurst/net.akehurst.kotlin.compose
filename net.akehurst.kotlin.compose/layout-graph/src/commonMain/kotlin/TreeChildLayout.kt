package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.max
import kotlin.math.min

/** Which end of an edge is the parent in a [TreeChildLayout]. */
enum class TreeParentEnd {
    /** Edges go from parent to child, as in composition or containment. */
    SOURCE,
    /** Edges go from child to parent, as in UML generalisation. */
    TARGET
}

/** Where the trunk to a node's children leaves the node in a [TreeChildLayout]. */
enum class TreeTrunkSide {
    /** From the middle of the right side, into a bus that runs down beside the children. */
    SIDE,
    /** From the bottom, near the left, running straight down past the children. */
    BOTTOM
}

/**
 * Indented tree layout: each node's children are placed one per row below it, shifted to the right,
 * like a file explorer. Roots follow each other down the page.
 *
 * Use it with [EdgeRouting.BUS]: the layout tells the router to draw each parent's edges as one trunk
 * with a branch to each child ([trunkSide]). A node with more than one parent is placed under the first one
 * (by edge id); its other edges are routed as usual. Edges that would close a cycle are ignored for placement.
 * All sizes are in dp.
 */
data class TreeChildLayout(
    val parentEnd: TreeParentEnd = TreeParentEnd.TARGET,
    val trunkSide: TreeTrunkSide = TreeTrunkSide.SIDE,
    /** Vertical gap between rows. */
    val rowSpacing: Double = 24.0,
    /** Extra vertical gap between one tree and the next root. */
    val treeSpacing: Double = 24.0,
    /** [TreeTrunkSide.SIDE]: horizontal distance from a parent's right side to the bus. */
    val trunkLength: Double = 32.0,
    /** [TreeTrunkSide.BOTTOM]: distance of the trunk from a parent's left side (at most half the parent's width). */
    val trunkInset: Double = 24.0,
    /** Horizontal length of a branch, from the bus to the child. */
    val branchLength: Double = 32.0
) : ChildLayoutAlgorithm {

    override fun layout(input: ChildLayoutInput): ChildLayoutResult {
        val nodeIds = input.nodes.map { it.id }
        val known = nodeIds.toSet()
        val size = { id: String -> input.nodeSizesById.getValue(id) }

        // Tree parent of each node: the first edge (by id) that makes it a child and does not close a cycle.
        val parentById = mutableMapOf<String, String>()
        fun isAncestor(candidate: String, of: String): Boolean {
            var current: String? = of
            while (current != null) {
                if (current == candidate) return true
                current = parentById[current]
            }
            return false
        }
        input.edges.sortedBy { it.id }.forEach { edge ->
            val (parent, child) = when (parentEnd) {
                TreeParentEnd.SOURCE -> edge.sourceId to edge.targetId
                TreeParentEnd.TARGET -> edge.targetId to edge.sourceId
            }
            if (parent !in known || child !in known || parent == child) return@forEach
            if (child in parentById || isAncestor(child, parent)) return@forEach
            parentById[child] = parent
        }
        val order = nodeIds.withIndex().associate { it.value to it.index }
        val childrenById = parentById.entries.groupBy({ it.value }, { it.key })
            .mapValues { (_, children) -> children.sortedBy { order.getValue(it) } }

        val topLeftById = mutableMapOf<String, Pair<Double, Double>>()
        val hints = mutableMapOf<String, BusHint>()
        var y = 0.0
        fun place(id: String, x: Double) {
            val (w, h) = size(id)
            topLeftById[id] = x to y
            y += h + rowSpacing
            val children = childrenById[id].orEmpty()
            if (children.isEmpty()) return
            val inset = min(trunkInset, w / 2.0)
            val childX = when (trunkSide) {
                TreeTrunkSide.SIDE -> x + w + trunkLength + branchLength
                TreeTrunkSide.BOTTOM -> x + inset + branchLength
            }
            hints[id] = when (trunkSide) {
                TreeTrunkSide.SIDE -> BusHint(side = BorderSide.RIGHT, allowSingleEdge = true)
                TreeTrunkSide.BOTTOM -> BusHint(side = BorderSide.BOTTOM, busAlongTrunk = true, trunkOffset = inset, allowSingleEdge = true)
            }
            children.forEach { place(it, childX) }
        }
        nodeIds.filter { it !in parentById }.forEachIndexed { i, root ->
            if (i > 0) y += treeSpacing
            place(root, 0.0)
        }

        val contentWidth = nodeIds.maxOfOrNull { topLeftById.getValue(it).first + size(it).first } ?: 0.0
        val contentHeight = nodeIds.maxOfOrNull { topLeftById.getValue(it).second + size(it).second } ?: 0.0
        return ChildLayoutResult(
            nodeSizesById = input.nodeSizesById,
            nodeTopLeftById = topLeftById,
            edgeRoutesByEdgeId = emptyMap(),
            contentWidth = max(0.0, contentWidth),
            contentHeight = max(0.0, contentHeight),
            busHintsByNodeId = hints
        )
    }
}
