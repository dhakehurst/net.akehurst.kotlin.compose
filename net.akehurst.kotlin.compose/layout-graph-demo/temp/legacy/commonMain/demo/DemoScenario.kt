package demo

// Deprecated source location. Active demo code moved to:
// src/commonMain/kotlin/DemoScenario.kt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.components.layout.graph.EdgeRouting
import net.akehurst.kotlin.components.layout.graph.SugiyamaEdge
import net.akehurst.kotlin.components.layout.graph.SugiyamaLayout

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

@Composable
fun DemoApp() {
    val scenarios = DemoScenarios.all
    var selectedScenarioId by remember { mutableStateOf(scenarios.first().id) }
    var overlay by remember { mutableStateOf(DebugOverlaySettings()) }
    val selectedScenario = scenarios.first { it.id == selectedScenarioId }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F6F6))) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxSize()
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFD8D8D8))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Scenario", style = MaterialTheme.typography.titleMedium)
            scenarios.forEach { scenario ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedScenarioId == scenario.id,
                        onClick = { selectedScenarioId = scenario.id }
                    )
                    Text(scenario.id)
                }
            }

            Text("Debug overlay", style = MaterialTheme.typography.titleMedium)
            DebugToggle("Bounds", overlay.showBounds) { overlay = overlay.copy(showBounds = it) }
            DebugToggle("Ports", overlay.showPorts) { overlay = overlay.copy(showPorts = it) }
            DebugToggle("Edge IDs", overlay.showEdgeIds) { overlay = overlay.copy(showEdgeIds = it) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFD8D8D8))
                .padding(8.dp)
        ) {
            LiveLayoutCanvas(scenario = selectedScenario, overlay = overlay)
        }
    }
}

@Composable
private fun DebugToggle(label: String, value: Boolean, update: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = value, onCheckedChange = update)
        Text(label)
    }
}

@Composable
private fun LiveLayoutCanvas(scenario: DemoScenario, overlay: DebugOverlaySettings) {
    val containerIds = scenario.nodes.mapNotNull { it.containerId }.toSet()
    val nodesById = remember(scenario) { scenario.nodes.associateBy { it.id } }
    val layoutData = remember(scenario) {
        val layout = SugiyamaLayout<DemoNode>(
            nodeWidth = { it.width.toDouble() },
            nodeHeight = { it.height.toDouble() },
            edgeRouting = EdgeRouting.DIRECT
        )
        val edges = scenario.edges.mapNotNull { edge ->
            val source = nodesById[edge.sourceId]
            val target = nodesById[edge.targetId]
            if (source != null && target != null) {
                SugiyamaEdge(edge.id, source, target)
            } else {
                null
            }
        }
        layout.layoutGraph(nodes = scenario.nodes, edges = edges)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        layoutData.edgeRoutes.forEach { (_, route) ->
            if (route.size >= 2) {
                drawPath(
                    path = Path().apply {
                        route.forEachIndexed { index, point ->
                            val x = point.first.toFloat()
                            val y = point.second.toFloat()
                            if (0 == index) moveTo(x, y) else lineTo(x, y)
                        }
                    },
                    color = Color(0xFF444444),
                    style = Stroke(width = 2f)
                )
            }
        }

        scenario.nodes.forEach { node ->
            val isContainer = node.id in containerIds
            val fill = if (isContainer) Color(0xFFEDF4FF) else Color(0xFFEFF8EF)
            val stroke = if (isContainer) Color(0xFF3F7ACC) else Color(0xFF409C55)
            val computedPosition = layoutData.nodePositions[node]
            val topLeft = Offset(
                x = computedPosition?.first?.toFloat() ?: node.x,
                y = computedPosition?.second?.toFloat() ?: node.y
            )
            val size = Size(node.width, node.height)

            drawRoundRect(
                color = fill,
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = stroke,
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2f)
            )

            if (overlay.showBounds) {
                drawRect(
                    color = Color(0x66D32F2F),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 1f)
                )
            }

            if (overlay.showPorts) {
                val ports = listOf(
                    Offset(topLeft.x + node.width / 2f, topLeft.y),
                    Offset(topLeft.x + node.width, topLeft.y + node.height / 2f),
                    Offset(topLeft.x + node.width / 2f, topLeft.y + node.height),
                    Offset(topLeft.x, topLeft.y + node.height / 2f)
                )
                ports.forEach { port ->
                    drawCircle(color = Color(0xFFD32F2F), radius = 3f, center = port)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(text = scenario.title, style = MaterialTheme.typography.titleMedium)
        Text(text = "id=${scenario.id}", style = MaterialTheme.typography.bodySmall)
        Text(text = "mode=live_layout", style = MaterialTheme.typography.bodySmall)
        if (overlay.showEdgeIds) {
            Text(
                text = scenario.edges.joinToString(prefix = "edges: ", separator = ", ") { it.id },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

