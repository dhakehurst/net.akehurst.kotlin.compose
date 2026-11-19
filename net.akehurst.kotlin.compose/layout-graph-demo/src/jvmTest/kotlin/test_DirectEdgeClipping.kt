package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
 * DIRECT edges must start and end on the drawn border of their nodes, not inside them,
 * for wide nodes with two lines of text (bug: ends and arrowheads landed on the node's text).
 */
@OptIn(ExperimentalTestApi::class)
class test_DirectEdgeClipping {

    private val outDir = File("../.gradle-build/layout-graph-demo/direct-edge-clipping").also { it.mkdirs() }
    private val nodeIds = listOf("a", "b", "c", "d")
    private val edges = mapOf(
        "a-b" to ("a" to "b"),
        "a-c" to ("a" to "c"),
        "b-d" to ("b" to "d"),
        "c-d" to ("c" to "d"),
    )
    private val edgeColour = Color(0xFF00C853)
    private val arrowColour = Color(0xFFFF4081)

    private fun arrowHead() = GraphLayoutEdgeSymbol(
        pathPoints = listOf(Offset(0f, 0f), Offset(-12f, 6f), Offset(-12f, -6f)),
        isClosed = true, fillColor = arrowColour, strokeColor = arrowColour, strokeWidth = 1f
    )

    /** [fill] true: the node content fills the node's box; false: it wraps its text. */
    private fun state(fill: Boolean): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        nodeIds.forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        edges.forEach { (id, ends) -> root.edges[id] = GraphLayoutCompoundEdge(id, ends.first, ends.second) }
        return GraphLayoutCompoundGraphState(id = "direct-clip-$fill", routing = EdgeRouting.DIRECT, root = root).also { s ->
            nodeIds.forEach { id ->
                s.addNodeContent(id) { _ ->
                    val m = if (fill) Modifier.fillMaxSize() else Modifier
                    Column(m.testTag("node-$id").border(1.dp, Color.Black).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("«block» node $id with a long name")
                        Text("second line of node $id")
                    }
                }
            }
            edges.keys.forEach { id ->
                s.addEdgeContent(id, GraphLayoutEdgeContent(lineColor = edgeColour, lineWidth = 2f, endSymbol = arrowHead()))
            }
        }
    }

    private class Render(val img: BufferedImage, val nodes: Map<String, Rect>)

    private fun render(fill: Boolean, name: String): Render {
        lateinit var result: Render
        runComposeUiTest {
            val s = state(fill)
            setContent {
                Box(Modifier.testTag("root").fillMaxSize().background(Color.White)) {
                    CompoundGraphLayoutView(s, GraphLayoutViewState(zoom = 1f, offset = Offset(20f, 20f)), { _, _ -> }, Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            val img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, "$name.png"))
            val nodes = nodeIds.associateWith { onNodeWithTag("node-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            result = Render(img, nodes)
        }
        return result
    }

    private fun pixels(img: BufferedImage, vararg cs: Color): List<Pair<Int, Int>> {
        val argbs = cs.map { it.toArgb() }.toSet()
        val list = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until img.width) for (y in 0 until img.height) if (img.getRGB(x, y) in argbs) list += x to y
        return list
    }

    private fun Rect.contains(x: Int, y: Int, inset: Float) =
        x > left + inset && x < right - inset && y > top + inset && y < bottom - inset

    private fun check(fill: Boolean, name: String) {
        val r = render(fill, name)
        val drawn = pixels(r.img, edgeColour, arrowColour)
        assertTrue(drawn.isNotEmpty(), "$name: no edges drawn")
        // Nothing of an edge (line or arrowhead) is drawn inside a node, inside its 1dp border.
        r.nodes.forEach { (id, bounds) ->
            val inside = drawn.filter { (x, y) -> bounds.contains(x, y, 2f) }
            assertTrue(inside.isEmpty(), "$name: ${inside.size} edge pixels inside node $id at $bounds, e.g. ${inside.take(5)}")
        }
        // Every node has an edge end close to its border.
        r.nodes.forEach { (id, bounds) ->
            val near = drawn.any { (x, y) -> bounds.inflate(8f).contains(Offset(x.toFloat(), y.toFloat())) }
            assertTrue(near, "$name: no edge reaches node $id at $bounds")
        }
    }

    @Test
    fun wide_two_line_nodes_wrapping_content() = check(fill = false, "wrap")

    @Test
    fun wide_two_line_nodes_filling_content() = check(fill = true, "fill")
}
