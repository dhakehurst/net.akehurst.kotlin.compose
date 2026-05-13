package net.akehurst.kotlin.components.layout.graph.demo

import net.akehurst.kotlin.components.layout.graph.CompoundLayoutEngine
import net.akehurst.kotlin.components.layout.graph.CompoundNodeLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_CompoundLayoutCrossBoundaryEdgeAnchoring {

    private val engine = CompoundLayoutEngine()

    // ── single container: InsideB → Outside ────────────────────────────────

    @Test
    fun single_container_edge_starts_at_source_boundary() {
        val result = engine.layout(DemoScenarios.singleContainerTwoNodes.toCompoundGraphState())
        val source = result.nodeLayoutsById.getValue("InsideB")
        val route = result.edgeRoutesByEdgeId.getValue("e_single_2")
        assertTrue(isOnRectBoundary(route.first(), source), "source endpoint must be on source boundary")
    }

    @Test
    fun single_container_edge_ends_at_target_boundary() {
        val result = engine.layout(DemoScenarios.singleContainerTwoNodes.toCompoundGraphState())
        val target = result.nodeLayoutsById.getValue("Outside")
        val route = result.edgeRoutesByEdgeId.getValue("e_single_2")
        assertTrue(isOnRectBoundary(route.last(), target), "target endpoint must be on target boundary")
    }

    @Test
    fun single_container_direct_edge_uses_only_endpoints() {
        val result = engine.layout(DemoScenarios.singleContainerTwoNodes.toCompoundGraphState())
        val route = result.edgeRoutesByEdgeId.getValue("e_single_2")
        assertEquals(2, route.size, "direct routing should use only source and target endpoints")
    }

    // ── sibling containers: L1 → R1 ────────────────────────────────────────

    @Test
    fun sibling_container_edge_starts_at_source_boundary() {
        val result = engine.layout(DemoScenarios.siblingContainersCrossEdges.toCompoundGraphState())
        val source = result.nodeLayoutsById.getValue("L1")
        val route = result.edgeRoutesByEdgeId.getValue("e_sibling_1")
        assertTrue(isOnRectBoundary(route.first(), source), "source endpoint must be on source boundary")
    }

    @Test
    fun sibling_container_edge_ends_at_target_boundary() {
        val result = engine.layout(DemoScenarios.siblingContainersCrossEdges.toCompoundGraphState())
        val target = result.nodeLayoutsById.getValue("R1")
        val route = result.edgeRoutesByEdgeId.getValue("e_sibling_1")
        assertTrue(isOnRectBoundary(route.last(), target), "target endpoint must be on target boundary")
    }

    @Test
    fun sibling_container_direct_edge_uses_only_endpoints() {
        val result = engine.layout(DemoScenarios.siblingContainersCrossEdges.toCompoundGraphState())
        val route = result.edgeRoutesByEdgeId.getValue("e_sibling_1")
        val source = result.nodeLayoutsById.getValue("L1")
        val target = result.nodeLayoutsById.getValue("R1")
        assertEquals(2, route.size, "direct routing should use only source and target endpoints")
        assertTrue(isOnRectBoundary(route.first(), source), "route start should be on source boundary")
        assertTrue(isOnRectBoundary(route.last(), target), "route end should be on target boundary")
    }

    // ── deep nesting: LeafB → External (one level up) ──────────────────────

    @Test
    fun deep_nesting_direct_edge_uses_only_endpoints() {
        val result = engine.layout(DemoScenarios.deepNesting.toCompoundGraphState())
        val route = result.edgeRoutesByEdgeId.getValue("e_deep_2")
        val source = result.nodeLayoutsById.getValue("LeafB")
        val target = result.nodeLayoutsById.getValue("External")
        assertEquals(2, route.size, "direct routing should use only source and target endpoints")
        assertTrue(isOnRectBoundary(route.first(), source), "source endpoint must be on source boundary")
        assertTrue(isOnRectBoundary(route.last(), target), "target endpoint must be on target boundary")
    }

    @Test
    fun direct_endpoints_follow_center_ray_clipping() {
        val result = engine.layout(DemoScenarios.siblingContainersCrossEdges.toCompoundGraphState())
        val source = result.nodeLayoutsById.getValue("L1")
        val target = result.nodeLayoutsById.getValue("R1")
        val route = result.edgeRoutesByEdgeId.getValue("e_sibling_1")

        val expectedSource = clipToBoundary(nodeCenter(source), nodeCenter(target), source)
        val expectedTarget = clipToBoundary(nodeCenter(target), nodeCenter(source), target)
        assertPointClose(expectedSource, route.first(), "source ray clipping")
        assertPointClose(expectedTarget, route.last(), "target ray clipping")
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun nodeCenter(node: CompoundNodeLayout) =
        node.globalX + node.width / 2.0 to node.globalY + node.height / 2.0

    private fun isOnRectBoundary(point: Pair<Double, Double>, container: CompoundNodeLayout): Boolean {
        val x = point.first
        val y = point.second
        val left = container.globalX
        val right = container.globalX + container.width
        val top = container.globalY
        val bottom = container.globalY + container.height
        val eps = 0.001

        val onLeft   = abs(x - left)   < eps && y in (top - eps)..(bottom + eps)
        val onRight  = abs(x - right)  < eps && y in (top - eps)..(bottom + eps)
        val onTop    = abs(y - top)    < eps && x in (left - eps)..(right + eps)
        val onBottom = abs(y - bottom) < eps && x in (left - eps)..(right + eps)
        return onLeft || onRight || onTop || onBottom
    }

    private fun assertPointClose(
        expected: Pair<Double, Double>,
        actual: Pair<Double, Double>,
        label: String
    ) {
        assertTrue(abs(expected.first  - actual.first)  < 0.001, "$label x: expected=${expected.first},  actual=${actual.first}")
        assertTrue(abs(expected.second - actual.second) < 0.001, "$label y: expected=${expected.second}, actual=${actual.second}")
    }

    private fun clipToBoundary(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        rect: CompoundNodeLayout
    ): Pair<Double, Double> {
        val dx = to.first - from.first
        val dy = to.second - from.second
        val left = rect.globalX
        val right = rect.globalX + rect.width
        val top = rect.globalY
        val bottom = rect.globalY + rect.height
        val eps = 1e-6
        if (abs(dx) < eps && abs(dy) < eps) return right to (top + rect.height / 2.0)

        val candidates = mutableListOf<Pair<Double, Pair<Double, Double>>>()
        if (abs(dx) >= eps) {
            val tr = (right - from.first) / dx
            val yr = from.second + tr * dy
            if (tr > eps && yr >= top - eps && yr <= bottom + eps) candidates += tr to (right to yr)
            val tl = (left - from.first) / dx
            val yl = from.second + tl * dy
            if (tl > eps && yl >= top - eps && yl <= bottom + eps) candidates += tl to (left to yl)
        }
        if (abs(dy) >= eps) {
            val tb = (bottom - from.second) / dy
            val xb = from.first + tb * dx
            if (tb > eps && xb >= left - eps && xb <= right + eps) candidates += tb to (xb to bottom)
            val tt = (top - from.second) / dy
            val xt = from.first + tt * dx
            if (tt > eps && xt >= left - eps && xt <= right + eps) candidates += tt to (xt to top)
        }
        return candidates.minBy { it.first }.second
    }
}
