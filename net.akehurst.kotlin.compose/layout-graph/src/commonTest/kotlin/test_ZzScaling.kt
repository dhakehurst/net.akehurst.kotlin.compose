package net.akehurst.kotlin.compose.layout.graph

import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.TimeSource

class test_ZzScaling {
    private fun graph(packages: Int, classes: Int, edgesPerClass: Int, routing: EdgeRouting): GraphLayoutCompoundGraphState {
        val rnd = Random(1)
        val root = GraphLayoutCompoundGraph("root")
        val pkgOf = mutableMapOf<String, String>()
        (0 until packages).forEach { p ->
            root.nodes["P$p"] = GraphLayoutCompoundNode("P$p")
            root.children["P$p"] = GraphLayoutCompoundGraph("P$p")
        }
        (0 until classes).forEach { c ->
            val p = "P${c % packages}"
            root.children.getValue(p).nodes["C$c"] = GraphLayoutCompoundNode("C$c", widthHint = 160.0, heightHint = 80.0)
            pkgOf["C$c"] = p
        }
        var n = 0
        (0 until classes).forEach { c ->
            repeat(edgesPerClass) {
                val t = rnd.nextInt(classes)
                if (t != c) {
                    val s = "C$c"; val d = "C$t"
                    val owner = if (pkgOf[s] == pkgOf[d]) root.children.getValue(pkgOf[s]!!) else root
                    val id = "E${n++}"; owner.edges[id] = GraphLayoutCompoundEdge(id, s, d)
                }
            }
        }
        return GraphLayoutCompoundGraphState(id = "s", routing = routing, root = root)
    }

    @Test
    fun scaling() {
        for (routing in listOf(EdgeRouting.RECTILINEAR)) for (c in listOf(25, 50, 100, 150, 200)) {
            val s = graph(10, c, 3, routing)
            val t = TimeSource.Monotonic.markNow()
            CompoundLayoutEngine().layout(s)
            println("SCALING $routing classes=$c edges~${c * 3}: ${t.elapsedNow()}")
        }
    }
}
