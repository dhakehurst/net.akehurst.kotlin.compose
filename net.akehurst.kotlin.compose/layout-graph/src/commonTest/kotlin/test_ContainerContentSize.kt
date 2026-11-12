package net.akehurst.kotlin.compose.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A container takes the measured size of its own content, like a leaf; hints stay minimums. */
class test_ContainerContentSize {

    private fun state(collapsed: Boolean, widthHint: Double? = null, heightHint: Double? = null): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["c"] = GraphLayoutCompoundNode(id = "c", kind = NodeKind.CONTAINER, widthHint = widthHint, heightHint = heightHint)
        root.children["c"] = GraphLayoutCompoundGraph(id = "c", isCollapsed = collapsed).also { g ->
            g.nodes["k"] = GraphLayoutCompoundNode(id = "k", widthHint = 20.0, heightHint = 20.0)
        }
        return GraphLayoutCompoundGraphState(id = "test", root = root)
    }

    @Test
    fun collapsed_container_takes_its_measured_content_size() {
        val result = CompoundLayoutEngine().layout(state(collapsed = true), leafSizesByNodeId = mapOf("c" to (300.0 to 200.0)))
        val c = result.nodeLayoutsById.getValue("c")
        assertEquals(300.0, c.width)
        assertEquals(200.0, c.height)
    }

    @Test
    fun collapsed_container_hints_are_minimums() {
        val result = CompoundLayoutEngine().layout(
            state(collapsed = true, widthHint = 400.0, heightHint = 50.0),
            leafSizesByNodeId = mapOf("c" to (300.0 to 200.0))
        )
        val c = result.nodeLayoutsById.getValue("c")
        assertEquals(400.0, c.width)
        assertEquals(200.0, c.height)
    }

    @Test
    fun expanded_container_is_at_least_its_measured_content_size() {
        val metrics = ContainerChildHostMetrics(originX = 4.0, originY = 30.0, insetRight = 4.0, insetBottom = 4.0)
        val result = CompoundLayoutEngine().layout(
            state(collapsed = false),
            containerMetricsByNodeId = mapOf("c" to metrics),
            leafSizesByNodeId = mapOf("c" to (300.0 to 40.0))
        )
        val c = result.nodeLayoutsById.getValue("c")
        assertEquals(300.0, c.width)
        // Its children still fit below the header.
        val k = result.nodeLayoutsById.getValue("k")
        assertTrue(c.height >= 30.0 + k.height + 4.0, "c height ${c.height} does not fit child k")
        assertTrue(c.height >= 40.0)
    }
}
