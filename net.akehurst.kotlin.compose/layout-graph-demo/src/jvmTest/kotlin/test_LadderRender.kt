package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import net.akehurst.kotlin.compose.layout.ladder.LadderGroup
import net.akehurst.kotlin.compose.layout.ladder.LadderEvent
import net.akehurst.kotlin.compose.layout.ladder.LadderLayoutColors
import net.akehurst.kotlin.compose.layout.ladder.LadderLayoutView
import net.akehurst.kotlin.compose.layout.ladder.LadderRung
import net.akehurst.kotlin.compose.layout.ladder.validate
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders every ladder demo scenario to a PNG in .gradle-build/layout-graph-demo/ladder-renders
 * and checks that headers and texts are placed sensibly.
 */
@OptIn(ExperimentalTestApi::class)
class test_LadderRender {

    private val outDir = File("../.gradle-build/layout-graph-demo/ladder-renders").also { it.mkdirs() }
    private val rungColour = Color(0xFF00A0FF)

    private class Render(val img: BufferedImage, val headers: Map<String, Rect>, val texts: Map<String, Rect>)

    private fun rungTexts(scenario: LadderDemoScenario): List<String> {
        fun walk(events: List<LadderEvent>): List<String> = events.flatMap { e ->
            when (e) {
                is LadderRung -> listOf(scenario.rungTexts[e.id] ?: e.id)
                is LadderGroup -> e.parts.flatMap { walk(it.events) }
                else -> emptyList()
            }
        }
        return walk(scenario.model.events).distinct()
    }

    private fun render(scenario: LadderDemoScenario, density: Float, name: String): Render {
        lateinit var result: Render
        runComposeUiTest {
            val state = scenario.toState()
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    Box(Modifier.size(900.dp, 700.dp).background(Color.White).testTag("root")) {
                        LadderLayoutView(
                            state = state,
                            viewState = GraphLayoutViewState(),
                            updateView = { _, _ -> },
                            modifier = Modifier.fillMaxSize(),
                            colors = LadderLayoutColors(rungLine = rungColour)
                        )
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("root").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(outDir, name))
            val bounds = { text: String -> onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            result = Render(
                img,
                scenario.model.rails.associate { it.id to bounds(it.id) },
                rungTexts(scenario).associateWith { bounds(it) }
            )
        }
        return result
    }

    private fun Rect.overlapsWith(other: Rect) = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun demo_scenarios_are_valid() {
        LadderDemoScenarios.all.forEach { assertEquals(emptyList(), it.model.validate(), it.id) }
    }

    @Test
    fun headers_and_texts_do_not_overlap() {
        LadderDemoScenarios.all.forEachIndexed { index, scenario ->
            val r = render(scenario, 1f, "%02d_%s.png".format(index + 1, scenario.id))
            val headers = scenario.model.rails.map { r.headers.getValue(it.id) }
            assertTrue(headers.zipWithNext().all { (a, b) -> a.right < b.left }, "${scenario.id}: headers not left to right: $headers")
            assertTrue(headers.map { it.top }.distinct().size == 1, "${scenario.id}: headers not in one row: $headers")
            val texts = r.texts.entries.toList()
            texts.forEachIndexed { i, (a, ra) ->
                texts.drop(i + 1).forEach { (b, rb) ->
                    assertTrue(!ra.overlapsWith(rb), "${scenario.id}: texts '$a' $ra and '$b' $rb overlap")
                }
                headers.forEach { h -> assertTrue(!ra.overlapsWith(h), "${scenario.id}: text '$a' overlaps a header") }
                assertTrue(ra.top > headers.maxOf { it.bottom }, "${scenario.id}: text '$a' is not below the headers")
            }
            // Rungs are drawn: pixels of the (blue) rung colour, possibly anti-aliased with the white background.
            var count = 0
            for (x in 0 until r.img.width) for (y in 0 until r.img.height) {
                val rgb = r.img.getRGB(x, y)
                val red = (rgb shr 16) and 0xFF
                val blue = rgb and 0xFF
                if (blue > 200 && blue - red > 80) count++
            }
            assertTrue(count > 50, "${scenario.id}: no rung lines drawn")
        }
    }

    @Test
    fun layout_scales_with_density() {
        LadderDemoScenarios.all.forEachIndexed { index, scenario ->
            val one = render(scenario, 1f, "density-1_%02d_%s.png".format(index + 1, scenario.id))
            val two = render(scenario, 2f, "density-2_%02d_%s.png".format(index + 1, scenario.id))
            (one.headers + one.texts).forEach { (key, a) ->
                val b = (two.headers + two.texts).getValue(key)
                // Same place in dp; text measures slightly differently at another density, so allow a little.
                assertTrue(abs(a.left - b.left / 2) <= 3 && abs(a.top - b.top / 2) <= 3, "${scenario.id}: '$key' at $a (1x) but ${b} (2x)")
            }
        }
    }
}
