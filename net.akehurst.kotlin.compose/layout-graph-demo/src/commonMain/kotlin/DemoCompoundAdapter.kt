package net.akehurst.kotlin.components.layout.graph.demo

import net.akehurst.kotlin.components.layout.graph.*

fun DemoScenario.toCompoundGraphState(): GraphLayoutCompoundGraphState {
    val normalizedNodes = normalizeRegionTiles(nodes)
    val root = GraphLayoutCompoundGraph(id = "root")
    val graphById = mutableMapOf(root.id to root)
    val parentGraphByGraphId = mutableMapOf<String, String?>()
    parentGraphByGraphId[root.id] = null

    val nodesById = normalizedNodes.associateBy { it.id }
    val containerIds = normalizedNodes.mapNotNull { it.containerId }.toSet()
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
    normalizedNodes.sortedBy { it.id }.forEach { node ->
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
        normalizedNodes.sortedBy { it.id }.forEach { node ->
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

private fun normalizeRegionTiles(nodes: List<DemoNode>): List<DemoNode> {
    val nodesById = nodes.associateBy { it.id }
    val childNodesByContainerId = nodes.filter { it.containerId != null }.groupBy { it.containerId!! }
    val normalizedById = nodes.associateBy { it.id }.toMutableMap()

    childNodesByContainerId.keys.sorted().forEach { containerId ->
        val container = nodesById[containerId] ?: return@forEach
        if (container.childLayout != ChildLayout.TESSELLATE) {
            return@forEach
        }
        val children = childNodesByContainerId[containerId].orEmpty().sortedBy { it.id }
        if (children.isEmpty()) {
            return@forEach
        }

        val cols = kotlin.math.ceil(kotlin.math.sqrt(children.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(children.size.toDouble() / cols.toDouble()).toInt().coerceAtLeast(1)
        val tileWidth = container.width / cols.toFloat()
        val tileHeight = container.height / rows.toFloat()

        children.forEachIndexed { index, child ->
            val row = index / cols
            val col = index % cols
            normalizedById[child.id] = child.copy(
                x = container.x + (col * tileWidth),
                y = container.y + (row * tileHeight),
                width = tileWidth,
                height = tileHeight
            )
        }
    }

    return nodes.map { normalizedById.getValue(it.id) }
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

