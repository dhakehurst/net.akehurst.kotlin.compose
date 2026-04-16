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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import net.akehurst.kotlin.compose.editor.api.FindReplaceState
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

/**
 * Compose implementation of [FindReplaceState] for the code editor find & replace feature.
 */
class FindReplaceStateCompose(
    private val getText: () -> CharSequence,
    private val replaceInText: (start: Int, end: Int, replacement: String) -> Unit,
    private val scrollToOffset: (offset: Int) -> Unit,
) : FindReplaceState {

    companion object {
        /** Background colour for all matches. */
        val MATCH_HIGHLIGHT = Color(0x5599CC00)
        /** Background colour for the current (focused) match. */
        val CURRENT_MATCH_HIGHLIGHT = Color(0xAAFF9632)
    }

    val searchQueryState = TextFieldState("")
    val replaceTextState = TextFieldState("")

    private val _matches = mutableStateListOf<IntRange>()
    private val _currentMatchIndex = mutableIntStateOf(-1)
    private val _isVisible = mutableStateOf(false)
    private val _isReplaceVisible = mutableStateOf(false)
    private val _isCaseSensitive = mutableStateOf(false)
    private val _isRegex = mutableStateOf(false)
    private val _isRegexError = mutableStateOf(false)

    // --- FindReplaceState ---

    override val isVisible: Boolean get() = _isVisible.value
    override val isReplaceVisible: Boolean get() = _isReplaceVisible.value
    override val query: String get() = searchQueryState.text.toString()
    override val replacement: String get() = replaceTextState.text.toString()
    override val matchCount: Int get() = _matches.size
    override val currentMatchIndex: Int get() = _currentMatchIndex.intValue
    override val isCaseSensitive: Boolean get() = _isCaseSensitive.value
    override val isRegex: Boolean get() = _isRegex.value
    override val isRegexError: Boolean get() = _isRegexError.value

    override fun open(showReplace: Boolean) {
        _isVisible.value = true
        _isReplaceVisible.value = showReplace
        updateMatches()
    }

    override fun close() {
        _isVisible.value = false
        _isReplaceVisible.value = false
        _matches.clear()
        _currentMatchIndex.intValue = -1
    }

    override fun findNext() {
        if (_matches.isEmpty()) return
        _currentMatchIndex.intValue = (_currentMatchIndex.intValue + 1) % _matches.size
        scrollToCurrentMatch()
    }

    override fun findPrevious() {
        if (_matches.isEmpty()) return
        _currentMatchIndex.intValue = if (_currentMatchIndex.intValue <= 0) _matches.size - 1 else _currentMatchIndex.intValue - 1
        scrollToCurrentMatch()
    }

    override fun toggleCaseSensitive() {
        _isCaseSensitive.value = !_isCaseSensitive.value
        updateMatches()
    }

    override fun toggleRegex() {
        _isRegex.value = !_isRegex.value
        updateMatches()
    }

    override fun replaceCurrent() {
        val idx = _currentMatchIndex.intValue
        if (idx < 0 || idx >= _matches.size) return
        val range = _matches[idx]
        replaceInText(range.first, range.last + 1, replacement)
        updateMatches()
    }

    override fun replaceAll() {
        if (_matches.isEmpty()) return
        // iterate in reverse so earlier offsets remain valid
        for (i in _matches.indices.reversed()) {
            val range = _matches[i]
            replaceInText(range.first, range.last + 1, replacement)
        }
        updateMatches()
    }

    // --- Internal ---

    fun updateMatches() {
        _matches.clear()
        _isRegexError.value = false
        val q = query
        if (q.isEmpty()) {
            _currentMatchIndex.intValue = -1
            return
        }
        val text = getText().toString()
        if (_isRegex.value) {
            val options = if (_isCaseSensitive.value) emptySet() else setOf(RegexOption.IGNORE_CASE)
            try {
                val regex = Regex(q, options)
                regex.findAll(text).forEach { result ->
                    if (result.range.isEmpty()) return@forEach // skip zero-length matches
                    _matches.add(result.range)
                }
            } catch (_: Exception) {
                _isRegexError.value = true
            }
        } else {
            val ignoreCase = !_isCaseSensitive.value
            var startIndex = 0
            while (startIndex <= text.length) {
                val idx = text.indexOf(q, startIndex, ignoreCase)
                if (idx < 0) break
                _matches.add(idx until idx + q.length)
                startIndex = idx + q.length
            }
        }
        // clamp current match index
        if (_matches.isEmpty()) {
            _currentMatchIndex.intValue = -1
        } else {
            _currentMatchIndex.intValue = _currentMatchIndex.intValue.coerceIn(0, _matches.size - 1)
        }
    }

    /**
     * Returns [TextMarkerDefault] entries for all current find matches,
     * with a distinct highlight for the current match.
     */
    fun getMatchMarkers(): List<TextMarkerDefault> {
        if (!_isVisible.value || _matches.isEmpty()) return emptyList()
        return _matches.mapIndexed { index, range ->
            val bg = if (index == _currentMatchIndex.intValue) CURRENT_MATCH_HIGHLIGHT else MATCH_HIGHLIGHT
            TextMarkerDefault(
                position = range.first,
                length = range.last - range.first + 1,
                style = SpanStyle(background = bg),
                decoration = TextDecorationStyle.NONE,
            )
        }
    }

    private fun scrollToCurrentMatch() {
        val idx = _currentMatchIndex.intValue
        if (idx < 0 || idx >= _matches.size) return
        scrollToOffset(_matches[idx].first)
    }
}
