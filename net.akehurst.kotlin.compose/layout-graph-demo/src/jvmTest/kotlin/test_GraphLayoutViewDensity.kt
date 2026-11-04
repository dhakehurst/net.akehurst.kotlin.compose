package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.EdgeRouting
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutGraphState
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutView
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The simple (non-compound) [GraphLayoutView] also lays out in dp: same layout in dp at 1x and 2x. */
@OptIn(ExperimentalTestApi::class)
class test_GraphLayoutViewDensity {

    private val nodeIds = listOf("A", "B", "C", "D")

    private fun boundsInDp(density: Float): Map<String, Rect> {
        var result: Map<String, Rect> = emptyMap()
        runComposeUiTest {
            val graph = GraphLayoutGraphState("g")
            nodeIds.forEach { id -> graph.addNode(id) { Box(Modifier.size(60.dp, 30.dp).testTag(id)) } }
            graph.addEdge("ab", "A", "B")
            graph.addEdge("ac", "A", "C")
            graph.addEdge("bd", "B", "D")
            graph.addEdge("cd", "C", "D")
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    Box(Modifier.size(1000.dp, 1000.dp)) {
                        GraphLayoutView(
                            graphState = graph,
                            viewState = GraphLayoutViewState(),
                            routing = EdgeRouting.DIRECT,
                            updateView = { _, _ -> },
                            routingSelectorsColor = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            waitForIdle()
            result = nodeIds.associateWith { id ->
                val node = onAllNodes(hasTestTag(id), useUnmergedTree = true).fetchSemanticsNodes().single()
                val p = node.positionInRoot
                Rect(p.x / density, p.y / density, (p.x + node.size.width) / density, (p.y + node.size.height) / density)
            }
        }
        return result
    }

    @Test
    fun same_layout_in_dp_at_1x_and_2x() {
        val at1 = boundsInDp(1f)
        val at2 = boundsInDp(2f)
        // The nodes must not all sit in one place, or the check below proves nothing.
        assertTrue(at1.values.map { it.topLeft }.toSet().size > 1, "nodes were not laid out: $at1")
        // Compare positions relative to node A: this view centres the diagram in the space it gets,
        // and the test window is smaller than the view at 2x, so the whole diagram may be shifted.
        fun Map<String, Rect>.relativeToA() = getValue("A").topLeft.let { a -> mapValues { (_, r) -> r.translate(-a) } }
        val rel1 = at1.relativeToA()
        val rel2 = at2.relativeToA()
        nodeIds.forEach { id ->
            val a = rel1.getValue(id)
            val b = rel2.getValue(id)
            val diff = maxOf(abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom))
            assertTrue(diff <= 1f, "$id, relative to A, is $b dp at 2x but $a dp at 1x")
        }
    }
}
