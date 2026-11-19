package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * [EdgeRouting.BUS] with the layered layout (top-down and left-right) and the indented tree layout
 * (trunk from the side and from the bottom). Writes PNGs for checking by eye.
 */
@OptIn(ExperimentalTestApi::class)
class test_BusRouting {

    private val outDir = File("../.gradle-build/layout-graph-demo/bus-routing").also { it.mkdirs() }

    // Half transparent, so a triangle drawn twice in the same place comes out darker.
    private val triangleFill = Color(0x80FF00FF)

    private fun triangle() = GraphLayoutEdgeSymbol(
        pathPoints = listOf(Offset(0f, 0f), Offset(-14f, 7f), Offset(-14f, -7f)),
        isClosed = true, fillColor = triangleFill, strokeColor = Color.Black, strokeWidth = 1.5f
    )

    /** Vehicle <- Car, Truck, Bike; Car <- Saloon, Estate (generalisations point child -> parent). */
    private val generalisations = listOf(
        "Car" to "Vehicle", "Truck" to "Vehicle", "Bike" to "Vehicle",
        "Saloon" to "Car", "Estate" to "Car"
    )
    private val nodeIds = listOf("Vehicle", "Car", "Truck", "Bike", "Saloon", "Estate")

    private fun state(name: String, layout: ChildLayoutAlgorithm): GraphLayoutCompoundGraphState {
        val root = GraphLayoutCompoundGraph("root", childLayout = layout)
        nodeIds.forEach { root.nodes[it] = GraphLayoutCompoundNode(it) }
        generalisations.forEach { (c, p) -> root.edges["$c-$p"] = GraphLayoutCompoundEdge("$c-$p", c, p) }
        return GraphLayoutCompoundGraphState(id = name, routing = EdgeRouting.BUS, root = root).also { s ->
            nodeIds.forEach { id ->
                s.addNodeContent(id) { _ ->
                    Box(Modifier.testTag("node-$id").border(1.dp, Color.Black).background(Color(0xFFF4F4F4)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(id)
                    }
                }
            }
            generalisations.forEach { (c, p) -> s.addEdgeContent("$c-$p", GraphLayoutEdgeContent(endSymbol = triangle())) }
        }
    }

    private fun render(name: String, layout: ChildLayoutAlgorithm): BufferedImage {
        lateinit var img: BufferedImage
        runComposeUiTest {
            val s = state(name, layout)
            setContent {
                Box(Modifier.testTag("root").fillMaxSize().background(Color.White)) {
                    CompoundGraphLayoutView(s, GraphLayoutViewState(zoom = 1f, offset = Offset(20f, 20f)), { _, _ -> }, Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, "$name.png"))
        }
        return img
    }

    /** Pixels where the triangle fill is drawn once over white, and where it is drawn more than once. */
    private fun triangleFillPixels(img: BufferedImage): Pair<Int, Int> {
        var once = 0
        var more = 0
        for (x in 0 until img.width) for (y in 0 until img.height) {
            val rgb = img.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
            if (r > 230 && b > 230) {
                if (g in 110..150) once++ else if (g < 90) more++
            }
        }
        return once to more
    }

    private fun check(name: String, layout: ChildLayoutAlgorithm) {
        val img = render(name, layout)
        val (once, more) = triangleFillPixels(img)
        assertTrue(once > 0, "$name: no triangle drawn")
        assertTrue(more == 0, "$name: $more pixels where a triangle is drawn on top of another (should be drawn once per bus)")
    }

    @Test
    fun a_layered_top_down() = check("A_layered_top_down", SugiyamaChildLayout())

    @Test
    fun b_layered_left_right() = check("B_layered_left_right", SugiyamaChildLayout(direction = LayerDirection.LEFT_RIGHT))

    @Test
    fun c_tree_trunk_from_side() = check("C_tree_trunk_from_side", TreeChildLayout(trunkSide = TreeTrunkSide.SIDE))

    @Test
    fun d_tree_trunk_from_bottom() = check("D_tree_trunk_from_bottom", TreeChildLayout(trunkSide = TreeTrunkSide.BOTTOM))
}
