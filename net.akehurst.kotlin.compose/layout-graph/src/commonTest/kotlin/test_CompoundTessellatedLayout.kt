package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertTrue

class test_CompoundTessellatedLayout {

    @Test
    fun tessellated_children_fill_their_graph_bounds() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Machine"] = GraphLayoutCompoundNode(id = "Machine", widthHint = 700.0, heightHint = 420.0)

        val machine = GraphLayoutCompoundGraph(
            id = "Machine",
            layoutAlgorithm = CompoundLayoutAlgorithm.TESSELLATED
        )
        machine.nodes["RegionA"] = GraphLayoutCompoundNode(id = "RegionA", widthHint = 180.0, heightHint = 240.0)
        machine.nodes["RegionB"] = GraphLayoutCompoundNode(id = "RegionB", widthHint = 300.0, heightHint = 120.0)

        root.children[machine.id] = machine

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "tess_fill", root = root))

        val machineGraph = result.graphLayoutsById.getValue("Machine")
        val regionA = result.nodeLayoutsById.getValue("RegionA")
        val regionB = result.nodeLayoutsById.getValue("RegionB")

        val eps = 1e-6
        val left = minOf(regionA.globalX, regionB.globalX)
        val right = maxOf(regionA.globalX + regionA.width, regionB.globalX + regionB.width)
        val top = minOf(regionA.globalY, regionB.globalY)
        val bottom = maxOf(regionA.globalY + regionA.height, regionB.globalY + regionB.height)

        assertTrue(kotlin.math.abs(left - machineGraph.globalOffsetX) <= eps)
        assertTrue(kotlin.math.abs(top - machineGraph.globalOffsetY) <= eps)
        assertTrue(kotlin.math.abs(right - (machineGraph.globalOffsetX + machineGraph.contentWidth)) <= eps)
        assertTrue(kotlin.math.abs(bottom - (machineGraph.globalOffsetY + machineGraph.contentHeight)) <= eps)
    }

    @Test
    fun cross_boundary_edge_between_tessellated_regions_stays_inside_parent_container() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Machine"] = GraphLayoutCompoundNode(id = "Machine", widthHint = 700.0, heightHint = 420.0)

        val machine = GraphLayoutCompoundGraph(
            id = "Machine",
            layoutAlgorithm = CompoundLayoutAlgorithm.TESSELLATED
        )
        machine.nodes["RegionA"] = GraphLayoutCompoundNode(id = "RegionA", widthHint = 280.0, heightHint = 280.0)
        machine.nodes["RegionB"] = GraphLayoutCompoundNode(id = "RegionB", widthHint = 280.0, heightHint = 280.0)
        machine.edges["e_cross"] = GraphLayoutCompoundEdge(id = "e_cross", sourceId = "Idle", targetId = "Wait")

        val regionA = GraphLayoutCompoundGraph(id = "RegionA")
        regionA.nodes["Idle"] = GraphLayoutCompoundNode(id = "Idle", widthHint = 100.0, heightHint = 48.0)
        val regionB = GraphLayoutCompoundGraph(id = "RegionB")
        regionB.nodes["Wait"] = GraphLayoutCompoundNode(id = "Wait", widthHint = 100.0, heightHint = 48.0)

        root.children[machine.id] = machine
        machine.children[regionA.id] = regionA
        machine.children[regionB.id] = regionB

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "tess_edge", root = root))

        val route = result.edgeRoutesByEdgeId.getValue("e_cross")
        val machineNode = result.nodeLayoutsById.getValue("Machine")

        val left = machineNode.globalX
        val right = machineNode.globalX + machineNode.width
        val top = machineNode.globalY
        val bottom = machineNode.globalY + machineNode.height
        val eps = 1e-6

        assertTrue(route.isNotEmpty())
        assertTrue(route.all { point ->
            point.first >= left - eps && point.first <= right + eps &&
                point.second >= top - eps && point.second <= bottom + eps
        }, "Route should remain inside Machine bounds: $route")
    }
}

