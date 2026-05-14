package net.akehurst.kotlin.components.layout.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * Compound graph root state used by the recursive layout pipeline.
 *
 * The layout algorithm only consumes [root] (UI-agnostic).
 * Compose content for each node / edge is held here, keyed by stable ID,
 * so the rendering layer can look them up without the algorithm needing to know about them.
 */
data class GraphLayoutCompoundGraphState(
    val id: String,
    val routing: EdgeRouting = EdgeRouting.DIRECT,
    val root: GraphLayoutCompoundGraph = GraphLayoutCompoundGraph("root")
) {
    /** Optional debug overlay toggle used by the demo renderer. */
    val showContentOrigins = mutableStateOf(false)
    /** Temporary visual diagnostics: measured node bounds + adjusted edge endpoints. */
    val showDebugOverlay = mutableStateOf(false)

    /**
     * Compose content for each node, keyed by node ID.
     * The lambda receives a children composable that renders the node's contained children
     * at their layout-computed local positions. Container composables should call children
     * at the appropriate place in their visual hierarchy; leaf composables may ignore it.
     */
    val nodeContentById = mutableStateMapOf<String, @Composable (@Composable () -> Unit) -> Unit>()

    /** Structured rendering information for each edge, keyed by edge ID. */
    val edgeContentById = mutableStateMapOf<String, GraphLayoutEdgeContent>()

    /**
     * Convenience: register Compose content for a node that already exists in [root] (or any child graph).
     * The [content] lambda receives the node's contained children as a composable argument.
     */
    fun addNodeContent(nodeId: String, content: @Composable (@Composable () -> Unit) -> Unit) {
        nodeContentById[nodeId] = content
    }

    /**
     * Convenience: register Compose content for an edge that already exists in [root] (or any child graph).
     */
    fun addEdgeContent(edgeId: String, content: GraphLayoutEdgeContent = GraphLayoutEdgeContent()) {
        edgeContentById[edgeId] = content
    }
}

enum class EdgeContentPosition {
    START,
    MIDDLE,
    END
}

/**
 * Symbol attached to an edge endpoint.
 *
 * [pathPoints] are expressed in symbol-local coordinates with the attachment point at `(0, 0)`.
 * The symbol's forward direction is positive X; the view rotates it to align with the edge tangent.
 */
data class GraphLayoutEdgeSymbol(
    val pathPoints: List<Offset>,
    val isClosed: Boolean = true,
    val fillColor: Color = Color.Transparent,
    val strokeColor: Color = Color(0xFF444444),
    val strokeWidth: Float = 1.5f
)

/**
 * Text label rendered at a deterministic position along an edge.
 */
data class GraphLayoutEdgeText(
    val position: EdgeContentPosition = EdgeContentPosition.MIDDLE,
    val content: @Composable () -> Unit
)

/**
 * Caller-facing edge rendering description.
 *
 * Geometry still comes from the layout result; this structure adds visuals that attach to that geometry,
 * such as endpoint symbols and text labels at the start, middle, or end of the route.
 */
data class GraphLayoutEdgeContent(
    val startSymbol: GraphLayoutEdgeSymbol? = null,
    val endSymbol: GraphLayoutEdgeSymbol? = null,
    val texts: List<GraphLayoutEdgeText> = emptyList()
)

/**
 * Core compound graph structure consumed by the layout engine.
 *
 * Layout semantics (nodes, edges, containment, collapse state) live here.
 * Container presentation geometry comes from Compose measurement at render time.
 */
data class GraphLayoutCompoundGraph(
    val id: String,
    val kind: CompoundGraphKind = CompoundGraphKind.GENERIC,
    /** Optional layout strategy for this graph's immediate children. Null inherits from parent. */
    val childLayout: ChildLayout? = null,
    val layoutProfile: CompoundLayoutProfile? = null,
    val nodes: MutableMap<String, GraphLayoutCompoundNode> = mutableMapOf(),
    val edges: MutableMap<String, GraphLayoutCompoundEdge> = mutableMapOf(),
    val children: MutableMap<String, GraphLayoutCompoundGraph> = mutableMapOf(),
    val routeBoundary: Boolean = true,
    val collapsePolicy: CollapsePolicy = CollapsePolicy.EXPANDED_BY_DEFAULT,
    var isCollapsed: Boolean = false
)

enum class ChildLayout {
    GRAPH,
    TESSELLATE
}

data class PaddingHint(
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
    val left: Double = 0.0,
)

data class GraphLayoutCompoundNode(
    val id: String,
    val kind: NodeKind = NodeKind.NORMAL,
    val widthHint: Double? = null,
    val heightHint: Double? = null,
    val containerPaddingHint: PaddingHint? = null,
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

enum class CompoundLayoutProfile {
    DEFAULT,
    HIERARCHY_BIASED,
    ORTHOGONAL_BIASED,
    COMPACT,
    CHANNEL_BIASED
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
    return GraphLayoutCompoundGraphState(id = id, routing = routing, root = root).also { compoundState ->
        nodesById.values.forEach { node ->
            compoundState.addNodeContent(node.id) {
                node.content()
            }
        }
        edgesById.values.forEach { edge ->
            compoundState.addEdgeContent(edge.id, GraphLayoutEdgeContent())
        }
    }
}

/**
 * Toggles the [GraphLayoutCompoundGraph.isCollapsed] flag of the child graph with [graphId].
 * No-op if the graph is not found.
 */
fun GraphLayoutCompoundGraphState.toggleCollapsed(graphId: String) {
    fun find(graph: GraphLayoutCompoundGraph): GraphLayoutCompoundGraph? {
        if (graph.id == graphId) return graph
        return graph.children.values.firstNotNullOfOrNull { find(it) }
    }
    find(root)?.let { it.isCollapsed = !it.isCollapsed }
}

/**
 * Returns all descendant child graphs (i.e. every graph that is a child of some graph in the
 * containment tree, excluding the root itself).  These are the graphs that can be
 * collapsed/expanded by the user.
 */
fun GraphLayoutCompoundGraphState.collapsibleChildGraphs(): List<GraphLayoutCompoundGraph> {
    val result = mutableListOf<GraphLayoutCompoundGraph>()
    fun walk(graph: GraphLayoutCompoundGraph) {
        graph.children.values.forEach { child ->
            result.add(child)
            walk(child)
        }
    }
    walk(root)
    return result
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

