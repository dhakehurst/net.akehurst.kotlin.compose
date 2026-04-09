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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.components.table.TableState
import net.akehurst.kotlin.compose.components.table.TableView
import net.akehurst.kotlinx.issues.api.Issue

interface ModalDialog

data class AlertInfo(
    val title: String,
    val message: String,
    val issues: Set<Issue<*>>,
) : ModalDialog

@Composable
fun AlertDialogWithIssues(
    alertInfo: AlertInfo,
    onDismissRequest: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = alertInfo.title) },
        text = {
            BoxWithConstraints {
                val windowHeight = maxHeight
                val windowWith = maxWidth
                Column {
                    Text(text = alertInfo.message)
                    if (alertInfo.issues.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        TableView(
                            state = TableState(),
                            tableModifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = windowWith * 0.6f)
                                .heightIn(max = windowHeight * 0.6f)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .border(width = 2.dp, color = Color.Black),
                            headerModifier = Modifier
                                .fillMaxWidth()
                                .border(width = 1.dp, color = Color.Black),
                            headerContent = {
                                tableHeader {
                                    tableHeaderCell(0, boxModifier = Modifier.width(100.dp)) { Text("Kind", fontWeight = FontWeight.Bold) }
                                    tableHeaderCell(1, boxModifier = Modifier.width(100.dp)) { Text("Location", fontWeight = FontWeight.Bold) }
                                    tableHeaderCell(2, boxModifier = Modifier.widthIn(min = 200.dp)) { Text("Message", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) }
                                }
                            },
                            bodyModifier = Modifier
                                .fillMaxWidth()
                        ) {
                            alertInfo.issues.forEach { issue ->
                                tableRow(
                                    rowModifier = {
                                        Modifier
                                            .fillMaxWidth()
                                            .border(width = 1.dp, color = Color.Black)
                                            .background(MaterialTheme.colorScheme.surface)
                                    }
                                ) {
                                    tableCell(0) { Text(issue.kind.name, modifier = Modifier.fillMaxWidth()) }
                                    tableCell(1) { Text(issue.location.toString(), modifier = Modifier.fillMaxWidth()) }
                                    tableCell(2, boxModifier = Modifier.fillMaxWidth()) { Text(issue.message, modifier = Modifier.fillMaxWidth()) }
                                }
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = { Button(onClick = onCloseRequest) { Text("Close") } },
    )
}