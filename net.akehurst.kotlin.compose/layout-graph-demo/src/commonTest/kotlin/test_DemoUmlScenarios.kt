package net.akehurst.kotlin.components.layout.graph.demo

import net.akehurst.kotlin.components.layout.graph.CompoundLayoutEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_DemoUmlScenarios {

    private val umlScenarioIds = setOf(
        "uml_class_associations_generalisation",
        "uml_statechart_nested_regions",
        "uml_use_case_diagram",
        "uml_composite_structure_diagram",
        "uml_deployment_diagram",
        "uml_class_package_crossing_relations"
    )

    @Test
    fun uml_scenarios_are_registered_in_demo_catalog() {
        val allIds = DemoScenarios.all.map { it.id }.toSet()
        assertTrue(umlScenarioIds.all { it in allIds })
    }

    @Test
    fun uml_scenarios_produce_layout_with_stable_edge_route_ids() {
        val engine = CompoundLayoutEngine()
        val umlScenarios = DemoScenarios.all.filter { it.id in umlScenarioIds }

        assertEquals(umlScenarioIds.size, umlScenarios.size)

        umlScenarios.forEach { scenario ->
            val result = engine.layout(scenario.toCompoundGraphState())
            val nodeIds = scenario.nodes.map { it.id }.toSet()
            val edgeIds = scenario.edges.map { it.id }.toSet()

            assertTrue(nodeIds.all { it in result.nodeLayoutsById.keys }, "missing node layout in ${scenario.id}")
            assertTrue(edgeIds.all { it in result.edgeRoutesByEdgeId.keys }, "missing edge route in ${scenario.id}")
        }
    }

    @Test
    fun uml_statechart_nested_regions_forward_edge_attaches_to_boundaries_and_stays_in_machine() {
        val scenario = DemoScenarios.umlStatechartNestedRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val route = result.edgeRoutesByEdgeId.getValue("e_state_nested_2")
        assertTrue(route.size >= 2, "expected route with at least two points for e_state_nested_2")

        val active = result.nodeLayoutsById.getValue("Active")
        val wait = result.nodeLayoutsById.getValue("Wait")
        val parallelB = result.nodeLayoutsById.getValue("ParallelB")
        val machine = result.nodeLayoutsById.getValue("Machine")
        val eps = 1e-6

        val start = route.first()
        val end = route.last()

        assertTrue(
            isPointOnRectBoundary(start, active.globalX, active.globalY, active.width, active.height, eps),
            "start point should lie on Active boundary: $start"
        )
        assertTrue(
            isPointOnRectBoundary(end, wait.globalX, wait.globalY, wait.width, wait.height, eps),
            "end point should lie on Wait boundary: $end"
        )

        val machineLeft = machine.globalX
        val machineRight = machine.globalX + machine.width
        val machineTop = machine.globalY
        val machineBottom = machine.globalY + machine.height

        assertTrue(
            route.all { point ->
                point.first >= machineLeft - eps && point.first <= machineRight + eps &&
                    point.second >= machineTop - eps && point.second <= machineBottom + eps
            },
            "route should remain inside Machine bounds: $route"
        )
        assertTrue(
            route.dropLast(1).none { point ->
                isPointOnRectBoundary(point, parallelB.globalX, parallelB.globalY, parallelB.width, parallelB.height, eps)
            },
            "route should not attach to ParallelB boundary: $route"
        )

    }

    @Test
    fun uml_statechart_nested_regions_reverse_edge_attaches_to_boundaries_and_stays_in_machine() {
        val scenario = DemoScenarios.umlStatechartNestedRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val reverseRoute = result.edgeRoutesByEdgeId.getValue("e_state_nested_4")
        assertTrue(reverseRoute.size >= 2, "expected route with at least two points for e_state_nested_4")

        val machine = result.nodeLayoutsById.getValue("Machine")
        val eps = 1e-6
        val machineLeft = machine.globalX
        val machineRight = machine.globalX + machine.width
        val machineTop = machine.globalY
        val machineBottom = machine.globalY + machine.height

        val done = result.nodeLayoutsById.getValue("Done")
        val idle = result.nodeLayoutsById.getValue("Idle")
        val parallelA = result.nodeLayoutsById.getValue("ParallelA")
        val reverseStart = reverseRoute.first()
        val reverseEnd = reverseRoute.last()

        assertTrue(
            isPointOnRectBoundary(reverseStart, done.globalX, done.globalY, done.width, done.height, eps),
            "start point should lie on Done boundary: $reverseStart"
        )
        assertTrue(
            isPointOnRectBoundary(reverseEnd, idle.globalX, idle.globalY, idle.width, idle.height, eps),
            "end point should lie on Idle boundary: $reverseEnd"
        )
        assertTrue(
            reverseRoute.all { point ->
                point.first >= machineLeft - eps && point.first <= machineRight + eps &&
                    point.second >= machineTop - eps && point.second <= machineBottom + eps
            },
            "reverse route should remain inside Machine bounds: $reverseRoute"
        )
        assertTrue(
            reverseRoute.dropLast(1).none { point ->
                isPointOnRectBoundary(point, parallelA.globalX, parallelA.globalY, parallelA.width, parallelA.height, eps)
            },
            "reverse route should not attach to ParallelA boundary: $reverseRoute"
        )
    }

    @Test
    fun state_like_regions_cross_edge_attaches_to_boundaries_and_stays_in_state_machine() {
        val scenario = DemoScenarios.stateLikeRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val route = result.edgeRoutesByEdgeId.getValue("e_state_2")
        assertTrue(route.size >= 2, "expected route with at least two points for e_state_2")

        val source = result.nodeLayoutsById.getValue("A2")
        val target = result.nodeLayoutsById.getValue("B1")
        val regionB = result.nodeLayoutsById.getValue("RegionB")
        val machine = result.nodeLayoutsById.getValue("StateMachine")
        val eps = 1e-6

        val start = route.first()
        val end = route.last()
        assertTrue(
            isPointOnRectBoundary(start, source.globalX, source.globalY, source.width, source.height, eps),
            "start point should lie on A2 boundary: $start"
        )
        assertTrue(
            isPointOnRectBoundary(end, target.globalX, target.globalY, target.width, target.height, eps),
            "end point should lie on B1 boundary: $end"
        )

        val machineLeft = machine.globalX
        val machineRight = machine.globalX + machine.width
        val machineTop = machine.globalY
        val machineBottom = machine.globalY + machine.height
        assertTrue(
            route.all { point ->
                point.first >= machineLeft - eps && point.first <= machineRight + eps &&
                    point.second >= machineTop - eps && point.second <= machineBottom + eps
            },
            "route should remain inside StateMachine bounds: $route"
        )
        assertTrue(
            route.dropLast(1).none { point ->
                isPointOnRectBoundary(point, regionB.globalX, regionB.globalY, regionB.width, regionB.height, eps)
            },
            "route should not attach to RegionB boundary: $route"
        )
    }

    @Test
    fun state_like_regions_states_are_positioned_below_region_title_area() {
        val scenario = DemoScenarios.stateLikeRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val regionA = result.nodeLayoutsById.getValue("RegionA")
        val regionB = result.nodeLayoutsById.getValue("RegionB")
        val a1 = result.nodeLayoutsById.getValue("A1")
        val a2 = result.nodeLayoutsById.getValue("A2")
        val b1 = result.nodeLayoutsById.getValue("B1")
        val b2 = result.nodeLayoutsById.getValue("B2")

        val minHeaderClearance = 20.0

        assertTrue(a1.globalY >= regionA.globalY + minHeaderClearance, "A1 overlaps RegionA title area")
        assertTrue(a2.globalY >= regionA.globalY + minHeaderClearance, "A2 overlaps RegionA title area")
        assertTrue(b1.globalY >= regionB.globalY + minHeaderClearance, "B1 overlaps RegionB title area")
        assertTrue(b2.globalY >= regionB.globalY + minHeaderClearance, "B2 overlaps RegionB title area")
    }

    private fun isPointOnRectBoundary(
        point: Pair<Double, Double>,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        eps: Double
    ): Boolean {
        val left = x
        val right = x + width
        val top = y
        val bottom = y + height

        val insideInclusive =
            point.first >= left - eps && point.first <= right + eps &&
                point.second >= top - eps && point.second <= bottom + eps
        if (!insideInclusive) return false

        val onVertical = kotlin.math.abs(point.first - left) <= eps || kotlin.math.abs(point.first - right) <= eps
        val onHorizontal = kotlin.math.abs(point.second - top) <= eps || kotlin.math.abs(point.second - bottom) <= eps
        return onVertical || onHorizontal
    }
}

