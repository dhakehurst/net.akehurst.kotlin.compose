package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A centre node with edges to nodes in the row below, each edge with an arrowhead and a MIDDLE label.
 * With rectilinear routing the edges leave the centre node on the same side; their labels must not overlap.
 */
@OptIn(ExperimentalTestApi::class)
class test_EdgeLabels {

    private val outDir = File("../.gradle-build/layout-graph-demo/edge-label-renders").also { it.mkdirs() }

    private fun state(routing: EdgeRouting, targets: Int): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["centre"] = GraphLayoutCompoundNode("centre")
        (1..targets).forEach { i ->
            root.nodes["t$i"] = GraphLayoutCompoundNode("t$i")
            root.edges["e$i"] = GraphLayoutCompoundEdge("e$i", "centre", "t$i")
        }
        return GraphLayoutCompoundGraphState("labels", routing, root).also { s ->
            root.nodes.keys.forEach { id ->
                s.addNodeContent(id) { _ ->
                    Box(Modifier.background(Color(0xFFEEEEFF)).border(1.dp, Color.Gray).padding(12.dp)) {
                        Text("Item $id")
                    }
                }
            }
            root.edges.keys.forEach { id ->
                s.addEdgeContent(
                    id, GraphLayoutEdgeContent(
                        endSymbol = GraphLayoutEdgeSymbol(
                            pathPoints = listOf(Offset(0f, 0f), Offset(-10f, -5f), Offset(-10f, 5f)),
                            fillColor = Color(0xFF444444)
                        ),
                        texts = listOf(GraphLayoutEdgeText(EdgeContentPosition.MIDDLE) {
                            Box(Modifier.testTag("label-$id").background(Color.White)) { Text("relates to $id") }
                        })
                    )
                )
            }
        }
    }

    private fun check(routing: EdgeRouting, targets: Int) = runComposeUiTest {
        val s = state(routing, targets)
        setContent {
            Box(Modifier.size(900.dp, 600.dp).background(Color.White).testTag("root")) {
                CompoundGraphLayoutView(s, GraphLayoutViewState(), { _, _ -> }, Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        ImageIO.write(
            onNodeWithTag("root").captureToImage().toAwtImage(), "png",
            File(outDir, "${routing.name.lowercase()}_$targets.png")
        )
        val labels: List<Pair<String, Rect>> = (1..targets).map { i ->
            "e$i" to onNodeWithTag("label-e$i", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        labels.forEachIndexed { a, (idA, ra) ->
            labels.drop(a + 1).forEach { (idB, rb) ->
                assertFalse(ra.overlaps(rb), "$routing: label of $idA $ra overlaps label of $idB $rb")
            }
        }
    }

    @Test
    fun rectilinear_two_targets() = check(EdgeRouting.RECTILINEAR, 2)

    @Test
    fun rectilinear_three_targets() = check(EdgeRouting.RECTILINEAR, 3)

    @Test
    fun direct_two_targets() = check(EdgeRouting.DIRECT, 2)

    @Test
    fun direct_three_targets() = check(EdgeRouting.DIRECT, 3)
}
