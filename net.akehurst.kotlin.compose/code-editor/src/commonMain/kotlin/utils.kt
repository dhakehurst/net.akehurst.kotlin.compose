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

@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import me.saket.extendedspans.SquigglyUnderlineSpanPainter
import net.akehurst.kotlin.compose.editor.ComposeEditorUtils.SQUIGGLY
import net.akehurst.kotlin.compose.editor.ComposeEditorUtils.STRAIGHT
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle
import net.akehurst.kotlin.compose.viewer.CodeViewerState

object ComposeEditorUtils {
    val STRAIGHT = SquigglyUnderlineSpanPainter(
        "STRAIGHT",
        width = 3.sp,
        wavelength = 20.sp,
        amplitude = 0.sp,
        bottomOffset = 1.sp,
        //animator = underlineAnimator
    )
    val SQUIGGLY = SquigglyUnderlineSpanPainter(
        "SQUIGGLY",
        width = 3.sp,
        wavelength = 15.sp,
        amplitude = 2.sp,
        bottomOffset = 1.sp,
        //animator = underlineAnimator
    )

    /**
     * Convert a text line number (based on '\n' positions in the source text) to a layout line index
     * in the [TextLayoutResult]. These differ when text wrapping causes a single text line to span
     * multiple layout lines.
     */
    internal fun textLineToLayoutLine(textLineNumber: Int, textLayoutResult: TextLayoutResult): Int {
        if (textLineNumber <= 0) return 0
        val text = textLayoutResult.layoutInput.text
        if (text.isEmpty()) return 0
        var newlineCount = 0
        for (i in text.indices) {
            if ('\n' == text[i]) {
                newlineCount++
                if (newlineCount == textLineNumber) {
                    return if (i + 1 < text.length) {
                        textLayoutResult.getLineForOffset(i + 1)
                    } else {
                        // trailing empty line after last newline
                        (textLayoutResult.lineCount - 1).coerceAtLeast(0)
                    }
                }
            }
        }
        // textLineNumber exceeds actual text line count
        return (textLayoutResult.lineCount - 1).coerceAtLeast(0)
    }

    // currently used for margin item positions and clamps the position to top/bottom of viewport
    internal fun offsetFromTopOfViewport(layoutLine: Int, viewFirstLine: Int, viewLastLine: Int, lineScrollOffset: Float, textLayoutResult: TextLayoutResult): Float {
        val firstLineTop = textLayoutResult.getLineTop(viewFirstLine)
        return when {
            layoutLine <= viewFirstLine -> 0f // if line is at or above first visible line, clamp to top of viewport
            layoutLine >= viewLastLine -> textLayoutResult.getLineTop(viewLastLine) - firstLineTop - lineScrollOffset // clamp to last visible line
            else -> textLayoutResult.getLineTop(layoutLine) - firstLineTop - lineScrollOffset
        }
    }

    internal fun lineHeight(textLayoutResult: TextLayoutResult, lineNumber: Int): Float {
        return if (lineNumber >= 0 && lineNumber < textLayoutResult.lineCount) {
            textLayoutResult.getLineBottom(lineNumber) - textLayoutResult.getLineTop(lineNumber)
        } else {
            0f
        }
    }

    internal fun annotateTextFieldBuffer(
        buffer: TextFieldBuffer,
        viewerState: CodeViewerState,
        ghostState: GhostTextState?,
        annotatedTextChange: (AnnotatedString) -> Unit
    ) {
        val rawText = buffer.asCharSequence()
        if (rawText.isNotEmpty()) {
            val annotatedText = if (ghostState?.show == true) {
                val replaceWholeText = ghostState.replaceWholeText
                val ghostTextStr = ghostState.ghostText

                // Account for the extra newline '\n' we added when appending the block
                val finalGhostText = if (replaceWholeText) "\n$ghostTextStr" else ghostTextStr
                val ghostLength = finalGhostText?.length ?: 0
                val ghostNewLines = finalGhostText?.count { it == '\n' } ?: 0

                val originalLength = (rawText.length - ghostLength).coerceAtLeast(0)

                // If replacing whole text, the ghost text starts exactly where the old text ends
                val actualGhostPosition = if (replaceWholeText) originalLength else ghostState.ghostPosition.coerceIn(0, originalLength)
                annotateTextWithGhost(
                    rawText = rawText,
                    viewFirstLine = viewerState.viewFirstLine,
                    viewLastLine = viewerState.viewLastLine + ghostNewLines,
                    lineTokens = viewerState.lineTokens,
                    markers = viewerState.textMarkersVisible,
                    ghostPosition = actualGhostPosition,
                    ghostLength = ghostLength,
                    ghostText = ghostTextStr, // Pass clean text without the extra layout newline
                    ghostTokens = ghostState.ghostTokens,
                    replaceWholeText = replaceWholeText
                )
            } else {
                annotateText(
                    rawText = rawText,
                    viewFirstLine = viewerState.viewFirstLine,
                    viewLastLine = viewerState.viewLastLine,
                    lineTokens = viewerState.lineTokens,
                    markers = viewerState.textMarkersVisible
                )
            }

            //buffer.setComposition(0, rawText.length, annotatedText.annotations?.map { AnnotatedString.Range(it.item, it.start, it.end) })
            //buffer.changeTracker.trackChange(0, rawText.length, annotatedText.length)

            annotatedText.spanStyles.forEach { range ->
                buffer.addStyle(range.item, range.start, range.end)
            }
            annotatedText.paragraphStyles.forEach { range ->
                buffer.addStyle(range.item, range.start, range.end)
            }

            //annotatedTextChange.invoke(annotatedText)
        }
    }

    fun annotateText(rawText: CharSequence, viewFirstLine: Int, viewLastLine: Int, lineTokens: Map<Int, List<EditorSegmentStyle>>, markers: List<TextMarkerDefault>): AnnotatedString {
        return if (rawText.isEmpty()) {
            AnnotatedString(rawText.toString())
        } else {
            // lines from textLayoutResult are possible different to actual ines in text defined by EOL.
            //  eg if lines are wrapped by the layout, thus have to compute own lineMetrics
            val lineMetrics = LineMetrics(rawText)
            buildAnnotatedString {
                append(rawText)
                // annotate from tokens
                for (lineNum in viewFirstLine..viewLastLine) {
                    val (lineStartPos, lineFinishPos) = lineMetrics.lineEnds(lineNum)
                    val toks = lineTokens.getOrElse(lineNum) { emptyList() }
                    for (tk in toks) {
                        val offsetStart = (lineStartPos + tk.start).coerceIn(lineStartPos, lineFinishPos)
                        val offsetFinish = (lineStartPos + tk.finish).coerceIn(lineStartPos, lineFinishPos)
                        addStyle(tk.style, offsetStart, offsetFinish)
                    }
                }

                // annotate from markers
                for (marker in markers) {
                    // println("Marker at: ${marker.position} length ${marker.length} line $lineNum")
                    val offsetStart = (marker.position).coerceIn(0, rawText.length)
                    val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
                    //println("Style at: ${offsetStart} .. ${offsetFinish}")
                    val ss = when (marker.decoration) {
                        TextDecorationStyle.NONE -> null
                        TextDecorationStyle.STRAIGHT -> STRAIGHT.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                        TextDecorationStyle.SQUIGGLY -> SQUIGGLY.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                    }
                    ss?.let { addStyle(it, offsetStart, offsetFinish) }
                }
            }
        }
    }

    fun annotateTextWithGhost(
        rawText: CharSequence,
        viewFirstLine: Int,
        viewLastLine: Int,
        lineTokens: Map<Int, List<EditorSegmentStyle>>,
        markers: List<TextMarkerDefault>,
        ghostPosition: Int = -1,
        ghostLength: Int = 0,
        ghostText: String? = null,
        ghostTokens: Map<Int, List<EditorSegmentStyle>> = emptyMap(),
        replaceWholeText: Boolean = false
    ): AnnotatedString {
        return if (rawText.isEmpty()) {
            AnnotatedString("")
        } else {
            val rawLineMetrics = LineMetrics(rawText.toString())

            // Recover clean text string limits for reference
            val originalText = if (ghostPosition != -1 && ghostLength > 0 && (ghostPosition + ghostLength) <= rawText.length) {
                StringBuilder(rawText).deleteRange(ghostPosition, ghostPosition + ghostLength).toString()
            } else {
                rawText.toString()
            }
            val originalLineMetrics = LineMetrics(originalText)

            buildAnnotatedString {
                append(rawText)

                // Tint original block background red if it's going to be replaced, have to do this first due to compose color blending rules
                if (replaceWholeText && ghostPosition != -1) {
                    addStyle(
                        style = SpanStyle(background = Color.Red.copy(alpha = 0.15f)),
                        start = 0,
                        end = ghostPosition
                    )
                }

                // Process base code layers normally
                for (lineNum in viewFirstLine..viewLastLine) {
                    val (lineStartPos, lineFinishPos) = try {
                        if (ghostPosition == -1) {
                            rawLineMetrics.lineEnds(lineNum)
                        } else {
                            originalLineMetrics.lineEnds(lineNum)
                        }
                    } catch (e: Exception) {
                        continue
                    }

                    val toks = lineTokens.getOrElse(lineNum) { emptyList() }
                    for (tk in toks) {
                        val origStart = lineStartPos + tk.start
                        val origFinish = lineStartPos + tk.finish

                        // Shift only applies in standard inline modes
                        val offsetStart = if (ghostPosition != -1 && !replaceWholeText && origStart >= ghostPosition) origStart + ghostLength else origStart
                        val offsetFinish = if (ghostPosition != -1 && !replaceWholeText && origFinish > ghostPosition) origFinish + ghostLength else origFinish

                        val finalStart = offsetStart.coerceIn(0, rawText.length)

                        // Blend the red tint over existing token backgrounds ---
                        var styleToApply = tk.style
                        if (replaceWholeText && ghostPosition != -1 && finalStart < ghostPosition) {
                            if (tk.style.background != Color.Unspecified) {
                                val redOverlay = Color.Red.copy(alpha = 0.15f)
                                // compositeOver layers the source color cleanly ON TOP OF the destination color
                                styleToApply = tk.style.copy(background = redOverlay.compositeOver(tk.style.background))
                            }
                        }

                        addStyle(styleToApply, finalStart, offsetFinish.coerceIn(0, rawText.length))
                    }
                }

                // 2. Map standard error markers
                for (marker in markers) {
                    val origStart = marker.position
                    val origFinish = marker.position + marker.length

                    val offsetStart = if (ghostPosition != -1 && !replaceWholeText && origStart >= ghostPosition) origStart + ghostLength else origStart
                    val offsetFinish = if (ghostPosition != -1 && !replaceWholeText && origFinish > ghostPosition) origFinish + ghostLength else origFinish

                    val clampedStart = offsetStart.coerceIn(0, rawText.length)
                    val clampedFinish = offsetFinish.coerceIn(0, rawText.length)

                    when (marker.decoration) {
                        TextDecorationStyle.NONE -> addStyle(marker.style, clampedStart, clampedFinish)
                        TextDecorationStyle.STRAIGHT -> {
                            val ss = STRAIGHT.decorate(marker.style, clampedStart, clampedFinish, builder = this)
                            ss?.let { addStyle(it, clampedStart, clampedFinish) }
                        }

                        TextDecorationStyle.SQUIGGLY -> {
                            val ss = SQUIGGLY.decorate(marker.style, clampedStart, clampedFinish, builder = this)
                            ss?.let { addStyle(it, clampedStart, clampedFinish) }
                        }
                    }
                }

                // 4. Map the granular Ghost Text tokens onto the appended space
                if (ghostPosition != -1 && ghostLength > 0 && ghostText != null) {
                    val ghostLineMetrics = LineMetrics(ghostText)
                    val ghostNewLines = ghostText.count { it == '\n' }

                    // Account for our structural layout padding newline '\n'
                    val actualInsertionOffset = if (replaceWholeText) ghostPosition + 1 else ghostPosition

                    for (gLineNum in 0..ghostNewLines) {
                        val (gLineStart, gLineFinish) = try {
                            ghostLineMetrics.lineEnds(gLineNum)
                        } catch (e: Exception) {
                            continue
                        }

                        val gToks = ghostTokens.getOrElse(gLineNum) { emptyList() }
                        for (tk in gToks) {
                            val absoluteGhostStart = actualInsertionOffset + gLineStart + tk.start
                            val absoluteGhostFinish = actualInsertionOffset + gLineStart + tk.finish

                            val finalStart = absoluteGhostStart.coerceIn(ghostPosition, rawText.length)
                            val finalFinish = absoluteGhostFinish.coerceIn(ghostPosition, rawText.length)

                            addStyle(tk.style, finalStart, finalFinish)

                            // Blend a green tint background or alpha mask over the new code suggestion
                            addStyle(
                                style = SpanStyle(
                                    color = tk.style.color.copy(alpha = 0.5f),
                                    background = if (replaceWholeText) Color.Green.copy(alpha = 0.1f) else Color.Unspecified
                                ),
                                start = finalStart,
                                end = finalFinish
                            )
                        }
                    }
                }
            }
        }
    }
}