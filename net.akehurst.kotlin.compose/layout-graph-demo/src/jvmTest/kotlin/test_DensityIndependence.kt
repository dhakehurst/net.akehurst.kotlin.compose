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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.CompoundGraphLayoutView
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The layout works in dp, so a diagram rendered at a higher density must be the same diagram,
 * only bigger: every node's bounds, in dp, must match its bounds at density 1.
 */
@OptIn(ExperimentalTestApi::class)
class test_DensityIndependence {

    /**
     * Allowed difference from 1x, in dp. At 2x every pixel is exactly half a dp, so only rounding is left.
     * At 1.5x text is measured in whole pixels (a third of a dp each), lines of text come out up to a
     * pixel taller, and that adds up through nested containers.
     */
    private val toleranceDp = mapOf(1.5f to 6f, 2f to 2f) // measured here: 5 and 1; a little margin for other fonts

    private fun nodeBoundsInDp(scenario: DemoScenario, density: Float): Map<String, Rect> {
        var result: Map<String, Rect> = emptyMap()
        val tagged = scenario.copy(nodes = scenario.nodes.map { node ->
            node.copy(content = { children -> Box(Modifier.testTag(node.id)) { node.content(children) } })
        })
        runComposeUiTest {
            val state = tagged.toCompoundGraphState()
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    // Big enough for every scenario, so nothing depends on the view size.
                    Box(Modifier.size(3000.dp, 3000.dp)) {
                        CompoundGraphLayoutView(
                            state = state,
                            viewState = GraphLayoutViewState(),
                            updateView = { _, _ -> },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            waitForIdle()
            // Collapsed containers hide their children, so only look at nodes that are shown.
            result = tagged.nodes.mapNotNull { node ->
                val found = onAllNodesWithTagSafe(node.id) ?: return@mapNotNull null
                node.id to Rect(found.left / density, found.top / density, found.right / density, found.bottom / density)
            }.toMap()
        }
        return result
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagSafe(tag: String): Rect? {
        val nodes = onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        // Not boundsInRoot: that is clipped to the test window, which is smaller than big diagrams at 2x.
        val node = nodes.firstOrNull() ?: return null
        val p = node.positionInRoot
        return Rect(p.x, p.y, p.x + node.size.width, p.y + node.size.height)
    }

    private fun Rect.differsFrom(other: Rect) = maxOf(
        abs(left - other.left), abs(top - other.top), abs(right - other.right), abs(bottom - other.bottom)
    )

    @Test
    fun every_scenario_has_the_same_layout_in_dp_at_1_5x_and_2x_density() {
        val problems = mutableListOf<String>()
        val worst = mutableMapOf(1.5f to 0f, 2f to 0f)
        DemoScenarios.all.forEach { scenario ->
            val at1 = nodeBoundsInDp(scenario, 1f)
            assertTrue(at1.isNotEmpty(), "no nodes found for ${scenario.id}")
            listOf(1.5f, 2f).forEach { d ->
                val atD = nodeBoundsInDp(scenario, d)
                if (atD.keys != at1.keys) problems += "${scenario.id} at ${d}x: shown nodes differ: ${at1.keys} vs ${atD.keys}"
                at1.forEach { (id, r1) ->
                    val rD = atD[id] ?: return@forEach
                    val diff = r1.differsFrom(rD)
                    worst[d] = maxOf(worst.getValue(d), diff)
                    if (diff > toleranceDp.getValue(d)) problems += "${scenario.id} at ${d}x: $id is $rD dp, at 1x $r1 dp (off by $diff dp)"
                }
            }
        }
        println("largest difference from 1x, by density: $worst (dp)")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
