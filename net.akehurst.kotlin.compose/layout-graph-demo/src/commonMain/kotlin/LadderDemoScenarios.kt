package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.layout.graph.EdgeContentPosition
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutEdgeContent
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutEdgeSymbol
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutEdgeText
import net.akehurst.kotlin.compose.layout.ladder.*

/** A ladder diagram for the demo: the model plus how to fill in its content. */
data class LadderDemoScenario(
    val id: String,
    val title: String,
    val model: LadderModel,
    val rungKinds: Map<String, RungKind> = emptyMap(),
    val rungTexts: Map<String, String> = emptyMap(),
    val groupLabels: Map<String, String> = emptyMap(),
    val partLabels: Map<String, String> = emptyMap(),
) {
    fun toState(): LadderLayoutState = LadderLayoutState(id, model).also { s ->
        model.rails.forEach { rail -> s.addRailHeader(rail.id) { RailHeader(rail.id) } }
        fun walk(events: List<LadderEvent>) {
            events.forEach { e ->
                when (e) {
                    is LadderRung -> s.addRungContent(e.id, rungContent(rungKinds[e.id] ?: RungKind.CALL, rungTexts[e.id] ?: e.id))
                    is LadderGroup -> {
                        s.addGroupLabel(e.id) { Text(groupLabels[e.id] ?: e.id, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                        e.parts.forEach { p ->
                            partLabels[p.id]?.let { label -> s.addGroupPartLabel(p.id) { Text("[$label]", style = MaterialTheme.typography.labelMedium) } }
                            walk(p.events)
                        }
                    }
                    else -> Unit
                }
            }
        }
        walk(model.events)
    }
}

enum class RungKind { CALL, SIGNAL, REPLY }

private val filledArrow = GraphLayoutEdgeSymbol(
    pathPoints = listOf(Offset(0f, 0f), Offset(-10f, -5f), Offset(-10f, 5f)),
    fillColor = Color(0xFF444444)
)
private val openArrow = GraphLayoutEdgeSymbol(
    pathPoints = listOf(Offset(-10f, -5f), Offset(0f, 0f), Offset(-10f, 5f)),
    isClosed = false
)

private fun rungContent(kind: RungKind, text: String) = GraphLayoutEdgeContent(
    endSymbol = if (kind == RungKind.CALL) filledArrow else openArrow,
    texts = listOf(GraphLayoutEdgeText(EdgeContentPosition.MIDDLE) { Text(text, style = MaterialTheme.typography.labelMedium) }),
    lineWidth = 1f,
    lineDash = if (kind == RungKind.REPLY) listOf(6f, 4f) else null
)

@Composable
private fun RailHeader(name: String) {
    Box(
        Modifier
            .background(Color(0xFFFFF8E1))
            .border(1.dp, Color(0xFF444444))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

object LadderDemoScenarios {

    val simpleCalls = LadderDemoScenario(
        id = "ladder_01_simple_calls",
        title = "Ladder: calls and replies with bars",
        model = LadderModel(
            rails = listOf(LadderRail("user"), LadderRail("shop"), LadderRail("stock")),
            events = listOf(
                LadderRung("order", "user", "shop"),
                LadderBarStart("shopBusy", "shop"),
                LadderRung("check", "shop", "stock"),
                LadderBarStart("stockBusy", "stock"),
                LadderRung("available", "stock", "shop"),
                LadderBarEnd("stockBusy"),
                LadderRung("confirm", "shop", "user"),
                LadderBarEnd("shopBusy"),
            )
        ),
        rungKinds = mapOf("available" to RungKind.REPLY, "confirm" to RungKind.REPLY),
        rungTexts = mapOf("order" to "order(item)", "check" to "inStock(item)", "available" to "true", "confirm" to "orderId")
    )

    val selfAndNested = LadderDemoScenario(
        id = "ladder_02_self_rungs",
        title = "Ladder: self-rungs and nested bars",
        model = LadderModel(
            rails = listOf(LadderRail("client"), LadderRail("server")),
            events = listOf(
                LadderRung("request", "client", "server"),
                LadderBarStart("handle", "server"),
                LadderRung("validate", "server", "server"),
                LadderBarStart("validating", "server"),
                LadderRung("log", "server", "server"),
                LadderBarEnd("validating"),
                LadderRung("response", "server", "client"),
                LadderBarEnd("handle"),
                LadderRung("notify", "client", "server"),
            )
        ),
        rungKinds = mapOf("response" to RungKind.REPLY, "log" to RungKind.SIGNAL, "notify" to RungKind.SIGNAL),
        rungTexts = mapOf("validate" to "validate()", "log" to "log(msg)")
    )

    val groups = LadderDemoScenario(
        id = "ladder_03_groups",
        title = "Ladder: groups with parts, nested",
        model = LadderModel(
            rails = listOf(LadderRail("app"), LadderRail("cache"), LadderRail("database")),
            events = listOf(
                LadderRung("get", "app", "cache"),
                LadderGroup(
                    "alt", listOf("app", "cache", "database"), listOf(
                        LadderGroupPart("hit", listOf(LadderRung("value", "cache", "app"))),
                        LadderGroupPart(
                            "miss", listOf(
                                LadderRung("query", "cache", "database"),
                                LadderGroup(
                                    "loop", listOf("database"), listOf(
                                        LadderGroupPart("eachRow", listOf(LadderRung("read", "database", "database")))
                                    )
                                ),
                                LadderRung("rows", "database", "cache"),
                                LadderRung("valueFromDb", "cache", "app"),
                            )
                        ),
                    )
                ),
                LadderRung("done", "app", "app"),
            )
        ),
        rungKinds = mapOf("value" to RungKind.REPLY, "rows" to RungKind.REPLY, "valueFromDb" to RungKind.REPLY),
        rungTexts = mapOf("valueFromDb" to "value from db", "read" to "readRow()"),
        groupLabels = mapOf("alt" to "alt", "loop" to "loop"),
        partLabels = mapOf("hit" to "in cache", "miss" to "else", "eachRow" to "for each row")
    )

    val all = listOf(simpleCalls, selfAndNested, groups)
}
