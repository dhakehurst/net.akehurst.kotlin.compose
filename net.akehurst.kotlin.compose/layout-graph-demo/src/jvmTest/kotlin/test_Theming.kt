package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class test_Theming {

    private val outDir = File("../.gradle-build/layout-graph-demo/theming-renders").also { it.mkdirs() }

    private fun chain(n: Int, edgeContent: (Int) -> GraphLayoutEdgeContent): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        (0 until n).forEach { i -> root.nodes["n$i"] = GraphLayoutCompoundNode("n$i") }
        (1 until n).forEach { i -> root.edges["e$i"] = GraphLayoutCompoundEdge("e$i", "n${i - 1}", "n$i") }
        return GraphLayoutCompoundGraphState("chain$n", EdgeRouting.DIRECT, root).also { s ->
            root.nodes.keys.forEach { id -> s.addNodeContent(id) { _ -> Box(Modifier.size(60.dp, 30.dp).background(Color(0xFF303060))) { Text(id, color = Color.White) } } }
            root.edges.keys.forEachIndexed { i, id -> s.addEdgeContent(id, edgeContent(i + 1)) }
        }
    }

    private fun countColour(img: java.awt.image.BufferedImage, c: Color): Int {
        val argb = c.toArgb()
        var count = 0
        for (x in 0 until img.width) for (y in 0 until img.height) if (img.getRGB(x, y) == argb) count++
        return count
    }

    @Test
    fun edge_line_colour_comes_from_the_edge_or_the_view() = runComposeUiTest {
        val viewLine = Color(0xFFFFEB3B)
        val edgeLine = Color(0xFF00E5FF)
        // e1 uses the view's colour, e2 its own colour and a wider line.
        val s = chain(3) { i -> if (i == 2) GraphLayoutEdgeContent(lineColor = edgeLine, lineWidth = 4f) else GraphLayoutEdgeContent() }
        setContent {
            Box(Modifier.size(500.dp, 400.dp).background(Color.Black).testTag("root")) {
                CompoundGraphLayoutView(
                    s, GraphLayoutViewState(), { _, _ -> }, Modifier.fillMaxSize(),
                    colors = CompoundGraphLayoutColors(edgeLine = viewLine)
                )
            }
        }
        waitForIdle()
        val img = onNodeWithTag("root").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(outDir, "dark_lines.png"))
        assertTrue(countColour(img, viewLine) > 20, "no pixels in the view's line colour")
        assertTrue(countColour(img, edgeLine) > 20, "no pixels in the edge's own line colour")
        assertEquals(0, countColour(img, Color(0xFF444444)), "the old fixed line colour is still drawn")
    }

    @Test
    fun missing_content_uses_the_error_colours() = runComposeUiTest {
        val error = Color(0xFFFF80AB)
        val root = GraphLayoutCompoundGraph("root").also { it.nodes["lonely"] = GraphLayoutCompoundNode("lonely", widthHint = 120.0, heightHint = 60.0) }
        val s = GraphLayoutCompoundGraphState("missing", EdgeRouting.DIRECT, root)
        setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.Black).testTag("root")) {
                CompoundGraphLayoutView(s, GraphLayoutViewState(), { _, _ -> }, Modifier.fillMaxSize(),
                    colors = CompoundGraphLayoutColors(error = error))
            }
        }
        waitForIdle()
        val img = onNodeWithTag("root").captureToImage().toAwtImage()
        assertTrue(countColour(img, error) > 20, "placeholder border not in the error colour")
    }

    @Test
    fun fit_and_centre_shrinks_a_big_graph_into_the_view() = runComposeUiTest {
        val s = chain(5) { GraphLayoutEdgeContent() }
        var viewState by mutableStateOf(GraphLayoutViewState())
        setContent {
            Box(Modifier.size(300.dp, 300.dp).background(Color.White).testTag("root")) {
                CompoundGraphLayoutView(
                    s, viewState, { o, z -> viewState = GraphLayoutViewState(z, o) }, Modifier.fillMaxSize(),
                    initialView = InitialViewPlacement.FIT_AND_CENTRE
                )
            }
        }
        waitForIdle()
        val img = onNodeWithTag("root").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(outDir, "fit_and_centre.png"))
        assertTrue(viewState.zoom < 1f, "a graph bigger than the view should be zoomed out, zoom=${viewState.zoom}")
        // Every node is inside the view.
        val view = onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        assertTrue(viewState.offset.x >= -0.5f && viewState.offset.y >= -0.5f, "offset ${viewState.offset} puts the graph outside the view $view")
    }

    @Test
    fun centre_puts_a_small_graph_in_the_middle_at_zoom_1() = runComposeUiTest {
        val s = chain(2) { GraphLayoutEdgeContent() }
        var viewState by mutableStateOf(GraphLayoutViewState())
        setContent {
            Box(Modifier.size(800.dp, 800.dp).background(Color.White).testTag("root")) {
                CompoundGraphLayoutView(
                    s, viewState, { o, z -> viewState = GraphLayoutViewState(z, o) }, Modifier.fillMaxSize(),
                    initialView = InitialViewPlacement.FIT_AND_CENTRE
                )
            }
        }
        waitForIdle()
        assertEquals(1f, viewState.zoom)
        assertTrue(viewState.offset.x > 100f && viewState.offset.y > 100f, "graph not moved towards the centre: ${viewState.offset}")
    }
}
