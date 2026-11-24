package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * With a focus node, the node stays where it is on the screen when the graph changes (for example on expand);
 * without one, the initial view placement applies as before.
 */
@OptIn(ExperimentalTestApi::class)
class test_FocusNode {

    private fun state(): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        listOf("A", "B").forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        root.edges["A-B"] = GraphLayoutCompoundEdge("A-B", "A", "B")
        return GraphLayoutCompoundGraphState(id = "focus", root = root).also { s ->
            listOf("A", "B", "C", "D").forEach { id -> s.addNodeContent(id) { _ -> node(id) } }
        }
    }

    @androidx.compose.runtime.Composable
    private fun node(id: String) = Text(id, Modifier.testTag("node-$id").border(1.dp, Color.Black).padding(8.dp))

    /** Adds B->C and B->D, so the layout changes around B. */
    private fun expandB(s: GraphLayoutCompoundGraphState) {
        listOf("C", "D").forEach {
            s.root.nodes[it] = GraphLayoutCompoundNode(it)
            s.root.edges["B-$it"] = GraphLayoutCompoundEdge("B-$it", "B", it)
        }
    }

    private class Result(val before: Offset, val after: Offset, val updates: List<GraphLayoutViewState>, val finalView: GraphLayoutViewState)

    private fun run(focus: String?, initialView: InitialViewPlacement): Result {
        lateinit var result: Result
        runComposeUiTest {
            val s = state()
            var view by mutableStateOf(GraphLayoutViewState(zoom = 1f, offset = Offset(300f, 40f)))
            var layoutKey by mutableStateOf(0)
            var focusId by mutableStateOf<String?>(null)
            val updates = mutableListOf<GraphLayoutViewState>()
            setContent {
                Box(Modifier.testTag("root").size(800.dp, 600.dp).background(Color.White)) {
                    CompoundGraphLayoutView(
                        state = s, viewState = view,
                        updateView = { o, z -> view = GraphLayoutViewState(z, o); updates += view },
                        modifier = Modifier.fillMaxSize(), layoutKey = layoutKey,
                        initialView = initialView, focusNodeId = focusId,
                    )
                }
            }
            waitForGraphLayout()
            fun centreOfB() = onNodeWithTag("node-B", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            val before = centreOfB()
            updates.clear()
            runOnIdle {
                expandB(s)
                focusId = focus
                layoutKey++
            }
            waitForGraphLayout()
            result = Result(before, centreOfB(), updates.toList(), view)
        }
        return result
    }

    @Test
    fun focus_node_stays_at_its_screen_position() {
        val r = run(focus = "B", initialView = InitialViewPlacement.FIT_AND_CENTRE)
        assertTrue(abs(r.before.x - r.after.x) <= 1f && abs(r.before.y - r.after.y) <= 1f, "B moved on the screen from ${r.before} to ${r.after}")
        assertTrue(r.updates.isNotEmpty(), "updateView was not called with the new offset")
        assertEquals(r.updates.last(), r.finalView)
    }

    @Test
    fun without_focus_node_the_initial_view_applies_as_before() {
        val r = run(focus = null, initialView = InitialViewPlacement.FIT_AND_CENTRE)
        // Fit and centre: the graph is re-centred, so B (now with children below it) moves.
        assertTrue(r.updates.isNotEmpty(), "the view was not re-fitted")
        assertTrue(abs(r.before.y - r.after.y) > 1f || abs(r.before.x - r.after.x) > 1f, "B did not move: ${r.before} -> ${r.after}")
    }

    @Test
    fun focus_node_not_in_the_new_layout_uses_the_initial_view() {
        val r = run(focus = "X", initialView = InitialViewPlacement.FIT_AND_CENTRE)
        assertTrue(r.updates.isNotEmpty(), "the view was not re-fitted")
    }
}
