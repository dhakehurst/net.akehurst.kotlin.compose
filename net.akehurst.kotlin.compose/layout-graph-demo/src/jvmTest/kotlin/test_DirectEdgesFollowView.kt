package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * DIRECT edges must be drawn between their nodes when the view is moved or zoomed,
 * for a root edge and for an edge inside a container (bug: they were shifted by the view offset).
 */
@OptIn(ExperimentalTestApi::class)
class test_DirectEdgesFollowView {

    private val outDir = File("../.gradle-build/layout-graph-demo/direct-edge-renders").also { it.mkdirs() }
    private val edgeColours = mapOf(
        "a-b" to Color(0xFF00E5FF),
        "p-q" to Color(0xFFFF4081),
        "x-d" to Color(0xFF00C853),
    )
    private val edgeEnds = mapOf("a-b" to ("c_a" to "c_b"), "x-d" to ("x" to "d"))

    private fun state(routing: EdgeRouting): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["c"] = GraphLayoutCompoundNode("c", kind = NodeKind.CONTAINER)
        root.nodes["x"] = GraphLayoutCompoundNode("x")
        root.children["c"] = GraphLayoutCompoundGraph("c").also { g ->
            g.nodes["c_a"] = GraphLayoutCompoundNode("c_a"); g.nodes["c_b"] = GraphLayoutCompoundNode("c_b")
            g.edges["a-b"] = GraphLayoutCompoundEdge("a-b", "c_a", "c_b")
        }
        root.nodes["d"] = GraphLayoutCompoundNode("d", kind = NodeKind.CONTAINER)
        root.children["d"] = GraphLayoutCompoundGraph("d", isCollapsed = true).also { g ->
            g.nodes["p"] = GraphLayoutCompoundNode("p"); g.nodes["q"] = GraphLayoutCompoundNode("q")
            g.edges["p-q"] = GraphLayoutCompoundEdge("p-q", "p", "q")
        }
        root.edges["x-d"] = GraphLayoutCompoundEdge("x-d", "x", "d")
        return GraphLayoutCompoundGraphState(id = "direct-view", routing = routing, root = root).also { s ->
            s.addNodeContent("c") { children -> Box(Modifier.testTag("node-c").fillMaxSize().border(2.dp, Color.Blue).padding(16.dp)) { children() } }
            s.addNodeContent("d") { children ->
                Box(Modifier.testTag("node-d").fillMaxSize().border(2.dp, Color.Red).padding(16.dp)) { Text("d (collapsed)"); children() }
            }
            listOf("c_a", "c_b", "x", "p", "q").forEach { id ->
                s.addNodeContent(id) { _ -> Text(id, Modifier.testTag("node-$id").border(1.dp, Color.Black).padding(8.dp)) }
            }
            edgeColours.forEach { (id, c) -> s.addEdgeContent(id, GraphLayoutEdgeContent(lineColor = c, lineWidth = 2f)) }
        }
    }

    private class Render(val img: BufferedImage, val nodes: Map<String, Rect>)

    /**
     * [fixedView] null: the view follows updateView, so FIT_AND_CENTRE moves it.
     * Otherwise the view is fixed at that offset and zoom.
     */
    private fun render(routing: EdgeRouting, fixedView: GraphLayoutViewState?, name: String): Render {
        lateinit var result: Render
        runComposeUiTest {
            val s = state(routing)
            setContent {
                Box(Modifier.testTag("root").fillMaxSize().background(Color.White)) {
                    if (fixedView == null) {
                        var vs by remember { mutableStateOf(GraphLayoutViewState()) }
                        CompoundGraphLayoutView(
                            state = s, viewState = vs, updateView = { o, z -> vs = GraphLayoutViewState(zoom = z, offset = o) },
                            modifier = Modifier.fillMaxSize(), initialView = InitialViewPlacement.FIT_AND_CENTRE,
                        )
                    } else {
                        CompoundGraphLayoutView(s, fixedView, { _, _ -> }, Modifier.fillMaxSize())
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, "${routing.name.lowercase()}_$name.png"))
            val nodes = listOf("c", "c_a", "c_b", "x", "d").associateWith {
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

    private fun check(routing: EdgeRouting, fixedView: GraphLayoutViewState?, name: String) {
        val r = render(routing, fixedView, name)
        val tol = 4f
        edgeEnds.forEach { (edgeId, ends) ->
            val (srcId, tgtId) = ends
            val src = r.nodes.getValue(srcId)
            val tgt = r.nodes.getValue(tgtId)
            val line = pixels(r.img, edgeColours.getValue(edgeId))
            assertTrue(line.isNotEmpty(), "$name: edge $edgeId not drawn")
            // The whole line lies in the box spanned by its two nodes.
            val span = Rect(
                minOf(src.left, tgt.left), minOf(src.top, tgt.top),
                maxOf(src.right, tgt.right), maxOf(src.bottom, tgt.bottom)
            )
            val outside = line.filterNot { (x, y) -> span.near(x, y, tol) }
            assertTrue(outside.isEmpty(), "$name: edge $edgeId has ${outside.size} pixels outside its nodes' span $span, e.g. ${outside.take(3)}")
            // And it touches both nodes.
            assertTrue(line.any { (x, y) -> src.near(x, y, tol) }, "$name: edge $edgeId does not touch $srcId at $src")
            assertTrue(line.any { (x, y) -> tgt.near(x, y, tol) }, "$name: edge $edgeId does not touch $tgtId at $tgt")
        }
        // The container edge stays inside its container.
        val c = r.nodes.getValue("c")
        val outsideC = pixels(r.img, edgeColours.getValue("a-b")).filterNot { (x, y) -> c.near(x, y, 0f) }
        assertTrue(outsideC.isEmpty(), "$name: edge a-b has ${outsideC.size} pixels outside container c at $c")
    }

    @Test
    fun direct_fit_and_centre() = check(EdgeRouting.DIRECT, null, "fit_and_centre")

    @Test
    fun direct_panned() = check(EdgeRouting.DIRECT, GraphLayoutViewState(zoom = 1f, offset = Offset(90f, 60f)), "panned")

    @Test
    fun direct_panned_and_zoomed() = check(EdgeRouting.DIRECT, GraphLayoutViewState(zoom = 1.5f, offset = Offset(-40f, 30f)), "panned_zoomed")

    @Test
    fun direct_unmoved() = check(EdgeRouting.DIRECT, GraphLayoutViewState(zoom = 1f), "unmoved")

    @Test
    fun rectilinear_fit_and_centre() = check(EdgeRouting.RECTILINEAR, null, "fit_and_centre")

    @Test
    fun rectilinear_panned_and_zoomed() = check(EdgeRouting.RECTILINEAR, GraphLayoutViewState(zoom = 1.5f, offset = Offset(-40f, 30f)), "panned_zoomed")
}
