package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlin.test.assertTrue

/**
 * A container is at least as large as the content it draws, like a leaf, whether collapsed or expanded
 * (bug: its size came from the hints or the default node size, so larger content overlapped its neighbours).
 */
@OptIn(ExperimentalTestApi::class)
class test_ContainerContentSize {

    private val outDir = File("../.gradle-build/layout-graph-demo/container-content-size-renders").also { it.mkdirs() }
    private val leaves = listOf("s", "t")
    private val containers = listOf("c", "d")

    /**
     * s above the containers c and d, side by side; t below c. The content of each container is 400 x 300 dp
     * and does not show the children (collapsed), or has a 400 dp wide header above one small child (expanded); the default node size is
     * 100 x 56 dp, so whichever container is on the left overlaps the other unless its content is measured.
     */
    private fun state(collapsed: Boolean): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        leaves.forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        containers.forEach { id ->
            root.nodes[id] = GraphLayoutCompoundNode(id, kind = NodeKind.CONTAINER)
            root.children[id] = GraphLayoutCompoundGraph(id, isCollapsed = collapsed).also { g ->
                g.nodes["$id-k"] = GraphLayoutCompoundNode("$id-k")
            }
        }
        listOf("s" to "c", "s" to "d", "c" to "t").forEach { (f, t) ->
            root.edges["$f-$t"] = GraphLayoutCompoundEdge("$f-$t", f, t)
        }
        return GraphLayoutCompoundGraphState(id = "container-content-size", root = root).also { s ->
            containers.forEach { id ->
                s.addNodeContent(id) { children ->
                    Column(Modifier.testTag("node-$id").fillMaxSize().border(2.dp, Color.Blue).padding(4.dp)) {
                        if (collapsed) {
                            // A collapsed container commonly does not show its children at all.
                            Box(Modifier.size(400.dp, 300.dp).background(Color.LightGray)) { Text("$id (collapsed)") }
                        } else {
                            Box(Modifier.size(400.dp, 24.dp).background(Color.LightGray)) { Text("$id (expanded)") }
                            Box { children() }
                        }
                    }
                }
                s.addNodeContent("$id-k") { _ -> Text("k", Modifier.testTag("node-$id-k").border(1.dp, Color.Black).padding(8.dp)) }
            }
            leaves.forEach { id ->
                s.addNodeContent(id) { _ -> Text(id, Modifier.testTag("node-$id").border(1.dp, Color.Black).padding(8.dp)) }
            }
        }
    }

    private fun check(collapsed: Boolean, name: String) = runComposeUiTest {
        val s = state(collapsed)
        setContent {
            Box(Modifier.testTag("root").fillMaxSize().background(Color.White)) {
                CompoundGraphLayoutView(s, GraphLayoutViewState(), { _, _ -> }, Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        ImageIO.write(onNodeWithTag("root").captureToImage().toAwtImage(), "png", File(outDir, "$name.png"))
        val bounds: Map<String, Rect> = (leaves + containers).associateWith {
            onNodeWithTag("node-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        containers.forEach { id ->
            val c = bounds.getValue(id)
            val minWidth = 400 * density.density
            assertTrue(c.width >= minWidth - 0.5f, "$name: $id is ${c.width} px wide, its content is $minWidth px")
            if (collapsed) {
                val minHeight = 300 * density.density
                assertTrue(c.height >= minHeight - 0.5f, "$name: $id is ${c.height} px high, its content is $minHeight px")
            } else {
                val k = onNodeWithTag("node-$id-k", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(c.contains(k.topLeft) && c.contains(k.bottomRight - Offset(1f, 1f)), "$name: child k $k is not inside $id $c")
            }
        }
        val all = (leaves + containers)
        all.forEachIndexed { i, a ->
            all.drop(i + 1).forEach { b ->
                val ra = bounds.getValue(a)
                val rb = bounds.getValue(b)
                assertTrue(!ra.overlaps(rb), "$name: $a $ra overlaps $b $rb")
            }
        }
    }

    @Test
    fun collapsed_container_fits_its_content() = check(collapsed = true, name = "collapsed")

    @Test
    fun expanded_container_fits_its_content() = check(collapsed = false, name = "expanded")
}
