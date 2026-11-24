package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class test_LayoutTransition {

    private fun node(id: String, x: Double, y: Double, w: Double = 40.0, h: Double = 20.0, owner: String = "root") =
        CompoundNodeLayout(id, owner, x, y, x, y, w, h, isContainer = false)

    private fun layout(nodes: List<CompoundNodeLayout>, edges: Map<String, Pair<String, String>>, routes: Map<String, List<Pair<Double, Double>>>) =
        CompoundLayoutResult(
            nodeLayoutsById = nodes.associateBy { it.nodeId },
            graphLayoutsById = emptyMap(),
            edgeRoutesByEdgeId = routes,
            edgeEndpointsByEdgeId = edges,
        )

    // A->B, then A->B, B->C with everything moved.
    private val from = layout(
        listOf(node("A", 0.0, 0.0), node("B", 0.0, 100.0)),
        mapOf("A-B" to ("A" to "B")),
        mapOf("A-B" to listOf(20.0 to 20.0, 20.0 to 100.0)),
    )
    private val to = layout(
        listOf(node("A", 100.0, 0.0), node("B", 100.0, 100.0, w = 60.0), node("C", 100.0, 200.0)),
        mapOf("A-B" to ("A" to "B"), "B-C" to ("B" to "C")),
        mapOf("A-B" to listOf(120.0 to 20.0, 120.0 to 100.0), "B-C" to listOf(120.0 to 120.0, 120.0 to 200.0)),
    )

    @Test
    fun progress_0_draws_the_old_positions() {
        val d = interpolateLayouts(from, to, 0f)
        val b = d.layout.nodeLayoutsById.getValue("B")
        assertEquals(0.0, b.globalX); assertEquals(100.0, b.globalY); assertEquals(40.0, b.width)
        assertEquals(listOf(20.0 to 20.0, 20.0 to 100.0), d.layout.edgeRoutesByEdgeId.getValue("A-B"))
        assertEquals(0f, d.nodeAlpha.getValue("C"))
        assertEquals(0f, d.edgeAlpha.getValue("B-C"))
    }

    @Test
    fun progress_1_is_the_new_layout() {
        val d = interpolateLayouts(from, to, 1f)
        assertSame(to, d.layout)
        assertEquals(1f, d.nodeAlpha["C"] ?: 1f)
    }

    @Test
    fun progress_half_is_halfway() {
        val d = interpolateLayouts(from, to, 0.5f)
        val b = d.layout.nodeLayoutsById.getValue("B")
        assertEquals(50.0, b.globalX); assertEquals(100.0, b.globalY); assertEquals(50.0, b.width)
        val ab = d.layout.edgeRoutesByEdgeId.getValue("A-B")
        assertEquals(70.0 to 20.0, ab.first())
        // The end stays on B's drawn top border, sliding from B's middle to a third of the way along.
        assertEquals(100.0, ab.last().second)
        assertTrue(ab.last().first > b.globalX && ab.last().first < b.globalX + b.width / 2.0, "${ab.last()}")
        assertEquals(0.5f, d.nodeAlpha.getValue("C"))
    }

    @Test
    fun a_new_node_grows_out_of_the_node_it_is_connected_to() {
        val c0 = interpolateLayouts(from, to, 0f).layout.nodeLayoutsById.getValue("C")
        // Centred on B's old centre (20, 110).
        assertEquals(20.0, c0.globalX + c0.width / 2.0)
        assertEquals(110.0, c0.globalY + c0.height / 2.0)
    }

    @Test
    fun a_removed_node_fades_out_at_its_old_place() {
        val d = interpolateLayouts(to, from, 0.25f)
        val c = d.layout.nodeLayoutsById.getValue("C")
        assertEquals(100.0, c.globalX); assertEquals(200.0, c.globalY)
        assertEquals(0.75f, d.nodeAlpha.getValue("C"))
        assertEquals(0.75f, d.edgeAlpha.getValue("B-C"))
    }

    @Test
    fun edge_ends_stay_on_the_drawn_node_borders() {
        for (p in listOf(0.1f, 0.3f, 0.6f, 0.9f)) {
            val d = interpolateLayouts(from, to, p).layout
            d.edgeRoutesByEdgeId.forEach { (id, route) ->
                val (s, t) = d.edgeEndpointsByEdgeId.getValue(id)
                listOf(route.first() to d.nodeLayoutsById.getValue(s), route.last() to d.nodeLayoutsById.getValue(t)).forEach { (pt, n) ->
                    val onX = abs(pt.first - n.globalX) < 1e-9 || abs(pt.first - n.globalX - n.width) < 1e-9
                    val onY = abs(pt.second - n.globalY) < 1e-9 || abs(pt.second - n.globalY - n.height) < 1e-9
                    val inX = pt.first >= n.globalX - 1e-9 && pt.first <= n.globalX + n.width + 1e-9
                    val inY = pt.second >= n.globalY - 1e-9 && pt.second <= n.globalY + n.height + 1e-9
                    assertTrue((onX && inY) || (onY && inX), "p=$p $id end $pt not on the border of ${n.nodeId} $n")
                }
            }
        }
    }

    @Test
    fun routes_with_different_numbers_of_points_are_resampled() {
        val a = listOf(0.0 to 0.0, 100.0 to 0.0)
        val b = listOf(0.0 to 0.0, 0.0 to 50.0, 100.0 to 50.0)
        val half = interpolateRoutes(a, b, 0.5)
        assertEquals(0.0 to 0.0, half.first())
        assertEquals(100.0 to 25.0, half.last())
        val r = resampleRoute(b, 4)
        assertEquals(4, r.size)
        assertTrue(abs(r[1].second - 50.0) < 1e-9 && abs(r[1].first) < 1e-9, "${r[1]}")
    }
}
