package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class test_ViewPlacementAndLabels {

    // ── initialViewState ────────────────────────────────────────────────────

    @Test
    fun as_given_leaves_the_view_state_alone() {
        assertNull(initialViewState(InitialViewPlacement.AS_GIVEN, 100f, 100f, 400f, 300f))
    }

    @Test
    fun centre_keeps_zoom_1() {
        val v = initialViewState(InitialViewPlacement.CENTRE, 100f, 50f, 400f, 300f)!!
        assertEquals(1f, v.zoom)
        assertEquals(Offset(150f, 125f), v.offset)
    }

    @Test
    fun fit_does_not_zoom_in_a_small_graph() {
        val v = initialViewState(InitialViewPlacement.FIT_AND_CENTRE, 100f, 50f, 400f, 300f)!!
        assertEquals(1f, v.zoom)
        assertEquals(Offset(150f, 125f), v.offset)
    }

    @Test
    fun fit_zooms_out_a_big_graph_and_centres_it() {
        // Width limits: 400 / 800 = 0.5.
        val v = initialViewState(InitialViewPlacement.FIT_AND_CENTRE, 800f, 200f, 400f, 300f)!!
        assertEquals(0.5f, v.zoom)
        assertEquals(Offset(0f, 100f), v.offset)
    }

    // ── placeEdgeLabels ─────────────────────────────────────────────────────

    private fun label(route: List<Pair<Double, Double>>, w: Float = 40f, h: Float = 10f): EdgeLabelToPlace {
        val (p, _) = pointAlongRoute(route, 50.0)
        return EdgeLabelToPlace(route, EdgeContentPosition.MIDDLE, w, h, Offset(p.first.toFloat(), p.second.toFloat()))
    }

    private fun box(c: Offset, l: EdgeLabelToPlace) = Rect(c.x - l.width / 2, c.y - l.height / 2, c.x + l.width / 2, c.y + l.height / 2)

    @Test
    fun labels_that_do_not_overlap_stay_at_their_usual_place() {
        val a = label(listOf(0.0 to 0.0, 0.0 to 100.0))
        val b = label(listOf(200.0 to 0.0, 200.0 to 100.0))
        assertEquals(listOf(a.defaultCentre, b.defaultCentre), placeEdgeLabels(listOf(a, b), emptyList(), 4f, 18f, true))
    }

    @Test
    fun overlapping_label_slides_along_its_own_route() {
        // Two parallel vertical edges 8 apart: the labels overlap at the middle.
        val a = label(listOf(0.0 to 0.0, 0.0 to 100.0))
        val b = label(listOf(8.0 to 0.0, 8.0 to 100.0))
        val centres = placeEdgeLabels(listOf(a, b), emptyList(), 4f, 18f, true)
        assertEquals(a.defaultCentre, centres[0])
        assertEquals(8f, centres[1].x)
        assertFalse(box(centres[0], a).overlaps(box(centres[1], b)))
    }

    @Test
    fun slid_label_avoids_nodes() {
        val a = label(listOf(0.0 to 0.0, 0.0 to 100.0))
        val b = label(listOf(8.0 to 0.0, 8.0 to 100.0))
        // A node covering the lower part of b's route, so it must go up.
        val node = Rect(-50f, 60f, 50f, 100f)
        val centres = placeEdgeLabels(listOf(a, b), listOf(node), 4f, 18f, true)
        val bBox = box(centres[1], b)
        assertFalse(bBox.overlaps(node))
        assertFalse(bBox.overlaps(box(centres[0], a)))
        kotlin.test.assertTrue(centres[1].y < 50f)
    }

    @Test
    fun off_keeps_usual_places() {
        val a = label(listOf(0.0 to 0.0, 0.0 to 100.0))
        val b = label(listOf(8.0 to 0.0, 8.0 to 100.0))
        assertEquals(listOf(a.defaultCentre, b.defaultCentre), placeEdgeLabels(listOf(a, b), emptyList(), 4f, 18f, false))
    }

    @Test
    fun point_along_route_follows_bends() {
        val route = listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0)
        assertEquals((10.0 to 5.0), pointAlongRoute(route, 15.0).first)
        assertEquals((5.0 to 0.0), pointAlongRoute(route, 5.0).first)
        assertEquals((10.0 to 10.0), pointAlongRoute(route, 99.0).first)
    }
}
