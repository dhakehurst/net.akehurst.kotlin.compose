package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class test_CompoundGraphLayoutViewMetrics {

    @Test
    fun required_child_host_bounds_follow_actual_child_extents() {
        val childNodes = listOf(
            CompoundNodeLayout(
                nodeId = "InsideA",
                ownerGraphId = "Container1",
                localX = 0.0,
                localY = 0.0,
                globalX = 24.0,
                globalY = 40.0,
                width = 100.0,
                height = 56.0,
                isContainer = false
            ),
            CompoundNodeLayout(
                nodeId = "InsideB",
                ownerGraphId = "Container1",
                localX = 0.0,
                localY = 156.0,
                globalX = 24.0,
                globalY = 196.0,
                width = 100.0,
                height = 56.0,
                isContainer = false
            )
        )

        assertEquals(100.0, requiredChildHostWidth(childNodes, contentOriginX = 24.0))
        assertEquals(212.0, requiredChildHostHeight(childNodes, contentOriginY = 40.0))
    }

    @Test
    fun measured_container_metrics_never_under_report_child_extents_when_host_bounds_are_clipped() {
        val childNodes = listOf(
            CompoundNodeLayout(
                nodeId = "InsideA",
                ownerGraphId = "Container1",
                localX = 0.0,
                localY = 0.0,
                globalX = 24.0,
                globalY = 40.0,
                width = 100.0,
                height = 56.0,
                isContainer = false
            ),
            CompoundNodeLayout(
                nodeId = "InsideB",
                ownerGraphId = "Container1",
                localX = 0.0,
                localY = 156.0,
                globalX = 24.0,
                globalY = 196.0,
                width = 100.0,
                height = 56.0,
                isContainer = false
            )
        )

        val metrics = resolveContainerChildHostMetrics(
            containerWidth = 124.0,
            containerHeight = 180.0,
            measuredHostLeft = 24.0,
            measuredHostTop = 40.0,
            measuredHostRight = 124.0,
            measuredHostBottom = 180.0,
            childNodes = childNodes,
            contentOriginX = 24.0,
            contentOriginY = 40.0
        )

        assertEquals(24.0, metrics.originX)
        assertEquals(40.0, metrics.originY)
        assertEquals(0.0, metrics.insetRight)
        assertEquals(0.0, metrics.insetBottom)

        val requiredContainerWidth = 100.0 + metrics.originX + metrics.insetRight
        val requiredContainerHeight = 212.0 + metrics.originY + metrics.insetBottom
        assertEquals(124.0, requiredContainerWidth)
        assertEquals(252.0, requiredContainerHeight)
    }
}

