package net.akehurst.kotlin.compose.layout.ladder

import net.akehurst.kotlin.compose.layout.graph.EdgeContentPosition
import kotlin.math.max
import kotlin.math.min

/** A width and height, in dp. */
data class LadderSize(val width: Double, val height: Double) {
    companion object {
        val ZERO = LadderSize(0.0, 0.0)
    }
}

/** A rectangle, in dp. */
data class LadderRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    fun translated(dx: Double, dy: Double) = LadderRect(left + dx, top + dy, right + dx, bottom + dy)
    fun scaledBy(f: Double) = LadderRect(left * f, top * f, right * f, bottom * f)
}

/** Spacing used by [LadderLayoutEngine]. All values are in dp. */
data class LadderLayoutConfig(
    /** Smallest horizontal space between the header boxes of neighbouring rails. */
    val railGap: Double = 40.0,
    /** Space between the bottom of the headers and the first event. */
    val headerGap: Double = 16.0,
    /** Smallest vertical distance from one rung (or the top of a group part) to the next rung. */
    val rungSpacing: Double = 28.0,
    /** Space between a rung's line and its texts, and between the texts. */
    val labelGap: Double = 4.0,
    /** Horizontal space kept between a rung's texts and the rails at its ends. */
    val labelMargin: Double = 8.0,
    /** How far a self-rung loops out to the right of its rail (or bar). */
    val selfRungWidth: Double = 30.0,
    /** Vertical distance between the outgoing and returning lines of a self-rung. */
    val selfRungHeight: Double = 20.0,
    /** Width of a bar. */
    val barWidth: Double = 12.0,
    /** How far a nested bar is moved right from the bar it is in. */
    val barNestOffset: Double = 6.0,
    /** Smallest height of a bar. */
    val minBarHeight: Double = 16.0,
    /** How far a group box reaches out past its leftmost and rightmost rails. */
    val groupMargin: Double = 16.0,
    /** Extra reach for each level of groups nested inside a group, so nested boxes do not touch. */
    val groupNestStep: Double = 8.0,
    /** Space above and below a group. */
    val groupGap: Double = 8.0,
    /** Space at the bottom of each group part, below its last event. */
    val groupPartPadding: Double = 8.0,
    /** Space around a group's label inside its tab, and around group part labels. */
    val tabPadding: Double = 4.0,
    /** Size of the cut corner at the bottom right of a group's tab. */
    val tabCorner: Double = 8.0,
    /** Space below the last event, down to the end of the rail lines. */
    val bottomGap: Double = 16.0,
)

/** Sizes of the caller's content, measured by the view, in dp. Missing entries count as zero size. */
data class LadderMeasuredSizes(
    val railHeaders: Map<String, LadderSize> = emptyMap(),
    /** Each rung's texts at each position, stacked as the view draws them. */
    val rungLabels: Map<String, Map<EdgeContentPosition, LadderSize>> = emptyMap(),
    val groupLabels: Map<String, LadderSize> = emptyMap(),
    val groupPartLabels: Map<String, LadderSize> = emptyMap(),
)

data class LadderRailLayout(
    val railId: String,
    /** Centre of the rail. */
    val x: Double,
    val header: LadderRect,
    /** The dashed line runs from [lineTop] to [lineBottom] at [x]. */
    val lineTop: Double,
    val lineBottom: Double,
)

data class LadderRungLayout(
    val rungId: String,
    val from: String,
    val to: String,
    /** The line, from start to end: two points for a rung between rails, four for a self-rung. */
    val points: List<Pair<Double, Double>>,
    /** Where each position's texts go. */
    val labels: Map<EdgeContentPosition, LadderRect>,
)

data class LadderBarLayout(
    val barId: String,
    val railId: String,
    /** 0 for a bar directly on the rail, 1 for a bar inside it, and so on. */
    val depth: Int,
    val rect: LadderRect,
)

data class LadderGroupPartLayout(
    val partId: String,
    val rect: LadderRect,
    /** Where the part's label goes, if it has one. */
    val label: LadderRect?,
)

data class LadderGroupLayout(
    val groupId: String,
    /** 0 for a top level group, 1 for a group inside a group, and so on. */
    val depth: Int,
    val rect: LadderRect,
    /** The tab at the top left, with a cut bottom right corner. */
    val tab: LadderRect,
    /** Where the group's label goes, inside [tab]. */
    val label: LadderRect,
    val parts: List<LadderGroupPartLayout>,
)

/** The laid out diagram. Its top left corner is at (0, 0). */
data class LadderLayoutResult(
    val width: Double,
    val height: Double,
    val rails: List<LadderRailLayout>,
    val rungs: List<LadderRungLayout>,
    val bars: List<LadderBarLayout>,
    /** Outer groups first, so drawing in order puts inner groups on top. */
    val groups: List<LadderGroupLayout>,
) {
    fun scaledBy(f: Double) = LadderLayoutResult(
        width = width * f,
        height = height * f,
        rails = rails.map { it.copy(x = it.x * f, header = it.header.scaledBy(f), lineTop = it.lineTop * f, lineBottom = it.lineBottom * f) },
        rungs = rungs.map { r -> r.copy(points = r.points.map { (it.first * f) to (it.second * f) }, labels = r.labels.mapValues { it.value.scaledBy(f) }) },
        bars = bars.map { it.copy(rect = it.rect.scaledBy(f)) },
        groups = groups.map { g ->
            g.copy(
                rect = g.rect.scaledBy(f), tab = g.tab.scaledBy(f), label = g.label.scaledBy(f),
                parts = g.parts.map { p -> p.copy(rect = p.rect.scaledBy(f), label = p.label?.scaledBy(f)) }
            )
        },
    )
}

/**
 * Lays out a [LadderModel]: rails as columns spaced so headers and rung texts fit,
 * and events from top to bottom in the order given. Works in dp.
 */
class LadderLayoutEngine(
    private val config: LadderLayoutConfig = LadderLayoutConfig()
) {

    private val positions = listOf(EdgeContentPosition.START, EdgeContentPosition.MIDDLE, EdgeContentPosition.END)

    fun layout(model: LadderModel, sizes: LadderMeasuredSizes = LadderMeasuredSizes()): LadderLayoutResult {
        val c = config
        val railIds = model.rails.map { it.id }.distinct()
        val railIndex = railIds.withIndex().associate { it.value to it.index }
        val n = railIds.size
        val headerSize = railIds.map { sizes.railHeaders[it] ?: LadderSize.ZERO }

        // ── Horizontal: gaps between neighbouring rail centres ──────────────
        val groupInfo = mutableMapOf<String, GroupInfo>()
        collectGroups(model.events, 0, railIndex, groupInfo)

        // How far groups and self-rungs reach out to the left and right of each rail.
        val reachLeft = DoubleArray(n)
        val reachRight = DoubleArray(n)
        val spans = mutableListOf<Triple<Int, Int, Double>>() // from rail, to rail, distance needed between their centres
        val barReach = c.barWidth / 2 + maxBarDepth(model.events, railIndex) * c.barNestOffset

        fun selfExtent(rungId: String): Double {
            val labelWidth = rowWidth(sizes.rungLabels[rungId])
            return barReach + max(c.selfRungWidth, labelWidth + c.labelGap)
        }

        forEachEvent(model.events) { e ->
            if (e is LadderRung) {
                val i = railIndex[e.from] ?: return@forEachEvent
                val j = railIndex[e.to] ?: return@forEachEvent
                if (i == j) {
                    reachRight[i] = max(reachRight[i], selfExtent(e.id) + c.labelMargin)
                } else {
                    val need = rowWidth(sizes.rungLabels[e.id]) + 2 * c.labelMargin + 2 * barReach
                    spans += Triple(min(i, j), max(i, j), need)
                }
            }
        }
        // Inner groups first: an outer group reaches past every group inside it.
        groupInfo.values.sortedByDescending { it.depth }.forEach { g ->
            if (g.firstRail < 0) return@forEach
            var left = c.groupMargin
            var right = c.groupMargin
            g.selfRungsOnLastRail.forEach { right = max(right, selfExtent(it) + c.groupPartPadding) }
            val inner = g.children.filter { it.firstRail >= 0 }
            inner.filter { it.firstRail == g.firstRail }.forEach { left = max(left, it.reachLeft + c.groupNestStep) }
            inner.filter { it.lastRail == g.lastRail }.forEach { right = max(right, it.reachRight + c.groupNestStep) }
            val labelNeed = groupLabelWidth(g.group, sizes)
            if (g.firstRail == g.lastRail) {
                right = max(right, labelNeed - left)
            } else {
                spans += Triple(g.firstRail, g.lastRail, labelNeed - left - right)
            }
            inner.filter { it.firstRail > g.firstRail }.forEach { spans += Triple(g.firstRail, it.firstRail, it.reachLeft + c.groupNestStep - left) }
            inner.filter { it.lastRail < g.lastRail }.forEach { spans += Triple(it.lastRail, g.lastRail, it.reachRight + c.groupNestStep - right) }
            reachLeft[g.firstRail] = max(reachLeft[g.firstRail], left)
            reachRight[g.lastRail] = max(reachRight[g.lastRail], right)
            g.reachLeft = left
            g.reachRight = right
        }

        val gaps = DoubleArray(max(0, n - 1)) { i ->
            max(
                headerSize[i].width / 2 + headerSize[i + 1].width / 2 + c.railGap,
                reachRight[i] + reachLeft[i + 1] + c.groupGap
            )
        }
        spans.sortedBy { it.second - it.first }.forEach { (from, to, need) ->
            val have = (from until to).sumOf { gaps[it] }
            if (have < need) {
                val extra = (need - have) / (to - from)
                (from until to).forEach { gaps[it] += extra }
            }
        }
        val railX = DoubleArray(n)
        for (i in 1 until n) railX[i] = railX[i - 1] + gaps[i - 1]

        // ── Vertical: walk the events top to bottom ─────────────────────────
        val headerBottom = headerSize.maxOfOrNull { it.height } ?: 0.0
        val walk = VerticalWalk(c, sizes, railIndex, railX, groupInfo)
        walk.cursor = headerBottom + c.headerGap
        walk.events(model.events, 0)
        val bottom = walk.cursor + c.bottomGap
        walk.openBars.values.flatten().forEach { bar -> walk.bars += bar.copy(bottom = max(walk.cursor, bar.top + c.minBarHeight)) }

        // ── Rung end points, now all bars are known ─────────────────────────
        val barsByRail = walk.bars.groupBy { it.railId }
        fun barEdge(rail: String, y: Double, direction: Int): Double {
            val x = railX[railIndex.getValue(rail)]
            val bar = barsByRail[rail].orEmpty()
                .filter { it.top - 1e-6 <= y && y <= it.bottom + 1e-6 }
                .maxByOrNull { it.depth }
                ?: return x
            return x + bar.depth * c.barNestOffset + direction * c.barWidth / 2
        }

        val rungs = walk.rungs.map { r ->
            val labelSizes = sizes.rungLabels[r.id].orEmpty()
            if (r.isSelf) {
                val xs = barEdge(r.from, r.y1, +1)
                val xe = barEdge(r.from, r.y2, +1)
                val loopX = railX[railIndex.getValue(r.from)] + barReach + c.selfRungWidth
                // Texts in one row above the outgoing line, left to right.
                var left = xs + c.labelGap
                val labels = positions.mapNotNull { p ->
                    val s = labelSizes[p] ?: return@mapNotNull null
                    val rect = LadderRect(left, r.y1 - c.labelGap - s.height, left + s.width, r.y1 - c.labelGap)
                    left += s.width + c.labelGap
                    p to rect
                }.toMap()
                LadderRungLayout(r.id, r.from, r.to, listOf(xs to r.y1, loopX to r.y1, loopX to r.y2, xe to r.y2), labels)
            } else {
                val fromX = railX[railIndex.getValue(r.from)]
                val toX = railX[railIndex.getValue(r.to)]
                val dir = if (toX > fromX) 1 else -1
                val xs = barEdge(r.from, r.y1, dir)
                val xe = barEdge(r.to, r.y1, -dir)
                val labelBottom = r.y1 - c.labelGap
                val labels = positions.mapNotNull { p ->
                    val s = labelSizes[p] ?: return@mapNotNull null
                    val left = when (p) {
                        EdgeContentPosition.START -> if (dir > 0) xs + c.labelMargin else xs - c.labelMargin - s.width
                        EdgeContentPosition.END -> if (dir > 0) xe - c.labelMargin - s.width else xe + c.labelMargin
                        EdgeContentPosition.MIDDLE -> (xs + xe) / 2 - s.width / 2
                    }
                    p to LadderRect(left, labelBottom - s.height, left + s.width, labelBottom)
                }.toMap()
                LadderRungLayout(r.id, r.from, r.to, listOf(xs to r.y1, xe to r.y1), labels)
            }
        }

        val rails = railIds.mapIndexed { i, id ->
            val s = headerSize[i]
            LadderRailLayout(id, railX[i], LadderRect(railX[i] - s.width / 2, 0.0, railX[i] + s.width / 2, s.height), s.height, bottom)
        }

        // ── Move the whole diagram so its top left is at (0, 0) ─────────────
        val rects = rails.map { it.header } + walk.bars.map { it.rect() } + walk.groups.map { it.rect } +
            rungs.flatMap { r -> r.labels.values + r.points.map { LadderRect(it.first, it.second, it.first, it.second) } }
        val minX = rects.minOfOrNull { it.left } ?: 0.0
        val maxX = rects.maxOfOrNull { it.right } ?: 0.0
        val minY = min(0.0, rects.minOfOrNull { it.top } ?: 0.0)
        val maxY = max(bottom, rects.maxOfOrNull { it.bottom } ?: 0.0)
        val dx = -minX
        val dy = -minY

        return LadderLayoutResult(
            width = maxX - minX,
            height = maxY - minY,
            rails = rails.map { it.copy(x = it.x + dx, header = it.header.translated(dx, dy), lineTop = it.lineTop + dy, lineBottom = it.lineBottom + dy) },
            rungs = rungs.map { r -> r.copy(points = r.points.map { (it.first + dx) to (it.second + dy) }, labels = r.labels.mapValues { it.value.translated(dx, dy) }) },
            bars = walk.bars.map { LadderBarLayout(it.id, it.railId, it.depth, it.rect().translated(dx, dy)) },
            groups = walk.groups.sortedBy { it.depth }.map { g ->
                g.copy(
                    rect = g.rect.translated(dx, dy), tab = g.tab.translated(dx, dy), label = g.label.translated(dx, dy),
                    parts = g.parts.map { p -> p.copy(rect = p.rect.translated(dx, dy), label = p.label?.translated(dx, dy)) }
                )
            },
        )
    }

    /** Width of a rung's texts placed side by side. */
    private fun rowWidth(labels: Map<EdgeContentPosition, LadderSize>?): Double {
        val widths = labels.orEmpty().values.map { it.width }.filter { it > 0 }
        return widths.sum() + max(0, widths.size - 1) * config.labelGap
    }

    private fun rowHeight(labels: Map<EdgeContentPosition, LadderSize>?): Double =
        labels.orEmpty().values.maxOfOrNull { it.height } ?: 0.0

    private fun tabSize(groupId: String, sizes: LadderMeasuredSizes): LadderSize {
        val label = sizes.groupLabels[groupId] ?: LadderSize.ZERO
        return LadderSize(label.width + 2 * config.tabPadding + config.tabCorner, label.height + 2 * config.tabPadding)
    }

    /** Width the group box needs so its tab and part labels fit. */
    private fun groupLabelWidth(group: LadderGroup, sizes: LadderMeasuredSizes): Double {
        val tab = tabSize(group.id, sizes).width
        return group.parts.mapIndexed { i, p ->
            val label = sizes.groupPartLabels[p.id]?.width ?: 0.0
            val labelWidth = if (label > 0) label + 2 * config.tabPadding else 0.0
            if (i == 0) tab + labelWidth else labelWidth
        }.maxOrNull() ?: tab
    }

    private class GroupInfo(val group: LadderGroup, val depth: Int, val firstRail: Int, val lastRail: Int) {
        /** The groups directly inside this one. */
        val children = mutableListOf<GroupInfo>()
        val selfRungsOnLastRail = mutableListOf<String>()
        var reachLeft = 0.0
        var reachRight = 0.0
    }

    /** Fills [into] for every group in [events], and returns the groups directly in [events]. */
    private fun collectGroups(events: List<LadderEvent>, depth: Int, railIndex: Map<String, Int>, into: MutableMap<String, GroupInfo>): List<GroupInfo> =
        events.filterIsInstance<LadderGroup>().map { e ->
            val indices = e.rails.mapNotNull { railIndex[it] }
            val info = GroupInfo(e, depth, indices.minOrNull() ?: -1, indices.maxOrNull() ?: -1)
            into[e.id] = info
            e.parts.forEach { info.children += collectGroups(it.events, depth + 1, railIndex, into) }
            if (info.lastRail >= 0) {
                forEachEvent(e.parts.flatMap { it.events }) { inside ->
                    if (inside is LadderRung && inside.isSelf && railIndex[inside.from] == info.lastRail) info.selfRungsOnLastRail += inside.id
                }
            }
            info
        }

    private fun maxBarDepth(events: List<LadderEvent>, railIndex: Map<String, Int>): Int {
        val open = mutableMapOf<String, String>() // bar id -> rail
        var deepest = 0
        forEachEvent(events) { e ->
            when (e) {
                is LadderBarStart -> {
                    if (e.rail in railIndex) {
                        deepest = max(deepest, open.values.count { it == e.rail })
                        open[e.id] = e.rail
                    }
                }
                is LadderBarEnd -> open.remove(e.id)
                else -> Unit
            }
        }
        return deepest
    }

    private fun forEachEvent(events: List<LadderEvent>, action: (LadderEvent) -> Unit) {
        events.forEach { e ->
            action(e)
            if (e is LadderGroup) e.parts.forEach { forEachEvent(it.events, action) }
        }
    }

    private data class PlacedRung(val id: String, val from: String, val to: String, val y1: Double, val y2: Double) {
        val isSelf get() = from == to
    }

    private data class PlacedBar(val id: String, val railId: String, val depth: Int, val x: Double, val width: Double, val top: Double, val bottom: Double) {
        fun rect() = LadderRect(x - width / 2, top, x + width / 2, bottom)
    }

    private inner class VerticalWalk(
        val c: LadderLayoutConfig,
        val sizes: LadderMeasuredSizes,
        val railIndex: Map<String, Int>,
        val railX: DoubleArray,
        val groupInfo: Map<String, GroupInfo>,
    ) {
        var cursor = 0.0
        val rungs = mutableListOf<PlacedRung>()
        val bars = mutableListOf<PlacedBar>()
        val groups = mutableListOf<LadderGroupLayout>()
        /** Open bars on each rail, innermost last. */
        val openBars = mutableMapOf<String, MutableList<PlacedBar>>()

        fun events(events: List<LadderEvent>, groupDepth: Int) {
            events.forEach { e ->
                when (e) {
                    is LadderRung -> rung(e)
                    is LadderBarStart -> barStart(e)
                    is LadderBarEnd -> barEnd(e)
                    is LadderGroup -> group(e, groupDepth)
                }
            }
        }

        private fun rung(e: LadderRung) {
            if (e.from !in railIndex || e.to !in railIndex) return
            val labelHeight = rowHeight(sizes.rungLabels[e.id])
            val y1 = cursor + max(c.rungSpacing, labelHeight + 2 * c.labelGap)
            val y2 = if (e.isSelf) y1 + c.selfRungHeight else y1
            rungs += PlacedRung(e.id, e.from, e.to, y1, y2)
            cursor = y2
        }

        private fun barStart(e: LadderBarStart) {
            val i = railIndex[e.rail] ?: return
            val open = openBars.getOrPut(e.rail) { mutableListOf() }
            val depth = open.size
            open += PlacedBar(e.id, e.rail, depth, railX[i] + depth * c.barNestOffset, c.barWidth, cursor, cursor)
        }

        private fun barEnd(e: LadderBarEnd) {
            val (rail, open) = openBars.entries.firstOrNull { (_, list) -> list.any { it.id == e.id } } ?: return
            val bar = open.first { it.id == e.id }
            open.remove(bar)
            if (open.isEmpty()) openBars.remove(rail)
            val bottom = max(cursor, bar.top + c.minBarHeight)
            cursor = bottom
            bars += bar.copy(bottom = bottom)
        }

        private fun group(e: LadderGroup, depth: Int) {
            val info = groupInfo[e.id]
            if (info == null || info.firstRail < 0) {
                // No known rails: lay out the contents without a box.
                e.parts.forEach { events(it.events, depth + 1) }
                return
            }
            val left = railX[info.firstRail] - info.reachLeft
            val right = railX[info.lastRail] + info.reachRight
            val top = cursor + c.groupGap
            val tabSize = tabSize(e.id, sizes)
            val labelSize = sizes.groupLabels[e.id] ?: LadderSize.ZERO
            val tab = LadderRect(left, top, left + tabSize.width, top + tabSize.height)
            val label = LadderRect(left + c.tabPadding, top + c.tabPadding, left + c.tabPadding + labelSize.width, top + c.tabPadding + labelSize.height)

            var partTop = top
            val parts = e.parts.mapIndexed { index, part ->
                val partLabelSize = sizes.groupPartLabels[part.id]
                val labelLeft = if (index == 0) tab.right + c.tabPadding else left + c.tabPadding
                val partLabel = partLabelSize?.let {
                    LadderRect(labelLeft, partTop + c.tabPadding, labelLeft + it.width, partTop + c.tabPadding + it.height)
                }
                val headerHeight = max(
                    if (index == 0) tabSize.height else 0.0,
                    partLabelSize?.let { it.height + 2 * c.tabPadding } ?: 0.0
                )
                cursor = partTop + headerHeight
                events(part.events, depth + 1)
                val partBottom = cursor + c.groupPartPadding
                val layout = LadderGroupPartLayout(part.id, LadderRect(left, partTop, right, partBottom), partLabel)
                partTop = partBottom
                layout
            }
            val bottom = if (parts.isEmpty()) tab.bottom + c.groupPartPadding else partTop
            groups += LadderGroupLayout(e.id, depth, LadderRect(left, top, right, bottom), tab, label, parts)
            cursor = bottom + c.groupGap
        }
    }
}
