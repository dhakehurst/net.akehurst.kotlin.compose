@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import me.saket.extendedspans.SquigglyUnderlineSpanPainter
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

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
        viewFirstLine: Int,
        viewLastLine: Int,
        lineStyles: Map<Int, List<EditorSegmentStyle>>,
        textMarkers: List<TextMarkerDefault>,
        annotatedTextChange: (AnnotatedString) -> Unit
    ) {
        val rawText = buffer.asCharSequence()
        if (rawText.isNotEmpty()) {
            val annotatedText = annotateText(rawText, viewFirstLine, viewLastLine, lineStyles, textMarkers)
            buffer.setComposition(0, rawText.length, annotatedText.annotations)
            buffer.changeTracker.trackChange(0, rawText.length, rawText.length)
            annotatedTextChange.invoke(annotatedText)
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
                    val offsetStart = (marker.position).coerceIn(0, rawText.length)
                    val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
                    when (marker.decoration) {
                        TextDecorationStyle.NONE -> addStyle(marker.style, offsetStart, offsetFinish)
                        TextDecorationStyle.STRAIGHT -> {
                            val ss = STRAIGHT.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                            ss?.let { addStyle(it, offsetStart, offsetFinish) }
                        }
                        TextDecorationStyle.SQUIGGLY -> {
                            val ss = SQUIGGLY.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                            ss?.let { addStyle(it, offsetStart, offsetFinish) }
                        }
                    }
                }
            }
        }
    }
}