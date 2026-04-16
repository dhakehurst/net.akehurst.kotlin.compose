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

// suppress these so we can access TextFieldScrollerPosition
@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.saket.extendedspans.ExtendedSpans
import me.saket.extendedspans.SquigglyUnderlineSpanPainter
import me.saket.extendedspans.drawBehind
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import net.akehurst.kotlin.compose.editor.api.*
import androidx.compose.foundation.text.input.TextFieldBuffer.ChangeList
import kotlin.comparisons.minOf
import kotlin.math.roundToInt
import kotlin.ranges.coerceIn

/*
 * KEY_PRESSED
 * KEY_TYPED
 * onValueChange
 * visualTransformation
 * visualTransformation
 * onTextLayout
 * onScroll
 * onScroll
 * KEY_RELEASED
 */

data class CursorDetails(
    val brush: SolidColor
) {
    var inView = true
    var position = 0
    var rect = Rect.Zero

    //val top get() = rect.topCenter
    //val bot get() = Offset(top.x, top.y + rect.height)
    fun updatePos(newPos: Int, inView: Boolean) {
        this.inView = inView
        this.position = newPos
    }

    fun updateRect(newRect: Rect) {
        this.rect = newRect
    }
}

data class CodeEditorState(
    val inputTextFieldState: State<TextFieldState>,
    val extendedSpans: ExtendedSpans,
    val inputScrollState: ScrollState,
//    val annotatedText: AnnotatedString,
    val giveFocus: Boolean,
    val lastTextLayoutResult: TextLayoutResult?,
    val viewFirstLine: Int,
    val viewLastLine: Int,
    val lineScrollOffset:Float,
    val lineTokens: Map<Int, List<EditorSegmentStyle>>,
//    val viewFirstLineStartTextPosition: Int,
//    val viewLastLineFinishTextPosition: Int,
//    val cursorDetails: CursorDetails,
//    val marginItemsVisible: List<MarginItem>,
//    val marginItemHovered: List<MarginItem>,
    val textMarkersVisible: List<TextMarkerDefault>,
)

class CodeEditorStateHolder(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
) : ComposeCodeEditor {

    companion object {
        /**
         * Find the start offset of the line containing [offset].
         */
        private fun CharSequence.lineStartOffset(offset: Int): Int {
            var i = offset.coerceIn(0, length)
            while (i > 0 && this[i - 1] != '\n') i--
            return i
        }

        /**
         * Find the end offset of the line containing [offset] (exclusive of newline).
         */
        private fun CharSequence.lineEndOffset(offset: Int): Int {
            var i = offset.coerceIn(0, length)
            while (i < length && this[i] != '\n') i++
            return i
        }

    }
    private val INDENT = "    " // 4 spaces TODO: make this configurable

    private val MARGIN_WIDTH = 20.dp
    private val _interactionSource = MutableInteractionSource()
    private var _giveFocus = false
    private var _isFocused = false
    private val _focusRequester by mutableStateOf(FocusRequester())

    private val _inputTextFieldState = mutableStateFlowHolderOf(TextFieldState(initialText))
    private val _inputRawText by derivedStateOf { snapshotFlow { _inputTextFieldState.value.text } }
    private val _lineStyles = mutableStateMapOf<Int, List<EditorSegmentStyle>>()

    internal var lastAnnotatedText: AnnotatedString? = null
    private val _inputScrollState by mutableStateOf(ScrollState(0))

    // so we can get the annotatedstring
    private var _lastTextLayoutResult: TextLayoutResult? = null

    private val _viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))
    private var _viewFirstLine by mutableStateOf(0)
    private var _viewLastLine by mutableStateOf(0)
    private var _lineScrollOffset by mutableStateOf(0f)
    private var _viewFirstLineStartTextPosition by mutableStateOf(0)
    private var _viewLastLineFinishTextPosition by mutableStateOf(0)

    private val _autocompleteState by mutableStateOf(
        AutocompleteStateCompose( //TODO: don't get 'value' of inputTextFieldState flow
            getText = { this._inputTextFieldState.value.text },
            getCursorPosition = { this._inputTextFieldState.value.selection.min },
            getMenuOffset = { cursorPos() },
            insertText = { offset, txt ->
                val start = _inputTextFieldState.value.selection.min
                val end = _inputTextFieldState.value.selection.max
//                val s = minOf(start,end)
//                val e = maxOf(start,end)
                this._inputTextFieldState.value.edit {
                    this.replace(start - offset, end, txt)
                }
            }
        )
    )

    private val _marginItemsStateHolder = MarginItemsStateHolder()

    private val _textMarkersState by mutableStateOf(TextMarkerState())
    private val _textMarkersVisible by derivedStateOf {
        _textMarkersState.markers.filter { _viewFirstLineStartTextPosition <= it.position && it.position <= _viewLastLineFinishTextPosition }
    }

    // val underlineAnimator = rememberSquigglyUnderlineAnimator()
    private val _extendedSpans = ExtendedSpans(ComposeEditorUtils.STRAIGHT, ComposeEditorUtils.SQUIGGLY)

    @Composable
    fun collectAsState(): CodeEditorState {
        return CodeEditorState(
            inputTextFieldState = this._inputTextFieldState.collectAsState(),
            extendedSpans = this._extendedSpans,
            inputScrollState = this._inputScrollState,
            giveFocus = this._giveFocus,
            lastTextLayoutResult = this._lastTextLayoutResult,
            viewFirstLine = this._viewFirstLine,
            viewLastLine = this._viewLastLine,
            lineScrollOffset = _lineScrollOffset,
            lineTokens = lineStyles,
            textMarkersVisible = _textMarkersVisible
        )
    }

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine:Int, lineScrollOffset:Float, textLayoutResult: TextLayoutResult?): MarginItemsState {
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

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        var handled = true
        when (ev.type) {
            KeyEventType.KeyDown -> when {
                this._autocompleteState.isVisible -> this._autocompleteState.handlePreviewKeyEvent(ev)
                ev.isShiftTab -> indentOrOutdentSelection(outdent = true)
                ev.isTab -> indentOrOutdentSelection(outdent = false)
                else -> when {
                    ev.isCtrlPressed || ev.isMetaPressed -> when {
                        ev.isCtrlSpace -> _autocompleteState.open()
                        else -> handled = false
                    }

                    else -> handled = false
                }
            }

            // KeyUp | KeyPressed
            else -> when {
            ev.isCtrlSpace ->   handled = true
                ev.isTab || ev.isShiftTab -> handled = true
                else -> handled = false
            }
        }
        return handled
    }

    private fun indentOrOutdentSelection(outdent: Boolean) {
        val tfs = _inputTextFieldState.value
        val text = tfs.text
        val selStart = tfs.selection.min
        val selEnd = tfs.selection.max
        val hasSelection = selStart != selEnd

        tfs.edit {
            if (hasSelection) {
                // If selEnd is at the very start of a line, exclude that line
                val effectiveSelEnd = if (selEnd > 0 && text.lineStartOffset(selEnd) == selEnd) selEnd - 1 else selEnd
                // Find all lines in the selection
                val firstLine = text.lineStartOffset(selStart)
                val lastLine = text.lineEndOffset(effectiveSelEnd)

                // Process lines from end to start so offsets remain valid
                var pos = lastLine
                while (pos >= firstLine) {
                    val lineStart = text.lineStartOffset(pos)
                    if (outdent) {
                        // Remove up to INDENT.length leading spaces
                        var removeCount = 0
                        while (removeCount < INDENT.length && lineStart + removeCount < length && charAt(lineStart + removeCount) == ' ') {
                            removeCount++
                        }
                        if (removeCount > 0) {
                            replace(lineStart, lineStart + removeCount, "")
                        }
                    } else {
                        replace(lineStart, lineStart, INDENT)
                    }
                    // Move to previous line
                    pos = if (lineStart > 0) lineStart - 1 else break
                }
            } else {
                // No selection
                val lineStart = text.lineStartOffset(selStart)
                if (outdent) {
                    // Remove up to INDENT.length leading spaces from current line
                    var removeCount = 0
                    while (removeCount < INDENT.length && lineStart + removeCount < length && charAt(lineStart + removeCount) == ' ') {
                        removeCount++
                    }
                    if (removeCount > 0) {
                        replace(lineStart, lineStart + removeCount, "")
                    }
                } else {
                    // Insert indent at cursor position
                    replace(selStart, selStart, INDENT)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun handleKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        return when (ev.type) {
            // KeyDown | KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> true
//                ev.isUndo -> {
//                    inputTextFieldState.undoState.undo()
//                    true
//                }
//
//                ev.isRedo -> {
//                    inputTextFieldState.undoState.redo()
//                    true
//                }

                else -> false
            }
        }
    }

    // useful helpers
    fun setNewText(text: String) {
        this._inputTextFieldState.update {
            TextFieldState(text, it.selection)
        }
        //this.inputTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    fun onInputScroll(i: ScrollState) {
        if (null != _lastTextLayoutResult) {
            updateViewDetails(_lastTextLayoutResult!!)
        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        _autocompleteState.close()
        updateViewDetails(textLayoutResult)
        _lastTextLayoutResult = textLayoutResult
        _extendedSpans.onTextLayout(textLayoutResult)
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = _inputScrollState.value.toFloat()
        val len = _inputScrollState.viewportSize
        _viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        _viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len-1)
        _viewFirstLineStartTextPosition = textLayoutResult.getLineStart(_viewFirstLine)
        _viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(_viewLastLine)
        val topOfFirstLine = textLayoutResult.getLineTop(_viewFirstLine)
        _lineScrollOffset = st - topOfFirstLine
    }

    fun cursorPos(): IntOffset {
        // update the drawn cursor position
        val sel = _inputTextFieldState.value.selection
        val tlr = _lastTextLayoutResult
        return if (null != tlr) {
            val txtLen = tlr.layoutInput.text.length
            val selStart = sel.start.coerceIn(0, txtLen)
            val currentLine = tlr.getLineForOffset(selStart)
            val lineBot = tlr.getLineBottom(currentLine).roundToInt()
            val lineEnd = tlr.getLineEnd(currentLine, true)
            val cursOffset = tlr.getHorizontalPosition(minOf(selStart, lineEnd), true).roundToInt()
            val scrollOffset = _inputScrollState.value
            //println("currentLine=${currentLine}, lineBot=${lineBot}, cursOffset=$cursOffset,  scrollOffset=$scrollOffset")
            return IntOffset(cursOffset, lineBot - scrollOffset)
        } else {
            IntOffset(0, 0)
        }
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

    override var annotatedText: AnnotatedString
        get() = this.lastAnnotatedText ?: AnnotatedString("")
        set(value) {
            this.lastAnnotatedText = value
            this.setNewText(value.text)
        }

    override var onTextChange: (CharSequence) -> Unit = {}

    override val marginItems: List<MarginItem> get() = _marginItemsStateHolder.value

    override val autocomplete: AutocompleteState get() = this._autocompleteState
    override var requestAutocompleteSuggestions: AutocompleteFunction
        get() = _autocompleteState.requestAutocompleteSuggestions
        set(value) {
            _autocompleteState.requestAutocompleteSuggestions = value
        }

    override fun focus() {
        this._giveFocus = true
    }

    override fun clearTextMarkers() {
        this._textMarkersState.clear()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        this._textMarkersState.addMarker(position, length, style, decoration)
    }

    override fun clearMarginItems() {
        this._marginItemsStateHolder.clear()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) {
        this._marginItemsStateHolder.addAnnotation(lineNumber, kind, text, icon, color)
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditorView(
    editorState: CodeEditorStateHolder = CodeEditorStateHolder(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
    ),
    textStyle: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    autocompleteModifier: Modifier = Modifier,
    marginItemHoverModifier: Modifier = Modifier,
) {
    val state: CodeEditorState = editorState.collectAsState()
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
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            textEditor(
                state = state,
                textStyle = textStyle,
                outputTransformation = {
                    ComposeEditorUtils.annotateTextFieldBuffer(
                        this,
                        state.viewFirstLine,
                        state.viewLastLine,
                        state.lineTokens,
                        state.textMarkersVisible,
                        { editorState.lastAnnotatedText = it}
                    )
                },
                onTextLayout = { r ->
                    r.invoke()?.let { editorState.onInputTextLayout(it) }
                },
                handlePreviewKeyEvent = { ev -> editorState.handlePreviewKeyEvent(ev) },
                handleKeyEvent = { ev -> editorState.handleKeyEvent(ev) },
                onTextChange = { editorState.onTextChange.invoke(it) },
                onInputScroll = { editorState.onInputScroll(it) },
                onFocusChanged = { editorState._isFocused = it; editorState._giveFocus = false }
            )

            AutocompletePopup2(
                state = editorState._autocompleteState,
                modifier = autocompleteModifier,
            )
        }
    }
}

@Composable
fun annotationMargin(state: MarginItemsState, marginItemHoverModifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .width(state.marginWidth)
            .fillMaxHeight()
//                .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .wrapContentWidth(unbounded = true, align = Alignment.Start)
            .zIndex(1f),
    ) {
        state.visibleItems.forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            Icon(
                imageVector = item.item.icon,
                contentDescription = item.item.text,
                tint = item.item.color,
                modifier = Modifier.width(state.marginWidth)
                    .offset(y = with(LocalDensity.current) {item.offsetFromTopOfViewport.toDp()})
                    .hoverable(interactionSource)
            )
            val isHovered = interactionSource.collectIsHoveredAsState().value
            if (isHovered) {
                Text(
                    text = item.item.text,
                    modifier = marginItemHoverModifier
                        .offset(
                            y = with(LocalDensity.current) {item.detailOffsetFromTopOfViewport.toDp()},
                            x = state.marginWidth / 2
                        )
                        .background(color = MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        .hoverable(interactionSource)
                )
            }
        }
    }
}

@Composable
fun textEditor(
    state: CodeEditorState,
    outputTransformation: OutputTransformation,
    textStyle: TextStyle,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit),
    handlePreviewKeyEvent: (KeyEvent) -> Boolean,
    handleKeyEvent: (KeyEvent) -> Boolean,
    onTextChange: (CharSequence) -> Unit,
    onInputScroll: (ScrollState) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester by remember { mutableStateOf(FocusRequester()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
//                .background(color = MaterialTheme.colorScheme.surface),
    ) {

        BasicTextField(
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            textStyle = textStyle,
            state = state.inputTextFieldState.value,
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) }
                .onKeyEvent { ev -> handleKeyEvent(ev) }
                .focusRequester(focusRequester)
                .drawBehind(state.extendedSpans, state.inputScrollState.value.toFloat()),
            onTextLayout = onTextLayout,
            scrollState = state.inputScrollState,
            interactionSource = interactionSource,
            outputTransformation = outputTransformation,
        )

        // to invoke the 'onTextChange' callback when text changes
        LaunchedEffect(state.inputTextFieldState.value.text) {
            snapshotFlow { state.inputTextFieldState.value.text }.collect { scope.launch { onTextChange(it) } }
        }

        LaunchedEffect(state.inputScrollState.value) {
            snapshotFlow { state.inputScrollState }.collect { onInputScroll(it) }
        }

        val isFocused by interactionSource.collectIsFocusedAsState()
        LaunchedEffect(isFocused) { onFocusChanged.invoke(isFocused) }

        SideEffect {
            if (state.giveFocus) {
                focusRequester.requestFocus()
            }
        }

    }
}
