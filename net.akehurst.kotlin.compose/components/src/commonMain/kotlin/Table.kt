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
package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

//TODO: fix this
@Stable
class TableState(
    isLazy:Boolean = false,
) {
    var isLazy by mutableStateOf(isLazy)
    val columnWidthPx = mutableStateMapOf<Int, Int>()
}

@Composable
fun TableView(
    state: TableState,
    tableModifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    footerModifier: Modifier = Modifier,
    headerContent: @Composable TableHeaderScope.() -> Unit = {},
    footerContent: @Composable TableFooterScope.() -> Unit = {},
    bodyContent: @Composable TableBodyScope.() -> Unit = {}
) {
    Column(
        modifier = tableModifier
    ) {
        // header
        Row(
            modifier = headerModifier,
        ) {
            headerContent.invoke(object : TableHeaderScope, RowScope by this {
                @Composable
                override fun tableHeader(rowModifier: Modifier, content: @Composable (TableHeaderRowScope.() -> Unit)) {
                    content.invoke(object : TableHeaderRowScope, RowScope by this {
                        @Composable
                        override fun tableHeaderCell(column: Int, boxModifier: Modifier, content: @Composable BoxScope.() -> Unit) {
                            Box(modifier = boxModifier.onSizeChanged { size -> state.columnWidthPx[column] = size.width }) {
                                content.invoke(this)
                            }
                        }
                    })
                }
            })
        }
        // body
        if (state.isLazy) {
            lazyBody(state, bodyModifier, bodyContent)
        } else {
            staticBody(state, bodyModifier, bodyContent)
        }
        // footer
        Row(
            modifier = footerModifier,
        ) {
            footerContent.invoke(object : TableFooterScope, RowScope by this {
                @Composable
                override fun tableFooter(rowModifier: Modifier, content: @Composable TableFooterRowScope.() -> Unit) {
                    content.invoke(object : TableFooterRowScope, RowScope by this {
                        @Composable
                        override fun tableFooterCell(boxModifier: Modifier, content: @Composable (BoxScope.() -> Unit)) {
                            Box(modifier = boxModifier) {
                                content.invoke(this)
                            }
                        }
                    })
                }
            })
        }
    }
}


@Composable
fun lazyBody(state: TableState, bodyModifier: Modifier, bodyContent: @Composable TableBodyScope.() -> Unit = {}) {
    // Create a fresh list for this composition (not remembered, so it's recreated on each composition)
    val rowConfigs = mutableListOf<Pair<@Composable () -> Modifier, @Composable TableRowScope.() -> Unit>>()

    // Execute bodyContent to collect row definitions
    bodyContent(
        object : TableBodyScope {
            @Composable
            override fun tableRow(rowModifier: @Composable () -> Modifier, content: @Composable TableRowScope.() -> Unit) {
                rowConfigs.add(Pair(rowModifier, content))
            }
        }
    )

    // Render the collected rows in LazyColumn
    LazyColumn(
        modifier = bodyModifier
    ) {
        items(rowConfigs.size) { index ->
            val (rowModifier, content) = rowConfigs[index]
            Row(modifier = rowModifier()) {
                val tableRowScope = object : TableRowScope, RowScope by this {
                    @Composable
                    override fun tableCell(column: Int, boxModifier: Modifier, content: @Composable BoxScope.() -> Unit) {
                        val widthPx = state.columnWidthPx[column] ?: 50
                        val widthDp = with(LocalDensity.current) { widthPx.toDp() }
                        Box(modifier = boxModifier.width(widthDp)) {
                            content.invoke(this)
                        }
                    }
                }
                content.invoke(tableRowScope)
            }
        }
    }
}





@Composable
fun staticBody(state: TableState, bodyModifier: Modifier, bodyContent: @Composable TableBodyScope.() -> Unit = {}) {
    // Create a fresh list for this composition (not remembered, so it's recreated on each composition)
    val rowConfigs = mutableListOf<Pair<@Composable () -> Modifier, @Composable TableRowScope.() -> Unit>>()

    // Execute bodyContent to collect row definitions
    bodyContent(
        object : TableBodyScope {
            @Composable
            override fun tableRow(rowModifier: @Composable () -> Modifier, content: @Composable TableRowScope.() -> Unit) {
                rowConfigs.add(Pair(rowModifier, content))
            }
        }
    )

    // Render the collected rows in Column
    Column(
        modifier = bodyModifier
    ) {
        rowConfigs.forEachIndexed { _, (rowModifier, content) ->
            Row(modifier = rowModifier()) {
                val tableRowScope = object : TableRowScope, RowScope by this {
                    @Composable
                    override fun tableCell(column: Int, boxModifier: Modifier, content: @Composable BoxScope.() -> Unit) {
                        val widthPx = state.columnWidthPx[column] ?: 50
                        val widthDp = with(LocalDensity.current) { widthPx.toDp() }
                        Box(modifier = boxModifier.width(widthDp)) {
                            content.invoke(this)
                        }
                    }
                }
                content.invoke(tableRowScope)
            }
        }
    }
}


interface TableHeaderScope : RowScope {
    @Composable
    fun tableHeader(rowModifier: Modifier = Modifier, content: @Composable TableHeaderRowScope.() -> Unit = {})
}

interface TableHeaderRowScope : RowScope {
    @Composable
    fun tableHeaderCell(column: Int, boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

interface TableFooterScope : RowScope {
    @Composable
    fun tableFooter(rowModifier: Modifier = Modifier, content: @Composable TableFooterRowScope.() -> Unit = {})
}

interface TableFooterRowScope : RowScope {
    @Composable
    fun tableFooterCell(boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

interface TableBodyScope {
    @Composable
    fun tableRow(rowModifier: @Composable () -> Modifier = { Modifier }, content: @Composable TableRowScope.() -> Unit = {})
}


interface TableRowScope : RowScope {
    @Composable
    fun tableCell(column: Int, boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

