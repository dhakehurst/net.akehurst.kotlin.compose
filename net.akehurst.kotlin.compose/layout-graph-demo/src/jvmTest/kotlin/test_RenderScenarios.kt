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
 * Renders every demo scenario to a PNG in build/scenario-renders, so the layout can be checked by eye.
 */
@OptIn(ExperimentalTestApi::class)
class test_RenderScenarios {

    private val outDir = File("build/scenario-renders").also { it.mkdirs() }

    @Test
    fun render_all_scenarios() {
        DemoScenarios.all.forEachIndexed { index, scenario ->
            runComposeUiTest {
                val state = scenario.toCompoundGraphState()
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
                ImageIO.write(image.toAwtImage(), "png", File(outDir, name))
            }
        }
    }
}
