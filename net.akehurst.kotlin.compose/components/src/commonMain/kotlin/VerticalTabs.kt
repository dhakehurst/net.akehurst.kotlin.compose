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
package net.akehurst.kotlin.compose.components.vtabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.components.table.SelectiveRoundedCornerShape

@Composable
fun VerticalTabbedPane(
    tabNames: List<String>,
    paneModifier: Modifier = Modifier,
    tabListModifier: Modifier = Modifier,
    tabContent: @Composable (tabName: String) -> Unit = { Text(text = it, modifier = Modifier.padding(5.dp)) },
    body: @Composable (selectedTab: Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    // keep the selection valid if the list of tabs gets shorter
    val shownTab = selectedTab.coerceIn(0, tabNames.lastIndex.coerceAtLeast(0))
    Row(
        modifier = paneModifier
    ) {
        Column(
            modifier = tabListModifier
        ) {
            tabNames.forEachIndexed { index, tabName ->
                VerticalTab(tabName, index == shownTab, onClick = { selectedTab = index }, tabContent)
            }
        }
        body.invoke(shownTab)
    }
}

@Composable
fun VerticalTab(tabName: String, isSelected: Boolean, onClick: () -> Unit, content: @Composable (tabName: String) -> Unit) {
    val shape = SelectiveRoundedCornerShape(
        cornerRadius = 5.dp,
        hasRight = isSelected.not(),
        topRightCurved = false,
        bottomRightCurved = false,
    )
    val bg = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.onBackground, shape = shape)
            .background(bg)
    ) {
        content.invoke(tabName)
    }
}