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
import net.akehurst.kotlin.compose.editor.CodeViewCore
import net.akehurst.kotlin.compose.editor.ComposeEditorUtils
import net.akehurst.kotlin.compose.editor.MarginItemListState
import net.akehurst.kotlin.compose.editor.TextMarkerDefault
import net.akehurst.kotlin.compose.editor.annotationMargin
import net.akehurst.kotlin.compose.editor.api.*

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

    private val _core = CodeViewCore(initialText)

    internal var lastAnnotatedText: AnnotatedString?
        get() = _core.lastAnnotatedText
        set(value) {
            _core.lastAnnotatedText = value
        }

    private val _textMarkersVisible by derivedStateOf {
        _core.textMarkersState.markers.filter { _core.isInView(it.position) }
    }

    @Composable
    fun collectAsState(): CodeViewerState = _core.collectViewerState(_textMarkersVisible)

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine: Int, lineScrollOffset: Float, textLayoutResult: TextLayoutResult?): MarginItemListState =
        _core.collectVisibleMarginItemsAsState(viewFirstLine, viewLastLine, lineScrollOffset, textLayoutResult)

    // useful helpers
    fun setNewText(text: String) = _core.setNewText(text)

    fun onInputScroll(i: ScrollState) = _core.onInputScroll()

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) = _core.onInputTextLayout(textLayoutResult)

    fun setAllLineStyles(value: Map<Int, List<EditorSegmentStyle>>) = _core.setAllLineStyles(value)

    fun setLineStyles(lineNumber: Int, styles: List<EditorSegmentStyle>) = _core.setLineStyles(lineNumber, styles)

    // --- ComposeCodeViewer ---

    override var rawText: String
        get() = _core.rawText
        set(value) {
            _core.rawText = value
        }

    override var lineStyles: Map<Int, List<EditorSegmentStyle>>
        get() = _core.lineStyles
        set(value) {
            _core.setAllLineStyles(value)
        }

    override var annotatedText: AnnotatedString
        get() = _core.annotatedText
        set(value) {
            _core.annotatedText = value
        }

    override val marginItems: List<net.akehurst.kotlin.compose.editor.api.MarginItem> get() = _core.marginItems

    override fun clearTextMarkers() {
        _core.clearTextMarkers()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        _core.addTextMarker(position, length, style, decoration)
    }

    override fun clearMarginItems() {
        _core.clearMarginItems()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
        _core.addMarginItem(lineNumber, kind, text, icon, color)
    }

}

/**
 * @param editorState the viewer state; when null, a state is created and remembered by this composable
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeViewerView(
    editorState: CodeViewerStateHolder? = null,
    textStyle: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    autocompleteModifier: Modifier = Modifier,
    marginItemHoverModifier: Modifier = Modifier,
) {
    val defaultTextColor = MaterialTheme.colorScheme.onBackground
    val editorState = editorState ?: remember {
        CodeViewerStateHolder(
            initialText = "",
            defaultTextStyle = SpanStyle(color = defaultTextColor)
        )
    }
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
                ComposeEditorUtils.annotateTextFieldBuffer(this, state, null,{ editorState.lastAnnotatedText = it })
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

        val scrollState = state.inputScrollState
        val currentOnInputScroll by rememberUpdatedState(onInputScroll)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { currentOnInputScroll(scrollState) }
        }

    }
}
