package net.akehurst.kotlin.compose.layout.ladder

import net.akehurst.kotlin.compose.layout.graph.EdgeContentPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_LadderLayoutEngine {

    private val config = LadderLayoutConfig()
    private val engine = LadderLayoutEngine(config)
    private val header = LadderSize(80.0, 30.0)

    private fun sizes(rails: List<String>, rungLabels: Map<String, Map<EdgeContentPosition, LadderSize>> = emptyMap(), groupLabels: Map<String, LadderSize> = emptyMap(), partLabels: Map<String, LadderSize> = emptyMap()) =
        LadderMeasuredSizes(rails.associateWith { header }, rungLabels, groupLabels, partLabels)

    private fun rails(vararg ids: String) = ids.map { LadderRail(it) }

    @Test
    fun rails_are_columns_left_to_right_with_headers_apart() {
        val model = LadderModel(rails("a", "b", "c"), emptyList())
        val result = engine.layout(model, sizes(listOf("a", "b", "c")))
        val xs = result.rails.map { it.x }
        assertEquals(listOf("a", "b", "c"), result.rails.map { it.railId })
        assertTrue(xs[0] < xs[1] && xs[1] < xs[2])
        result.rails.zipWithNext().forEach { (l, r) ->
            assertTrue(r.header.left - l.header.right >= config.railGap - 1e-9, "headers too close: ${l.header} ${r.header}")
        }
        assertEquals(0.0, result.rails.minOf { it.header.left }, 1e-9)
        result.rails.forEach { assertEquals(it.header.bottom, it.lineTop, 1e-9) }
    }

    @Test
    fun rungs_go_top_to_bottom_in_order() {
        val model = LadderModel(
            rails("a", "b"),
            listOf(LadderRung("m1", "a", "b"), LadderRung("m2", "b", "a"), LadderRung("m3", "a", "b"))
        )
        val result = engine.layout(model, sizes(listOf("a", "b")))
        val ys = result.rungs.map { it.points.first().second }
        assertEquals(listOf("m1", "m2", "m3"), result.rungs.map { it.rungId })
        assertTrue(ys.zipWithNext().all { (a, b) -> b - a >= config.rungSpacing - 1e-9 }, "ys=$ys")
        val ax = result.rails[0].x
        val bx = result.rails[1].x
        assertEquals(listOf(ax to ys[0], bx to ys[0]), result.rungs[0].points)
        assertEquals(listOf(bx to ys[1], ax to ys[1]), result.rungs[1].points)
        assertTrue(result.rails.all { it.lineBottom > ys.last() })
    }

    @Test
    fun wide_rung_label_pushes_rails_apart() {
        val model = LadderModel(rails("a", "b", "c"), listOf(LadderRung("m1", "a", "c")))
        val narrow = engine.layout(model, sizes(listOf("a", "b", "c")))
        val wide = engine.layout(
            model, sizes(listOf("a", "b", "c"), rungLabels = mapOf("m1" to mapOf(EdgeContentPosition.MIDDLE to LadderSize(500.0, 16.0))))
        )
        val span = wide.rails[2].x - wide.rails[0].x
        assertTrue(span >= 500.0 + 2 * config.labelMargin, "span=$span")
        assertTrue(span > narrow.rails[2].x - narrow.rails[0].x)
        val label = wide.rungs[0].labels.getValue(EdgeContentPosition.MIDDLE)
        val line = wide.rungs[0].points
        assertEquals((line[0].first + line[1].first) / 2, (label.left + label.right) / 2, 1e-9)
        assertEquals(line[0].second - config.labelGap, label.bottom, 1e-9)
    }

    @Test
    fun self_rung_loops_out_to_the_right_and_back() {
        val model = LadderModel(rails("a", "b"), listOf(LadderRung("s", "a", "a")))
        val result = engine.layout(model, sizes(listOf("a", "b")))
        val p = result.rungs.single().points
        val ax = result.rails[0].x
        assertEquals(4, p.size)
        assertEquals(ax, p[0].first, 1e-9)
        assertEquals(ax, p[3].first, 1e-9)
        assertTrue(p[1].first > ax)
        assertEquals(p[0].second, p[1].second, 1e-9)
        assertEquals(p[2].second, p[3].second, 1e-9)
        assertEquals(config.selfRungHeight, p[3].second - p[0].second, 1e-9)
        assertTrue(p[1].first < result.rails[1].x)
    }

    @Test
    fun bar_spans_from_call_to_reply_and_rungs_end_on_its_edges() {
        val model = LadderModel(
            rails("a", "b"),
            listOf(
                LadderRung("call", "a", "b"),
                LadderBarStart("bar", "b"),
                LadderRung("reply", "b", "a"),
                LadderBarEnd("bar"),
            )
        )
        val result = engine.layout(model, sizes(listOf("a", "b")))
        val bar = result.bars.single()
        val call = result.rungs.first { it.rungId == "call" }.points
        val reply = result.rungs.first { it.rungId == "reply" }.points
        assertEquals(call[0].second, bar.rect.top, 1e-9)
        assertEquals(reply[0].second, bar.rect.bottom, 1e-9)
        assertEquals(bar.rect.left, call[1].first, 1e-9)
        assertEquals(bar.rect.left, reply[0].first, 1e-9)
        assertEquals(result.rails[1].x, (bar.rect.left + bar.rect.right) / 2, 1e-9)
    }

    @Test
    fun nested_bar_moves_right_and_self_rung_returns_to_it() {
        val model = LadderModel(
            rails("a"),
            listOf(
                LadderBarStart("outer", "a"),
                LadderRung("self", "a", "a"),
                LadderBarStart("inner", "a"),
                LadderBarEnd("inner"),
                LadderBarEnd("outer"),
            )
        )
        val result = engine.layout(model, sizes(listOf("a")))
        val outer = result.bars.first { it.barId == "outer" }
        val inner = result.bars.first { it.barId == "inner" }
        assertEquals(0, outer.depth)
        assertEquals(1, inner.depth)
        assertEquals(config.barNestOffset, inner.rect.left - outer.rect.left, 1e-9)
        assertTrue(inner.rect.height >= config.minBarHeight - 1e-9)
        assertTrue(outer.rect.top <= inner.rect.top && outer.rect.bottom >= inner.rect.bottom)
        val p = result.rungs.single().points
        assertEquals(outer.rect.right, p[0].first, 1e-9)
        assertEquals(inner.rect.right, p[3].first, 1e-9)
        assertEquals(inner.rect.top, p[3].second, 1e-9)
    }

    @Test
    fun group_spans_its_rails_and_contains_its_parts() {
        val model = LadderModel(
            rails("a", "b", "c"),
            listOf(
                LadderRung("before", "a", "b"),
                LadderGroup(
                    "alt", listOf("a", "b"), listOf(
                        LadderGroupPart("ok", listOf(LadderRung("m1", "a", "b"))),
                        LadderGroupPart("else", listOf(LadderRung("m2", "b", "a"), LadderRung("m3", "a", "b"))),
                    )
                ),
                LadderRung("after", "b", "c"),
            )
        )
        val result = engine.layout(
            model, sizes(
                listOf("a", "b", "c"),
                groupLabels = mapOf("alt" to LadderSize(24.0, 16.0)),
                partLabels = mapOf("ok" to LadderSize(40.0, 16.0), "else" to LadderSize(40.0, 16.0))
            )
        )
        val g = result.groups.single()
        val y = result.rungs.associate { it.rungId to it.points.first().second }
        val ax = result.rails[0].x
        val bx = result.rails[1].x
        val cx = result.rails[2].x
        assertTrue(g.rect.left < ax && g.rect.right > bx && g.rect.right < cx, "group ${g.rect}")
        assertTrue(y.getValue("before") < g.rect.top && g.rect.bottom < y.getValue("after"))
        val (ok, other) = g.parts
        assertEquals(g.rect.top, ok.rect.top, 1e-9)
        assertEquals(ok.rect.bottom, other.rect.top, 1e-9)
        assertEquals(g.rect.bottom, other.rect.bottom, 1e-9)
        assertTrue(y.getValue("m1") in ok.rect.top..ok.rect.bottom)
        assertTrue(y.getValue("m2") in other.rect.top..other.rect.bottom)
        assertTrue(y.getValue("m3") in other.rect.top..other.rect.bottom)
        // The tab is at the top left, with the label inside it; the first part's label is right of the tab.
        assertEquals(g.rect.left, g.tab.left, 1e-9)
        assertEquals(g.rect.top, g.tab.top, 1e-9)
        assertTrue(g.label.left >= g.tab.left && g.label.right <= g.tab.right && g.label.bottom <= g.tab.bottom)
        assertTrue(ok.label!!.left >= g.tab.right)
        assertTrue(other.label!!.top >= other.rect.top)
        // A rung's texts never reach above its part.
        assertTrue(y.getValue("m1") - config.rungSpacing >= ok.rect.top + g.tab.height - 1e-9)
    }

    @Test
    fun nested_groups_are_inside_their_outer_group() {
        val inner = LadderGroup("inner", listOf("a", "b"), listOf(LadderGroupPart("ip", listOf(LadderRung("m", "a", "b")))))
        val outer = LadderGroup("outer", listOf("a", "b"), listOf(LadderGroupPart("op", listOf(inner))))
        val result = engine.layout(LadderModel(rails("a", "b"), listOf(outer)), sizes(listOf("a", "b")))
        val o = result.groups.first { it.groupId == "outer" }
        val i = result.groups.first { it.groupId == "inner" }
        assertEquals(listOf("outer", "inner"), result.groups.map { it.groupId })
        assertTrue(o.rect.left < i.rect.left && o.rect.right > i.rect.right && o.rect.top < i.rect.top && o.rect.bottom > i.rect.bottom)
    }

    @Test
    fun inner_group_with_wide_label_stays_inside_outer_group() {
        val loop = LadderGroup("loop", listOf("c"), listOf(LadderGroupPart("lp", listOf(LadderRung("self", "c", "c")))))
        val alt = LadderGroup("alt", listOf("a", "b", "c"), listOf(LadderGroupPart("ap", listOf(LadderRung("m", "a", "c"), loop))))
        val inB = LadderGroup("inB", listOf("b"), listOf(LadderGroupPart("bp", listOf(LadderRung("x", "b", "b")))))
        val outer = LadderGroup("outer", listOf("a", "c"), listOf(LadderGroupPart("op", listOf(alt, inB))))
        val result = engine.layout(
            LadderModel(rails("a", "b", "c", "d"), listOf(outer)),
            sizes(listOf("a", "b", "c", "d"), groupLabels = mapOf("loop" to LadderSize(40.0, 16.0)), partLabels = mapOf("lp" to LadderSize(200.0, 16.0)))
        )
        val g = result.groups.associateBy { it.groupId }
        fun inside(innerId: String, outerId: String) {
            val i = g.getValue(innerId).rect
            val o = g.getValue(outerId).rect
            assertTrue(o.left < i.left && o.right > i.right && o.top < i.top && o.bottom > i.bottom, "$innerId $i not inside $outerId $o")
        }
        inside("loop", "alt")
        inside("alt", "outer")
        inside("inB", "outer")
        val loopRect = g.getValue("loop").rect
        assertTrue(loopRect.width >= 200.0 + 40.0, "loop too narrow for its labels: $loopRect")
        assertTrue(g.getValue("outer").rect.right < result.rails[3].header.left + result.rails[3].header.width / 2)
    }

    @Test
    fun validate_reports_problems() {
        val model = LadderModel(
            rails("a"),
            listOf(
                LadderRung("m", "a", "x"),
                LadderBarEnd("never"),
                LadderBarStart("open", "a"),
                LadderGroup("g", emptyList(), emptyList()),
            )
        )
        val messages = model.validate().map { it.message }
        assertTrue(messages.any { "unknown rail 'x'" in it }, "$messages")
        assertTrue(messages.any { "'never' ends but was not started" in it }, "$messages")
        assertTrue(messages.any { "'open' is started but never ends" in it }, "$messages")
        assertTrue(messages.any { "has no rails" in it }, "$messages")
        assertTrue(LadderModel(rails("a", "b"), listOf(LadderRung("m", "a", "b"))).validate().isEmpty())
    }

    @Test
    fun unknown_rails_and_open_bars_do_not_break_the_layout() {
        val model = LadderModel(rails("a"), listOf(LadderRung("m", "a", "x"), LadderBarStart("open", "a"), LadderRung("s", "a", "a")))
        val result = engine.layout(model, sizes(listOf("a")))
        assertEquals(listOf("s"), result.rungs.map { it.rungId })
        assertTrue(result.bars.single().rect.bottom >= result.rungs.single().points.last().second)
    }
}
