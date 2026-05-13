package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertTrue

class TestEdgeRouteAdjustment {

    // ── rectExitPoint: going from the CENTER of a node outward ───────────────

    @Test
    fun rectExitPoint_exits_right_boundary() {
        val exit = rectExitPoint(
            center = 150.0 to 125.0, toward = 350.0 to 125.0,
            rectLeft = 100.0, rectTop = 100.0, rectRight = 200.0, rectBottom = 150.0
        )
        assertTrue(kotlin.math.abs(exit.first  - 200.0) < 1e-6, "X should be at right boundary (200), got ${exit.first}")
        assertTrue(kotlin.math.abs(exit.second - 125.0) < 1e-6, "Y should stay 125, got ${exit.second}")
    }

    @Test
    fun rectExitPoint_exits_left_boundary() {
        val exit = rectExitPoint(
            center = 150.0 to 125.0, toward = -50.0 to 125.0,
            rectLeft = 100.0, rectTop = 100.0, rectRight = 200.0, rectBottom = 150.0
        )
        assertTrue(kotlin.math.abs(exit.first  - 100.0) < 1e-6, "X should be at left boundary (100), got ${exit.first}")
        assertTrue(kotlin.math.abs(exit.second - 125.0) < 1e-6, "Y should stay 125, got ${exit.second}")
    }

    @Test
    fun rectExitPoint_exits_bottom_boundary() {
        val exit = rectExitPoint(
            center = 150.0 to 125.0, toward = 150.0 to 300.0,
            rectLeft = 100.0, rectTop = 100.0, rectRight = 200.0, rectBottom = 150.0
        )
        assertTrue(kotlin.math.abs(exit.first  - 150.0) < 1e-6, "X should stay 150, got ${exit.first}")
        assertTrue(kotlin.math.abs(exit.second - 150.0) < 1e-6, "Y should be at bottom boundary (150), got ${exit.second}")
    }

    @Test
    fun rectExitPoint_exits_top_boundary() {
        val exit = rectExitPoint(
            center = 150.0 to 125.0, toward = 150.0 to 0.0,
            rectLeft = 100.0, rectTop = 100.0, rectRight = 200.0, rectBottom = 150.0
        )
        assertTrue(kotlin.math.abs(exit.first  - 150.0) < 1e-6, "X should stay 150, got ${exit.first}")
        assertTrue(kotlin.math.abs(exit.second - 100.0) < 1e-6, "Y should be at top boundary (100), got ${exit.second}")
    }

    @Test
    fun rectExitPoint_diagonal_exits_correct_boundary() {
        val exit = rectExitPoint(
            center = 150.0 to 125.0, toward = 300.0 to 200.0,
            rectLeft = 100.0, rectTop = 100.0, rectRight = 200.0, rectBottom = 150.0
        )
        val onRight  = kotlin.math.abs(exit.first  - 200.0) < 1e-3 && exit.second in (100.0 - 1e-3)..(150.0 + 1e-3)
        val onBottom = kotlin.math.abs(exit.second - 150.0) < 1e-3 && exit.first  in (100.0 - 1e-3)..(200.0 + 1e-3)
        assertTrue(onRight || onBottom, "Exit point should be on right or bottom boundary, got $exit")
    }

    @Test
    fun rectExitPoint_no_movement_returns_center() {
        val center = 150.0 to 125.0
        val exit = rectExitPoint(center, center, 100.0, 100.0, 200.0, 150.0)
        assertTrue(kotlin.math.abs(exit.first  - center.first)  < 1e-6)
        assertTrue(kotlin.math.abs(exit.second - center.second) < 1e-6)
    }

    @Test
    fun coordinate_conversion_viewport_to_global_round_trips() {
        val globalToViewportOffsetX = -20.0
        val globalToViewportOffsetY = -10.0
        val nodeGlobalLeft = 50.0; val nodeGlobalTop = 30.0
        val nodeGlobalRight = 150.0; val nodeGlobalBottom = 80.0

        val vL = nodeGlobalLeft + globalToViewportOffsetX
        val vT = nodeGlobalTop  + globalToViewportOffsetY
        val vR = nodeGlobalRight + globalToViewportOffsetX
        val vB = nodeGlobalBottom + globalToViewportOffsetY

        // global = viewport - offset
        val gL = vL - globalToViewportOffsetX; val gT = vT - globalToViewportOffsetY
        val gR = vR - globalToViewportOffsetX; val gB = vB - globalToViewportOffsetY

        assertTrue(kotlin.math.abs(gL - nodeGlobalLeft)   < 1e-6)
        assertTrue(kotlin.math.abs(gT - nodeGlobalTop)    < 1e-6)
        assertTrue(kotlin.math.abs(gR - nodeGlobalRight)  < 1e-6)
        assertTrue(kotlin.math.abs(gB - nodeGlobalBottom) < 1e-6)

        val srcCenter = (gL + gR) / 2.0 to (gT + gB) / 2.0
        val exit = rectExitPoint(srcCenter, 300.0 to 55.0, gL, gT, gR, gB)
        assertTrue(kotlin.math.abs(exit.first - nodeGlobalRight) < 1e-3,
            "Should exit at right boundary ($nodeGlobalRight), got ${exit.first}")
    }
}

private fun rectExitPoint(
    center: Pair<Double, Double>,
    toward: Pair<Double, Double>,
    rectLeft: Double, rectTop: Double, rectRight: Double, rectBottom: Double
): Pair<Double, Double> {
    val dx = toward.first - center.first
    val dy = toward.second - center.second
    if (kotlin.math.abs(dx) < 1e-9 && kotlin.math.abs(dy) < 1e-9) return center
    var tExit = Double.MAX_VALUE
    if (kotlin.math.abs(dx) > 1e-9) {
        val t = if (dx > 0) (rectRight - center.first) / dx else (rectLeft - center.first) / dx
        if (t > 0) tExit = minOf(tExit, t)
    }
    if (kotlin.math.abs(dy) > 1e-9) {
        val t = if (dy > 0) (rectBottom - center.second) / dy else (rectTop - center.second) / dy
        if (t > 0) tExit = minOf(tExit, t)
    }
    if (tExit == Double.MAX_VALUE) return center
    return (center.first + tExit * dx) to (center.second + tExit * dy)
}
