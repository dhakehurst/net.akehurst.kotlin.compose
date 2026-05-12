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
}

