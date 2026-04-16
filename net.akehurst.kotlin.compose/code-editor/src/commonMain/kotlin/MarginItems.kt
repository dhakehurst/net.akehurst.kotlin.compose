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

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.akehurst.kotlin.compose.editor.api.MarginItem

data class MarginItemDefault(
    override val lineNumber: Int,
    override val kind: String,
    override val text: String,
    override val icon: ImageVector,
    override val color: Color
) : MarginItem

data class MarginItemListState(
    val marginWidth: Dp,
    val visibleItems: List<MarginItemState>
)

data class MarginItemState(
    val item: MarginItem,
    val offsetFromTopOfViewport: Float,
    val detailOffsetFromTopOfViewport: Float,
)

@Stable
class MarginItemsStateHolder {

    val _mutableStateFlow = MutableStateFlow(emptyList<MarginItem>())
    val stateFlow = _mutableStateFlow.asStateFlow()

    val value get() = _mutableStateFlow.value

    fun clear() {
        _mutableStateFlow.update { emptyList() }
    }

    fun addAnnotation(lineNumber: Int, kind:String, text: String, icon: ImageVector, color: Color) {
        _mutableStateFlow.update {
            it + MarginItemDefault(lineNumber, kind, text, icon, color)
        }
    }
}