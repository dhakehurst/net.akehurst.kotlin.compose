package net.akehurst.kotlin.components.layout.graph.demo

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
            graphById[containerId] = GraphLayoutCompoundGraph(id = containerId)
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

    return GraphLayoutCompoundGraphState(id = id, root = root)
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

