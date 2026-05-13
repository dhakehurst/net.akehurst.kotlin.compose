package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class test_CompoundCollapseExpand {

    // ── Model helpers ────────────────────────────────────────────────────────

    @Test
    fun toggleCollapsed_flips_isCollapsed_flag() {
        val child = GraphLayoutCompoundGraph(id = "Container")
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container")
        root.children[child.id] = child

        val state = GraphLayoutCompoundGraphState(id = "test", root = root)
        assertFalse(child.isCollapsed)

        state.toggleCollapsed("Container")
        assertTrue(child.isCollapsed)

        state.toggleCollapsed("Container")
        assertFalse(child.isCollapsed)
    }

    @Test
    fun collapsibleChildGraphs_returns_all_descendant_graphs_excluding_root() {
        val root = GraphLayoutCompoundGraph(id = "root")
        val childA = GraphLayoutCompoundGraph(id = "A")
        val childB = GraphLayoutCompoundGraph(id = "B")
        val grandChild = GraphLayoutCompoundGraph(id = "G")

        root.nodes["A"] = GraphLayoutCompoundNode(id = "A")
        root.nodes["B"] = GraphLayoutCompoundNode(id = "B")
        root.children["A"] = childA
        root.children["B"] = childB
        childA.nodes["G"] = GraphLayoutCompoundNode(id = "G")
        childA.children["G"] = grandChild

        val state = GraphLayoutCompoundGraphState(id = "test", root = root)
        val collapsible = state.collapsibleChildGraphs()

        assertEquals(3, collapsible.size)
        assertTrue(collapsible.any { it.id == "A" })
        assertTrue(collapsible.any { it.id == "B" })
        assertTrue(collapsible.any { it.id == "G" })
    }

    // ── Layout engine: visibility ────────────────────────────────────────────

    @Test
    fun collapsed_container_hides_internal_nodes_from_layout_result() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 220.0, heightHint = 140.0)

        val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
        child.nodes["Hidden"] = GraphLayoutCompoundNode(id = "Hidden", widthHint = 90.0, heightHint = 48.0)
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        assertNotNull(result.nodeLayoutsById["Container"], "Container node must be in layout")
        assertNull(result.nodeLayoutsById["Hidden"], "Node inside collapsed container must not be in layout")
    }

    @Test
    fun collapsed_container_internal_edge_is_hidden_from_layout_result() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 220.0, heightHint = 140.0)

        val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
        child.nodes["A"] = GraphLayoutCompoundNode(id = "A", widthHint = 80.0, heightHint = 40.0)
        child.nodes["B"] = GraphLayoutCompoundNode(id = "B", widthHint = 80.0, heightHint = 40.0)
        child.edges["e_ab"] = GraphLayoutCompoundEdge(id = "e_ab", sourceId = "A", targetId = "B")
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        assertNull(result.edgeRoutesByEdgeId["e_ab"], "Internal edge of collapsed container must not be rendered")
    }

    @Test
    fun collapsed_container_isContainer_flag_remains_true_in_layout() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 220.0, heightHint = 140.0)

        val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
        child.nodes["Inner"] = GraphLayoutCompoundNode(id = "Inner", widthHint = 80.0, heightHint = 40.0)
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        val containerLayout = assertNotNull(result.nodeLayoutsById["Container"])
        assertTrue(containerLayout.isContainer, "Collapsed container should still report isContainer = true")
    }

    @Test
    fun collapsed_container_uses_hint_size_not_child_inflated_size() {
        val hintW = 150.0
        val hintH = 80.0

        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(
            id = "Container", widthHint = hintW, heightHint = hintH
        )

        // Child has big content that would inflate the container if not collapsed.
        val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
        for (i in 1..5) {
            child.nodes["N$i"] = GraphLayoutCompoundNode(id = "N$i", widthHint = 200.0, heightHint = 100.0)
        }
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        val layout = assertNotNull(result.nodeLayoutsById["Container"])
        assertEquals(hintW, layout.width, "Width must equal widthHint when collapsed")
        assertEquals(hintH, layout.height, "Height must equal heightHint when collapsed")
    }

    // ── Layout engine: external edge routing ─────────────────────────────────

    @Test
    fun external_edge_to_hidden_node_is_rerouted_to_collapsed_container_boundary() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Source"] = GraphLayoutCompoundNode(id = "Source", widthHint = 100.0, heightHint = 56.0)
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 200.0, heightHint = 140.0)
        // Edge at root level referencing a node inside the collapsed container.
        root.edges["e1"] = GraphLayoutCompoundEdge(id = "e1", sourceId = "Source", targetId = "Hidden")

        val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
        child.nodes["Hidden"] = GraphLayoutCompoundNode(id = "Hidden", widthHint = 90.0, heightHint = 48.0)
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        val route = assertNotNull(result.edgeRoutesByEdgeId["e1"],
            "Edge to hidden node must be rerouted to the collapsed container boundary")
        assertTrue(route.size >= 2, "Route must have at least 2 points")

        val containerLayout = assertNotNull(result.nodeLayoutsById["Container"])
        // The last point of the route must lie on the container boundary (within epsilon).
        val eps = 1e-5
        val last = route.last()
        val onBoundary =
            kotlin.math.abs(last.first - containerLayout.globalX) < eps ||
                kotlin.math.abs(last.first - (containerLayout.globalX + containerLayout.width)) < eps ||
                kotlin.math.abs(last.second - containerLayout.globalY) < eps ||
                kotlin.math.abs(last.second - (containerLayout.globalY + containerLayout.height)) < eps
        assertTrue(onBoundary, "Last route point $last must lie on container boundary")
    }

    // ── Layout engine: collapse policy ──────────────────────────────────────

    @Test
    fun collapse_policy_default_collapsed_hides_children_on_first_layout() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(
            id = "Container", widthHint = 200.0, heightHint = 120.0
        )

        val child = GraphLayoutCompoundGraph(
            id = "Container",
            collapsePolicy = CollapsePolicy.COLLAPSED_BY_DEFAULT,
            isCollapsed = true
        )
        child.nodes["Inner"] = GraphLayoutCompoundNode(id = "Inner", widthHint = 80.0, heightHint = 40.0)
        root.children[child.id] = child

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))

        assertNull(result.nodeLayoutsById["Inner"],
            "Node inside COLLAPSED_BY_DEFAULT container must be hidden on first layout")
    }

    // ── Layout engine: determinism ───────────────────────────────────────────

    @Test
    fun identical_collapsed_input_produces_identical_layout_twice() {
        fun buildResult(): CompoundLayoutResult {
            val root = GraphLayoutCompoundGraph(id = "root")
            root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 220.0, heightHint = 140.0)
            root.nodes["Outside"] = GraphLayoutCompoundNode(id = "Outside", widthHint = 100.0, heightHint = 56.0)
            root.edges["e_out"] = GraphLayoutCompoundEdge(id = "e_out", sourceId = "Outside", targetId = "Inner")

            val child = GraphLayoutCompoundGraph(id = "Container", isCollapsed = true)
            child.nodes["Inner"] = GraphLayoutCompoundNode(id = "Inner", widthHint = 90.0, heightHint = 48.0)
            root.children[child.id] = child

            return CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "test", root = root))
        }

        val r1 = buildResult()
        val r2 = buildResult()

        assertEquals(r1.nodeLayoutsById["Container"]?.globalX, r2.nodeLayoutsById["Container"]?.globalX)
        assertEquals(r1.nodeLayoutsById["Container"]?.globalY, r2.nodeLayoutsById["Container"]?.globalY)
        assertEquals(r1.nodeLayoutsById["Outside"]?.globalX, r2.nodeLayoutsById["Outside"]?.globalX)
        assertEquals(r1.edgeRoutesByEdgeId["e_out"], r2.edgeRoutesByEdgeId["e_out"])
    }
}

