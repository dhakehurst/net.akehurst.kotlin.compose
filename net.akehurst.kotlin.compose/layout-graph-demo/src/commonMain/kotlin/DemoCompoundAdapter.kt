package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.components.layout.graph.CollapsePolicy
import net.akehurst.kotlin.components.layout.graph.GraphLayoutCompoundEdge
import net.akehurst.kotlin.components.layout.graph.GraphLayoutCompoundGraph
import net.akehurst.kotlin.components.layout.graph.GraphLayoutCompoundGraphState
import net.akehurst.kotlin.components.layout.graph.GraphLayoutCompoundNode

fun DemoScenario.toCompoundGraphState(): GraphLayoutCompoundGraphState {
    val root = GraphLayoutCompoundGraph(id = "root")
    val graphById = mutableMapOf(root.id to root)
    val parentGraphByGraphId = mutableMapOf<String, String?>()
    parentGraphByGraphId[root.id] = null

    val nodesById = nodes.associateBy { it.id }
    val containerIds = nodes.mapNotNull { it.containerId }.toSet()
    containerIds.forEach { containerId ->
        if (containerId !in graphById) {
            val containerNode = nodesById[containerId]
            val collapsed = containerNode?.defaultCollapsed == true
            graphById[containerId] = GraphLayoutCompoundGraph(
                id = containerId,
                collapsePolicy = if (collapsed) CollapsePolicy.COLLAPSED_BY_DEFAULT else CollapsePolicy.EXPANDED_BY_DEFAULT,
                isCollapsed = collapsed
            )
        }
    }

    val ownerGraphByNodeId = mutableMapOf<String, String>()
    nodes.sortedBy { it.id }.forEach { node ->
        val ownerGraphId = node.containerId ?: root.id
        val owner = graphById.getOrPut(ownerGraphId) { GraphLayoutCompoundGraph(id = ownerGraphId) }
        owner.nodes[node.id] = GraphLayoutCompoundNode(
            id = node.id,
            widthHint = node.width.toDouble(),
            heightHint = node.height.toDouble()
        )
        ownerGraphByNodeId[node.id] = ownerGraphId
    }

    containerIds.sorted().forEach { containerId ->
        val containerNode = nodesById[containerId] ?: return@forEach
        val parentGraphId = containerNode.containerId ?: root.id
        val parent = graphById.getOrPut(parentGraphId) { GraphLayoutCompoundGraph(id = parentGraphId) }
        val child = graphById.getValue(containerId)
        parent.children[containerId] = child
        parentGraphByGraphId[containerId] = parentGraphId
    }

    edges.sortedBy { it.id }.forEach { edge ->
        val sourceOwner = ownerGraphByNodeId[edge.sourceId] ?: root.id
        val targetOwner = ownerGraphByNodeId[edge.targetId] ?: root.id
        val ownerGraphId = lowestCommonAncestor(sourceOwner, targetOwner, parentGraphByGraphId, root.id)
        val owner = graphById.getOrPut(ownerGraphId) { GraphLayoutCompoundGraph(id = ownerGraphId) }
        owner.edges[edge.id] = GraphLayoutCompoundEdge(
            id = edge.id,
            sourceId = edge.sourceId,
            targetId = edge.targetId
        )
    }

    return GraphLayoutCompoundGraphState(id = id, root = root).also { state ->
        // Register Compose content for every node.
        // Containers get a shaded background + header label; leaf nodes get a centred label.
        // Any node whose content is NOT registered here would show the red ⚠ error indicator.
        nodes.sortedBy { it.id }.forEach { node ->
            val isContainer = nodes.any { it.containerId == node.id }
            node.content?.let{state.addNodeContent(node.id,it)}
                ?: state.addNodeContent(node.id) {
                    if (isContainer) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE8F0FE))
                                .border(1.5.dp, Color(0xFF3F7ACC))
                                .padding(start = 8.dp, top = 4.dp)
                        ) {
                            Text(
                                text = node.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF3F7ACC)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFEFF8EF))
                                .border(1.5.dp, Color(0xFF409C55))
                        ) {
                            Text(
                                text = node.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2D6E3E)
                            )
                        }
                    }
                }
        }

        // Register edge content: empty list signals "intentionally unlabelled plain line".
        // Remove or skip the addEdgeContent call for an edge to see the red ⚠ error indicator instead.
        edges.sortedBy { it.id }.forEach { edge ->
            state.addEdgeContent(edge.id, emptyList())
        }
    }
}

private fun lowestCommonAncestor(
    graphA: String,
    graphB: String,
    parentGraphByGraphId: Map<String, String?>,
    rootId: String
): String {
    val ancestorsA = mutableSetOf<String>()
    var currentA: String? = graphA
    while (currentA != null) {
        ancestorsA.add(currentA)
        currentA = parentGraphByGraphId[currentA]
    }

    var currentB: String? = graphB
    while (currentB != null) {
        if (currentB in ancestorsA) {
            return currentB
        }
        currentB = parentGraphByGraphId[currentB]
    }

    return rootId
}

