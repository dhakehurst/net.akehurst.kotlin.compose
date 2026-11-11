package net.akehurst.kotlin.compose.layout.ladder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import net.akehurst.kotlin.compose.layout.graph.GraphLayoutEdgeContent

/**
 * A ladder diagram: [rails] side by side, left to right, and [events] from top to bottom.
 *
 * Rails are the uprights, each with a header at the top and a dashed line below it.
 * Rungs are the horizontal arrows between rails. Bars sit on a rail between a [LadderBarStart]
 * and its [LadderBarEnd]. Groups are boxes across a range of rails, split into parts.
 *
 * The model holds only structure. What things look like is held by [LadderLayoutState], by id.
 */
data class LadderModel(
    val rails: List<LadderRail>,
    val events: List<LadderEvent>
)

/** An upright of the ladder. */
data class LadderRail(
    val id: String
)

/** Something that happens on the ladder, in top to bottom order. */
sealed interface LadderEvent {
    val id: String
}

/**
 * A horizontal arrow from rail [from] to rail [to].
 * When [from] and [to] are the same rail, it is a self-rung: a loop out to the right and back.
 */
data class LadderRung(
    override val id: String,
    val from: String,
    val to: String
) : LadderEvent {
    val isSelf: Boolean get() = from == to
}

/**
 * Starts a bar on [rail], at the current position: on the line of the rung just before it, if there is one.
 * The bar ends at the [LadderBarEnd] with the same [id]. Bars on one rail may nest; a nested bar
 * is drawn slightly to the right of the one it is in.
 */
data class LadderBarStart(
    override val id: String,
    val rail: String
) : LadderEvent

/** Ends the bar started by the [LadderBarStart] with the same [id]. */
data class LadderBarEnd(
    override val id: String
) : LadderEvent

/**
 * A box across the rails from the leftmost to the rightmost of [rails], split into [parts]
 * by dashed lines. Its label (for example "alt" or "loop") is drawn in a tab at the top left.
 */
data class LadderGroup(
    override val id: String,
    val rails: List<String>,
    val parts: List<LadderGroupPart>
) : LadderEvent

/** One part of a [LadderGroup], holding its own [events]. It may have a label (for example a condition). */
data class LadderGroupPart(
    val id: String,
    val events: List<LadderEvent>
)

/**
 * A ladder diagram to draw with [LadderLayoutView]: the [model], plus content for its parts, by id.
 *
 * Missing content is shown as a red placeholder, except for group part labels, which are optional.
 */
data class LadderLayoutState(
    val id: String,
    val model: LadderModel
) {
    /** The header drawn at the top of each rail, by rail id. */
    val railHeaderById = mutableStateMapOf<String, @Composable () -> Unit>()

    /** How each rung is drawn (end symbols, texts, line colour, width and dash), by rung id. */
    val rungContentById = mutableStateMapOf<String, GraphLayoutEdgeContent>()

    /** The label drawn in the tab of each group, by group id. */
    val groupLabelById = mutableStateMapOf<String, @Composable () -> Unit>()

    /** The optional label drawn at the top left of each group part, by part id. */
    val groupPartLabelById = mutableStateMapOf<String, @Composable () -> Unit>()

    fun addRailHeader(railId: String, content: @Composable () -> Unit) {
        railHeaderById[railId] = content
    }

    fun addRungContent(rungId: String, content: GraphLayoutEdgeContent = GraphLayoutEdgeContent()) {
        rungContentById[rungId] = content
    }

    fun addGroupLabel(groupId: String, content: @Composable () -> Unit) {
        groupLabelById[groupId] = content
    }

    fun addGroupPartLabel(partId: String, content: @Composable () -> Unit) {
        groupPartLabelById[partId] = content
    }
}

/** Something wrong with a [LadderModel]. The layout still draws what it can. */
data class LadderProblem(val eventId: String, val message: String)

/**
 * Checks [this] model for unknown rails, duplicate ids, bars that end without a start or never end,
 * and groups with no rails or no parts.
 */
fun LadderModel.validate(): List<LadderProblem> {
    val problems = mutableListOf<LadderProblem>()
    val railIds = rails.map { it.id }.toSet()
    rails.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys.forEach {
        problems += LadderProblem(it, "rail id '$it' is used more than once")
    }
    val seen = mutableSetOf<String>()
    val openBars = mutableSetOf<String>()
    fun checkRail(eventId: String, railId: String) {
        if (railId !in railIds) problems += LadderProblem(eventId, "unknown rail '$railId'")
    }
    fun walk(events: List<LadderEvent>) {
        events.forEach { e ->
            if (e !is LadderBarEnd && !seen.add(e.id)) problems += LadderProblem(e.id, "id '${e.id}' is used more than once")
            when (e) {
                is LadderRung -> { checkRail(e.id, e.from); checkRail(e.id, e.to) }
                is LadderBarStart -> { checkRail(e.id, e.rail); openBars += e.id }
                is LadderBarEnd -> if (!openBars.remove(e.id)) problems += LadderProblem(e.id, "bar '${e.id}' ends but was not started")
                is LadderGroup -> {
                    if (e.rails.isEmpty()) problems += LadderProblem(e.id, "group '${e.id}' has no rails")
                    if (e.parts.isEmpty()) problems += LadderProblem(e.id, "group '${e.id}' has no parts")
                    e.rails.forEach { checkRail(e.id, it) }
                    e.parts.forEach { p ->
                        if (!seen.add(p.id)) problems += LadderProblem(p.id, "id '${p.id}' is used more than once")
                        walk(p.events)
                    }
                }
            }
        }
    }
    walk(events)
    openBars.sorted().forEach { problems += LadderProblem(it, "bar '$it' is started but never ends") }
    return problems
}
