package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.runtime.Composable
import net.akehurst.kotlin.components.layout.graph.ChildLayout
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeContent
import net.akehurst.kotlin.components.layout.graph.PaddingHint

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
    /** Optional first-pass width hint passed to the layout engine. When null the engine uses its default. */
    val widthHint: Float? = null,
    /** Optional first-pass height hint passed to the layout engine. When null the engine uses its default. */
    val heightHint: Float? = null,
    val paddingHint: PaddingHint? = null,
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


