package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class test_CompoundRecursiveLayout {

    @Test
    fun nested_children_are_positioned_inside_container_bounds() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 220.0, heightHint = 140.0)
        root.nodes["Outside"] = GraphLayoutCompoundNode(id = "Outside", widthHint = 100.0, heightHint = 56.0)

        val child = GraphLayoutCompoundGraph(id = "Container")
        child.nodes["A"] = GraphLayoutCompoundNode(id = "A", widthHint = 90.0, heightHint = 48.0)
        child.nodes["B"] = GraphLayoutCompoundNode(id = "B", widthHint = 90.0, heightHint = 48.0)
        child.edges["e_ab"] = GraphLayoutCompoundEdge(id = "e_ab", sourceId = "A", targetId = "B")

        root.children[child.id] = child

        val state = GraphLayoutCompoundGraphState(id = "nested", root = root)
        val result = CompoundLayoutEngine().layout(state)

        val container = assertNotNull(result.nodeLayoutsById["Container"])
        val a = assertNotNull(result.nodeLayoutsById["A"])
        val b = assertNotNull(result.nodeLayoutsById["B"])

        assertTrue(a.globalX >= container.globalX)
        assertTrue(a.globalY >= container.globalY)
        assertTrue(a.globalX + a.width <= container.globalX + container.width)
        assertTrue(a.globalY + a.height <= container.globalY + container.height)

        assertTrue(b.globalX >= container.globalX)
        assertTrue(b.globalY >= container.globalY)
        assertTrue(b.globalX + b.width <= container.globalX + container.width)
        assertTrue(b.globalY + b.height <= container.globalY + container.height)
    }

    @Test
    fun graph_and_node_transforms_match_local_plus_accumulated_offsets() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["L1"] = GraphLayoutCompoundNode(id = "L1", widthHint = 260.0, heightHint = 170.0)

        val level1 = GraphLayoutCompoundGraph(id = "L1")
        level1.nodes["L2"] = GraphLayoutCompoundNode(id = "L2", widthHint = 180.0, heightHint = 120.0)

        val level2 = GraphLayoutCompoundGraph(id = "L2")
        level2.nodes["Leaf"] = GraphLayoutCompoundNode(id = "Leaf", widthHint = 80.0, heightHint = 40.0)

        root.children["L1"] = level1
        level1.children["L2"] = level2

        val state = GraphLayoutCompoundGraphState(id = "deep", root = root)
        val result = CompoundLayoutEngine().layout(state)

        val l1Graph = assertNotNull(result.graphLayoutsById["L1"])
        val l2Graph = assertNotNull(result.graphLayoutsById["L2"])
        val l1Node = assertNotNull(result.nodeLayoutsById["L1"])
        val l2Node = assertNotNull(result.nodeLayoutsById["L2"])
        val leafNode = assertNotNull(result.nodeLayoutsById["Leaf"])

        assertEquals(l1Node.globalX + l1Graph.padding, l1Graph.globalOffsetX)
        assertEquals(l1Node.globalY + l1Graph.padding + l1Graph.headerHeight, l1Graph.globalOffsetY)

        assertEquals(l2Node.globalX + l2Graph.padding, l2Graph.globalOffsetX)
        assertEquals(l2Node.globalY + l2Graph.padding + l2Graph.headerHeight, l2Graph.globalOffsetY)

        val expectedLeafGlobalX = leafNode.localX + l2Graph.globalOffsetX
        val expectedLeafGlobalY = leafNode.localY + l2Graph.globalOffsetY
        assertEquals(expectedLeafGlobalX, leafNode.globalX)
        assertEquals(expectedLeafGlobalY, leafNode.globalY)
    }
}

