package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_OrthogonalEdgeRouter {

    private val router = OrthogonalEdgeRouter()

    private fun node(id: String, x: Double, y: Double, w: Double = 100.0, h: Double = 50.0, container: String? = null) =
        RouterNode(id, x, y, w, h, container)

    private fun assertOrthogonal(route: List<Pair<Double, Double>>) {
        route.zipWithNext().forEach { (a, b) ->
            assertTrue(abs(a.first - b.first) < 1e-6 || abs(a.second - b.second) < 1e-6, "segment $a -> $b is not horizontal or vertical")
        }
    }

    private fun onBorder(p: Pair<Double, Double>, n: RouterNode): Boolean {
        val e = 1e-6
        val insideX = p.first >= n.x - e && p.first <= n.x + n.width + e
        val insideY = p.second >= n.y - e && p.second <= n.y + n.height + e
        val onVertical = abs(p.first - n.x) < e || abs(p.first - (n.x + n.width)) < e
        val onHorizontal = abs(p.second - n.y) < e || abs(p.second - (n.y + n.height)) < e
        return insideX && insideY && (onVertical || onHorizontal)
    }

    private fun crossesInterior(route: List<Pair<Double, Double>>, n: RouterNode): Boolean =
        route.zipWithNext().any { (a, b) ->
            // Sample along the segment; enough for axis-aligned segments.
            (0..20).any { i ->
                val t = i / 20.0
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t
                x > n.x + 1e-6 && x < n.x + n.width - 1e-6 && y > n.y + 1e-6 && y < n.y + n.height - 1e-6
            }
        }

    @Test
    fun straight_vertical_edge_has_two_points_on_the_borders() {
        val a = node("A", 0.0, 0.0)
        val b = node("B", 0.0, 200.0)
        val route = router.route(listOf(a, b), listOf(RouterEdge("e", "A", "B"))).getValue("e")
        assertEquals(listOf(50.0 to 50.0, 50.0 to 200.0), route)
    }

    @Test
    fun edge_goes_around_a_node_in_the_way() {
        val a = node("A", 0.0, 0.0)
        val middle = node("M", 0.0, 150.0)
        val b = node("B", 0.0, 300.0)
        val route = router.route(listOf(a, middle, b), listOf(RouterEdge("e", "A", "B"))).getValue("e")
        assertOrthogonal(route)
        assertTrue(!crossesInterior(route, middle), "route $route crosses M")
        assertTrue(onBorder(route.first(), a), "route starts at ${route.first()}, not on the border of A")
        assertTrue(onBorder(route.last(), b), "route ends at ${route.last()}, not on the border of B")
    }

    @Test
    fun edge_goes_around_an_unrelated_container_but_into_its_own() {
        val box = node("Box", 0.0, 150.0, 200.0, 100.0)
        val inner = node("Inner", 50.0, 175.0, container = "Box")
        val other = node("Other", 300.0, 150.0, 200.0, 100.0)
        val a = node("A", 0.0, 0.0)
        val b = node("B", 400.0, 400.0)
        val routes = router.route(
            listOf(box, inner, other, a, b),
            listOf(RouterEdge("toInner", "A", "Inner"), RouterEdge("toB", "A", "B"))
        )
        val toB = routes.getValue("toB")
        assertOrthogonal(toB)
        assertTrue(!crossesInterior(toB, box), "route $toB crosses Box")
        assertTrue(!crossesInterior(toB, other), "route $toB crosses Other")
        val toInner = routes.getValue("toInner")
        assertOrthogonal(toInner)
    }

    @Test
    fun overlapping_edges_are_pushed_apart() {
        val a = node("A", 0.0, 0.0)
        val b = node("B", 0.0, 200.0)
        val routes = router.route(listOf(a, b), listOf(RouterEdge("e1", "A", "B"), RouterEdge("e2", "B", "A")))
        val x1 = routes.getValue("e1").first().first
        val x2 = routes.getValue("e2").first().first
        assertTrue(abs(x1 - x2) >= router.edgeSpacing - 1e-6, "edges overlap at x=$x1 and x=$x2")
    }

    @Test
    fun routing_is_deterministic() {
        val nodes = listOf(node("A", 0.0, 0.0), node("M", 30.0, 150.0), node("B", 60.0, 300.0))
        val edges = listOf(RouterEdge("e", "A", "B"))
        assertEquals(router.route(nodes, edges), router.route(nodes, edges))
    }
}
