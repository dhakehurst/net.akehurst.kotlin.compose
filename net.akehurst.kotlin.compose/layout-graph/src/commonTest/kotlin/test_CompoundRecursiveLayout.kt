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

        assertEquals(l1Node.globalX + 12.0, l1Graph.globalOffsetX)
        assertEquals(l1Node.globalY + 24.0, l1Graph.globalOffsetY)

        assertEquals(l2Node.globalX + 12.0, l2Graph.globalOffsetX)
        assertEquals(l2Node.globalY + 24.0, l2Graph.globalOffsetY)

        val expectedLeafGlobalX = leafNode.localX + l2Graph.globalOffsetX
        val expectedLeafGlobalY = leafNode.localY + l2Graph.globalOffsetY
        assertEquals(expectedLeafGlobalX, leafNode.globalX)
        assertEquals(expectedLeafGlobalY, leafNode.globalY)

        assertEquals("root", result.graphId)
        assertTrue(result.localBounds.right >= result.localBounds.left)
        assertTrue(result.globalBounds.right >= result.globalBounds.left)
        assertTrue("L1" in result.childResults)
        assertTrue("Leaf" in result.childResults.getValue("L1").childResults.getValue("L2").globalNodePositions)
    }

    @Test
    fun parent_level_layout_treats_child_graphs_as_atomic_nodes_for_cross_container_edges() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Left"] = GraphLayoutCompoundNode(id = "Left", widthHint = 220.0, heightHint = 120.0)
        root.nodes["Right"] = GraphLayoutCompoundNode(id = "Right", widthHint = 220.0, heightHint = 120.0)

        val left = GraphLayoutCompoundGraph(id = "Left")
        left.nodes["LA"] = GraphLayoutCompoundNode(id = "LA", widthHint = 90.0, heightHint = 48.0)

        val right = GraphLayoutCompoundGraph(id = "Right")
        right.nodes["RB"] = GraphLayoutCompoundNode(id = "RB", widthHint = 90.0, heightHint = 48.0)

        root.children[left.id] = left
        root.children[right.id] = right
        root.edges["e_lr"] = GraphLayoutCompoundEdge(id = "e_lr", sourceId = "LA", targetId = "RB")

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "atomic", root = root))

        val leftContainer = assertNotNull(result.nodeLayoutsById["Left"])
        val rightContainer = assertNotNull(result.nodeLayoutsById["Right"])
        assertTrue(rightContainer.globalY > leftContainer.globalY)
    }

    @Test
    fun child_layout_profile_override_changes_local_level_spacing() {
        fun createState(childProfile: CompoundLayoutProfile?): GraphLayoutCompoundGraphState {
            val root = GraphLayoutCompoundGraph(
                id = "root",
                layoutProfile = CompoundLayoutProfile.HIERARCHY_BIASED
            )
            root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 260.0, heightHint = 170.0)

            val child = GraphLayoutCompoundGraph(
                id = "Container",
                layoutProfile = childProfile
            )
            child.nodes["A"] = GraphLayoutCompoundNode(id = "A", widthHint = 90.0, heightHint = 48.0)
            child.nodes["B"] = GraphLayoutCompoundNode(id = "B", widthHint = 90.0, heightHint = 48.0)
            child.edges["e_ab"] = GraphLayoutCompoundEdge(id = "e_ab", sourceId = "A", targetId = "B")
            root.children[child.id] = child

            return GraphLayoutCompoundGraphState(id = "profile_$childProfile", root = root)
        }

        val inherited = CompoundLayoutEngine().layout(createState(childProfile = null))
        val compact = CompoundLayoutEngine().layout(createState(childProfile = CompoundLayoutProfile.COMPACT))

        val inheritedA = assertNotNull(inherited.nodeLayoutsById["A"])
        val inheritedB = assertNotNull(inherited.nodeLayoutsById["B"])
        val compactA = assertNotNull(compact.nodeLayoutsById["A"])
        val compactB = assertNotNull(compact.nodeLayoutsById["B"])

        val inheritedDeltaY = inheritedB.localY - inheritedA.localY
        val compactDeltaY = compactB.localY - compactA.localY
        assertTrue(compactDeltaY < inheritedDeltaY)

        val childGraphLayout = assertNotNull(compact.graphLayoutsById["Container"])
        assertEquals(CompoundLayoutProfile.COMPACT, childGraphLayout.profile)
    }

    @Test
    fun cross_container_route_avoids_unrelated_container_interior() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Left"] = GraphLayoutCompoundNode(id = "Left", widthHint = 210.0, heightHint = 130.0)
        root.nodes["Middle"] = GraphLayoutCompoundNode(id = "Middle", widthHint = 210.0, heightHint = 130.0)
        root.nodes["Right"] = GraphLayoutCompoundNode(id = "Right", widthHint = 210.0, heightHint = 130.0)

        val left = GraphLayoutCompoundGraph(id = "Left")
        left.nodes["L1"] = GraphLayoutCompoundNode(id = "L1", widthHint = 90.0, heightHint = 48.0)

        val middle = GraphLayoutCompoundGraph(id = "Middle")
        middle.nodes["M1"] = GraphLayoutCompoundNode(id = "M1", widthHint = 90.0, heightHint = 48.0)

        val right = GraphLayoutCompoundGraph(id = "Right")
        right.nodes["R1"] = GraphLayoutCompoundNode(id = "R1", widthHint = 90.0, heightHint = 48.0)

        root.children[left.id] = left
        root.children[middle.id] = middle
        root.children[right.id] = right
        root.edges["e_lr"] = GraphLayoutCompoundEdge(id = "e_lr", sourceId = "L1", targetId = "R1")

        val result = CompoundLayoutEngine().layout(GraphLayoutCompoundGraphState(id = "obstacle", root = root))
        val route = result.edgeRoutesByEdgeId.getValue("e_lr")
        val middleContainer = assertNotNull(result.nodeLayoutsById["Middle"])

        val intersectsInterior = route.zipWithNext().any { (a, b) ->
            segmentIntersectsRectInterior(
                a = a,
                b = b,
                left = middleContainer.globalX,
                right = middleContainer.globalX + middleContainer.width,
                top = middleContainer.globalY,
                bottom = middleContainer.globalY + middleContainer.height
            )
        }

        assertTrue(!intersectsInterior, "Route should avoid unrelated container interior: $route")
    }

    @Test
    fun container_size_and_child_origin_follow_measured_child_host_metrics() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 50.0, heightHint = 50.0)

        val child = GraphLayoutCompoundGraph(id = "Container")
        child.nodes["Inner"] = GraphLayoutCompoundNode(id = "Inner", widthHint = 120.0, heightHint = 80.0)
        root.children[child.id] = child

        val state = GraphLayoutCompoundGraphState(id = "measured_metrics", root = root)
        val measured = ContainerChildHostMetrics(
            originX = 10.0,
            originY = 20.0,
            insetRight = 30.0,
            insetBottom = 40.0
        )

        val result = CompoundLayoutEngine().layout(
            state = state,
            containerMetricsByNodeId = mapOf("Container" to measured)
        )

        val container = assertNotNull(result.nodeLayoutsById["Container"])
        val childGraph = assertNotNull(result.graphLayoutsById["Container"])

        assertEquals(160.0, container.width)
        assertEquals(140.0, container.height)
        assertEquals(container.globalX + measured.originX, childGraph.globalOffsetX)
        assertEquals(container.globalY + measured.originY, childGraph.globalOffsetY)
    }

    @Test
    fun container_size_and_child_origin_use_deterministic_fallback_metrics_when_not_provided() {
        val root = GraphLayoutCompoundGraph(id = "root")
        root.nodes["Container"] = GraphLayoutCompoundNode(id = "Container", widthHint = 50.0, heightHint = 50.0)

        val child = GraphLayoutCompoundGraph(id = "Container")
        child.nodes["Inner"] = GraphLayoutCompoundNode(id = "Inner", widthHint = 120.0, heightHint = 80.0)
        root.children[child.id] = child

        val state = GraphLayoutCompoundGraphState(id = "fallback_metrics", root = root)
        val result = CompoundLayoutEngine().layout(state = state)

        val container = assertNotNull(result.nodeLayoutsById["Container"])
        val childGraph = assertNotNull(result.graphLayoutsById["Container"])

        assertEquals(144.0, container.width)
        assertEquals(116.0, container.height)
        assertEquals(container.globalX + 12.0, childGraph.globalOffsetX)
        assertEquals(container.globalY + 24.0, childGraph.globalOffsetY)
    }

    private fun segmentIntersectsRectInterior(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        left: Double,
        right: Double,
        top: Double,
        bottom: Double
    ): Boolean {
        val eps = 1e-6
        val insetLeft = left + eps
        val insetRight = right - eps
        val insetTop = top + eps
        val insetBottom = bottom - eps
        if (insetLeft >= insetRight || insetTop >= insetBottom) {
            return false
        }

        val dx = b.first - a.first
        val dy = b.second - a.second
        var t0 = 0.0
        var t1 = 1.0

        fun clip(p: Double, q: Double): Boolean {
            if (kotlin.math.abs(p) < eps) {
                return q >= 0.0
            }
            val r = q / p
            return if (p < 0.0) {
                if (r > t1) false else {
                    if (r > t0) t0 = r
                    true
                }
            } else {
                if (r < t0) false else {
                    if (r < t1) t1 = r
                    true
                }
            }
        }

        if (!clip(-dx, a.first - insetLeft)) return false
        if (!clip(dx, insetRight - a.first)) return false
        if (!clip(-dy, a.second - insetTop)) return false
        if (!clip(dy, insetBottom - a.second)) return false
        return t0 < t1 && t1 > 0.0 && t0 < 1.0
    }
}

