package net.akehurst.kotlin.components.layout.graph.demo

import net.akehurst.kotlin.components.layout.graph.*

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
            val childLayout = containerNode?.childLayout
            val isRegionContainer = containerNode
                ?.containerId
                ?.let { parentId -> nodesById[parentId]?.childLayout == ChildLayout.TESSELLATE }
                ?: false
            graphById[containerId] = GraphLayoutCompoundGraph(
                id = containerId,
                childLayout = childLayout,
                routeBoundary = !isRegionContainer,
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
            widthHint = node.widthHint?.toDouble(),
            heightHint = node.heightHint?.toDouble(),
            containerPaddingHint = node.paddingHint
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
        nodes.sortedBy { it.id }.forEach { node ->
            node.content.let { state.addNodeContent(node.id, it) }
        }

        // Register edge rendering content.
        // Use GraphLayoutEdgeContent() for an intentionally plain line.
        // Remove or skip the addEdgeContent call for an edge to see the red ⚠ error indicator instead.
        edges.sortedBy { it.id }.forEach { edge ->
            state.addEdgeContent(edge.id, edge.content)
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

