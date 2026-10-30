package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class test_Ports {

    private val eps = 1e-6

    private fun node(id: String, w: Double = 100.0, h: Double = 50.0, ports: List<GraphLayoutCompoundPort> = emptyList()) =
        GraphLayoutCompoundNode(id = id, widthHint = w, heightHint = h, ports = ports)

    private fun port(id: String, side: BorderSide? = null, placement: BorderPlacement = BorderPlacement.STRADDLE) =
        GraphLayoutCompoundPort(id, side, placement, widthHint = 20.0, heightHint = 20.0)

    private fun edge(id: String, s: String, t: String) = GraphLayoutCompoundEdge(id, s, t)

    private fun layout(root: GraphLayoutCompoundGraph, routing: EdgeRouting = EdgeRouting.RECTILINEAR) =
        CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "s", routing = routing, root = root))

    private fun CompoundNodeLayout.cx() = globalX + width / 2.0
    private fun CompoundNodeLayout.cy() = globalY + height / 2.0

    @Test
    fun free_port_goes_on_the_side_facing_the_node_it_connects_to() {
        // Sugiyama puts B below Host, so the port should be on Host's bottom side, centred on the border line.
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("P")))
        root.nodes["B"] = node("B")
        root.edges["e1"] = edge("e1", "Host", "B")
        root.edges["e2"] = edge("e2", "P", "B")
        val r = layout(root)

        val host = assertNotNull(r.nodeLayoutsById["Host"])
        val p = assertNotNull(r.nodeLayoutsById["P"])
        assertEquals(BorderSide.BOTTOM, p.borderSide)
        assertEquals("Host", p.borderHostId)
        assertEquals(host.globalY + host.height, p.cy(), eps, "straddles the bottom border")
        assertTrue(p.globalX >= host.globalX - eps && p.globalX + p.width <= host.globalX + host.width + eps, "within the side")
    }

    @Test
    fun pinned_side_is_kept() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("P", BorderSide.LEFT)))
        root.nodes["B"] = node("B")
        root.edges["e1"] = edge("e1", "Host", "B")
        root.edges["e2"] = edge("e2", "P", "B")
        val r = layout(root)

        val host = assertNotNull(r.nodeLayoutsById["Host"])
        val p = assertNotNull(r.nodeLayoutsById["P"])
        assertEquals(BorderSide.LEFT, p.borderSide)
        assertEquals(host.globalX, p.cx(), eps)
    }

    @Test
    fun inside_and_outside_placements_touch_the_border() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node(
            "Host",
            ports = listOf(port("In", BorderSide.RIGHT, BorderPlacement.INSIDE), port("Out", BorderSide.RIGHT, BorderPlacement.OUTSIDE))
        )
        val r = layout(root)

        val host = assertNotNull(r.nodeLayoutsById["Host"])
        val inside = assertNotNull(r.nodeLayoutsById["In"])
        val outside = assertNotNull(r.nodeLayoutsById["Out"])
        val right = host.globalX + host.width
        assertEquals(right, inside.globalX + inside.width, eps)
        assertEquals(right, outside.globalX, eps)
        assertFalse(inside.globalY < outside.globalY + outside.height && outside.globalY < inside.globalY + inside.height, "they do not overlap")
    }

    @Test
    fun owner_grows_to_fit_its_ports_and_room_is_kept_around_it() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", 40.0, 40.0, ports = (1..4).map { port("P$it", BorderSide.TOP) })
        root.nodes["Above"] = node("Above")
        root.edges["e"] = edge("e", "Above", "Host")
        val r = layout(root)

        val host = assertNotNull(r.nodeLayoutsById["Host"])
        val ports = (1..4).map { assertNotNull(r.nodeLayoutsById["P$it"]) }.sortedBy { it.globalX }
        assertTrue(host.width >= 4 * 20.0, "host width ${host.width}")
        ports.zipWithNext().forEach { (a, b) -> assertTrue(a.globalX + a.width <= b.globalX + eps, "$a overlaps $b") }
        val above = assertNotNull(r.nodeLayoutsById["Above"])
        ports.forEach { p -> assertTrue(p.globalY >= above.globalY + above.height - eps, "port ${p.nodeId} overlaps Above") }
    }

    @Test
    fun children_of_a_container_keep_clear_of_its_ports() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("P", BorderSide.BOTTOM, BorderPlacement.INSIDE)))
        val inner = GraphLayoutCompoundGraph("Host")
        inner.nodes["C"] = node("C", 60.0, 30.0)
        root.children["Host"] = inner
        root.edges["e"] = edge("e", "C", "P")
        val r = layout(root)

        val p = assertNotNull(r.nodeLayoutsById["P"])
        val c = assertNotNull(r.nodeLayoutsById["C"])
        assertTrue(c.globalY + c.height <= p.globalY + eps, "child $c overlaps border node $p")
    }

    @Test
    fun edges_leave_a_port_through_its_side() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("P", BorderSide.RIGHT)))
        root.nodes["B"] = node("B")
        val inner = GraphLayoutCompoundGraph("Host")
        inner.nodes["C"] = node("C", 30.0, 20.0)
        root.children["Host"] = inner
        root.edges["out"] = edge("out", "P", "B")
        root.edges["in"] = edge("in", "C", "P")
        val r = layout(root)

        val p = assertNotNull(r.nodeLayoutsById["P"])
        val outRoute = r.edgeRoutesByEdgeId.getValue("out")
        assertEquals(p.globalX + p.width to p.cy(), outRoute.first(), "outward edge starts at the outer side")
        assertTrue(outRoute[1].first > outRoute[0].first, "and goes right first")
        val inRoute = r.edgeRoutesByEdgeId.getValue("in")
        assertEquals(p.globalX to p.cy(), inRoute.last(), "inward edge ends at the inner side")
        assertTrue(inRoute[inRoute.lastIndex - 1].first < inRoute.last().first, "arriving from the left")
    }

    @Test
    fun port_ids_must_not_clash_with_node_ids() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("B")))
        root.nodes["B"] = node("B")
        val result = GraphLayoutCompoundGraphState(id = "s", root = root).validateInvariants()
        assertFalse(result.isValid)
    }

    @Test
    fun children_connected_to_left_and_right_ports_are_placed_on_that_side() {
        // Without the ports, A and B would be ordered by id (A left of B). The ports put B left and A right.
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("L", BorderSide.LEFT), port("R", BorderSide.RIGHT)))
        val inner = GraphLayoutCompoundGraph("Host")
        inner.nodes["A"] = node("A", 40.0, 20.0)
        inner.nodes["B"] = node("B", 40.0, 20.0)
        root.children["Host"] = inner
        root.edges["e1"] = edge("e1", "L", "B")
        root.edges["e2"] = edge("e2", "A", "R")
        val r = layout(root)

        val a = assertNotNull(r.nodeLayoutsById["A"])
        val b = assertNotNull(r.nodeLayoutsById["B"])
        assertTrue(b.globalX < a.globalX, "B (${b.globalX}) should be left of A (${a.globalX})")
    }

    @Test
    fun ports_stay_on_a_collapsed_owner() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Host"] = node("Host", ports = listOf(port("P", BorderSide.RIGHT)))
        val inner = GraphLayoutCompoundGraph("Host", isCollapsed = true)
        inner.nodes["C"] = node("C")
        root.children["Host"] = inner
        root.edges["e"] = edge("e", "C", "P")
        val r = layout(root)

        val host = assertNotNull(r.nodeLayoutsById["Host"])
        val p = assertNotNull(r.nodeLayoutsById["P"])
        assertEquals(host.globalX + host.width, p.cx(), eps)
    }

    @Test
    fun ports_on_a_port_sit_on_its_outer_side_and_it_grows_to_fit_them() {
        val root = GraphLayoutCompoundGraph("root")
        val nested = listOf(port("Re"), port("Im"))
        root.nodes["Host"] = node(
            "Host",
            ports = listOf(GraphLayoutCompoundPort("C", BorderSide.BOTTOM, widthHint = 20.0, heightHint = 20.0, ports = nested))
        )
        root.nodes["A"] = node("A")
        root.nodes["B"] = node("B")
        root.edges["e1"] = edge("e1", "Host", "A")
        root.edges["e2"] = edge("e2", "Re", "A")
        root.edges["e3"] = edge("e3", "Im", "B")
        val r = layout(root)

        val c = assertNotNull(r.nodeLayoutsById["C"])
        val re = assertNotNull(r.nodeLayoutsById["Re"])
        val im = assertNotNull(r.nodeLayoutsById["Im"])
        assertEquals("C", re.borderHostId)
        assertEquals(BorderSide.BOTTOM, re.borderSide)
        assertEquals(BorderSide.BOTTOM, im.borderSide)
        assertEquals(c.globalY + c.height, re.cy(), eps, "Re straddles the bottom of C")
        assertTrue(c.width >= re.width + im.width, "C is wide enough for both (${c.width})")
        assertFalse(re.globalX < im.globalX + im.width && im.globalX < re.globalX + re.width, "Re and Im do not overlap")
        val a = assertNotNull(r.nodeLayoutsById["A"])
        val b = assertNotNull(r.nodeLayoutsById["B"])
        listOf(a, b).forEach { n -> assertTrue(n.globalY >= re.globalY + re.height - eps, "${n.nodeId} overlaps the nested ports") }
    }

    @Test
    fun a_free_port_on_a_port_does_not_face_into_the_owner() {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["Above"] = node("Above")
        root.nodes["Host"] = node(
            "Host",
            ports = listOf(GraphLayoutCompoundPort("C", BorderSide.BOTTOM, widthHint = 40.0, heightHint = 20.0, ports = listOf(port("N"))))
        )
        root.edges["e1"] = edge("e1", "Above", "Host")
        root.edges["e2"] = edge("e2", "Above", "N")
        val r = layout(root)
        assertTrue(r.nodeLayoutsById.getValue("N").borderSide != BorderSide.TOP)
    }

    @Test
    fun layout_is_deterministic_with_ports() {
        fun build() = GraphLayoutCompoundGraph("root").also { root ->
            root.nodes["Host"] = node("Host", ports = listOf(port("P"), port("Q")))
            root.nodes["B"] = node("B")
            root.edges["e1"] = edge("e1", "P", "B")
            root.edges["e2"] = edge("e2", "B", "Q")
        }
        val a = layout(build())
        val b = layout(build())
        assertEquals(a.nodeLayoutsById, b.nodeLayoutsById)
        assertEquals(a.edgeRoutesByEdgeId, b.edgeRoutesByEdgeId)
        assertTrue(abs(a.nodeLayoutsById.getValue("P").cx() - a.nodeLayoutsById.getValue("Q").cx()) > eps ||
            abs(a.nodeLayoutsById.getValue("P").cy() - a.nodeLayoutsById.getValue("Q").cy()) > eps, "P and Q are not on top of each other")
    }
}
