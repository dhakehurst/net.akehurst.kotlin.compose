package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.runtime.Composable
import net.akehurst.kotlin.components.layout.graph.ChildLayout
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeContent

/**
 * Deterministic IDs are required so visual comparisons stay stable between runs.
 */
data class DemoScenario(
    val id: String,
    val title: String,
    val nodes: List<DemoNode>,
    val edges: List<DemoEdge>
)

data class DemoNode(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val containerId: String? = null,
    /** If true, this container starts collapsed (policy: COLLAPSED_BY_DEFAULT). */
    val defaultCollapsed: Boolean = false,
    /** Optional layout strategy for this node's immediate children. */
    val childLayout: ChildLayout? = null,
    val content: (@Composable (@Composable () -> Unit) -> Unit)
)


data class DemoEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val content: GraphLayoutEdgeContent = GraphLayoutEdgeContent()
)

data class DebugOverlaySettings(
    val showBounds: Boolean = true,
    val showPorts: Boolean = false,
    val showEdgeIds: Boolean = false,
    val showContentOrigins: Boolean = false
)


