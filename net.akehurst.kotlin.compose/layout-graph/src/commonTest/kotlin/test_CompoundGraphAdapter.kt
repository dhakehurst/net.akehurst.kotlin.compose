package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class test_CompoundGraphAdapter {

    @Test
    fun flat_adapter_preserves_nodes_edges_and_passes_invariants() {
        val flat = GraphLayoutGraphState(id = "flat")
        flat.addNode("A")
        flat.addNode("B")
        flat.addEdge(edgeId = "e1", sourceId = "A", targetId = "B")

        val compound = flat.toCompoundGraphState()

        assertEquals("flat", compound.id)
        assertEquals(setOf("A", "B"), compound.root.nodes.keys)
        assertEquals(setOf("e1"), compound.root.edges.keys)

        val validation = compound.validateInvariants()
        assertTrue(validation.isValid, "Expected valid compound state, got: ${validation.errors}")
    }

    @Test
    fun invariants_reject_duplicate_node_ownership() {
        val childA = GraphLayoutCompoundGraph(id = "childA")
        val childB = GraphLayoutCompoundGraph(id = "childB")
        childA.nodes["N"] = GraphLayoutCompoundNode(id = "N")
        childB.nodes["N"] = GraphLayoutCompoundNode(id = "N")

        val root = GraphLayoutCompoundGraph(id = "root")
        root.children[childA.id] = childA
        root.children[childB.id] = childB

        val state = GraphLayoutCompoundGraphState(id = "compound", root = root)
        val validation = state.validateInvariants()

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("contained by both") })
    }

    @Test
    fun invariants_reject_containment_cycles() {
        val root = GraphLayoutCompoundGraph(id = "root")
        val child = GraphLayoutCompoundGraph(id = "child")
        root.children[child.id] = child
        child.children[root.id] = root

        val state = GraphLayoutCompoundGraphState(id = "compound", root = root)
        val validation = state.validateInvariants()

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("Containment cycle") })
    }
}