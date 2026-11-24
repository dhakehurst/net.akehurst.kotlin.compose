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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * With stableRelayout, a new layout keeps the order of the nodes already shown; without it, the graph is
 * laid out from scratch (here, new node "a" sorts first, so it goes to the left).
 */
@OptIn(ExperimentalTestApi::class)
class test_StableRelayout {

    private fun orderAfterAddingA(stable: Boolean): Pair<List<String>, List<String>> {
        lateinit var result: Pair<List<String>, List<String>>
        runComposeUiTest {
            val root = GraphLayoutCompoundGraph("root")
            fun add(id: String) {
                root.nodes[id] = GraphLayoutCompoundNode(id)
                if (id != "R") root.edges["R-$id"] = GraphLayoutCompoundEdge("R-$id", "R", id)
            }
            listOf("R", "b", "c", "d").forEach(::add)
            val s = GraphLayoutCompoundGraphState(id = "stable", root = root).also { s ->
                listOf("R", "a", "b", "c", "d").forEach { id ->
                    s.addNodeContent(id) { _ -> Text(id, Modifier.testTag("node-$id").border(1.dp, Color.Black).padding(8.dp)) }
                }
            }
            var view by mutableStateOf(GraphLayoutViewState(zoom = 1f, offset = Offset(20f, 20f)))
            var layoutKey by mutableStateOf(0)
            setContent {
                Box(Modifier.size(800.dp, 600.dp).background(Color.White)) {
                    CompoundGraphLayoutView(
                        state = s, viewState = view, updateView = { o, z -> view = GraphLayoutViewState(z, o) },
                        modifier = Modifier.fillMaxSize(), layoutKey = layoutKey, stableRelayout = stable,
                    )
                }
            }
            waitForGraphLayout()
            fun order(ids: List<String>) = ids.sortedBy { onNodeWithTag("node-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x }
            val before = order(listOf("b", "c", "d"))
            runOnIdle {
                add("a")
                layoutKey++
            }
            waitForGraphLayout()
            result = before to order(listOf("a", "b", "c", "d"))
        }
        return result
    }

    @Test
    fun from_scratch_by_default() {
        val (before, after) = orderAfterAddingA(stable = false)
        assertEquals(listOf("b", "c", "d"), before)
        assertEquals(listOf("a", "b", "c", "d"), after)
    }

    @Test
    fun stable_relayout_keeps_the_order_shown() {
        val (before, after) = orderAfterAddingA(stable = true)
        assertEquals(listOf("b", "c", "d"), before)
        assertEquals(listOf("b", "c", "d"), after.filter { it != "a" })
        assertEquals(false, after.first() == "a", "a is placed below R, not at the left end: $after")
    }
}
