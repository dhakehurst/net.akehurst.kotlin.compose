package net.akehurst.kotlin.components.layout.graph

/**
 * Compound graph root state used by the recursive layout pipeline.
 */
data class GraphLayoutCompoundGraphState(
    val id: String,
    val routing: EdgeRouting = EdgeRouting.DIRECT,
    val root: GraphLayoutCompoundGraph = GraphLayoutCompoundGraph("root")
)

data class GraphLayoutCompoundGraph(
    val id: String,
    val kind: CompoundGraphKind = CompoundGraphKind.GENERIC,
    val nodes: MutableMap<String, GraphLayoutCompoundNode> = mutableMapOf(),
    val edges: MutableMap<String, GraphLayoutCompoundEdge> = mutableMapOf(),
    val children: MutableMap<String, GraphLayoutCompoundGraph> = mutableMapOf(),
    val collapsePolicy: CollapsePolicy = CollapsePolicy.EXPANDED_BY_DEFAULT,
    var isCollapsed: Boolean = false,
    val padding: Double = 24.0
)

data class GraphLayoutCompoundNode(
    val id: String,
    val kind: NodeKind = NodeKind.NORMAL,
    val widthHint: Double? = null,
    val heightHint: Double? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class GraphLayoutCompoundEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val kind: EdgeKind = EdgeKind.ADJACENCY
)

enum class CompoundGraphKind {
    GENERIC,
    TREE_LIKE,
    SERIES_PARALLEL
}

enum class NodeKind {
    NORMAL,
    CONTAINER,
    PORTAL,
    DUMMY
}

enum class EdgeKind {
    ADJACENCY,
    HIERARCHY,
    SELF_LOOP,
    CROSS_BOUNDARY
}

enum class CollapsePolicy {
    EXPANDED_BY_DEFAULT,
    COLLAPSED_BY_DEFAULT
}

data class CompoundGraphValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Step 1 adapter: keeps current flat callers working while routing through the new compound model.
 */
fun GraphLayoutGraphState.toCompoundGraphState(rootId: String = "root"): GraphLayoutCompoundGraphState {
    val root = GraphLayoutCompoundGraph(id = rootId)
    nodesById.values.forEach { node ->
        root.nodes[node.id] = GraphLayoutCompoundNode(id = node.id)
    }
    edgesById.values.forEach { edge ->
        root.edges[edge.id] = GraphLayoutCompoundEdge(
            id = edge.id,
            sourceId = edge.sourceId,
            targetId = edge.targetId,
            kind = if (edge.sourceId == edge.targetId) EdgeKind.SELF_LOOP else EdgeKind.ADJACENCY
        )
    }
    return GraphLayoutCompoundGraphState(id = id, routing = routing, root = root)
}

fun GraphLayoutCompoundGraphState.validateInvariants(): CompoundGraphValidationResult {
    val errors = mutableListOf<String>()
    val seenGraphIds = mutableSetOf<String>()
    val nodeOwnerByNodeId = mutableMapOf<String, String>()
    val allNodeIds = mutableSetOf<String>()

    fun walk(graph: GraphLayoutCompoundGraph, path: List<String>) {
        if (graph.id in path) {
            errors.add("Containment cycle detected at graph '${graph.id}' via path ${path + graph.id}")
            return
        }
        if (!seenGraphIds.add(graph.id)) {
            errors.add("Graph id '${graph.id}' is reused; graph ids must be unique in the containment tree")
            return
        }

        graph.nodes.values.forEach { node ->
            allNodeIds.add(node.id)
            val existingOwner = nodeOwnerByNodeId[node.id]
            if (existingOwner != null) {
                errors.add("Node '${node.id}' is contained by both '$existingOwner' and '${graph.id}'")
            } else {
                nodeOwnerByNodeId[node.id] = graph.id
            }
        }

        graph.children.values.forEach { child ->
            walk(child, path + graph.id)
        }
    }

    walk(root, emptyList())

    val validatedEdgeGraphs = mutableSetOf<String>()

    fun validateEdges(graph: GraphLayoutCompoundGraph) {
        if (!validatedEdgeGraphs.add(graph.id)) {
            return
        }
        graph.edges.values.forEach { edge ->
            if (edge.sourceId !in allNodeIds) {
                errors.add("Edge '${edge.id}' source '${edge.sourceId}' does not exist")
            }
            if (edge.targetId !in allNodeIds) {
                errors.add("Edge '${edge.id}' target '${edge.targetId}' does not exist")
            }
        }
        graph.children.values.forEach(::validateEdges)
    }

    validateEdges(root)
    return CompoundGraphValidationResult(errors = errors.distinct())
}

