/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle
import net.akehurst.kotlin.compose.viewer.CodeViewerStateHolder
import net.akehurst.kotlin.compose.viewer.CodeViewerView
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders text markers and checks that their colour reaches the screen:
 * underlines (straight and squiggly) are drawn in the marker colour, and a marker without
 * decoration (e.g. a find match) applies its style.
 */
@OptIn(ExperimentalTestApi::class)
class test_TextMarkers {

    companion object {
        const val TEXT = "first line\nsome error here\nlast line"
        val MARKER_COLOR = Color(0xFFFF0000)
    }

    private fun ImageBitmap.countPixels(color: Color): Int {
        val pixels = this.toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            if (pixels[x, y] == color) count++
        }
        return count
    }

    private fun renderEditor(decoration: TextDecorationStyle, style: SpanStyle): ImageBitmap {
        var image: ImageBitmap? = null
        runComposeUiTest {
            val state = CodeEditorStateHolder(initialText = TEXT)
            val pos = TEXT.indexOf("error")
            state.addTextMarker(pos, "error".length, style, decoration)
            setContent {
                Box(Modifier.size(400.dp, 200.dp).background(Color.White).testTag("editor")) {
                    CodeEditorView(editorState = state)
                }
            }
            waitForIdle()
            image = onNodeWithTag("editor").captureToImage()
        }
        return image!!
    }

    private fun renderViewer(decoration: TextDecorationStyle, style: SpanStyle): ImageBitmap {
        var image: ImageBitmap? = null
        runComposeUiTest {
            val state = CodeViewerStateHolder(initialText = TEXT)
            val pos = TEXT.indexOf("error")
            state.addTextMarker(pos, "error".length, style, decoration)
            setContent {
                Box(Modifier.size(400.dp, 200.dp).background(Color.White).testTag("viewer")) {
                    CodeViewerView(editorState = state)
                }
            }
            waitForIdle()
            image = onNodeWithTag("viewer").captureToImage()
        }
        return image!!
    }

    @Test
    fun editor_squiggly_underline_is_drawn() {
        val count = renderEditor(TextDecorationStyle.SQUIGGLY, SpanStyle(color = MARKER_COLOR)).countPixels(MARKER_COLOR)
        assertTrue(count > 20, "expected a red squiggly underline, found $count red pixels")
    }

    @Test
    fun editor_straight_underline_is_drawn() {
        val count = renderEditor(TextDecorationStyle.STRAIGHT, SpanStyle(color = MARKER_COLOR)).countPixels(MARKER_COLOR)
        assertTrue(count > 20, "expected a red straight underline, found $count red pixels")
    }

    @Test
    fun editor_marker_without_decoration_applies_its_style() {
        val count = renderEditor(TextDecorationStyle.NONE, SpanStyle(background = MARKER_COLOR)).countPixels(MARKER_COLOR)
        assertTrue(count > 100, "expected a red background behind the marked text, found $count red pixels")
    }

    @Test
    fun viewer_squiggly_underline_is_drawn() {
        val count = renderViewer(TextDecorationStyle.SQUIGGLY, SpanStyle(color = MARKER_COLOR)).countPixels(MARKER_COLOR)
        assertTrue(count > 20, "expected a red squiggly underline, found $count red pixels")
    }
}
