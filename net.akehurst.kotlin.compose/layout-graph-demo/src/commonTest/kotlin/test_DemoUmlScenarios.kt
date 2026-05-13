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
    fun uml_statechart_nested_regions_forward_edge_uses_only_endpoints_and_stays_in_machine() {
        val scenario = DemoScenarios.umlStatechartNestedRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val route = result.edgeRoutesByEdgeId.getValue("e_state_nested_2")
        assertEquals(2, route.size, "direct routing should use only source and target endpoints for e_state_nested_2")

        val active = result.nodeLayoutsById.getValue("Active")
        val wait = result.nodeLayoutsById.getValue("Wait")
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
    }

    @Test
    fun uml_statechart_nested_regions_reverse_edge_uses_only_endpoints_and_stays_in_machine() {
        val scenario = DemoScenarios.umlStatechartNestedRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val reverseRoute = result.edgeRoutesByEdgeId.getValue("e_state_nested_4")
        assertEquals(2, reverseRoute.size, "direct routing should use only source and target endpoints for e_state_nested_4")

        val machine = result.nodeLayoutsById.getValue("Machine")
        val eps = 1e-6
        val machineLeft = machine.globalX
        val machineRight = machine.globalX + machine.width
        val machineTop = machine.globalY
        val machineBottom = machine.globalY + machine.height

        val done = result.nodeLayoutsById.getValue("Done")
        val idle = result.nodeLayoutsById.getValue("Idle")
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
    }

    @Test
    fun state_like_regions_cross_edge_attaches_to_boundaries_and_stays_in_state_machine() {
        val scenario = DemoScenarios.stateLikeRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val route = result.edgeRoutesByEdgeId.getValue("e_state_2")
        assertEquals(2, route.size, "direct routing should use only source and target endpoints for e_state_2")

        val source = result.nodeLayoutsById.getValue("A2")
        val target = result.nodeLayoutsById.getValue("B1")
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
    }

    @Test
    fun state_like_regions_states_have_valid_size_inside_regions() {
        val scenario = DemoScenarios.stateLikeRegions
        val result = CompoundLayoutEngine().layout(scenario.toCompoundGraphState())

        val a1 = result.nodeLayoutsById.getValue("A1")
        val a2 = result.nodeLayoutsById.getValue("A2")
        val b1 = result.nodeLayoutsById.getValue("B1")
        val b2 = result.nodeLayoutsById.getValue("B2")

        assertTrue(a1.width > 0 && a1.height > 0, "A1 must have valid dimensions")
        assertTrue(a2.width > 0 && a2.height > 0, "A2 must have valid dimensions")
        assertTrue(b1.width > 0 && b1.height > 0, "B1 must have valid dimensions")
        assertTrue(b2.width > 0 && b2.height > 0, "B2 must have valid dimensions")
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

