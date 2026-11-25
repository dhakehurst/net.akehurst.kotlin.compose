package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class test_LayoutErrorAndScroll {

    private fun graph(withBadEdge: Boolean): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        listOf("A", "B").forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        root.edges["A-B"] = GraphLayoutCompoundEdge("A-B", "A", "B")
        if (withBadEdge) root.edges["A-X"] = GraphLayoutCompoundEdge("A-X", "A", "missing")
        return GraphLayoutCompoundGraphState(id = "g", root = root).also { s ->
            listOf("A", "B").forEach { id -> s.addNodeContent(id) { _ -> Text(id) } }
        }
    }

    @Test
    fun invalid_graph_shows_error_content_and_calls_onLayoutError() = runComposeUiTest {
        val errors = mutableListOf<Throwable>()
        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                CompoundGraphLayoutView(
                    state = graph(withBadEdge = true),
                    viewState = GraphLayoutViewState(1f, Offset.Zero),
                    updateView = { _, _ -> },
                    layoutErrorContent = { e -> Text("failed: ${e.message}", Modifier.testTag("myError")) },
                    onLayoutError = { errors += it },
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { errors.isNotEmpty() }
        waitForIdle()
        onNodeWithTag(CompoundGraphLayoutViewTags.LAYOUT_ERROR).assertExists()
        onNodeWithTag("myError").assertExists()
        assertTrue(errors.first().message!!.contains("missing"), "message: ${errors.first().message}")
    }

    @Test
    fun valid_layout_replaces_error_content() = runComposeUiTest {
        var key by mutableStateOf(0)
        val badState = graph(withBadEdge = true)
        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                CompoundGraphLayoutView(
                    state = badState,
                    viewState = GraphLayoutViewState(1f, Offset.Zero),
                    updateView = { _, _ -> },
                    layoutKey = key,
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTagExists(CompoundGraphLayoutViewTags.LAYOUT_ERROR) }
        runOnIdle {
            badState.root.edges.remove("A-X")
            key++
        }
        waitUntil(timeoutMillis = 10_000) { !onAllNodesWithTagExists(CompoundGraphLayoutViewTags.LAYOUT_ERROR) }
        waitForGraphLayout()
        onNodeWithTag(CompoundGraphLayoutViewTags.LAYOUT_ERROR).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagExists(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun scroll_zooms_the_view_and_does_not_scroll_the_parent() = runComposeUiTest {
        var view by mutableStateOf(GraphLayoutViewState(1f, Offset.Zero))
        lateinit var parentScroll: ScrollState
        setContent {
            parentScroll = rememberScrollState()
            Column(Modifier.size(400.dp, 300.dp).verticalScroll(parentScroll)) {
                Box(Modifier.fillMaxWidth().height(250.dp).testTag("graph")) {
                    CompoundGraphLayoutView(
                        state = graph(withBadEdge = false),
                        viewState = view,
                        updateView = { o, z -> view = GraphLayoutViewState(z, o) },
                    )
                }
                Box(Modifier.fillMaxWidth().height(600.dp))
            }
        }
        waitForGraphLayout()
        onNodeWithTag("graph").performMouseInput { scroll(-3f, ScrollWheel.Vertical) } // zoom in: the small graph already fits, so it cannot zoom out below 1
        waitForIdle()
        assertNotEquals(1f, view.zoom, "scrolling over the graph should zoom it")
        assertEquals(0, parentScroll.value, "the parent should not scroll as well")
    }
}
