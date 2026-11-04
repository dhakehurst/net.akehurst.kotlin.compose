/**
 * Copyright 2022 Saket Narayan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalTextApi::class)

package me.saket.extendedspans

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap

@Stable
class ExtendedSpans(
    val painters: List<ExtendedSpanPainter>
) {
    constructor(vararg painters: ExtendedSpanPainter) : this(painters.toList())

    internal var drawInstructions = emptyList<SpanDrawInstructions>()

    /**
     * The string annotations (added by the painters' decorate) of the text that is being laid out.
     * Set this before layout when the text goes through a TextFieldBuffer, because the buffer
     * drops string annotations. When empty, the annotations of the laid out text are used.
     */
    var stringAnnotations = emptyList<AnnotatedString.Range<String>>()

    /**
     * Prepares [text] to be rendered by [painters].
     *
     * [RoundedCornerSpanPainter] and [SquigglyUnderlineSpanPainter] use this for removing background
     * and underline spans so that they can be drawn manually.
     */
    fun extend(text: AnnotatedString): AnnotatedString {
        return buildAnnotatedString {
            append(text.text)

            // For onTextLayout to be called if a new instance of ExtendedSpans is applied with the same text.
            val uniqueKey = this@ExtendedSpans.hashCode().toString()
            addStringAnnotation(EXTENDED_SPANS_MARKER_TAG, annotation = uniqueKey, start = 0, end = 0)

            text.spanStyles.fastForEach {
                val decorated = painters.fastFold(initial = it.item) { updated, painter ->
                    painter.decorate(updated, it.start, it.end,  builder = this)
                }
                addStyle(decorated, it.start, it.end)
            }
            text.paragraphStyles.fastForEach {
                addStyle(it.item, it.start, it.end)
            }
            text.getStringAnnotations(start = 0, end = text.length).fastForEach {
                addStringAnnotation(tag = it.tag, annotation = it.item, start = it.start, end = it.end)
            }
            text.getTtsAnnotations(start = 0, end = text.length).fastForEach {
                addTtsAnnotation(it.item, it.start, it.end)
            }
            @Suppress("DEPRECATION")
            text.getUrlAnnotations(start = 0, end = text.length).fastForEach {
                addUrlAnnotation(it.item, it.start, it.end)
            }
            text.getLinkAnnotations(start = 0, end = text.length).fastForEach { range ->
                when (val item = range.item) {
                    is LinkAnnotation.Url -> addLink(item, range.start, range.end)
                    is LinkAnnotation.Clickable -> addLink(item, range.start, range.end)
                }
            }
        }
    }

    fun onTextLayout(layoutResult: TextLayoutResult) {
        //layoutResult.checkIfExtendWasCalled()
        val annotations = stringAnnotations.ifEmpty {
            val text = layoutResult.layoutInput.text
            text.getStringAnnotations(start = 0, end = text.length)
        }
        drawInstructions = painters.fastMap {
            it.drawInstructionsFor(layoutResult, annotations)
        }
    }

    private fun TextLayoutResult.checkIfExtendWasCalled() {
        val wasExtendCalled = layoutInput.text.getStringAnnotations(
            tag = EXTENDED_SPANS_MARKER_TAG,
            start = 0,
            end = 0
        ).isNotEmpty()
        check(wasExtendCalled) {
            "ExtendedSpans#extend(AnnotatedString) wasn't called for this Text()."
        }
    }

    companion object {
        private const val EXTENDED_SPANS_MARKER_TAG = "extended_spans_marker"
    }
}

fun Modifier.drawBehind(spans: ExtendedSpans, scrollOffset:Float): Modifier {
    return drawBehind {
        translate(top = -scrollOffset) {
            spans.drawInstructions.fastForEach { instructions ->
                with(instructions) {
                    draw()
                }
            }
        }
    }
}