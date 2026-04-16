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
package net.akehurst.kotlin.compose.components.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf

//TODO: use nak.kotlinx.Tree
data class TreeViewNode(
    val id: String
) {
    var content: @Composable () -> Unit = { Text(text = id, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    var hasChildren: Boolean = false
    var fetchChildren: (suspend () -> List<TreeViewNode>)? = null
    var children = mutableStateFlowHolderOf<List<TreeViewNode>>(emptyList())
    var expanded by mutableStateOf(false)

    val data: MutableMap<String, Any?> = mutableMapOf()

    suspend fun fetchedChildren() = fetchChildren?.let{
        val fetched = it.invoke()
        children.update { fetched }
        children
    } ?: children
}



@Stable
class TreeViewStateHolder(

) {
    companion object {
        /**
         * Computes a flattened list of currently visible nodes (respecting expanded state)
         * and a map from child node to its parent node.
         */
        fun flattenVisible(
            nodes: List<TreeViewNode>,
            parent: TreeViewNode? = null,
            outList: MutableList<TreeViewNode> = mutableListOf(),
            outParentMap: MutableMap<TreeViewNode, TreeViewNode?> = mutableMapOf(),
        ): Pair<List<TreeViewNode>, Map<TreeViewNode, TreeViewNode?>> {
            for (node in nodes) {
                outList.add(node)
                outParentMap[node] = parent
                if (node.expanded && node.hasChildren) {
                    flattenVisible(node.children.value, node, outList, outParentMap)
                }
            }
            return outList to outParentMap
        }
    }

    var items = mutableStateFlowHolderOf(listOf(TreeViewNode("<no content>")))
    val lazyListState = LazyListState()
    var selectedItem by mutableStateOf<TreeViewNode?>(null)

    fun updateItems(newItems: List<TreeViewNode>) {
        items.update { newItems }
    }

    /**
     * Expands the given node and all its descendants, fetching children as needed.
     */
    suspend fun expandAll(node: TreeViewNode) {
        if (node.hasChildren) {
            node.expanded = true
            val childList = node.fetchedChildren().value
            for (child in childList) {
                expandAll(child)
            }
        }
    }

    /**
     * Collapses the given node and all its descendants.
     */
    suspend fun collapseAll(node: TreeViewNode) {
        node.expanded = false
        val childList = node.fetchedChildren().value
        for (child in childList) {
            collapseAll(child)
        }
    }

}

@Composable
fun TreeView(
    stateHolder: TreeViewStateHolder,
    onSelectItem: (item: TreeViewNode) -> Unit = {},
    expanded: @Composable (Modifier) -> Unit = { expMod -> Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Close", modifier = expMod) },
    collapsed: @Composable (Modifier) -> Unit = { colMod -> Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Open", modifier = colMod.graphicsLayer { rotationZ = -90f }) },
    modifier: Modifier = Modifier,
) {

    val nodes = stateHolder.items.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val toggleExpanded: (TreeViewNode) -> Unit = { node ->
        if (node.expanded) {
            node.expanded = false
        } else {
            node.expanded = true
            if (node.hasChildren) {
                coroutineScope.launch {
                    node.fetchedChildren()
                }
            }
        }
    }

    val selectNode: (TreeViewNode) -> Unit = { node ->
        stateHolder.selectedItem = node
        onSelectItem(node)
    }

    LazyColumn(
        state = stateHolder.lazyListState,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val (visibleNodes, parentMap) = TreeViewStateHolder.flattenVisible(nodes.value)
                val currentIndex = visibleNodes.indexOf(stateHolder.selectedItem)
                when (event.key) {
                    Key.DirectionDown -> {
                        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(visibleNodes.lastIndex)
                        selectNode(visibleNodes[nextIndex])
                        coroutineScope.launch { stateHolder.lazyListState.animateScrollToItem(nextIndex) }
                        true
                    }

                    Key.DirectionUp -> {
                        val prevIndex = if (currentIndex < 0) 0 else (currentIndex - 1).coerceAtLeast(0)
                        selectNode(visibleNodes[prevIndex])
                        coroutineScope.launch { stateHolder.lazyListState.animateScrollToItem(prevIndex) }
                        true
                    }

                    Key.DirectionRight -> {
                        val sel = stateHolder.selectedItem
                        val hasModifier = event.isCtrlPressed || event.isMetaPressed
                        if (sel != null && sel.hasChildren) {
                            if (hasModifier) {
                                coroutineScope.launch { stateHolder.expandAll(sel) }
                            } else if (!sel.expanded) {
                                toggleExpanded(sel)
                            } else {
                                val children = sel.children.value
                                if (children.isNotEmpty()) {
                                    selectNode(children.first())
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        val sel = stateHolder.selectedItem
                        val hasModifier = event.isCtrlPressed || event.isMetaPressed
                        if (sel != null) {
                            if (hasModifier) {
                                coroutineScope.launch { stateHolder.collapseAll(sel) }
                            } else if (sel.expanded) {
                                toggleExpanded(sel)
                            } else {
                                val parent = parentMap[sel]
                                if (parent != null) {
                                    selectNode(parent)
                                }
                            }
                        }
                        true
                    }

                    else -> false
                }
            },
    ) {
        nodes(
            level = 0,
            parentPath = "",
            nodes = nodes.value,
            isExpanded = { it.expanded },
            toggleExpanded = toggleExpanded,
            onSelectItem = selectNode,
            selectedItem = stateHolder.selectedItem,
            expanded,
            collapsed
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

fun LazyListScope.nodes(
    level: Int,
    parentPath: String,
    nodes: List<TreeViewNode>,
    isExpanded: (TreeViewNode) -> Boolean,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    selectedItem: TreeViewNode?,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit
) {
    nodes.forEach { node ->
        node(
            level,
            parentPath,
            node,
            isExpanded = isExpanded,
            toggleExpanded = toggleExpanded,
            onSelectItem = onSelectItem,
            selectedItem = selectedItem,
            expanded,
            collapsed
        )
    }
}

fun LazyListScope.node(
    level: Int,
    parentPath: String,
    node: TreeViewNode,
    isExpanded: (TreeViewNode) -> Boolean,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    selectedItem: TreeViewNode?,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit
) {
    val nodePath = "$parentPath/${node.id}"
    val isNodeExpanded = isExpanded(node)
    item(key = nodePath) {
        // Collect children as Compose state so recomposition is triggered when children are fetched
        val childrenState by node.children.collectAsState()
        val isSelected = selectedItem == node
        val rowModifier = if (isSelected) {
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
        } else {
            Modifier.fillMaxWidth()
        }

        Row(modifier = rowModifier) {
            Spacer(Modifier.width((20 * level).dp))
            when {
                node.hasChildren -> when {
                    isNodeExpanded -> expanded.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
                    else -> collapsed.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
                }

                else -> Spacer(Modifier.width(20.dp))
            }
            Row(modifier = Modifier.clickable { onSelectItem.invoke(node) }) {
                node.content.invoke()
            }
        }
        if (isNodeExpanded) {
            Column {
                childrenState.forEach { child ->
                    NodeRow(
                        level = level + 1,
                        node = child,
                        isExpanded = isExpanded,
                        toggleExpanded = toggleExpanded,
                        onSelectItem = onSelectItem,
                        selectedItem = selectedItem,
                        expanded = expanded,
                        collapsed = collapsed,
                    )
                }
            }
        }
    }
}

@Composable
fun NodeRow(
    level: Int,
    node: TreeViewNode,
    isExpanded: (TreeViewNode) -> Boolean,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    selectedItem: TreeViewNode?,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit,
) {
    val childrenState by node.children.collectAsState()
    val nodeExpanded = isExpanded(node)
    val isSelected = selectedItem == node
    val rowModifier = if (isSelected) {
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(modifier = rowModifier) {
        Spacer(Modifier.width((20 * level).dp))
        when {
            node.hasChildren -> when {
                nodeExpanded -> expanded.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
                else -> collapsed.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
            }

            else -> Spacer(Modifier.width(20.dp))
        }
        Row(modifier = Modifier.clickable { onSelectItem.invoke(node) }) {
            node.content.invoke()
        }
    }
    if (nodeExpanded) {
        Column {
            childrenState.forEach { child ->
                NodeRow(
                    level = level + 1,
                    node = child,
                    isExpanded = isExpanded,
                    toggleExpanded = toggleExpanded,
                    onSelectItem = onSelectItem,
                    selectedItem = selectedItem,
                    expanded = expanded,
                    collapsed = collapsed,
                )
            }
        }
    }
}