package net.akehurst.kotlin.compose.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Layout stability: with the previous layout as a hint, nodes keep their layers and their order in a layer,
 * unless another order has fewer crossings.
 */
class test_StableLayout {

    private fun rect(x: Double, y: Double) = CompoundRect(x, y, x + 100.0, y + 50.0)

    private fun layout(nodes: List<String>, edges: List<SugiyamaEdge<String>>, previous: Map<String, CompoundRect> = emptyMap()) =
        SugiyamaLayout<String>(previousBounds = { previous[it] }).layoutGraph(nodes, edges)

    private fun xOrder(d: SugiyamaLayoutData<String>, ids: List<String>) = ids.sortedBy { d.nodePositions.getValue(it).first }

    @Test
    fun previous_order_in_a_layer_is_kept_when_it_costs_no_crossings() {
        val children = listOf("a", "b", "c", "d", "e")
        val edges = children.map { SugiyamaEdge("R-$it", "R", it) }
        val fromScratch = layout(listOf("R") + children, edges)
        assertEquals(children, xOrder(fromScratch, children))

        // Previously shown the other way round.
        val previous = mapOf("R" to rect(400.0, 0.0)) + children.reversed().mapIndexed { i, id -> id to rect(i * 200.0, 150.0) }
        val stable = layout(listOf("R") + children, edges, previous)
        assertEquals(children.reversed(), xOrder(stable, children))
    }

    @Test
    fun a_node_keeps_its_previous_layer() {
        val nodes = listOf("A", "B", "C")
        val edges = listOf(SugiyamaEdge("A-B", "A", "B"))
        val fromScratch = layout(nodes, edges)
        assertEquals(fromScratch.nodePositions.getValue("A").second, fromScratch.nodePositions.getValue("C").second, "C (unconnected) starts in the top layer")

        // C was shown beside B, below A.
        val previous = mapOf("A" to rect(0.0, 0.0), "B" to rect(0.0, 150.0), "C" to rect(200.0, 150.0))
        val stable = layout(nodes, edges, previous)
        assertEquals(stable.nodePositions.getValue("B").second, stable.nodePositions.getValue("C").second, "C stays in B's layer")
        assertTrue(stable.nodePositions.getValue("A").second < stable.nodePositions.getValue("B").second)
    }

    @Test
    fun a_new_node_goes_below_its_parent_and_existing_nodes_keep_their_order() {
        val nodes = listOf("R", "a", "b", "c")
        val edges = listOf(SugiyamaEdge("R-a", "R", "a"), SugiyamaEdge("R-b", "R", "b"), SugiyamaEdge("R-c", "R", "c"))
        val previous = mapOf("R" to rect(200.0, 0.0), "c" to rect(0.0, 150.0), "b" to rect(200.0, 150.0), "a" to rect(400.0, 150.0))
        val withNew = layout(nodes + "n", edges + SugiyamaEdge("c-n", "c", "n"), previous)
        assertEquals(listOf("c", "b", "a"), xOrder(withNew, listOf("a", "b", "c")))
        assertEquals(withNew.nodePositions.getValue("c").second < withNew.nodePositions.getValue("n").second, true)
    }

    @Test
    fun an_order_with_fewer_crossings_wins() {
        val nodes = listOf("A", "B", "C", "D")
        val edges = listOf(SugiyamaEdge("A-C", "A", "C"), SugiyamaEdge("B-D", "B", "D"))
        // Previously A-C and B-D crossed.
        val previous = mapOf("A" to rect(0.0, 0.0), "B" to rect(200.0, 0.0), "D" to rect(0.0, 150.0), "C" to rect(200.0, 150.0))
        val stable = layout(nodes, edges, previous)
        val a = stable.nodePositions.getValue("A").first
        val b = stable.nodePositions.getValue("B").first
        val c = stable.nodePositions.getValue("C").first
        val d = stable.nodePositions.getValue("D").first
        assertTrue((a < b) == (c < d), "A-C and B-D do not cross: A=$a B=$b C=$c D=$d")
    }

    @Test
    fun relayout_from_its_own_layout_without_crossings_is_unchanged() {
        val nodes = listOf("R", "a", "b", "c", "a1", "a2", "c1")
        val edges = listOf("R-a", "R-b", "R-c", "a-a1", "a-a2", "c-c1").map { val (s, t) = it.split("-"); SugiyamaEdge(it, s, t) }
        val first = layout(nodes, edges)
        val previous = first.nodePositions.mapValues { (_, p) -> rect(p.first, p.second) }
        val second = layout(nodes, edges, previous)
        assertEquals(first.nodePositions, second.nodePositions)
    }

    private fun starState(childLayout: ChildLayoutAlgorithm? = null): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph(id = "root", childLayout = childLayout)
        listOf("R", "a", "b", "c").forEach { root.nodes[it] = GraphLayoutCompoundNode(id = it, widthHint = 80.0, heightHint = 40.0) }
        listOf("a", "b", "c").forEach { root.edges["R-$it"] = GraphLayoutCompoundEdge(id = "R-$it", sourceId = "R", targetId = it) }
        return GraphLayoutCompoundGraphState(id = "star", root = root)
    }

    /** The previous layout with a, b and c swapped round (c where a was, a where c was). */
    private fun CompoundLayoutResult.withEndsSwapped(): CompoundLayoutResult {
        val a = nodeLayoutsById.getValue("a")
        val c = nodeLayoutsById.getValue("c")
        return copy(
            nodeLayoutsById = nodeLayoutsById +
                ("a" to a.copy(localX = c.localX, localY = c.localY)) +
                ("c" to c.copy(localX = a.localX, localY = a.localY))
        )
    }

    @Test
    fun engine_passes_the_previous_layout_to_the_child_layout() {
        val state = starState()
        val first = CompoundLayoutEngine().layout(state)
        fun order(r: CompoundLayoutResult) = listOf("a", "b", "c").sortedBy { r.nodeLayoutsById.getValue(it).globalX }
        assertEquals(listOf("a", "b", "c"), order(first))

        val stable = CompoundLayoutEngine().layout(state, previous = first.withEndsSwapped())
        assertEquals(listOf("c", "b", "a"), order(stable))
    }

    @Test
    fun engine_previous_layout_for_left_right_layers() {
        val state = starState(SugiyamaChildLayout(direction = LayerDirection.LEFT_RIGHT))
        val first = CompoundLayoutEngine().layout(state)
        fun order(r: CompoundLayoutResult) = listOf("a", "b", "c").sortedBy { r.nodeLayoutsById.getValue(it).globalY }
        assertEquals(listOf("a", "b", "c"), order(first))
        val rx = first.nodeLayoutsById.getValue("R").globalX
        assertTrue(listOf("a", "b", "c").all { first.nodeLayoutsById.getValue(it).globalX > rx })

        val stable = CompoundLayoutEngine().layout(state, previous = first.withEndsSwapped())
        assertEquals(listOf("c", "b", "a"), order(stable))
    }
}
