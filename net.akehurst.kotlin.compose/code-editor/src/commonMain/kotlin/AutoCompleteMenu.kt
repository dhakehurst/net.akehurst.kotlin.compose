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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.editor.api.*

fun String.fixLength(maxLen: Int) = when {
    maxLen < 0 -> this
    maxLen > this.length -> this
    else -> this.substring(0, maxLen - 1) + "\u2026"
}

fun <T> MutableList<T>.setOrAdd(index: Int, element: T) {
    when {
        index < this.size -> this[index] = element
        index == this.size -> this.add(element)
        else -> {
            while (this.size < index) {
                this.add(-1 as T)
            }
            this.add(element)
        }
    }
}

/*
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutocompletePopup(
    modifier: Modifier = Modifier,
    state: AutocompleteStateCompose
) {
    if (state.isVisible) {
        Surface(
            shadowElevation = 1.dp,
            border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.onSurface),

            //.wrapContentSize(unbounded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
//                    .width(300.dp)
//                    .heightIn(min = 30.dp, max = 250.dp)

            ) {
                // List
                LazyColumn(
                    state = state.lazyListState
                ) {
                    itemsIndexed(state.items) { idx, item ->
                        Row(
                            modifier = Modifier
                                .background(color = if (state.isSelected(idx)) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .padding(top = 2.dp, bottom = 3.dp, start = 2.dp, end = 2.dp)
                                .onClick(
                                    onClick = { state.choose(item) },
                                )
                        ) {
                            item.label?.let { Text("${it.fixLength(state.itemLabelLength)}: ") }
                            Spacer(Modifier.width(20.dp))
                            Text(item.text.fixLength(state.itemTextLength))
                        }
                    }
                }
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }

                    state.items.isEmpty() -> {
                        Text("No suggestions")
                    }
                }
            }
        }
    }
}
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutocompletePopup2(
    modifier: Modifier = Modifier,
    state: AutocompleteStateCompose
) {
    if (state.isVisible) {
        Box(
            modifier = Modifier
                .offset { state.getMenuOffset() }
                .padding(vertical = 2.dp)
                .onPreviewKeyEvent { ev -> state.handlePreviewKeyEvent(ev) }
        ) {
            state.dropdownItemHeight.clear()
            DropdownMenu(
                expanded = state.isVisible,
                onDismissRequest = { state.close() },
                border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.onSurface),
                properties = PopupProperties(focusable = false),
                scrollState = state.scrollState,
                modifier = modifier
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }

                    state.items.isEmpty() -> {
                        Text("No suggestions")
                    }

                    else -> {
                        var nonDividerIdx = 0
                        state.items.forEachIndexed { idx, item ->
                            val oddNum = nonDividerIdx % 2 == 0
                            val rowBgColour = when {
                                state.isSelected(idx) -> MaterialTheme.colorScheme.primaryContainer
                                oddNum -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val textDefaultColour = when {
                                state.isSelected(idx) -> MaterialTheme.colorScheme.onPrimaryContainer
                                oddNum -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            when (item) {
                                is AutocompleteItemDivider -> HorizontalDivider(
                                    thickness = 2.dp, color = Color.Black,
                                    modifier = Modifier
                                        // need height of items in order to scroll
                                        .onSizeChanged { size ->
                                            state.dropdownItemHeight.setOrAdd(idx, size.height)
                                        }
                                )

                                is AutocompleteItemContent -> {
                                    nonDividerIdx++
//                                    DropdownMenuItem(
                                    MyDropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
//                                                    .layout { measurable, constraints ->
//                                                        val placeable = measurable.measure(constraints)
//                                                        state.dropdownItemHeight.setOrAdd(idx,placeable.height)
//                                                        layout(width = placeable.width, height = placeable.height) {
//                                                            placeable.placeRelative(x = 0, y = 0)
//                                                        }
//                                                    }
//                                                    .onSizeChanged { size ->
//                                                        state.dropdownItemHeight.setOrAdd(idx,size.height)
//                                                    }
                                                    //.border(1.dp, Color.Green)
                                                    .fillMaxWidth()
                                            ) {
                                                item.label?.let { Text(it.fixLength(state.itemLabelLength), fontSize = 0.7.em, modifier = Modifier.weight(0.3f), color = Color.Gray) }
                                                Spacer(Modifier.width(5.dp))
                                                Text(item.text.fixLength(state.itemTextLength), modifier = Modifier.weight(0.7f), color = textDefaultColour)
                                            }
                                        },
                                        onClick = { state.choose(item) },
                                        contentPadding = PaddingValues(5.dp),
                                        modifier = Modifier
                                            // need height of items in order to scroll
                                            .onSizeChanged { size ->
                                                state.dropdownItemHeight.setOrAdd(idx, size.height)
                                            }
//                                            .height(with(LocalDensity.current) {
//                                                state.dropdownItemHeight.getOrNull(idx)?.toDp() ?: 20.dp }
//                                            )
//                                            .layout { measurable, constraints ->
//                                                val placeable = measurable.measure(constraints)
//                                                val h = state.dropdownItemHeight.getOrNull(idx)?: 48
//                                                layout(width = placeable.width, height = h) {
//                                                     val yOffset = (h - placeable.height) / 2
//                                                     placeable.placeRelative(x = 0, y = yOffset)
//                                                }
//                                            }
                                            .background(color = rowBgColour)
                                    )
                                }

                                else -> error("subtype not handled")
                            }
                        }
                    }
                }
            }
        }
    }
}


@Stable
class AutocompleteStateCompose(
    //val editorState: EditorState,
    val getText: () -> CharSequence,
    val getCursorPosition: () -> Int,
    val getMenuOffset: () -> IntOffset,
    val insertText: (offset: Int, text: String) -> Unit
) : AutocompleteState {

    var requestAutocompleteSuggestions: AutocompleteFunction = { _, _ -> }

    var itemTextLength by mutableStateOf(-1)
    var itemLabelLength by mutableStateOf(-1)
    val lazyListState = LazyListState()
    val scrollState = ScrollState(0)
    val dropdownItemHeight = mutableListOf<Int>()
    override var isVisible by mutableStateOf(false)
    override var isLoading by mutableStateOf(true)
    var selectedIndex by mutableStateOf<Int>(-1)
    var items = mutableStateListOf<AutocompleteItem>()

    //val scrollState by mutableStateOf(ScrollState(0))

    val selectedItem get() = items.getOrNull(selectedIndex)

    fun open() {
        isVisible = true
        isLoading = true
        requestSuggestions(false, 0, 0)
    }

    override fun clear() {
        items.clear()
    }

    fun scrollToSelected() {
        CoroutineScope(Dispatchers.Default).launch {
            val dist = dropdownItemHeight.take(selectedIndex).sum()
            scrollState.scrollTo(dist - 100)
        }
    }

    fun selectNext() {
        var nextItemIdx = selectedIndex + 1
        while (items.getOrNull(nextItemIdx) !is AutocompleteItemContent && nextItemIdx < items.size) {
            nextItemIdx++
        }
        when {
            nextItemIdx < items.size -> {
                selectedIndex = nextItemIdx
                scrollToSelected()
            }

            else -> Unit
        }
    }

    fun selectPrevious() {
        var nextItemIdx = selectedIndex - 1
        while (items.getOrNull(nextItemIdx) !is AutocompleteItemContent && nextItemIdx >= 0) {
            nextItemIdx--
        }
        when {
            nextItemIdx >= 0 -> {
                selectedIndex = nextItemIdx
                scrollToSelected()
            }

            else -> Unit
        }
    }

    fun selectPath(propDelta: Int) {
        requestSuggestions(true, propDelta, 0)
    }

    fun choose(item: AutocompleteItem) {
        selectedIndex = items.indexOf(item)
        chooseSelected()
    }

    fun isSelected(idx: Int): Boolean = selectedIndex == idx

    fun chooseSelected() {
        val sel = this.selectedItem
        when (sel) {
            is AutocompleteItemContent -> {
                insertText(sel.offset, sel.text)
                close()
            }
        }
    }

    fun close() {
        isVisible = false
        items.clear()
        selectedIndex = -1
    }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        var handled = true
        when {
            ev.isCtrlSpace -> requestSuggestions(true, 0, +1)
            else -> when (ev.key) {
                Key.Escape -> this.close()
                Key.Enter -> this.chooseSelected()
                Key.DirectionDown -> this.selectNext()
                Key.DirectionUp -> this.selectPrevious()
                Key.DirectionRight -> this.selectPath(+1)
                Key.DirectionLeft -> this.selectPath(-1)
                else -> handled = false
            }
        }
        return handled
    }

    private fun requestSuggestions(isOpen: Boolean, proposalPathDelta: Int, depthDelta: Int) {
        this.clear()
        val result = object : AutocompleteSuggestion {
            override fun provide(items: List<AutocompleteItem>) {
                this@AutocompleteStateCompose.items.addAll(items)
                isLoading = false
                if (items.isNotEmpty()) {
                    selectedIndex = 0
                    scrollToSelected()
                }
            }
        }
        CoroutineScope(Dispatchers.Default).launch {
            requestAutocompleteSuggestions.invoke(AutocompleteRequestData(getCursorPosition(), getText(), isOpen, selectedIndex, proposalPathDelta, depthDelta), result)
        }
    }

}

