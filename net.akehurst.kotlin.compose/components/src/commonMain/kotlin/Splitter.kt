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

package net.akehurst.kotlin.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Defines the orientation of a Split.
 */
enum class SplitOrientation { Horizontal, Vertical }

fun adjustSplitterWeights(totalSize:Float, f1:Float, f2:Float, dragAmount:Float) : Pair<Float,Float> {
    val deltaRatio = dragAmount / totalSize
    // Adjust weights, ensuring they stay positive
    var newF1 = (f1 + deltaRatio).coerceAtLeast(0.01f)
    var newF2 = (f2 - deltaRatio).coerceAtLeast(0.01f)
    // Re-normalize if necessary to maintain sum
    val adjustment = (f1 + f2) - (newF1 + newF2)
    newF1 = newF1 + adjustment / 2
    newF2 = newF2 + adjustment / 2
    // Ensure final weights are still positive after adjustment
    newF1 = newF1.coerceAtLeast(0.01f)
    newF2 = newF2.coerceAtLeast(0.01f)
    return Pair(newF1,newF2)
}

/**
 * A draggable splitter that allows resizing of adjacent panes.
 * @param orientation The orientation of the splitter (Vertical for horizontal resizing, Horizontal for vertical resizing).
 * @param thickness The visual thickness of the splitter.
 * @param onDrag Callback providing the drag amount.
 */
@Composable
fun Splitter(
    orientation: SplitOrientation,
    thickness: Dp,
    onDrag: (Float) -> Unit
) {
    val draggableState = rememberDraggableState { delta ->
        onDrag(delta)
    }

    Spacer(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.outlineVariant)
            .run {
                if (orientation == SplitOrientation.Vertical) {
                    width(thickness).fillMaxHeight()
                } else {
                    height(thickness).fillMaxWidth()
                }
            }
            .draggable(
                state = draggableState,
                orientation = if (orientation == SplitOrientation.Vertical) Orientation.Horizontal else Orientation.Vertical
            )
    )
}