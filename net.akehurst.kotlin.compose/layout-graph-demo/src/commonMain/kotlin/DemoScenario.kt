package net.akehurst.kotlin.components.layout.graph.demo

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
    val containerId: String? = null
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


