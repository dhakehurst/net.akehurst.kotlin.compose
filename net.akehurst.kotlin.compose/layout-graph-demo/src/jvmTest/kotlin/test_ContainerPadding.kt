package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.CompoundGraphLayoutView
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the demo scenario from 5_Documentation/graph-layout/Container-Padding-Bug.md
 * and checks that the padded container encloses its children.
 */
@OptIn(ExperimentalTestApi::class)
class test_ContainerPadding {

    @Test
    fun padded_container_encloses_its_children() = runComposeUiTest {
        // Tag each node's content so its rendered bounds can be found.
        val scenario = DemoScenarios.singleContainerTwoNodesWithPadding.let { sc ->
            sc.copy(nodes = sc.nodes.map { node ->
                node.copy(content = { children -> Box(Modifier.testTag(node.id)) { node.content(children) } })
            })
        }
        val state = scenario.toCompoundGraphState()

        setContent {
            Box(Modifier.size(1200.dp, 900.dp)) {
                CompoundGraphLayoutView(
                    state = state,
                    viewState = GraphLayoutViewState(),
                    updateView = { _, _ -> },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        waitForIdle()

        val padding = 20f * density.density
        val container = onNodeWithTag("Container1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val paddedArea = Rect(container.left + padding, container.top + padding, container.right - padding, container.bottom - padding)
        val eps = 0.5f

        listOf("InsideA", "InsideB").forEach { id ->
            val child = onNodeWithTag(id, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                child.left >= paddedArea.left - eps && child.top >= paddedArea.top - eps &&
                    child.right <= paddedArea.right + eps && child.bottom <= paddedArea.bottom + eps,
                "$id $child is not inside the padded area $paddedArea of Container1 $container"
            )
        }
    }
}
