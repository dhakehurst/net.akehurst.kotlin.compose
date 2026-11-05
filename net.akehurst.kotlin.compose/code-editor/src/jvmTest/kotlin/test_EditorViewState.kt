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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.editor.api.simple.EditorSegmentStyleSimple
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class test_EditorViewState {

    companion object {
        val MARKER_COLOR = Color(0xFFFF0000)
    }

    @Test
    fun marker_that_starts_above_the_view_is_shown() = runComposeUiTest {
        val text = (0 until 60).joinToString("\n") { "line $it" }
        val state = CodeEditorStateHolder(initialText = text)
        // one marker from the start of line 0 to the end of line 40
        val end = text.indexOf("line 40") + "line 40".length
        state.addTextMarker(0, end, SpanStyle(background = MARKER_COLOR), TextDecorationStyle.NONE)
        var scrollState: ScrollState? = null
        setContent {
            scrollState = state.collectAsState().viewerState.inputScrollState
            Box(Modifier.size(400.dp, 200.dp).background(Color.White).testTag("editor")) {
                CodeEditorView(editorState = state)
            }
        }
        waitForIdle()
        // scroll so that line 0 (where the marker starts) is out of view, but lines up to 40 are still marked
        runOnUiThread { scrollState!!.dispatchRawDelta(scrollState!!.maxValue / 3f) }
        waitForIdle()
        assertTrue(scrollState!!.value > 0, "the editor did not scroll")

        val pixels = onNodeWithTag("editor").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (pixels[x, y] == MARKER_COLOR) count++
        assertTrue(count > 100, "expected the marked lines in view to be highlighted, found $count red pixels")
    }

    @Test
    fun autocomplete_stays_open_when_markers_change() = runComposeUiTest {
        val state = CodeEditorStateHolder(initialText = "some text\nmore text")
        setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                CodeEditorView(editorState = state)
            }
        }
        waitForIdle()
        runOnUiThread { state._autocompleteState.open() }
        waitForIdle()
        // a new marker restyles the text, which lays it out again
        runOnUiThread { state.addTextMarker(0, 4, SpanStyle(color = MARKER_COLOR), TextDecorationStyle.SQUIGGLY) }
        waitForIdle()
        assertTrue(state._autocompleteState.isVisible, "autocomplete was closed by a marker change")
    }

    @Test
    fun autocomplete_closes_when_the_text_changes() = runComposeUiTest {
        val state = CodeEditorStateHolder(initialText = "some text\nmore text")
        setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                CodeEditorView(editorState = state)
            }
        }
        waitForIdle()
        runOnUiThread { state._autocompleteState.open() }
        waitForIdle()
        runOnUiThread { state.rawText = "some text\nmore textx" }
        waitForIdle()
        assertFalse(state._autocompleteState.isVisible, "autocomplete stayed open after the text changed")
    }

    @Test
    fun annotated_text_follows_marker_changes() = runComposeUiTest {
        val state = CodeEditorStateHolder(initialText = "some error here")
        setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                CodeEditorView(editorState = state)
            }
        }
        waitForIdle()
        runOnUiThread { state.addTextMarker(5, 5, SpanStyle(background = MARKER_COLOR), TextDecorationStyle.NONE) }
        waitForIdle()
        val styled = state.annotatedText.spanStyles.filter { it.item.background == MARKER_COLOR }
        assertEquals(listOf(5 to 10), styled.map { it.start to it.end }, "annotatedText does not include the new marker")
    }
    @Test
    fun line_styles_follow_wrapped_lines_when_scrolled() = runComposeUiTest {
        // every line is long enough to wrap onto several rows of a narrow editor
        val lines = (0 until 40).map { n -> "line $n " + "word ".repeat(30) }
        val text = lines.joinToString("\n")
        val state = CodeEditorStateHolder(initialText = text)
        // colour the whole of the last line red, and nothing else
        val last = lines.size - 1
        state.lineStyles = mapOf(last to listOf(EditorSegmentStyleSimple(0, lines[last].length, SpanStyle(color = MARKER_COLOR))))
        var scrollState: ScrollState? = null
        var viewerState: net.akehurst.kotlin.compose.viewer.CodeViewerState? = null
        setContent {
            viewerState = state.collectAsState().viewerState
            scrollState = viewerState!!.inputScrollState
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag("editor")) {
                CodeEditorView(editorState = state)
            }
        }
        waitForIdle()
        runOnUiThread { scrollState!!.dispatchRawDelta(scrollState!!.maxValue.toFloat()) }
        waitForIdle()
        assertTrue(scrollState!!.value > 0, "the editor did not scroll")
        val layout = viewerState!!
        assertTrue(layout.viewFirstLine > layout.viewFirstTextLine, "lines did not wrap, so the test does not check anything")

        val lastStart = text.length - lines[last].length
        val styled = state.annotatedText.spanStyles.filter { it.item.color == MARKER_COLOR }
        assertEquals(listOf(lastStart to text.length), styled.map { it.start to it.end }, "the last line's style is not on the last line")

        val pixels = onNodeWithTag("editor").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            if (c.red > 0.6f && c.green < 0.4f && c.blue < 0.4f) count++
        }
        assertTrue(count > 100, "expected the last line, which is in view, to be drawn red, found $count red pixels")
    }
}
