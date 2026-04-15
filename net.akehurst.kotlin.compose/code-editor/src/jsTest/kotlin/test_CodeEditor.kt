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

package test

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformSpanStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextDecorationLineStyle
import androidx.compose.ui.text.style.TextDecoration
import net.akehurst.kotlin.compose.editor.ComposableCodeEditor
import net.akehurst.kotlin.compose.editor.api.*
import kotlin.test.Test

data class AcItem(
    override val text: String
) : AutocompleteItemContent {
    override val offset: Int get() = 0
    override val label: String? get() = text
    override fun equalTo(other: AutocompleteItem): Boolean = when {
        other !is AcItem -> false
        this.text != other.text -> false
        else -> true
    }
}

class test_CodeEditor {

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun main() {
        val composeEditor = ComposableCodeEditor(
            initialText = """
                    \red{Hello} \blue{World}
                    info
                    error
                """.trimIndent(),
        )
        //requestAutocompleteSuggestions = { req, result -> requestAutocompleteSuggestions(req, result) }
        val info = Regex("info")
        val err = Regex("error")
        val wavyStyle = PlatformSpanStyle(textDecorationLineStyle = TextDecorationLineStyle.Wavy)
        composeEditor.onTextChange = {
            val lines = it.split("\n")
            composeEditor.clearMarginItems()
            composeEditor.clearTextMarkers()
            lines.forEachIndexed { idx, ln ->
                when {
                    ln.contains("info") -> composeEditor.addMarginItem(idx, "Info", "Some info", Icons.Outlined.Info, Color.Blue)
                    ln.contains("error") -> composeEditor.addMarginItem(idx, "Error", "Some error", Icons.Outlined.Warning, Color.Red)
                }
            }

            info.findAll(it).forEach {
                composeEditor.addTextMarker(
                    it.range.start,
                    it.range.endInclusive - it.range.start + 1,
                    SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline, platformStyle = wavyStyle)
                )
            }
            err.findAll(it).forEach {
                composeEditor.addTextMarker(
                    it.range.start,
                    it.range.endInclusive - it.range.start + 1,
                    SpanStyle(color = Color.Red, textDecoration = TextDecoration.Underline, platformStyle = wavyStyle)
                )
            }

            composeEditor.lineStyles = lines.mapIndexed { idx, ln -> Pair(idx, getLineTokens(ln)) }.toMap()
        }
        /*
                singleWindowApplication(
                    title = "Code Editor 3 Test",
                ) {
                    Surface {
                        composeEditor.content(autocompleteModifier = Modifier.width(500.dp))
                    }
                }
                TODO
         */
    }

    private fun requestAutocompleteSuggestions(req: AutocompleteRequestData, result: AutocompleteSuggestion) {
        result.provide(
            listOf(
                AcItem("if"),
                AcItem("else"),
            )
        )
    }

    fun getLineTokens(lineText: String): List<EditorSegmentStyle> {
        val t1 = Regex("[\\\\]red[{](.*)[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last + 1
                override val style: SpanStyle get() = SpanStyle(color = Color.Red)
            }
        }
        val t2 = Regex("[\\\\]blue[{](.*)[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last + 1
                override val style: SpanStyle get() = SpanStyle(color = Color.Blue)
            }
        }
        val t3 = Regex("else|if|[{]|[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last + 1
                override val style: SpanStyle get() = SpanStyle(color = Color.Magenta)
            }
        }
        return (t1 + t2 + t3).toList()
    }

}