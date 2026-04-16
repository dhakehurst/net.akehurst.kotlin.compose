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

package net.akehurst.kotlin.compose.editor.api

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

typealias LineTokensFunction = ((lineNumber: Int, lineStartPosition: Int, lineText: String) -> List<EditorSegmentStyle>)
typealias AutocompleteFunction = suspend (request:AutocompleteRequestData, result: AutocompleteSuggestion) -> Unit


data class AutocompleteRequestData(
    val position: Int,
    val text: CharSequence,
    val isOpen: Boolean,
    val currentIndex:Int,
    val proposalPathDelta:Int,
    val depthDelta:Int
)
enum class TextDecorationStyle {
    NONE,STRAIGHT, SQUIGGLY
}

interface ComposeCodeViewer {
    var rawText: String
    var lineStyles: Map<Int,List<EditorSegmentStyle>>
    var annotatedText: AnnotatedString

    /**
     * indicators that sit in the margin next to a specific line
     */
    val marginItems: List<MarginItem>

    fun clearTextMarkers()
    fun addTextMarker(position:Int, length:Int, style: SpanStyle, decoration:TextDecorationStyle = TextDecorationStyle.NONE)
    fun clearMarginItems()
    fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color)

}

interface ComposeCodeEditor : ComposeCodeViewer {

    var onTextChange: (CharSequence) -> Unit
    val autocomplete: AutocompleteState
    var requestAutocompleteSuggestions: AutocompleteFunction
    val findReplace: FindReplaceState

    fun focus()
}

interface FindReplaceState {
    val isVisible: Boolean
    val isReplaceVisible: Boolean
    val query: String
    val replacement: String
    val matchCount: Int
    val currentMatchIndex: Int
    val isCaseSensitive: Boolean
    val isRegex: Boolean
    val isRegexError: Boolean

    fun open(showReplace: Boolean = false)
    fun close()
    fun findNext()
    fun findPrevious()
    fun toggleCaseSensitive()
    fun toggleRegex()
    fun replaceCurrent()
    fun replaceAll()
}

interface AutocompleteState {
    val isVisible: Boolean
    val isLoading: Boolean

    fun clear()
}

interface EditorSegmentStyle {
    /**
     * style for this segment of text
     */
    val style: SpanStyle

    /**
     * starting offset in the line for this segment. Lines start at offset 0
     */
    val start: Int

    /**
     * finish offset (exclusive) in the line for this segment. Lines start at offset 0
     */
    val finish: Int
}

interface AutocompleteItem

interface AutocompleteItemContent : AutocompleteItem {

    /**
     * text to insert if selected
     */
    val text: String

    val offset:Int

    /**
     * when not null will use this label in the autocomplete list with text in (...)
     */
    val label: String?

    fun equalTo(other: AutocompleteItem): Boolean
}

object AutocompleteItemDivider : AutocompleteItem {}

interface AutocompleteSuggestion {
    fun provide(items: List<AutocompleteItem>)
}

interface MarginItem {
    val lineNumber: Int
    val kind: String
    val text: String
    val icon: ImageVector
    val color: Color
}