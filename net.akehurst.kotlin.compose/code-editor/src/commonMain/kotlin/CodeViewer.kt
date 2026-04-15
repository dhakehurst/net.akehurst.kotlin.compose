/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

// suppress these so we can access TextFieldScrollerPosition
@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.viewer

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import me.saket.extendedspans.drawBehind
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import net.akehurst.kotlin.compose.editor.ComposeEditorUtils
import net.akehurst.kotlin.compose.editor.MarginItemState
import net.akehurst.kotlin.compose.editor.MarginItemsState
import net.akehurst.kotlin.compose.editor.MarginItemsStateHolder
import net.akehurst.kotlin.compose.editor.TextMarkerDefault
import net.akehurst.kotlin.compose.editor.TextMarkerState
import net.akehurst.kotlin.compose.editor.annotationMargin
import net.akehurst.kotlin.compose.editor.api.*
import kotlin.collections.set

data class CodeViewerState(
    val inputTextFieldState: State<TextFieldState>,
    val extendedSpans: me.saket.extendedspans.ExtendedSpans,
    val inputScrollState: ScrollState,
    val lastTextLayoutResult: TextLayoutResult?,
    val viewFirstLine: Int,
    val viewLastLine: Int,
    val lineScrollOffset: Float,
    val lineTokens: Map<Int, List<EditorSegmentStyle>>,
    val textMarkersVisible: List<TextMarkerDefault>,
)

class CodeViewerStateHolder(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
) : ComposeCodeViewer {

    private val MARGIN_WIDTH = 20.dp
    private val _interactionSource = MutableInteractionSource()

    private val _inputTextFieldState = mutableStateFlowHolderOf(TextFieldState(initialText))
    private val _inputRawText by derivedStateOf { snapshotFlow { _inputTextFieldState.value.text } }
    private val _lineStyles = androidx.compose.runtime.mutableStateMapOf<Int, List<EditorSegmentStyle>>()

    internal var lastAnnotatedText: AnnotatedString? = null
    private val _inputScrollState by mutableStateOf(ScrollState(0))

    // so we can get the annotatedstring
    private var _lastTextLayoutResult: TextLayoutResult? = null

    private var _viewFirstLine by mutableStateOf(0)
    private var _viewLastLine by mutableStateOf(0)
    private var _lineScrollOffset by mutableStateOf(0f)
    private var _viewFirstLineStartTextPosition by mutableStateOf(0)
    private var _viewLastLineFinishTextPosition by mutableStateOf(0)

    private val _marginItemsStateHolder = MarginItemsStateHolder()

    private val _textMarkersState by mutableStateOf(TextMarkerState())
    private val _textMarkersVisible by derivedStateOf {
        _textMarkersState.markers.filter { _viewFirstLineStartTextPosition <= it.position && it.position <= _viewLastLineFinishTextPosition }
    }

    private val _extendedSpans = me.saket.extendedspans.ExtendedSpans(ComposeEditorUtils.STRAIGHT, ComposeEditorUtils.SQUIGGLY)

    @Composable
    fun collectAsState(): CodeViewerState {
        return CodeViewerState(
            inputTextFieldState = this._inputTextFieldState.collectAsState(),
            extendedSpans = this._extendedSpans,
            inputScrollState = this._inputScrollState,
            lastTextLayoutResult = this._lastTextLayoutResult,
            viewFirstLine = this._viewFirstLine,
            viewLastLine = this._viewLastLine,
            lineScrollOffset = _lineScrollOffset,
            lineTokens = lineStyles,
            textMarkersVisible = _textMarkersVisible
        )
    }

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine: Int, lineScrollOffset: Float, textLayoutResult: TextLayoutResult?): MarginItemsState {
        val visibleItems = _marginItemsStateHolder.stateFlow.collectAsState()
        return MarginItemsState(
            marginWidth = MARGIN_WIDTH,
            visibleItems = visibleItems.value.map { item ->
                val layoutLine = textLayoutResult?.let { tlr -> ComposeEditorUtils.textLineToLayoutLine(item.lineNumber, tlr) } ?: item.lineNumber
                val offsetFromTopOfViewport = textLayoutResult?.let { tlr -> ComposeEditorUtils.offsetFromTopOfViewport(layoutLine, viewFirstLine, viewLastLine, lineScrollOffset, tlr) } ?: 0f
                val detailOffset = textLayoutResult?.let { tlr -> offsetFromTopOfViewport + ComposeEditorUtils.lineHeight(tlr, layoutLine) / 2 } ?: 0f
                MarginItemState(item, offsetFromTopOfViewport, detailOffset)
            }
        )
    }

    // useful helpers
    fun setNewText(text: String) {
        this._inputTextFieldState.update {
            TextFieldState(text, it.selection)
        }
    }

    fun onInputScroll(i: ScrollState) {
        if (null != _lastTextLayoutResult) {
            updateViewDetails(_lastTextLayoutResult!!)
        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        updateViewDetails(textLayoutResult)
        _lastTextLayoutResult = textLayoutResult
        _extendedSpans.onTextLayout(textLayoutResult)
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = _inputScrollState.value.toFloat()
        val len = _inputScrollState.viewportSize
        _viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        _viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len - 1)
        _viewFirstLineStartTextPosition = textLayoutResult.getLineStart(_viewFirstLine)
        _viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(_viewLastLine)
        val topOfFirstLine = textLayoutResult.getLineTop(_viewFirstLine)
        _lineScrollOffset = st - topOfFirstLine
    }

    fun setAllLineStyles(value: Map<Int, List<EditorSegmentStyle>>) {
        _lineStyles.clear()
        _lineStyles.putAll(value)
    }

    fun setLineStyles(lineNumber: Int, styles: List<EditorSegmentStyle>) {
        _lineStyles[lineNumber] = styles
    }

    // --- ComposeCodeEditor ---

    override var rawText: String
        get() = this._inputTextFieldState.value.text.toString()
        set(value) {
            this.setNewText(value)
        }

    override var lineStyles: Map<Int, List<EditorSegmentStyle>>
        get() = _lineStyles
        set(value) {
            setAllLineStyles(value)
        }

    override var annotatedText: androidx.compose.ui.text.AnnotatedString
        get() = this.lastAnnotatedText ?: androidx.compose.ui.text.AnnotatedString("")
        set(value) {
            this.lastAnnotatedText = value
            this.setNewText(value.text)
        }

    override val marginItems: List<net.akehurst.kotlin.compose.editor.api.MarginItem> get() = _marginItemsStateHolder.value

    override fun clearTextMarkers() {
        this._textMarkersState.clear()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        this._textMarkersState.addMarker(position, length, style, decoration)
    }

    override fun clearMarginItems() {
        this._marginItemsStateHolder.clear()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
        this._marginItemsStateHolder.addAnnotation(lineNumber, kind, text, icon, color)
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeViewerView(
    editorState: CodeViewerStateHolder = CodeViewerStateHolder(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
    ),
    textStyle: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    autocompleteModifier: Modifier = Modifier,
    marginItemHoverModifier: Modifier = Modifier,
) {
    val state: CodeViewerState = editorState.collectAsState()
    val marginItemsState = editorState.collectVisibleMarginItemsAsState( //TODO: maybe pass the CodeEditorState !
        state.viewFirstLine,
        state.viewLastLine,
        state.lineScrollOffset,
        state.lastTextLayoutResult
    )

    Row(
        modifier = modifier
    ) {
        annotationMargin(marginItemsState, marginItemHoverModifier)
        BasicCodeViewer(
            state = state,
            outputTransformation = {
                ComposeEditorUtils.annotateTextFieldBuffer(
                    this,
                    state.viewFirstLine,
                    state.viewLastLine,
                    state.lineTokens,
                    state.textMarkersVisible,
                    { editorState.lastAnnotatedText = it }
                )
            },
            textStyle = textStyle,
            onTextLayout = { r ->
                r.invoke()?.let { editorState.onInputTextLayout(it) }
            },
            onInputScroll = { editorState.onInputScroll(it) },
        )
    }
}

@Composable
fun BasicCodeViewer(
    state: CodeViewerState,
    outputTransformation: OutputTransformation,
    textStyle: TextStyle,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit),
    onInputScroll: (ScrollState) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        BasicTextField(
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            textStyle = textStyle,
            state = state.inputTextFieldState.value,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind(state.extendedSpans, state.inputScrollState.value.toFloat()),
            onTextLayout = onTextLayout,
            scrollState = state.inputScrollState,
            outputTransformation = outputTransformation,
        )

        LaunchedEffect(state.inputScrollState.value) {
            snapshotFlow { state.inputScrollState }.collect { onInputScroll(it) }
        }

    }
}
