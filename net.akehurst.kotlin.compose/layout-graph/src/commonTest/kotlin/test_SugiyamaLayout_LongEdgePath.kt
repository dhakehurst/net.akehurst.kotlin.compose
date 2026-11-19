package net.akehurst.kotlin.compose.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A long edge (spanning more than one layer) is routed through its own dummy nodes,
 * not through another edge's real node.
 */
class test_SugiyamaLayout_LongEdgePath {

    @Test
    fun long_edge_does_not_pass_through_a_real_node() {
        val nodes = listOf("A", "B", "C")
        val e3 = SugiyamaEdge("e3", "A", "C")
        val edges = listOf(SugiyamaEdge("e1", "A", "B"), SugiyamaEdge("e2", "B", "C"), e3)
        val d = SugiyamaLayout<String>().layoutGraph(nodes, edges)

        // Default node size is 100 x 50.
        val (bx, by) = d.nodePositions.getValue("B")
        val route = d.edgeRoutes.getValue(e3)
        assertEquals(3, route.size, "e3 spans two layers, so it has one waypoint: $route")
        val insideB = route.filter { (x, y) -> x > bx && x < bx + 100.0 && y > by && y < by + 50.0 }
        assertTrue(insideB.isEmpty(), "e3 has waypoints inside B at ${d.nodePositions["B"]}: $insideB (route $route)")
    }
}
