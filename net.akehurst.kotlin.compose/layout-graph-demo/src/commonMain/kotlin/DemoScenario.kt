package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.runtime.Composable

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
    /** If true, this container starts collapsed and has [CollapsePolicy.COLLAPSED_BY_DEFAULT]. */
    val defaultCollapsed: Boolean = false,
    val content: (@Composable () -> Unit)? = null
)

data class DemoEdge(
    val id: String,
    val sourceId: String,
    val targetId: String
)

data class DebugOverlaySettings(
    val showBounds: Boolean = true,
    val showPorts: Boolean = false,
    val showEdgeIds: Boolean = false
)


