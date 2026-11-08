package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dashed and dotted edge lines: same geometry, labels and symbols as a solid line, only gaps in the line.
 */
@OptIn(ExperimentalTestApi::class)
class test_DashedEdges {

    private val outDir = File("../.gradle-build/layout-graph-demo/dashed-renders").also { it.mkdirs() }
    private val lineColour = Color(0xFF00E5FF)
    private val symbolColour = Color(0xFFFF4081)

    private class Render(val img: BufferedImage, val labels: Map<String, Rect>, val nodes: Map<String, Rect>)

    /** A centre node with edges to [targets] nodes, so RECTILINEAR routes have bends. */
    private fun state(routing: EdgeRouting, lineDash: List<Float>?): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root")
        root.nodes["centre"] = GraphLayoutCompoundNode("centre")
        (1..3).forEach { i ->
            root.nodes["t$i"] = GraphLayoutCompoundNode("t$i")
            root.edges["e$i"] = GraphLayoutCompoundEdge("e$i", "centre", "t$i")
        }
        return GraphLayoutCompoundGraphState("dashed", routing, root).also { s ->
            root.nodes.keys.forEach { id ->
                s.addNodeContent(id) { _ ->
                    Box(Modifier.testTag("node-$id").size(80.dp, 40.dp).background(Color(0xFF303060))) { Text(id, color = Color.White) }
                }
            }
            root.edges.keys.forEach { id ->
                s.addEdgeContent(
                    id, GraphLayoutEdgeContent(
                        endSymbol = GraphLayoutEdgeSymbol(
                            pathPoints = listOf(Offset(0f, 0f), Offset(-12f, -6f), Offset(-12f, 6f)),
                            fillColor = symbolColour,
                            strokeColor = symbolColour
                        ),
                        texts = listOf(GraphLayoutEdgeText(EdgeContentPosition.MIDDLE) {
                            Box(Modifier.testTag("label-$id")) { Text(id, color = Color.White) }
                        }),
                        lineColor = lineColour,
                        lineWidth = 3f,
                        lineDash = lineDash
                    )
                )
            }
        }
    }

    private fun render(routing: EdgeRouting, lineDash: List<Float>?, name: String): Render {
        lateinit var result: Render
        runComposeUiTest {
            val s = state(routing, lineDash)
            setContent {
                Box(Modifier.size(700.dp, 500.dp).background(Color.Black).testTag("root")) {
                    CompoundGraphLayoutView(s, GraphLayoutViewState(), { _, _ -> }, Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            val img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, "${routing.name.lowercase()}_$name.png"))
            val bounds = { tag: String -> onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            result = Render(
                img,
                (1..3).associate { "e$it" to bounds("label-e$it") },
                (listOf("centre") + (1..3).map { "t$it" }).associateWith { bounds("node-$it") }
            )
        }
        return result
    }

    private fun pixels(img: BufferedImage, c: Color): Set<Pair<Int, Int>> {
        val argb = c.toArgb()
        val set = mutableSetOf<Pair<Int, Int>>()
        for (x in 0 until img.width) for (y in 0 until img.height) if (img.getRGB(x, y) == argb) set += x to y
        return set
    }

    private fun check(routing: EdgeRouting, dash: List<Float>, name: String) {
        val solid = render(routing, null, "solid")
        val dashed = render(routing, dash, name)

        assertEquals(solid.nodes, dashed.nodes, "$routing: node positions changed")
        assertEquals(solid.labels, dashed.labels, "$routing: label positions changed")

        val solidLine = pixels(solid.img, lineColour)
        val dashedLine = pixels(dashed.img, lineColour)
        assertTrue(dashedLine.isNotEmpty(), "$routing: no $name line drawn")
        // Same geometry: every dash lies on the solid line. On a slanted line the dash ends are anti-aliased
        // differently, so the solid render only has to be drawn there, not in exactly the line colour.
        val background = Color.Black.toArgb()
        val offLine = dashedLine.filter { (x, y) -> solid.img.getRGB(x, y) == background }
        assertTrue(offLine.isEmpty(), "$routing: ${offLine.size} $name pixels are not on the solid route, e.g. ${offLine.take(5)}")
        // It has gaps.
        assertTrue(dashedLine.size < solidLine.size * 0.85, "$routing: $name line has no gaps (${dashedLine.size} of ${solidLine.size} pixels)")

        // End symbols stay solid and in place.
        assertEquals(pixels(solid.img, symbolColour), pixels(dashed.img, symbolColour), "$routing: end symbols differ")
    }

    @Test
    fun null_dash_draws_a_solid_line() {
        // Null is the default: same pixels as an edge content that never mentions lineDash.
        val a = render(EdgeRouting.RECTILINEAR, null, "solid")
        val b = render(EdgeRouting.RECTILINEAR, null, "solid_again")
        assertEquals(pixels(a.img, lineColour), pixels(b.img, lineColour))
        assertEquals(null, GraphLayoutEdgeContent().lineDash)
    }

    @Test
    fun dashed_rectilinear() = check(EdgeRouting.RECTILINEAR, listOf(6f, 4f), "dashed")

    @Test
    fun dotted_rectilinear() = check(EdgeRouting.RECTILINEAR, listOf(1.5f, 3f), "dotted")

    @Test
    fun dashed_direct() = check(EdgeRouting.DIRECT, listOf(6f, 4f), "dashed")
}
