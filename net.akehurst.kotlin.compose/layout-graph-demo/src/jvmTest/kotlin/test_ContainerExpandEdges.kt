package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Edges inside a container whose content draws a header above its children must be drawn at their
 * nodes after the container is expanded and the layout key changes (bug: the measured child host
 * metrics were written to the state of the first layout, so the new layout used the defaults and
 * the edges were drawn about 70 px away from their nodes).
 */
@OptIn(ExperimentalTestApi::class)
class test_ContainerExpandEdges {

    private val outDir = File("../.gradle-build/layout-graph-demo/container-expand-renders").also { it.mkdirs() }
    private val edgeColours = mapOf(
        "a-b" to Color(0xFF00E5FF),
        "a-c2" to Color(0xFFFF4081),
    )
    private val edgeEnds = mapOf("a-b" to ("a" to "b"), "a-c2" to ("a" to "c2"))

    private fun state(collapsed: Boolean): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["c"] = GraphLayoutCompoundNode("c", kind = NodeKind.CONTAINER)
        root.children["c"] = GraphLayoutCompoundGraph("c", isCollapsed = collapsed).also { g ->
            listOf("a", "b", "c2").forEach { g.nodes[it] = GraphLayoutCompoundNode(it) }
            g.edges["a-b"] = GraphLayoutCompoundEdge("a-b", "a", "b")
            g.edges["a-c2"] = GraphLayoutCompoundEdge("a-c2", "a", "c2")
        }
        return GraphLayoutCompoundGraphState(id = "container-expand", root = root).also { s ->
            // A header of three lines (about 70 px) above the children.
            s.addNodeContent("c") { children ->
                Column(Modifier.testTag("node-c").fillMaxSize().border(2.dp, Color.Blue).padding(8.dp)) {
                    Text("«block»")
                    Text("c")
                    Text("third header line")
                    Box { children() }
                }
            }
            listOf("a", "b", "c2").forEach { id ->
                s.addNodeContent(id) { _ -> Text(id, Modifier.testTag("node-$id").border(1.dp, Color.Black).padding(8.dp)) }
            }
            edgeColours.forEach { (id, c) -> s.addEdgeContent(id, GraphLayoutEdgeContent(lineColor = c, lineWidth = 2f)) }
        }
    }

    private class Render(val img: BufferedImage, val nodes: Map<String, Rect>)

    /**
     * Once the first layout is done: when [toggle], the collapse state of c is flipped; when [toggle]
     * or [bumpKey], the layout key is changed.
     */
    private fun render(startCollapsed: Boolean, toggle: Boolean, name: String, bumpKey: Boolean = toggle): Render {
        lateinit var result: Render
        runComposeUiTest {
            val s = state(startCollapsed)
            var layoutKey by mutableStateOf(0)
            setContent {
                Box(Modifier.testTag("root").fillMaxSize().background(Color.White)) {
                    CompoundGraphLayoutView(
                        state = s, viewState = GraphLayoutViewState(), updateView = { _, _ -> },
                        modifier = Modifier.fillMaxSize(), layoutKey = layoutKey,
                    )
                }
            }
            waitForIdle()
            if (toggle || bumpKey) {
                runOnIdle {
                    if (toggle) s.toggleCollapsed("c")
                    layoutKey++
                }
                waitForIdle()
            }
            val img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, "$name.png"))
            val nodes = listOf("c", "a", "b", "c2").associateWith {
                onNodeWithTag("node-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            result = Render(img, nodes)
        }
        return result
    }

    private fun pixels(img: BufferedImage, c: Color): List<Pair<Int, Int>> {
        val argb = c.toArgb()
        val list = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until img.width) for (y in 0 until img.height) if (img.getRGB(x, y) == argb) list += x to y
        return list
    }

    private fun Rect.near(x: Int, y: Int, tol: Float) =
        x >= left - tol && x <= right + tol && y >= top - tol && y <= bottom + tol

    private fun check(r: Render, name: String) {
        val tol = 4f
        edgeEnds.forEach { (edgeId, ends) ->
            val (srcId, tgtId) = ends
            val src = r.nodes.getValue(srcId)
            val tgt = r.nodes.getValue(tgtId)
            val line = pixels(r.img, edgeColours.getValue(edgeId))
            assertTrue(line.isNotEmpty(), "$name: edge $edgeId not drawn")
            assertTrue(line.any { (x, y) -> src.near(x, y, tol) }, "$name: edge $edgeId does not touch $srcId at $src")
            assertTrue(line.any { (x, y) -> tgt.near(x, y, tol) }, "$name: edge $edgeId does not touch $tgtId at $tgt")
            // No part of the line is above the top or below the bottom of its two nodes.
            val top = minOf(src.top, tgt.top) - tol
            val bottom = maxOf(src.bottom, tgt.bottom) + tol
            val off = line.filterNot { (_, y) -> y >= top && y <= bottom }
            assertTrue(off.isEmpty(), "$name: edge $edgeId has ${off.size} pixels above or below its nodes (y $top..$bottom), e.g. ${off.take(3)}")
        }
    }

    @Test
    fun expanded_from_the_start() = check(render(startCollapsed = false, toggle = false, name = "expanded_from_start"), "expanded_from_start")

    @Test
    fun expanded_after_toggle_and_new_layout_key() = check(render(startCollapsed = true, toggle = true, name = "expanded_after_toggle"), "expanded_after_toggle")

    @Test
    fun new_layout_key_while_expanded() = check(render(startCollapsed = false, toggle = false, bumpKey = true, name = "new_key_while_expanded"), "new_key_while_expanded")
}
