package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_BusEdgeRouting {

    private val e = 1e-6

    private fun node(id: String, x: Double, y: Double, w: Double = 100.0, h: Double = 50.0) = RouterNode(id, x, y, w, h, null)

    private fun assertOrthogonal(route: List<Pair<Double, Double>>) {
        route.zipWithNext().forEach { (a, b) ->
            assertTrue(abs(a.first - b.first) < e || abs(a.second - b.second) < e, "segment $a -> $b is not horizontal or vertical")
        }
    }

    // Parent P on top, children A, B, C in a row below; edges go child -> parent, as UML generalisations.
    private val parent = node("P", 150.0, 0.0)
    private val children = listOf(node("A", 0.0, 200.0), node("B", 150.0, 200.0), node("C", 300.0, 200.0))
    private val generalisations = listOf(
        BusRouterEdge("a", "A", "P"),
        BusRouterEdge("b", "B", "P"),
        BusRouterEdge("c", "C", "P")
    )

    @Test
    fun children_below_a_parent_share_one_trunk_and_a_bus_midway() {
        val result = BusEdgeRouter().route(listOf(parent) + children, generalisations)
        assertEquals(listOf(EdgeBus("P", BorderSide.BOTTOM, listOf("a", "b", "c"))), result.buses)
        assertEquals(emptyList(), result.unroutedEdgeIds)
        // The bus is midway between the parent's bottom (50) and the children's top (200).
        val busY = 125.0
        result.routesByEdgeId.forEach { (id, route) ->
            assertOrthogonal(route)
            // Every route ends at the same point: the middle of the parent's bottom.
            assertEquals(200.0 to 50.0, route.last(), "edge $id")
            // ... and starts at the top of its child.
            assertEquals(200.0, route.first().second, "edge $id")
            // Edges that bend do so on the bus.
            if (route.size > 2) assertTrue(route.any { abs(it.second - busY) < e }, "edge $id does not use the bus at y=$busY: $route")
        }
        // The middle child is straight below the parent.
        assertEquals(listOf(200.0 to 200.0, 200.0 to 50.0), result.routesByEdgeId.getValue("b"))
    }

    @Test
    fun different_keys_do_not_share_a_bus() {
        val four = children + node("D", 450.0, 200.0)
        val edges = listOf(
            BusRouterEdge("a", "A", "P", targetKey = "gen"),
            BusRouterEdge("b", "B", "P", targetKey = "gen"),
            BusRouterEdge("c", "C", "P", targetKey = "assoc"),
            BusRouterEdge("d", "D", "P", targetKey = "assoc")
        )
        val result = BusEdgeRouter().route(listOf(parent) + four, edges)
        assertEquals(setOf(listOf("a", "b"), listOf("c", "d")), result.buses.map { it.edgeIds }.toSet())
        // The two trunks leave the parent at different points.
        val trunkA = result.routesByEdgeId.getValue("a").last()
        val trunkC = result.routesByEdgeId.getValue("c").last()
        assertTrue(abs(trunkA.first - trunkC.first) > 1.0, "trunks overlap: $trunkA, $trunkC")
    }

    @Test
    fun a_single_edge_is_not_a_bus() {
        val result = BusEdgeRouter().route(listOf(parent, children[0]), listOf(generalisations[0]))
        assertEquals(emptyList(), result.buses)
        assertEquals(listOf("a"), result.unroutedEdgeIds)
    }

    @Test
    fun an_edge_joins_the_bus_at_its_bigger_end_only() {
        // C has two parents: P (with A and B) and Q (with C only). C's edge to P joins P's bus;
        // its edge to Q stays a plain edge (Q's group would have only one edge).
        val q = node("Q", 450.0, 0.0)
        val edges = generalisations + BusRouterEdge("c2", "C", "Q")
        val result = BusEdgeRouter().route(listOf(parent, q) + children, edges)
        assertEquals(listOf(EdgeBus("P", BorderSide.BOTTOM, listOf("a", "b", "c"))), result.buses)
        assertEquals(listOf("c2"), result.unroutedEdgeIds)
    }

    @Test
    fun ties_go_to_the_target_end() {
        // Edges s1, s2 leave S; edges t1, t2 enter T. Edge "m" from S to T could join either group of 3:
        // S's group (s1, s2, m) or T's group (t1, t2, m); the target end (T) wins.
        val s = node("S", 300.0, 0.0)
        val t = node("T", 300.0, 400.0)
        val nodes = listOf(
            s, t,
            node("s1", 0.0, 200.0), node("s2", 600.0, 200.0),
            node("t1", 150.0, 200.0), node("t2", 450.0, 200.0)
        )
        val edges = listOf(
            BusRouterEdge("m", "S", "T"),
            BusRouterEdge("s1", "S", "s1"), BusRouterEdge("s2", "S", "s2"),
            BusRouterEdge("t1", "t1", "T"), BusRouterEdge("t2", "t2", "T")
        )
        val result = BusEdgeRouter().route(nodes, edges)
        val busOfM = result.buses.first { "m" in it.edgeIds }
        assertEquals("T", busOfM.nodeId)
    }

    @Test
    fun a_node_in_the_way_of_a_branch_leaves_that_edge_out() {
        // M sits between the bus line and child C, so C's branch would cross it.
        val m = node("M", 300.0, 140.0, h = 40.0)
        val result = BusEdgeRouter().route(listOf(parent, m) + children, generalisations)
        assertEquals(listOf(EdgeBus("P", BorderSide.BOTTOM, listOf("a", "b"))), result.buses)
        assertEquals(listOf("c"), result.unroutedEdgeIds)
    }

    @Test
    fun branches_into_the_same_side_of_a_node_are_spread() {
        // C has two parents P and Q in the same row; both buses reach C's top, at different points.
        val q = node("Q", 450.0, 0.0)
        val d = node("D", 450.0, 200.0)
        val edges = generalisations + listOf(BusRouterEdge("c2", "C", "Q"), BusRouterEdge("d", "D", "Q"))
        val result = BusEdgeRouter().route(listOf(parent, q, d) + children, edges)
        assertEquals(2, result.buses.size, "${result.buses}")
        val c1 = result.routesByEdgeId.getValue("c").first()
        val c2 = result.routesByEdgeId.getValue("c2").first()
        assertEquals(200.0, c1.second)
        assertEquals(200.0, c2.second)
        assertTrue(abs(c1.first - c2.first) > 10.0, "both branches enter C at the same point: $c1, $c2")
    }

    private fun crossesInterior(route: List<Pair<Double, Double>>, n: RouterNode): Boolean =
        route.zipWithNext().any { (a, b) ->
            (0..40).any { i ->
                val t = i / 40.0
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t
                x > n.x + e && x < n.x + n.width - e && y > n.y + e && y < n.y + n.height - e
            }
        }

    @Test
    fun no_bus_route_crosses_a_node_after_the_bus_moves() {
        // C at the bottom; edges from P, P2, P3 (top row) and Q (a middle row) all end at C.
        // R blocks Q's branch, so Q leaves the bus and the bus moves up, midway to the top row.
        // P's branch from there up to P would then cross T: P must leave the bus too.
        val nodes = listOf(
            node("C", 500.0, 600.0),
            node("P", 0.0, 0.0), node("P2", 700.0, 0.0), node("P3", 900.0, 0.0),
            node("Q", 300.0, 400.0),
            node("R", 320.0, 470.0, w = 60.0, h = 30.0),
            node("T", 0.0, 150.0)
        )
        val edges = listOf("P", "P2", "P3", "Q").map { BusRouterEdge("e$it", it, "C") }
        val result = BusEdgeRouter().route(nodes, edges)
        result.routesByEdgeId.forEach { (id, route) ->
            val ends = edges.first { it.id == id }.let { setOf(it.sourceId, it.targetId) }
            nodes.filter { it.id !in ends }.forEach { other ->
                assertTrue(!crossesInterior(route, other), "edge $id crosses ${other.id}: $route")
            }
        }
        assertEquals(listOf(listOf("eP2", "eP3")), result.buses.map { it.edgeIds })
    }

    @Test
    fun a_branch_does_not_run_through_the_node_of_another_branch() {
        // P has edges to T (next row, straight below), X (next row) and C (three rows down, below T).
        // C's branch from the bus would run down through T, which is an end of the same bus: C must leave the bus.
        val nodes = listOf(
            node("P", 0.0, 0.0), node("T", 0.0, 140.0), node("X", 200.0, 140.0), node("C", 0.0, 420.0)
        )
        val edges = listOf(BusRouterEdge("pt", "P", "T"), BusRouterEdge("px", "P", "X"), BusRouterEdge("pc", "P", "C"))
        val result = BusEdgeRouter().route(nodes, edges)
        assertEquals(listOf(listOf("pt", "px")), result.buses.map { it.edgeIds })
        assertEquals(listOf("pc"), result.unroutedEdgeIds)
    }

    @Test
    fun layered_bus_routes_never_cross_a_node() {
        // Random layered graphs: no edge on a bus may run through a node that is not one of its ends.
        val problems = mutableListOf<String>()
        for (seed in 0 until 150) {
            val rnd = kotlin.random.Random(seed)
            val n = 6 + rnd.nextInt(8)
            val root = GraphLayoutCompoundGraph("root", childLayout = SugiyamaChildLayout())
            (0 until n).forEach { root.nodes["N$it"] = GraphLayoutCompoundNode("N$it", widthHint = 60.0 + rnd.nextInt(80), heightHint = 40.0) }
            repeat(n + rnd.nextInt(n)) {
                val a = rnd.nextInt(n); val b = rnd.nextInt(n)
                if (a != b) root.edges["e$it"] = GraphLayoutCompoundEdge("e$it", "N$a", "N$b")
            }
            val r = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState("s$seed", routing = EdgeRouting.BUS, root = root))
            val onBus = r.edgeBuses.flatMap { it.edgeIds }.toSet()
            r.edgeRoutesByEdgeId.filterKeys { it in onBus }.forEach { (id, route) ->
                val edge = root.edges.getValue(id)
                r.nodeLayoutsById.values.filter { it.nodeId != edge.sourceId && it.nodeId != edge.targetId }.forEach { nl ->
                    if (crossesInterior(route, RouterNode(nl.nodeId, nl.globalX, nl.globalY, nl.width, nl.height, null))) {
                        problems += "seed $seed: edge $id crosses ${nl.nodeId}: $route"
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.take(10).joinToString("\n"))
    }

    @Test
    fun buses_sharing_a_channel_are_spread_across_it() {
        // P's bus (A, C) and Q's bus (B, D) lie in the same gap (y 50 to 200) and overlap along x.
        val p = node("P", 150.0, 0.0)
        val q = node("Q", 300.0, 0.0)
        val nodes = listOf(p, q, node("A", 0.0, 200.0), node("B", 150.0, 200.0), node("C", 300.0, 200.0), node("D", 450.0, 200.0))
        val edges = listOf(
            BusRouterEdge("a", "A", "P"), BusRouterEdge("c", "C", "P"),
            BusRouterEdge("b", "B", "Q"), BusRouterEdge("d", "D", "Q")
        )
        val result = BusEdgeRouter().route(nodes, edges)
        assertEquals(setOf("P" to listOf("a", "c"), "Q" to listOf("b", "d")), result.buses.map { it.nodeId to it.edgeIds }.toSet())
        // The horizontal (bus) segment of each route.
        fun busY(id: String) = result.routesByEdgeId.getValue(id).zipWithNext().single { (s, t) -> abs(s.second - t.second) < e && abs(s.first - t.first) > e }.first.second
        assertEquals(busY("a"), busY("c"))
        assertEquals(busY("b"), busY("d"))
        // Spread evenly over the gap: at a third and two thirds.
        assertEquals(setOf(100.0, 150.0), setOf(busY("a"), busY("b")))
    }

    @Test
    fun along_trunk_hint_runs_the_bus_down_from_the_bottom() {
        // Indented tree: P at top left, children in a column below, to the right of the trunk.
        val p = node("P", 0.0, 0.0)
        val kids = listOf(node("A", 56.0, 74.0), node("B", 56.0, 148.0))
        val edges = listOf(BusRouterEdge("a", "A", "P"), BusRouterEdge("b", "B", "P"))
        val hint = BusHint(BorderSide.BOTTOM, busAlongTrunk = true, trunkOffset = 24.0)
        val result = BusEdgeRouter().route(listOf(p) + kids, edges, mapOf("P" to hint))
        assertEquals(listOf(EdgeBus("P", BorderSide.BOTTOM, listOf("a", "b"))), result.buses)
        // A: from its left side, at its middle, to the trunk at x=24, then up to P's bottom.
        assertEquals(listOf(56.0 to 99.0, 24.0 to 99.0, 24.0 to 50.0), result.routesByEdgeId.getValue("a"))
        assertEquals(listOf(56.0 to 173.0, 24.0 to 173.0, 24.0 to 50.0), result.routesByEdgeId.getValue("b"))
    }

    // ---- through the engine ----

    private fun triangle() = GraphLayoutEdgeSymbol(listOf(Offset(0f, 0f), Offset(-14f, 7f), Offset(-14f, -7f)))
    private fun arrow() = GraphLayoutEdgeSymbol(listOf(Offset(0f, 0f), Offset(-12f, 6f), Offset(-12f, -6f)))

    private fun generalisationState(
        childLayout: ChildLayoutAlgorithm,
        endSymbols: Map<String, GraphLayoutEdgeSymbol> = emptyMap(),
        groups: Map<String, String> = emptyMap()
    ): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root", childLayout = childLayout)
        listOf("P", "A", "B", "C").forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        listOf("A", "B", "C").forEach { c ->
            val id = "e$c"
            root.edges[id] = GraphLayoutCompoundEdge(id, c, "P", busGroup = groups[id])
        }
        return GraphLayoutCompoundGraphState("bus", routing = EdgeRouting.BUS, root = root).also { s ->
            listOf("eA", "eB", "eC").forEach { id -> s.addEdgeContent(id, GraphLayoutEdgeContent(endSymbol = endSymbols[id] ?: triangle())) }
        }
    }

    @Test
    fun engine_puts_a_layered_generalisation_tree_on_one_bus() {
        val result = CompoundLayoutEngine().layout(generalisationState(SugiyamaChildLayout()))
        assertEquals(listOf("P" to listOf("eA", "eB", "eC")), result.edgeBuses.map { it.nodeId to it.edgeIds })
        val p = result.nodeLayoutsById.getValue("P")
        val ends = listOf("eA", "eB", "eC").map { result.edgeRoutesByEdgeId.getValue(it).last() }.toSet()
        assertEquals(1, ends.size, "edges do not share a trunk: $ends")
        val end = ends.single()
        assertTrue(abs(end.second - p.globalY) < e || abs(end.second - (p.globalY + p.height)) < e, "trunk $end is not on P's top or bottom")
    }

    @Test
    fun engine_does_not_merge_edges_with_different_symbols_or_groups() {
        val differentSymbol = CompoundLayoutEngine().layout(generalisationState(SugiyamaChildLayout(), endSymbols = mapOf("eC" to arrow())))
        assertEquals(listOf(listOf("eA", "eB")), differentSymbol.edgeBuses.map { it.edgeIds })

        val differentGroup = CompoundLayoutEngine().layout(generalisationState(SugiyamaChildLayout(), groups = mapOf("eA" to "x")))
        assertEquals(listOf(listOf("eB", "eC")), differentGroup.edgeBuses.map { it.edgeIds })
    }

    @Test
    fun engine_with_rectilinear_routing_has_no_buses() {
        val state = generalisationState(SugiyamaChildLayout()).copy(routing = EdgeRouting.RECTILINEAR)
        assertEquals(emptyList(), CompoundLayoutEngine().layout(state).edgeBuses)
    }

    @Test
    fun left_right_layered_layout_puts_layers_in_columns() {
        val result = CompoundLayoutEngine().layout(generalisationState(SugiyamaChildLayout(direction = LayerDirection.LEFT_RIGHT)))
        val n = result.nodeLayoutsById
        val p = n.getValue("P")
        listOf("A", "B", "C").forEach { c ->
            val child = n.getValue(c)
            // Children and parent are in different columns.
            assertTrue(child.globalX + child.width <= p.globalX + e || p.globalX + p.width <= child.globalX + e, "$c and P overlap horizontally")
        }
        // The children share a column.
        assertEquals(1, listOf("A", "B", "C").map { n.getValue(it).globalX }.toSet().size)
        val bus = result.edgeBuses.single()
        assertTrue(bus.side == BorderSide.LEFT || bus.side == BorderSide.RIGHT, "bus side ${bus.side}")
    }

    @Test
    fun tree_layout_indents_children_under_their_parent() {
        val layout = TreeChildLayout()
        val sizes = listOf("P", "A", "B", "C", "A1").associateWith { 100.0 to 50.0 }
        val result = layout.layout(
            ChildLayoutInput(
                graphId = "g",
                nodes = sizes.keys.map { GraphLayoutCompoundNode(it) },
                nodeSizesById = sizes,
                edges = listOf(
                    ChildLayoutEdge("eA", "A", "P"), ChildLayoutEdge("eB", "B", "P"),
                    ChildLayoutEdge("eC", "C", "P"), ChildLayoutEdge("eA1", "A1", "A")
                )
            )
        )
        val tl = result.nodeTopLeftById
        // Rows in pre-order: P, A, A1, B, C.
        assertEquals(listOf("P", "A", "A1", "B", "C"), tl.entries.sortedBy { it.value.second }.map { it.key })
        // Children start right of the parent's right side, past trunk and branch.
        assertEquals(100.0 + 32.0 + 32.0, tl.getValue("A").first)
        assertEquals(tl.getValue("A").first, tl.getValue("C").first)
        assertEquals(tl.getValue("A").first + 164.0, tl.getValue("A1").first)
        assertEquals(setOf("P", "A"), result.busHintsByNodeId.keys)
        assertEquals(BusHint(BorderSide.RIGHT, allowSingleEdge = true), result.busHintsByNodeId.getValue("P"))
    }

    @Test
    fun tree_layout_with_source_parents() {
        val sizes = listOf("P", "A").associateWith { 100.0 to 50.0 }
        val result = TreeChildLayout(parentEnd = TreeParentEnd.SOURCE, trunkSide = TreeTrunkSide.BOTTOM).layout(
            ChildLayoutInput("g", sizes.keys.map { GraphLayoutCompoundNode(it) }, sizes, listOf(ChildLayoutEdge("e", "P", "A")))
        )
        assertEquals(0.0 to 0.0, result.nodeTopLeftById.getValue("P"))
        assertEquals(24.0 + 32.0 to 74.0, result.nodeTopLeftById.getValue("A"))
        assertEquals(BusHint(BorderSide.BOTTOM, busAlongTrunk = true, trunkOffset = 24.0, allowSingleEdge = true), result.busHintsByNodeId.getValue("P"))
    }

    @Test
    fun engine_routes_tree_layout_edges_from_the_parents_side() {
        val result = CompoundLayoutEngine().layout(generalisationState(TreeChildLayout()))
        val p = result.nodeLayoutsById.getValue("P")
        val bus = result.edgeBuses.single()
        assertEquals(BorderSide.RIGHT, bus.side)
        listOf("eA", "eB", "eC").forEach { id ->
            val route = result.edgeRoutesByEdgeId.getValue(id)
            assertOrthogonal(route)
            assertEquals(p.globalX + p.width to p.globalY + p.height / 2.0, route.last(), "edge $id")
        }
    }

    @Test
    fun engine_routes_tree_layout_edges_down_from_the_parents_bottom() {
        val result = CompoundLayoutEngine().layout(generalisationState(TreeChildLayout(trunkSide = TreeTrunkSide.BOTTOM)))
        val p = result.nodeLayoutsById.getValue("P")
        assertEquals(BorderSide.BOTTOM, result.edgeBuses.single().side)
        listOf("eA", "eB", "eC").forEach { id ->
            val route = result.edgeRoutesByEdgeId.getValue(id)
            assertOrthogonal(route)
            assertEquals(p.globalX + 24.0 to p.globalY + p.height, route.last(), "edge $id")
            assertEquals(3, route.size, "edge $id: $route")
        }
    }
}
