package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.CompoundGraphLayoutView
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutCompoundGraphState
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutViewState
import net.akehurst.kotlin.compose.layout.graph.collapsibleChildGraphs
import net.akehurst.kotlin.compose.layout.graph.toggleCollapsed
import net.akehurst.kotlin.compose.layout.graph.InitialViewPlacement
import net.akehurst.kotlin.compose.layout.ladder.LadderLayoutView

@Composable
fun DemoApp() {
    val scenarios = DemoScenarios.all
    var selectedScenarioId by remember { mutableStateOf(scenarios.first().id) }
    var overlay by remember { mutableStateOf(DebugOverlaySettings()) }
    var selectedLadderId by remember { mutableStateOf<String?>(null) }
    val selectedScenario = scenarios.first { it.id == selectedScenarioId }
    val selectedLadder = LadderDemoScenarios.all.firstOrNull { it.id == selectedLadderId }

    // Compound state lives here so the sidebar can toggle collapse on it.
    val compoundState = remember(selectedScenarioId) { selectedScenario.toCompoundGraphState() }

    // Incrementing this triggers layout recomputation inside CompoundGraphLayoutView.
    // It is reset to 0 whenever the scenario changes (because of the remember key).
    var collapseVersion by remember(selectedScenarioId) { mutableStateOf(0) }

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
                        selected = selectedLadder == null && selectedScenarioId == scenario.id,
                        onClick = { selectedScenarioId = scenario.id; selectedLadderId = null }
                    )
                    Text(scenario.title)
                }
            }

            Text("Ladder", style = MaterialTheme.typography.titleMedium)
            LadderDemoScenarios.all.forEach { scenario ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedLadderId == scenario.id,
                        onClick = { selectedLadderId = scenario.id }
                    )
                    Text(scenario.title)
                }
            }

            Text("Debug overlay", style = MaterialTheme.typography.titleMedium)
            DebugToggle("Rendered bounds + endpoints", overlay.showBounds) { overlay = overlay.copy(showBounds = it) }
            DebugToggle("Ports", overlay.showPorts) { overlay = overlay.copy(showPorts = it) }
            DebugToggle("Edge IDs", overlay.showEdgeIds) { overlay = overlay.copy(showEdgeIds = it) }
            DebugToggle("Content origins", overlay.showContentOrigins) { overlay = overlay.copy(showContentOrigins = it) }

            // Collapse/expand toggles — re-read collapseVersion so the sidebar recomposes
            // after each toggle and reflects the updated isCollapsed value.
            val collapsibleGraphs = if (collapseVersion >= 0) {
                compoundState.collapsibleChildGraphs().sortedBy { it.id }
            } else emptyList()

            if (collapsibleGraphs.isNotEmpty()) {
                Text("Containers", style = MaterialTheme.typography.titleMedium)
                collapsibleGraphs.forEach { childGraph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = childGraph.isCollapsed,
                            onCheckedChange = {
                                compoundState.toggleCollapsed(childGraph.id)
                                collapseVersion++
                            }
                        )
                        Text(childGraph.id, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFD8D8D8))
                .padding(8.dp)
        ) {
            if (selectedLadder != null) {
                LiveLadderCanvas(selectedLadder)
            } else LiveLayoutCanvas(
                title = selectedScenario.title,
                scenarioId = selectedScenario.id,
                compoundState = compoundState,
                collapseVersion = collapseVersion,
                overlay = overlay
            )
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
private fun LiveLayoutCanvas(
    title: String,
    scenarioId: String,
    compoundState: GraphLayoutCompoundGraphState,
    collapseVersion: Int,
    overlay: DebugOverlaySettings
) {
    var viewState by remember(scenarioId) { mutableStateOf(GraphLayoutViewState()) }
    compoundState.showContentOrigins.value = overlay.showContentOrigins
    compoundState.showDebugOverlay.value = overlay.showBounds

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "id=$scenarioId  mode=compound_layout",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        CompoundGraphLayoutView(
            state = compoundState,
            layoutKey = collapseVersion,
            viewState = viewState,
            updateView = { offset: Offset, zoom: Float ->
                viewState = GraphLayoutViewState(zoom = zoom, offset = offset)
            },
            modifier = Modifier.weight(1f).fillMaxSize()
        )
    }
}

@Composable
private fun LiveLadderCanvas(scenario: LadderDemoScenario) {
    val state = remember(scenario.id) { scenario.toState() }
    var viewState by remember(scenario.id) { mutableStateOf(GraphLayoutViewState()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = scenario.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "id=${scenario.id}  mode=ladder",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LadderLayoutView(
            state = state,
            viewState = viewState,
            updateView = { offset: Offset, zoom: Float -> viewState = GraphLayoutViewState(zoom = zoom, offset = offset) },
            modifier = Modifier.weight(1f).fillMaxSize(),
            initialView = InitialViewPlacement.CENTRE
        )
    }
}
