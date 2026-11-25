package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/** minZoom / maxZoom of the graph views; by default the user can zoom out until the whole graph fits. */
class test_ZoomLimits {

    @Test
    fun default_min_zoom_fits_a_large_graph() {
        // 10 000 x 2 000 in a 400 x 300 view: the width limits, 400 / 10 000 = 0.04
        assertEquals(0.04f, effectiveMinZoom(null, 10_000f, 2_000f, 400f, 300f), 1e-6f)
    }

    @Test
    fun default_min_zoom_leaves_a_small_graph_room_to_zoom_out() {
        assertEquals(0.5f, effectiveMinZoom(null, 100f, 50f, 400f, 300f))
    }

    @Test
    fun default_min_zoom_for_a_medium_graph_is_where_it_fits() {
        // 600 x 300 in a 400 x 300 view fits at 400 / 600, which is above 0.5, so 0.5 applies
        assertEquals(0.5f, effectiveMinZoom(null, 600f, 300f, 400f, 300f))
        // 1 000 x 300 fits at 0.4
        assertEquals(0.4f, effectiveMinZoom(null, 1_000f, 300f, 400f, 300f), 1e-6f)
    }

    @Test
    fun given_min_zoom_is_used() {
        assertEquals(0.2f, effectiveMinZoom(0.2f, 10_000f, 2_000f, 400f, 300f))
    }

    @Test
    fun zoom_out_stops_at_min_zoom() {
        val state = GraphLayoutViewState(zoom = 1f, offset = Offset.Zero)
        val next = zoomAboutPointer(state, Offset.Zero, 0.001f, minZoom = 0.04f, maxZoom = 5f)
        assertEquals(0.04f, next.zoom)
    }

    @Test
    fun zoom_in_stops_at_max_zoom() {
        val state = GraphLayoutViewState(zoom = 1f, offset = Offset.Zero)
        val next = applyPanAndZoomGesture(state, Offset.Zero, Offset.Zero, 100f, minZoom = 0.5f, maxZoom = 8f)
        assertEquals(8f, next.zoom)
    }

    @Test
    fun fit_and_centre_zooms_out_a_large_graph_fully_with_the_default_min_zoom() {
        val min = effectiveMinZoom(null, 4_000f, 300f, 400f, 300f)
        val v = initialViewState(InitialViewPlacement.FIT_AND_CENTRE, 4_000f, 300f, 400f, 300f, minZoom = min)!!
        assertEquals(0.1f, v.zoom, 1e-6f)
    }
}
