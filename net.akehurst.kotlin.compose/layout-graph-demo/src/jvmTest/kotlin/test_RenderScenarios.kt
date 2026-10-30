package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.CompoundGraphLayoutView
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders every demo scenario to a PNG in .gradle-build/layout-graph-demo/scenario-renders
 * (under the Gradle project root), so the layout can be checked by eye.
 */
@OptIn(ExperimentalTestApi::class)
class test_RenderScenarios {

    // Tests run in the module folder; the build output folder is .gradle-build/<module> under the root.
    private val outDir = File("../.gradle-build/layout-graph-demo/scenario-renders").also { it.mkdirs() }

    @Test
    fun render_all_scenarios() = renderAll(debug = false)

    /** Same renders with the debug overlay on (measured node bounds, edge endpoints, content origins). */
    @Test
    fun render_all_scenarios_debug() = renderAll(debug = true)

    /**
     * Laptop-style screens: higher density, and views smaller than the diagram.
     * Written to density-<d>/ and small-view/.
     */
    @Test
    fun render_all_scenarios_at_other_densities_and_sizes() {
        listOf(1.5f, 2f).forEach { d ->
            renderAll(debug = true, density = d, size = 1400 to 900, subDir = "density-$d")
        }
        renderAll(debug = true, density = 1f, size = 300 to 250, subDir = "small-view")
    }

    private fun renderAll(debug: Boolean, density: Float = 1f, size: Pair<Int, Int> = 1200 to 900, subDir: String? = null) {
        val dir = when {
            subDir != null -> File(outDir, subDir).also { it.mkdirs() }
            debug -> File(outDir, "debug").also { it.mkdirs() }
            else -> outDir
        }
        DemoScenarios.all.forEachIndexed { index, scenario ->
            runComposeUiTest {
                val state = scenario.toCompoundGraphState()
                state.showDebugOverlay.value = debug
                state.showContentOrigins.value = debug
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density)) {
                    Box(Modifier.size(size.first.dp, size.second.dp).background(Color.White).testTag("root")) {
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
                val image = onNodeWithTag("root").captureToImage()
                val name = "%02d_%s.png".format(index + 1, scenario.id)
                ImageIO.write(image.toAwtImage(), "png", File(dir, name))
            }
        }
    }
}
