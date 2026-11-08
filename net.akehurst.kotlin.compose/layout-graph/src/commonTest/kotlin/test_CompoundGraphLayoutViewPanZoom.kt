package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.ui.geometry.Offset
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_CompoundGraphLayoutViewPanZoom {

    @Test
    fun zoom_about_pointer_keeps_pointer_anchored() {
        val state = GraphLayoutViewState(
            zoom = 1f,
            offset = Offset(10f, 20f)
        )
        val pointer = Offset(100f, 60f)

        val next = zoomAboutPointer(
            state = state,
            pointer = pointer,
            targetZoom = 2f
        )

        assertOffsetClose(Offset(-80f, -20f), next.offset)
        assertEquals(2f, next.zoom)
        assertWorldPointAnchored(state, next, pointer)
    }

    @Test
    fun pan_and_zoom_gesture_applies_pan_in_view_pixels() {
        val state = GraphLayoutViewState(
            zoom = 2f,
            offset = Offset(0f, 0f)
        )

        val next = applyPanAndZoomGesture(
            state = state,
            centroid = Offset(120f, 50f),
            pan = Offset(12f, -7f),
            zoomChange = 1f
        )

        assertOffsetClose(Offset(12f, -7f), next.offset)
        assertEquals(2f, next.zoom)
    }

    @Test
    fun zoom_anchor_is_correct_at_bottom_right_pointer() {
        val state = GraphLayoutViewState(
            zoom = 1.25f,
            offset = Offset(40f, -30f)
        )
        val pointer = Offset(980f, 720f)

        val next = zoomAboutPointer(
            state = state,
            pointer = pointer,
            targetZoom = 2.75f
        )

        assertWorldPointAnchored(state, next, pointer)
    }

    @Test
    fun zoom_round_trip_returns_to_original_state_within_tolerance() {
        val state = GraphLayoutViewState(
            zoom = 1.4f,
            offset = Offset(120f, -80f)
        )
        val pointer = Offset(840f, 610f)

        val zoomIn = zoomAboutPointer(state, pointer, 2.8f)
        val zoomOut = zoomAboutPointer(zoomIn, pointer, state.zoom)

        assertOffsetClose(state.offset, zoomOut.offset, tolerance = 0.001f)
        assertTrue((state.zoom - zoomOut.zoom).absoluteValue <= 0.001f)
    }

    @Test
    fun scroll_delta_zoom_factor_is_smooth_and_directional() {
        val zoomIn = zoomFactorFromScrollDelta(deltaY = -1f, density = 1f)
        val zoomOut = zoomFactorFromScrollDelta(deltaY = 1f, density = 1f)

        assertTrue(zoomIn > 1f)
        assertTrue(zoomOut < 1f)
    }

    @Test
    fun scroll_delta_normalized_by_density_is_consistent() {
        val scaleDensity1 = zoomFactorFromScrollDelta(deltaY = 3f, density = 1f)
        val scaleDensity2 = zoomFactorFromScrollDelta(deltaY = 6f, density = 2f)

        assertTrue((scaleDensity1 - scaleDensity2).absoluteValue <= 0.0001f)
    }

    private fun assertOffsetClose(expected: Offset, actual: Offset, tolerance: Float = 0.0001f) {
        assertTrue((expected.x - actual.x).absoluteValue <= tolerance)
        assertTrue((expected.y - actual.y).absoluteValue <= tolerance)
    }

    private fun worldPointFromScreen(state: GraphLayoutViewState, screen: Offset): Offset {
        return (screen - state.offset) / state.zoom
    }

    private fun assertWorldPointAnchored(before: GraphLayoutViewState, after: GraphLayoutViewState, pointer: Offset) {
        val beforeWorld = worldPointFromScreen(before, pointer)
        val afterWorld = worldPointFromScreen(after, pointer)
        assertOffsetClose(beforeWorld, afterWorld, tolerance = 0.001f)
    }
}
