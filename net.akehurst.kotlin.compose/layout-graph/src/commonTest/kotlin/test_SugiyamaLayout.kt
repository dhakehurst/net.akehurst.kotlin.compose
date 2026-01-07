package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class test_SugiyamaLayout {


    private fun edge(start: Int, finish: Int) = SugiyamaEdge("$start->$finish", start, finish)

    @Test
    fun simple_two_nodes() {
        val sl = SugiyamaLayout<Int>()
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        // Verify nodes are positioned
        assertNotNull(actual.nodePositions[1])
        assertNotNull(actual.nodePositions[2])

        // Verify edge route exists
        val route = actual.edgeRoutes[e12]
        assertNotNull(route)
        assertTrue(route!!.size >= 2, "Edge route should have at least 2 points")
    }

    @Test
    fun empty_graph() {
        val sl = SugiyamaLayout<Int>()
        val actual = sl.layoutGraph(emptyList(), emptyList())

        assertTrue(actual.nodePositions.isEmpty())
        assertTrue(actual.edgeRoutes.isEmpty())
    }

    @Test
    fun single_node_no_edges() {
        val sl = SugiyamaLayout<Int>()
        val actual = sl.layoutGraph(listOf(1), emptyList())

        assertEquals(1, actual.nodePositions.size)
        assertNotNull(actual.nodePositions[1])
        assertTrue(actual.edgeRoutes.isEmpty())
    }

    @Test
    fun three_nodes_linear() {
        val sl = SugiyamaLayout<Int>()
        val e12 = edge(1, 2)
        val e23 = edge(2, 3)
        val actual = sl.layoutGraph(
            listOf(1, 2, 3),
            listOf(e12, e23)
        )

        // All nodes should be positioned
        assertEquals(3, actual.nodePositions.size)

        // All edges should have routes
        assertEquals(2, actual.edgeRoutes.size)
        assertNotNull(actual.edgeRoutes[e12])
        assertNotNull(actual.edgeRoutes[e23])
    }

    @Test
    fun diamond_graph() {
        val sl = SugiyamaLayout<Int>()
        val actual = sl.layoutGraph(
            listOf(1, 2, 3, 4),
            listOf(edge(1, 2), edge(1, 3), edge(2, 4), edge(3, 4))
        )

        // All nodes should be positioned
        assertEquals(4, actual.nodePositions.size)

        // All edges should have routes
        assertEquals(4, actual.edgeRoutes.size)
    }

    @Test
    fun cycle_detection() {
        val sl = SugiyamaLayout<Int>()
        // Graph with a cycle: 1->2->3->1
        val actual = sl.layoutGraph(
            listOf(1, 2, 3),
            listOf(edge(1, 2), edge(2, 3), edge(3, 1))
        )

        // Should still produce valid layout (cycle will be broken)
        assertEquals(3, actual.nodePositions.size)
        assertEquals(3, actual.edgeRoutes.size)
    }

    @Test
    fun self_loop() {
        val sl = SugiyamaLayout<Int>()
        val e11 = edge(1, 1)
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(
            listOf(1, 2),
            listOf(e11, e12)
        )

        // Should handle self-loop
        assertEquals(2, actual.nodePositions.size)
        assertEquals(2, actual.edgeRoutes.size)

        // Self-loop should have a route
        val selfLoopRoute = actual.edgeRoutes[e11]
        assertNotNull(selfLoopRoute)
        assertTrue(selfLoopRoute!!.size >= 2, "Self-loop should have multiple points")
    }

    @Test
    fun multi_edges() {
        val sl = SugiyamaLayout<Int>()
        // Multiple edges between same nodes (different ids)
        val e12a = SugiyamaEdge("a", 1, 2)
        val e12b = SugiyamaEdge("b", 1, 2)
        val e12c = SugiyamaEdge("c", 1, 2)
        val actual = sl.layoutGraph(
            listOf(1, 2),
            listOf(e12a, e12b, e12c)
        )

        // Should handle multi-edges
        assertEquals(2, actual.nodePositions.size)
        assertTrue(actual.edgeRoutes.containsKey(e12a))
    }

    @Test
    fun rectilinear_routing() {
        val sl = SugiyamaLayout<Int>(edgeRouting = EdgeRouting.RECTILINEAR)
        val actual = sl.layoutGraph(
            listOf(1, 2, 3),
            listOf(edge(1, 2), edge(2, 3))
        )

        // Check that routes are orthogonal (all segments horizontal or vertical)
        actual.edgeRoutes.values.forEach { route ->
            for (i in 0 until route.size - 1) {
                val p1 = route[i]
                val p2 = route[i + 1]
                assertTrue(
                    p1.first == p2.first || p1.second == p2.second,
                    "Route segment should be horizontal or vertical"
                )
            }
        }
    }

    @Test
    fun direct_routing() {
        val sl = SugiyamaLayout<Int>(edgeRouting = EdgeRouting.DIRECT)
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(
            listOf(1, 2),
            listOf(e12)
        )

        val route = actual.edgeRoutes[e12]
        assertNotNull(route)
        // Direct routing should have minimal points
        assertTrue(route!!.size >= 2)
    }

    @Test
    fun custom_node_dimensions() {
        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) 200.0 else 100.0 },
            nodeHeight = { if (1 == it) 100.0 else 50.0 }
        )
        val actual = sl.layoutGraph(
            listOf(1, 2),
            listOf(edge(1, 2))
        )

        // Nodes should be positioned with custom dimensions taken into account
        assertNotNull(actual.nodePositions[1])
        assertNotNull(actual.nodePositions[2])

        // Layout should accommodate larger node
        assertTrue(actual.totalWidth >= 200.0)
    }

    @Test
    fun complex_graph() {
        val sl = SugiyamaLayout<Int>()
        // More complex graph structure
        val actual = sl.layoutGraph(
            listOf(1, 2, 3, 4, 5, 6),
            listOf(
                edge(1, 2), edge(1, 3),
                edge(2, 4), edge(2, 5),
                edge(3, 5), edge(3, 6),
                edge(4, 6), edge(5, 6)
            )
        )

        // All nodes positioned
        assertEquals(6, actual.nodePositions.size)

        // All edges routed
        assertEquals(8, actual.edgeRoutes.size)

        // Layout dimensions should be reasonable
        assertTrue(actual.totalWidth > 0)
        assertTrue(actual.totalHeight > 0)
    }

    @Test
    fun disconnected_components() {
        val sl = SugiyamaLayout<Int>()
        // Graph with disconnected components
        val actual = sl.layoutGraph(
            listOf(1, 2, 3, 4),
            listOf(edge(1, 2), edge(3, 4))
        )

        // All nodes should still be positioned
        assertEquals(4, actual.nodePositions.size)
        assertEquals(2, actual.edgeRoutes.size)
    }

    @Test
    fun port_strategy_unique() {
        val sl = SugiyamaLayout<Int>(
            portStrategy = PortStrategy.UNIQUE,
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        // Multiple edges from same node
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e14 = edge(1, 4)
        val actual = sl.layoutGraph(
            listOf(1, 2, 3, 4),
            listOf(e12, e13, e14)
        )

        // All edges should have routes
        assertEquals(3, actual.edgeRoutes.size)

        // Routes should start from different points on node 1
        val routes = listOf(
            actual.edgeRoutes[e12]!!,
            actual.edgeRoutes[e13]!!,
            actual.edgeRoutes[e14]!!
        )

        // First points should be different (unique ports)
        val startPoints = routes.map { it.first() }.toSet()
        assertTrue(startPoints.size >= 2, "Should use different ports for different edges")
    }

    @Test
    fun spacing_parameters() {
        val sl = SugiyamaLayout<Int>(
            layerSpacing = 200.0,
            nodeSpacing = 150.0
        )
        val actual = sl.layoutGraph(
            listOf(1, 2, 3),
            listOf(edge(1, 2), edge(1, 3))
        )

        // With larger spacing, total dimensions should be larger
        assertTrue(actual.totalHeight >= 200.0, "Should respect layer spacing")
        assertTrue(actual.totalWidth >= 150.0, "Should respect node spacing")
    }
}