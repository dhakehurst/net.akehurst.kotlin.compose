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

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.AutocompleteState
import net.akehurst.kotlin.compose.editor.api.ComposeCodeEditor
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.FindReplaceState
import net.akehurst.kotlin.compose.editor.api.MarginItem
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

class ComposableCodeEditor(
    initialText: String = ""
) : ComposeCodeEditor {

    val editorState = CodeEditorStateHolder(
        initialText = initialText
    )

    override var rawText: String
        get() = editorState.rawText
        set(value) {
            editorState.setNewText(value)
        }

    override var annotatedText
        get() = editorState.annotatedText
        set(value) {
            editorState.annotatedText = value
        }

    override var onTextChange: (CharSequence) -> Unit
        get() = editorState.onTextChange
        set(value) {
            editorState.onTextChange = value
        }

    override var lineStyles: Map<Int, List<EditorSegmentStyle>>
        get() = editorState.lineStyles
        set(value) {
            editorState.setAllLineStyles(value)
        }

    override val autocomplete: AutocompleteState get() = editorState.autocomplete

    override val findReplace: FindReplaceState get() = editorState.findReplace

    override var requestAutocompleteSuggestions: AutocompleteFunction
        get() = editorState.requestAutocompleteSuggestions
        set(value) {
            editorState.requestAutocompleteSuggestions = value
        }

    override val marginItems: List<MarginItem> get() = editorState.marginItems

    override fun focus() = editorState.focus()

    override fun clearMarginItems() = editorState.clearMarginItems()

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) =
        editorState.addMarginItem(lineNumber, kind, text, icon, color)

    override fun clearTextMarkers() = editorState.clearTextMarkers()

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) =
        editorState.addTextMarker(position, length, style, decoration)

    @Composable
    fun content(
        textStyle: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier.fillMaxSize(),
        autocompleteModifier: Modifier = Modifier,
        marginItemHoverModifier: Modifier = Modifier
    ) {
        CodeEditorView(
            textStyle = textStyle,
            modifier = modifier,
            autocompleteModifier = autocompleteModifier,
            marginItemHoverModifier = marginItemHoverModifier,
            editorState = editorState
        )
    }

}
