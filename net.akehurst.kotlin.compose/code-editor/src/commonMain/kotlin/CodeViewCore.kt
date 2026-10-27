/**
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import me.saket.extendedspans.ExtendedSpans
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.MarginItem
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle
import net.akehurst.kotlin.compose.viewer.CodeViewerState

/**
 * State and behaviour shared by CodeViewerStateHolder and CodeEditorStateHolder:
 * the text, scrolling, which lines are in view, line styles, text markers and margin items.
 */
internal class CodeViewCore(initialText: String) {

    companion object {
        private val MARGIN_WIDTH = 20.dp
    }

    val inputTextFieldState = mutableStateFlowHolderOf(TextFieldState(initialText))
    val lineStyles = mutableStateMapOf<Int, List<EditorSegmentStyle>>()

    var lastAnnotatedText: AnnotatedString? = null
    val inputScrollState = ScrollState(0)

    // so we can get the annotatedstring
    var lastTextLayoutResult: TextLayoutResult? = null
        private set

    var viewFirstLine by mutableStateOf(0)
        private set
    var viewLastLine by mutableStateOf(0)
        private set
    var lineScrollOffset by mutableStateOf(0f)
        private set
    private var viewFirstLineStartTextPosition by mutableStateOf(0)
    private var viewLastLineFinishTextPosition by mutableStateOf(0)

    val marginItemsStateHolder = MarginItemsStateHolder()
    val textMarkersState = TextMarkerState()
    val extendedSpans = ExtendedSpans(ComposeEditorUtils.STRAIGHT, ComposeEditorUtils.SQUIGGLY)

    /** True when the text [position] is on one of the lines currently in view. */
    fun isInView(position: Int): Boolean = position in viewFirstLineStartTextPosition..viewLastLineFinishTextPosition

    @Composable
    fun collectViewerState(textMarkersVisible: List<TextMarkerDefault>): CodeViewerState = CodeViewerState(
        inputTextFieldState = inputTextFieldState.collectAsState(),
        extendedSpans = extendedSpans,
        inputScrollState = inputScrollState,
        lastTextLayoutResult = lastTextLayoutResult,
        viewFirstLine = viewFirstLine,
        viewLastLine = viewLastLine,
        lineScrollOffset = lineScrollOffset,
        lineTokens = lineStyles,
        textMarkersVisible = textMarkersVisible,
    )

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine: Int, lineScrollOffset: Float, textLayoutResult: TextLayoutResult?): MarginItemListState {
        val visibleItems = marginItemsStateHolder.stateFlow.collectAsState()
        return MarginItemListState(
            marginWidth = MARGIN_WIDTH,
            visibleItems = visibleItems.value.map { item ->
                val layoutLine = textLayoutResult?.let { tlr -> ComposeEditorUtils.textLineToLayoutLine(item.lineNumber, tlr) } ?: item.lineNumber
                val offsetFromTopOfViewport = textLayoutResult?.let { tlr -> ComposeEditorUtils.offsetFromTopOfViewport(layoutLine, viewFirstLine, viewLastLine, lineScrollOffset, tlr) } ?: 0f
                val detailOffset = textLayoutResult?.let { tlr -> offsetFromTopOfViewport + ComposeEditorUtils.lineHeight(tlr, layoutLine) / 2 } ?: 0f
                MarginItemState(item, offsetFromTopOfViewport, detailOffset)
            }
        )
    }

    fun setNewText(text: String) {
        inputTextFieldState.update {
            TextFieldState(text, it.selection)
        }
    }

    fun onInputScroll() {
        lastTextLayoutResult?.let { updateViewDetails(it) }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        updateViewDetails(textLayoutResult)
        lastTextLayoutResult = textLayoutResult
        extendedSpans.onTextLayout(textLayoutResult)
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = inputScrollState.value.toFloat()
        val len = inputScrollState.viewportSize
        viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len - 1)
        viewFirstLineStartTextPosition = textLayoutResult.getLineStart(viewFirstLine)
        viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(viewLastLine)
        val topOfFirstLine = textLayoutResult.getLineTop(viewFirstLine)
        lineScrollOffset = st - topOfFirstLine
    }

    fun setAllLineStyles(value: Map<Int, List<EditorSegmentStyle>>) {
        lineStyles.clear()
        lineStyles.putAll(value)
    }

    fun setLineStyles(lineNumber: Int, styles: List<EditorSegmentStyle>) {
        lineStyles[lineNumber] = styles
    }

    var rawText: String
        get() = inputTextFieldState.value.text.toString()
        set(value) {
            setNewText(value)
        }

    var annotatedText: AnnotatedString
        get() = lastAnnotatedText ?: AnnotatedString("")
        set(value) {
            lastAnnotatedText = value
            setNewText(value.text)
        }

    val marginItems: List<MarginItem> get() = marginItemsStateHolder.value

    fun clearTextMarkers() = textMarkersState.clear()

    fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) =
        textMarkersState.addMarker(position, length, style, decoration)

    fun clearMarginItems() = marginItemsStateHolder.clear()

    fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) =
        marginItemsStateHolder.addAnnotation(lineNumber, kind, text, icon, color)
}
