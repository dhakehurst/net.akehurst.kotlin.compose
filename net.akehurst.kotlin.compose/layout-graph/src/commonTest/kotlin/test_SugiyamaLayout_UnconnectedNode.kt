package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A node with no edges stays next to the other nodes of its layer;
 * it is not left behind with an empty band between it and them.
 */
class test_SugiyamaLayout_UnconnectedNode {

    @Test
    fun unconnected_node_stays_next_to_its_layer() {
        val tops = listOf("T1", "T2", "T3", "T4", "T5")
        val bottoms = listOf("B1", "B2", "B3", "B4")
        val nodes = listOf("U") + tops + bottoms
        val edges = tops.flatMapIndexed { i, t ->
            bottoms.mapIndexed { j, b -> SugiyamaEdge("e_${t}_$b", t, b) }
        }
        val d = SugiyamaLayout<String>().layoutGraph(nodes, edges)

        // Default node size is 100 x 50, default nodeSpacing 100.
        val (ux, uy) = d.nodePositions.getValue("U")
        val sameLayer = tops.map { d.nodePositions.getValue(it) }
        assertTrue(sameLayer.all { abs(it.second - uy) < 1e-6 }, "U is in the top layer: ${d.nodePositions}")
        val nearestGap = sameLayer.minOf { (x, _) -> if (x > ux) x - (ux + 100.0) else ux - (x + 100.0) }
        assertTrue(abs(nearestGap - 100.0) < 1e-6, "gap between U and the nearest node of its layer is $nearestGap, expected 100.0: ${d.nodePositions}")
    }
}
