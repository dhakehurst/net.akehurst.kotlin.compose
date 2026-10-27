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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
/**
 * A node in a [TreeView].
 *
 * Nodes are compared by object identity, so two nodes with the same [id] in different branches are distinct.
 * Sibling nodes must have distinct ids, because the path of ids is used as the list item key.
 */
class TreeViewNode(
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

    override fun toString(): String = "TreeViewNode($id)"
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
     * Collapses the given node and all its already fetched descendants.
     * Does not fetch children.
     */
    fun collapseAll(node: TreeViewNode) {
        node.expanded = false
        for (child in node.children.value) {
            collapseAll(child)
        }
    }

}

/**
 * One row of the tree as currently shown: the node, its depth and its id path from the root.
 */
private data class VisibleTreeNode(
    val node: TreeViewNode,
    val level: Int,
    val path: String
)

/**
 * Flattens the expanded part of the tree into rows, in display order.
 * Returns a value, so it is not a separate recompose scope: any change to an expanded flag
 * or to the children of an expanded node recomposes the calling [TreeView].
 */
@Composable
private fun visibleTreeNodes(nodes: List<TreeViewNode>, level: Int, parentPath: String): List<VisibleTreeNode> {
    val result = mutableListOf<VisibleTreeNode>()
    for (node in nodes) {
        key(node) {
            val path = "$parentPath/${node.id}"
            result.add(VisibleTreeNode(node, level, path))
            if (node.expanded && node.hasChildren) {
                val children by node.children.collectAsState()
                result.addAll(visibleTreeNodes(children, level + 1, path))
            }
        }
    }
    return result
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
    val visibleNodes = visibleTreeNodes(nodes.value, 0, "")
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

    // index is into visibleNodes, which matches the LazyColumn item index
    val selectAt: (Int) -> Unit = { index ->
        selectNode(visibleNodes[index].node)
        coroutineScope.launch { stateHolder.lazyListState.animateScrollToItem(index) }
    }

    LazyColumn(
        state = stateHolder.lazyListState,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (visibleNodes.isEmpty()) return@onPreviewKeyEvent false
                val currentIndex = visibleNodes.indexOfFirst { it.node === stateHolder.selectedItem }
                when (event.key) {
                    Key.DirectionDown -> {
                        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(visibleNodes.lastIndex)
                        selectAt(nextIndex)
                        true
                    }

                    Key.DirectionUp -> {
                        val prevIndex = if (currentIndex < 0) 0 else (currentIndex - 1).coerceAtLeast(0)
                        selectAt(prevIndex)
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
                            } else if (currentIndex >= 0 && currentIndex < visibleNodes.lastIndex && visibleNodes[currentIndex + 1].level > visibleNodes[currentIndex].level) {
                                // first child is the next visible row
                                selectAt(currentIndex + 1)
                            }
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        val sel = stateHolder.selectedItem
                        val hasModifier = event.isCtrlPressed || event.isMetaPressed
                        if (sel != null) {
                            if (hasModifier) {
                                stateHolder.collapseAll(sel)
                            } else if (sel.expanded) {
                                toggleExpanded(sel)
                            } else if (currentIndex > 0) {
                                // parent is the nearest earlier row one level up
                                val level = visibleNodes[currentIndex].level
                                val parentIndex = (currentIndex - 1 downTo 0).firstOrNull { visibleNodes[it].level == level - 1 }
                                if (parentIndex != null) {
                                    selectAt(parentIndex)
                                }
                            }
                        }
                        true
                    }

                    else -> false
                }
            },
    ) {
        items(visibleNodes, key = { it.path }) { visibleNode ->
            TreeNodeRow(
                level = visibleNode.level,
                node = visibleNode.node,
                toggleExpanded = toggleExpanded,
                onSelectItem = selectNode,
                isSelected = stateHolder.selectedItem === visibleNode.node,
                expanded = expanded,
                collapsed = collapsed,
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun TreeNodeRow(
    level: Int,
    node: TreeViewNode,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    isSelected: Boolean,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit,
) {
    val rowModifier = if (isSelected) {
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(modifier = rowModifier) {
        Spacer(Modifier.width((20 * level).dp))
        when {
            node.hasChildren -> when {
                node.expanded -> expanded.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
                else -> collapsed.invoke(Modifier.size(24.dp).clickable { toggleExpanded(node) })
            }

            else -> Spacer(Modifier.width(20.dp))
        }
        Row(modifier = Modifier.clickable { onSelectItem.invoke(node) }) {
            node.content.invoke()
        }
    }
}
