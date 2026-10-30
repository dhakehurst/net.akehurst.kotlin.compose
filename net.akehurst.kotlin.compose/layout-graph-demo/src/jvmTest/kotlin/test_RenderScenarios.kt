package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
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

    private fun renderAll(debug: Boolean) {
        val dir = if (debug) File(outDir, "debug").also { it.mkdirs() } else outDir
        DemoScenarios.all.forEachIndexed { index, scenario ->
            runComposeUiTest {
                val state = scenario.toCompoundGraphState()
                state.showDebugOverlay.value = debug
                state.showContentOrigins.value = debug
                setContent {
                    Box(Modifier.size(1200.dp, 900.dp).background(Color.White).testTag("root")) {
                        CompoundGraphLayoutView(
                            state = state,
                            viewState = GraphLayoutViewState(),
                            updateView = { _, _ -> },
                            modifier = Modifier.fillMaxSize()
                        )
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
