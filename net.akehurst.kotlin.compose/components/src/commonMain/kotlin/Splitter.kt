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

/**
 * Move [dragAmount] (in the same units as [totalSize]) of weight from the second pane to the first.
 * The sum of the two weights stays the same and neither weight goes below 0.01.
 */
fun adjustSplitterWeights(totalSize:Float, f1:Float, f2:Float, dragAmount:Float) : Pair<Float,Float> {
    val minWeight = 0.01f
    val sum = f1 + f2
    if (totalSize <= 0f || sum <= 2 * minWeight) return Pair(f1, f2)
    val deltaRatio = dragAmount / totalSize
    val newF1 = (f1 + deltaRatio).coerceIn(minWeight, sum - minWeight)
    return Pair(newF1, sum - newF1)
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