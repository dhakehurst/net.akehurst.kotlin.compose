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

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.saket.extendedspans.ExtendedSpans
import me.saket.extendedspans.drawBehind
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import net.akehurst.kotlin.compose.editor.api.*
import net.akehurst.kotlin.compose.viewer.CodeViewerState
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

data class GhostTextState(
    val ghostText: String?,
    val ghostTokens: Map<Int, List<EditorSegmentStyle>>,
    val ghostPosition: Int,
    val isGhostVisible: Boolean,
    val replaceWholeText: Boolean
) {
    val show get() = isGhostVisible && ghostText.isNullOrEmpty().not()
}

data class CodeEditorState(
    val viewerState: CodeViewerState,
    val ghostTextState: GhostTextState?,
    val giveFocus: Boolean,
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

    private var _giveFocus by mutableStateOf(false)

    private val _core = CodeViewCore(initialText)
    private val _inputTextFieldState get() = _core.inputTextFieldState
    private val _inputScrollState get() = _core.inputScrollState
    private val _lastTextLayoutResult get() = _core.lastTextLayoutResult

    internal var lastAnnotatedText: AnnotatedString?
        get() = _core.lastAnnotatedText
        set(value) {
            _core.lastAnnotatedText = value
        }

    private val _viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))

    internal val _autocompleteState by mutableStateOf(
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
                    replace(start - offset, end, txt)
                }
            }
        )
    )

    internal var _composableScope: CoroutineScope? = null

    internal val _findReplaceState = FindReplaceStateCompose(
        getText = { this._inputTextFieldState.value.text },
        replaceInText = { start, end, replacement ->
            this._inputTextFieldState.value.edit { this.replace(start, end, replacement) }
        },
        replaceRangesInText = { ranges, replacement ->
            // one edit, so replace-all is a single undo step; reverse order keeps earlier offsets valid
            this._inputTextFieldState.value.edit {
                ranges.sortedByDescending { it.first }.forEach { range -> this.replace(range.first, range.last + 1, replacement) }
            }
        },
        scrollToOffset = { offset ->
            _composableScope?.launch {
                val tlr = _lastTextLayoutResult
                if (null != tlr) {
                    val line = tlr.getLineForOffset(offset.coerceIn(0, tlr.layoutInput.text.length))
                    val lineTop = tlr.getLineTop(line).roundToInt()
                    _inputScrollState.animateScrollTo(lineTop)
                }
            }
        },
    )

    private val _textMarkersVisible by derivedStateOf {
        val userMarkers = _core.textMarkersState.markers.filter { _core.isInView(it.position) }
        val findMarkers = _findReplaceState.getMatchMarkers().filter { _core.isInView(it.position) }
        findMarkers + userMarkers
    }


    @Composable
    fun collectAsState(): CodeEditorState {
        val currentTextFieldState = this._inputTextFieldState.collectAsState().value
        val selection = currentTextFieldState.selection

        // Detect if the user is currently dragging to select a range of text
        val isSelectingRange = selection.min != selection.max

        val ghostTextState = if (isSelectingRange) {
            //  If selecting text, skip reading selection coordinates dynamically.
            // This stops the recomposition cascade and allows mouse drag selection to function naturally.
            null
        } else {
            // Only track cursor changes and evaluate ghost code when the caret is resting at a single point
            val currentText = currentTextFieldState.text.toString()
            val currentCursor = selection.max
            ghostText.invoke(currentText, currentCursor)
        }

        return CodeEditorState(
            viewerState = _core.collectViewerState(_textMarkersVisible),
            giveFocus = this._giveFocus,
            ghostTextState = ghostTextState
        )
    }

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine: Int, lineScrollOffset: Float, textLayoutResult: TextLayoutResult?): MarginItemListState =
        _core.collectVisibleMarginItemsAsState(viewFirstLine, viewLastLine, lineScrollOffset, textLayoutResult)

    var onGhostTextAccepted: ((GhostTextState) -> Unit)? = null

    fun acceptGhostText(ghostState: GhostTextState) {
        val txt = ghostState.ghostText ?: return
        _inputTextFieldState.value.edit {
            if (ghostState.replaceWholeText) {
                // Overwrite the entire file content
                replace(0, length, txt)
            } else {
                // Insert standard context inline right at the target offset
                replace(ghostState.ghostPosition, ghostState.ghostPosition, txt)
            }
        }
        onGhostTextAccepted?.invoke(ghostState)
    }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        val tfs = _inputTextFieldState.value
        val currentText = tfs.text.toString()
        val currentCursor = tfs.selection.max

        val currentGhostState = ghostText.invoke(currentText, currentCursor)
        val isGhostActive = currentGhostState != null &&
                currentGhostState.isGhostVisible &&
                !currentGhostState.ghostText.isNullOrEmpty()

        var handled = true
        when (ev.type) {
            KeyEventType.KeyDown -> when {
                this._findReplaceState.isVisible && ev.isEscape -> _findReplaceState.close()
                // keys the popup does not handle (e.g. normal typing) go through to the text field
                this._autocompleteState.isVisible -> handled = this._autocompleteState.handlePreviewKeyEvent(ev)

                // Ctrl+Enter accepts the ghost text
                isGhostActive && ev.isCtrlEnter -> {
                    acceptGhostText(currentGhostState)
                    handled = true
                }

                // Intercept Right Arrow to skip affinity bounds
                isGhostActive && ev.key == Key.DirectionRight && !currentGhostState.replaceWholeText && currentCursor == currentGhostState.ghostPosition -> {
                    if (currentCursor < tfs.text.length) {
                        tfs.edit { this.selection = androidx.compose.ui.text.TextRange(currentCursor + 1) }
                    }
                    handled = true
                }

                ev.isShiftTab -> indentOrOutdentSelection(outdent = true)
                ev.isTab -> indentOrOutdentSelection(outdent = false)
                else -> when {
                    ev.isCtrlPressed || ev.isMetaPressed -> when {
                        ev.isCtrlSpace -> _autocompleteState.open()
                        ev.isCtrlF -> _findReplaceState.open(showReplace = false)
                        ev.isCtrlR -> _findReplaceState.open(showReplace = true)
                        else -> handled = false
                    }
                    else -> handled = false
                }
            }

            // KeyUp / KeyPressed
            else -> when {
                isGhostActive && ev.isCtrlEnter -> handled = true // Swallow release event
                isGhostActive && ev.key == Key.DirectionRight && !currentGhostState.replaceWholeText && currentCursor == currentGhostState.ghostPosition -> handled = true
                ev.isCtrlSpace -> handled = true
                ev.isCtrlF || ev.isCtrlR -> handled = true
                ev.isEscape && _findReplaceState.isVisible -> handled = true
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
        return when (ev.type) {
            // KeyDown | KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> true
                ev.isCtrlF || ev.isCtrlR -> true
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
    fun setNewText(text: String) = _core.setNewText(text)

    fun onInputScroll(i: ScrollState) = _core.onInputScroll()

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        _autocompleteState.close()
        _core.onInputTextLayout(textLayoutResult)
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
            IntOffset(cursOffset, lineBot - scrollOffset)
        } else {
            IntOffset(0, 0)
        }
    }

    fun setAllLineStyles(value: Map<Int, List<EditorSegmentStyle>>) = _core.setAllLineStyles(value)

    fun setLineStyles(lineNumber: Int, styles: List<EditorSegmentStyle>) = _core.setLineStyles(lineNumber, styles)

    fun performOutputTransformation(buffer: TextFieldBuffer, state: CodeEditorState) {
        val ghostState = state.ghostTextState

        if (ghostState?.show == true) {
            val textToInject = ghostState.ghostText!!
            if (ghostState.replaceWholeText) {
                // Append the replacement text to the very end of the buffer
                val appendPosition = buffer.length
                buffer.insert(appendPosition, "\n$textToInject")
            } else {
                // Standard inline ghost text injection
                buffer.insert(ghostState.ghostPosition, textToInject)
            }

            // Pass to the styling engine
            ComposeEditorUtils.annotateTextFieldBuffer(
                buffer = buffer,
                viewerState = state.viewerState,
                ghostState = ghostState,
                annotatedTextChange = { this.lastAnnotatedText = it }
            )
        } else {
            // Baseline styling pass
            ComposeEditorUtils.annotateTextFieldBuffer(
                buffer = buffer,
                viewerState = state.viewerState,
                ghostState = null,
                annotatedTextChange = { this.lastAnnotatedText = it }
            )
        }
    }
    // --- ComposeCodeEditor ---

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

    override var onTextChange: (CharSequence) -> Unit = {}

    override val marginItems: List<MarginItem> get() = _core.marginItems

    override val autocomplete: AutocompleteState get() = this._autocompleteState
    override val findReplace: FindReplaceState get() = this._findReplaceState
    override var requestAutocompleteSuggestions: AutocompleteFunction
        get() = _autocompleteState.requestAutocompleteSuggestions
        set(value) {
            _autocompleteState.requestAutocompleteSuggestions = value
        }

    var ghostText: (text: String, cursorPosition: Int) -> GhostTextState? = { _, _ -> null }


    override fun focus() {
        this._giveFocus = true
    }

    internal fun onFocusChanged(isFocused: Boolean) {
        if (isFocused) this._giveFocus = false
    }

    override fun clearTextMarkers() {
        _core.clearTextMarkers()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        _core.addTextMarker(position, length, style, decoration)
    }

    override fun clearMarginItems() {
        _core.clearMarginItems()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) {
        _core.addMarginItem(lineNumber, kind, text, icon, color)
    }

}

/**
 * @param editorState the editor state; when null, a state is created and remembered by this composable
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditorView(
    editorState: CodeEditorStateHolder? = null,
    textStyle: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    autocompleteModifier: Modifier = Modifier,
    marginItemHoverModifier: Modifier = Modifier,
) {
    val defaultTextColor = MaterialTheme.colorScheme.onBackground
    val editorState = editorState ?: remember {
        CodeEditorStateHolder(
            initialText = "",
            defaultTextStyle = SpanStyle(color = defaultTextColor)
        )
    }
    val state: CodeEditorState = editorState.collectAsState()
    val composableScope = rememberCoroutineScope()
    SideEffect { editorState._composableScope = composableScope }

    // keep find matches in step with the text, otherwise replace would use stale offsets
    val currentText = state.viewerState.inputTextFieldState.value.text.toString()
    val findVisible = editorState._findReplaceState.isVisible
    LaunchedEffect(currentText, findVisible) {
        if (findVisible) editorState._findReplaceState.updateMatches()
    }

    // Asynchronously update lastAnnotatedText outside the layout pass ---
    LaunchedEffect(state.viewerState.inputTextFieldState.value.text) {
        val cleanText = state.viewerState.inputTextFieldState.value.text.toString()
        val cleanAnnotatedString = ComposeEditorUtils.annotateText(
            rawText = cleanText,
            viewFirstLine = state.viewerState.viewFirstLine,
            viewLastLine = state.viewerState.viewLastLine,
            lineTokens = state.viewerState.lineTokens,
            markers = state.viewerState.textMarkersVisible
        )
        editorState.lastAnnotatedText = cleanAnnotatedString
    }


    val marginItemsState = editorState.collectVisibleMarginItemsAsState(
        state.viewerState.viewFirstLine,
        state.viewerState.viewLastLine,
        state.viewerState.lineScrollOffset,
        state.viewerState.lastTextLayoutResult
    )

    Row(modifier = modifier) {
        annotationMargin(marginItemsState, marginItemHoverModifier)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            textEditor(
                state = state,
                textStyle = textStyle,
                outputTransformation = { editorState.performOutputTransformation(this, state) },
                onTextLayout = { r ->
                    r.invoke()?.let { editorState.onInputTextLayout(it) }
                },
                handlePreviewKeyEvent = { ev -> editorState.handlePreviewKeyEvent(ev) },
                handleKeyEvent = { ev -> editorState.handleKeyEvent(ev) },
                onTextChange = { editorState.onTextChange.invoke(it) },
                onInputScroll = { editorState.onInputScroll(it) },
                onFocusChanged = { editorState.onFocusChanged(it) }
            )

            AutocompletePopup2(
                state = editorState._autocompleteState,
                modifier = autocompleteModifier,
            )

            if (editorState._findReplaceState.isVisible) {
                FindReplaceBar(
                    state = editorState._findReplaceState,
                    modifier = Modifier.align(Alignment.TopEnd).zIndex(2f),
                )
            }
        }
    }
}

@Composable
fun annotationMargin(state: MarginItemListState, marginItemHoverModifier: Modifier = Modifier) {
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
                    .offset(y = with(LocalDensity.current) { item.offsetFromTopOfViewport.toDp() })
                    .hoverable(interactionSource)
            )
            val isHovered = interactionSource.collectIsHoveredAsState().value
            if (isHovered) {
                Text(
                    text = item.item.text,
                    modifier = marginItemHoverModifier
                        .offset(
                            y = with(LocalDensity.current) { item.detailOffsetFromTopOfViewport.toDp() },
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
            state = state.viewerState.inputTextFieldState.value,
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) }
                .onKeyEvent { ev -> handleKeyEvent(ev) }
                .focusRequester(focusRequester)
                .drawBehind(state.viewerState.extendedSpans, state.viewerState.inputScrollState.value.toFloat()),
            onTextLayout = onTextLayout,
            scrollState = state.viewerState.inputScrollState,
            interactionSource = interactionSource,
            outputTransformation = outputTransformation,
        )

        // invoke the 'onTextChange' callback once for the initial text and then once per change
        val textFieldState = state.viewerState.inputTextFieldState
        val currentOnTextChange by rememberUpdatedState(onTextChange)
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.value.text.toString() }.collect { currentOnTextChange(it) }
        }

        val scrollState = state.viewerState.inputScrollState
        val currentOnInputScroll by rememberUpdatedState(onInputScroll)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { currentOnInputScroll(scrollState) }
        }

        val isFocused by interactionSource.collectIsFocusedAsState()
        LaunchedEffect(isFocused) { onFocusChanged.invoke(isFocused) }

        LaunchedEffect(state.giveFocus) {
            if (state.giveFocus) {
                focusRequester.requestFocus()
            }
        }

    }
}
