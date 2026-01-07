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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import net.akehurst.kotlin.compose.components.tree.TreeView
import net.akehurst.kotlin.compose.components.tree.TreeViewNode
import net.akehurst.kotlin.compose.components.tree.TreeViewStateHolder

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TreeView Test") {
        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                TreeViewTestContent()
            }
        }
    }
}

@Composable
fun TreeViewTestContent() {
    val stateHolder = remember { TreeViewStateHolder() }

    LaunchedEffect(Unit) {
        // Simulate loading root items
        val root1 = TreeViewNode("Root 1").apply {
            content = { Text("Root 1") }
            hasChildren = true
            fetchChildren = {
                delay(500) // Simulate async load
                listOf(
                    TreeViewNode("Child 1.1").apply {
                        content = { Text("Child 1.1") }
                        hasChildren = true
                        fetchChildren = {
                            delay(500)
                            listOf(
                                TreeViewNode("Grandchild 1.1.1").apply {
                                    content = { Text("Grandchild 1.1.1") }
                                },
                                TreeViewNode("Grandchild 1.1.2").apply {
                                    content = { Text("Grandchild 1.1.2") }
                                }
                            )
                        }
                    },
                    TreeViewNode("Child 1.2").apply {
                        content = { Text("Child 1.2") }
                    }
                )
            }
        }

        val root2 = TreeViewNode("Root 2").apply {
            content = { Text("Root 2") }
            hasChildren = true
            fetchChildren = {
                delay(500)
                listOf(
                    TreeViewNode("Child 2.1").apply {
                        content = { Text("Child 2.1") }
                    }
                )
            }
        }

        stateHolder.updateItems(listOf(root1, root2))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TreeView Refresh Test", style = MaterialTheme.typography.headlineMedium)
        Text("Expand nodes to test lazy loading of children", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))

        TreeView(
            stateHolder = stateHolder,
            onSelectItem = { node ->
                println("Selected: ${node.id}")
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

