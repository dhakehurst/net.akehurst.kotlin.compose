package net.akehurst.kotlin.compose.layout.graph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

//data class Node(val id: String)
//data class Edge(val source: Node, val target: Node)
//data class Graph(val nodes: List<Node>, val edges: List<Edge>)

//data class Point(val x: Double, val y: Double)
data class SugiyamaLayoutData<NT : Any>(
    val totalWidth: Double = 100.0,
    val totalHeight: Double= 100.0,
    val nodePositions: Map<NT, Pair<Double, Double>> = emptyMap(),
    val edgeRoutes: Map<SugiyamaEdge<NT>, List<Pair<Double, Double>>> = emptyMap(),
) {
}

enum class EdgeRouting {
    DIRECT,
    RECTILINEAR,
    /**
     * Like [RECTILINEAR], but edges that meet a node on the same side share one trunk and a bus line,
     * with a branch to each of their other ends (see [BusEdgeRouter]).
     * Only [CompoundGraphLayoutView] supports it; a [SugiyamaLayout] on its own routes such edges as [DIRECT].
     */
    BUS
}

enum class EdgePriority {
    FEWEST_CROSSINGS,
    SHORTEST_EDGES,
    FIRST_COME_FIRST_SERVED
}

enum class PortStrategy {
    SHARED,      // Multiple edges can share the same port if they go in similar directions
    UNIQUE       // Each edge gets its own unique connection point
}

data class SugiyamaEdge<NT>(
    val id:String,
    val start:NT,
    val finish:NT
)

private enum class ConnectionSide { TOP, BOTTOM, LEFT, RIGHT }

/**
 * Implements the Sugiyama-style layered graph layout algorithm.
 *
 * The algorithm works in several steps:
 * 1. Cycle Removal: Makes the graph acyclic by reversing some edges.
 * 2. Layer Assignment: Assigns each node to a layer (y-coordinate).
 * 3. Crossing Reduction: Reorders nodes within layers to minimize edge crossings.
 * 4. Coordinate Assignment: Assigns x and y coordinates to each node.
 *
 * @param nodeWidth The width of each node.
 * @param nodeHeight The height of each node.
 * @param layerSpacing The vertical distance between layers.
 * @param nodeSpacing The horizontal distance between nodes in the same layer.
 */
class SugiyamaLayout<NT : Any>(
    private val nodeWidth: (NT) -> Double = { 100.0 },
    private val nodeHeight: (NT) -> Double = { 50.0 },
    private val layerSpacing: Double = 100.0,
    private val nodeSpacing: Double = 100.0,
    private val edgeRouting: EdgeRouting = EdgeRouting.DIRECT,
    private val portStrategy: PortStrategy = PortStrategy.UNIQUE,
    private val edgePriority: EdgePriority = EdgePriority.FEWEST_CROSSINGS,
    private val nodeClearance: Double = 0.25, // as fraction of nodeSpacing
    /** Negative to keep a node at the left of its layer, positive to keep it at the right. */
    private val horizontalPull: (NT) -> Double = { 0.0 },
    /**
     * Where a node was in the layout this one replaces (null for a node that was not there). When any node has
     * one, nodes keep their previous layer where the edges allow it, and the previous order in each layer is
     * kept unless crossing reduction finds an order with fewer crossings.
     */
    private val previousBounds: (NT) -> CompoundRect? = { null }
) {

    private companion object {
        /** Upper bound on the transpose passes after each sweep; a pass that swaps nothing ends them earlier. */
        const val MAX_TRANSPOSE_PASSES = 100
    }

    private var dummyIdCounter = 0
    private fun nextDummyId(): String = "dummy_${dummyIdCounter++}"

    /**
     * Categorizes edges into regular edges, self-loops, and multi-edges.
     */
    private fun categorizeEdges(edges: List<SugiyamaEdge<NT>>): Triple<List<SugiyamaEdge<NT>>, List<SugiyamaEdge<NT>>, Map<SugiyamaEdge<NT>, Int>> {
        val selfLoops = edges.filter { it.start == it.finish }
        val nonSelfLoops = edges.filter { it.start != it.finish }

        // Count occurrences of each edge (for multi-edges)
        val edgeCounts = mutableMapOf<SugiyamaEdge<NT>, Int>()
        val regularEdges = mutableListOf<SugiyamaEdge<NT>>()

        nonSelfLoops.forEach { edge ->
            val count = (edgeCounts[edge] ?: 0) + 1
            edgeCounts[edge] = count
            if (count == 1) {
                regularEdges.add(edge)
            }
        }

        return Triple(regularEdges, selfLoops, edgeCounts.filter { it.value > 1 })
    }

    // Internal representation of a node in the layered graph
    private data class SNode<NT : Any>(
        val originalNode: NT?, // null for dummy nodes
        val layer: Int,
        val id: String
    ) {
        val isDummy: Boolean get() = originalNode == null
        var posInLayer: Int = 0
    }

    // Internal representation of an edge in the layered graph
    private data class SEdge<NT : Any>(
        val from: SNode<NT>,
        val to: SNode<NT>,
        val originalEdge: SugiyamaEdge<NT>? = null // Track original edge for multi-edges
    )

    // Port information for a node
    private data class Port(
        val position: Pair<Double, Double>,
        val side: ConnectionSide,
        val angle: Double // angle from node center to port
    )

    // Edge routing state
    private data class EdgeRoutingInfo<NT : Any>(
        val edge: SugiyamaEdge<NT>,
        val sourcePort: Port,
        val targetPort: Port,
        val crossingCount: Int = 0,
        val length: Double = 0.0
    )


    /**
     * Computes the layout for the given graph.
     */
    fun layoutGraph(nodes: List<NT>, edges: List<SugiyamaEdge<NT>>): SugiyamaLayoutData<NT> {
        dummyIdCounter = 0

        if (nodes.isEmpty()) {
            return SugiyamaLayoutData()
        }

        // Handle self-loops and multi-edges
        val (regularEdges, selfLoops, multiEdges) = categorizeEdges(edges)

        // 1. Cycle Removal
        val (newEdges, reversedEdges) = makeAcyclic(nodes, regularEdges)

        // 2. Layer Assignment
        val previous = nodes.mapNotNull { node -> previousBounds(node)?.let { node to it } }.toMap()
        val layers = if (previous.isEmpty()) assignLayers(nodes, newEdges) else assignLayersKeepingPrevious(nodes, newEdges, previous)

        // 3. Add dummy nodes for long edges
        val (layeredNodes, layeredEdges) = createLayeredGraph(nodes, newEdges, layers)

        // 4. Crossing Reduction (improved with multiple heuristics)
        if (previous.isEmpty()) reduceCrossingsImproved(layeredNodes, layeredEdges) else reduceCrossingsFromPrevious(layeredNodes, layeredEdges, previous)

        // 4b. Nodes pulled to one side (e.g. connected to a port on that side of their container) go to
        // that end of their layer; the order found above is kept otherwise.
        layeredNodes.forEach { layer ->
            fun side(n: SNode<NT>) = n.originalNode?.let(horizontalPull)?.let { if (it < 0) -1 else if (it > 0) 1 else 0 } ?: 0
            if (layer.any { side(it) != 0 }) {
                val sorted = layer.sortedBy { side(it) } // stable
                layer.clear()
                layer.addAll(sorted)
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }
        }

        // 5. Coordinate Assignment with port assignment
        return assignCoordinates(layeredNodes, layeredEdges, nodes, newEdges, reversedEdges, edges, selfLoops, multiEdges)
    }

    /**
     * Step 1: Makes the graph acyclic by reversing edges that form cycles.
     * This implementation uses a DFS-based approach to find and reverse back edges.
     * return edges, reveresedEdges
     */
    private fun makeAcyclic(nodes: List<NT>, edges: List<SugiyamaEdge<NT>>): Pair<List<SugiyamaEdge<NT>>, List<SugiyamaEdge<NT>>> {
        val backEdges = mutableSetOf<SugiyamaEdge<NT>>()
        val visitedNodes = mutableSetOf<NT>()
        val recursionStackNodes = mutableSetOf<NT>()
        val adj = edges.groupBy { it.start }

        fun findBackEdges(u: NT) {
            visitedNodes.add(u)
            recursionStackNodes.add(u)
            adj[u]?.forEach { edge ->
                val v = edge.finish
                if (v in recursionStackNodes) {
                    backEdges.add(edge)
                } else if (v !in visitedNodes) {
                    findBackEdges(v)
                }
            }
            recursionStackNodes.remove(u)
        }

        for (node in nodes) {
            if (node !in visitedNodes) {
                findBackEdges(node)
            }
        }

        val newEdges = edges.toMutableList()
        val reversedEdges = mutableListOf<SugiyamaEdge<NT>>()
        backEdges.forEach {
            newEdges.remove(it)
            newEdges.add(SugiyamaEdge<NT>(it.id, it.finish, it.start))
            reversedEdges.add(it)
        }

        return Pair(newEdges, reversedEdges)
    }

    /**
     * Step 2: Assigns each node to a layer using the longest path algorithm (topological sort).
     */
    private fun assignLayers(nodes: List<NT>, edges: List<SugiyamaEdge<NT>>): Map<NT, Int> {
        val layers = mutableMapOf<NT, Int>() //Node -> Layer
        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        edges.forEach { inDegree[it.finish] = inDegree.getValue(it.finish) + 1 }
        val outEdges = edges.groupBy { it.start }

        val queueNodes = ArrayDeque<NT>()
        nodes.filter { inDegree[it] == 0 }.forEach {
            queueNodes.add(it)
            layers[it] = 0
        }

        while (queueNodes.isNotEmpty()) {
            val u = queueNodes.removeFirst()
            outEdges[u].orEmpty().forEach { edge ->
                val v = edge.finish
                layers[v] = max(layers.getOrElse(v) { 0 }, layers.getValue(u) + 1)
                inDegree[v] = inDegree.getValue(v) - 1
                if (inDegree.getValue(v) == 0) {
                    queueNodes.add(v)
                }
            }
        }

        nodes.forEach { node ->
            if (node !in layers) {
                val qNodes = ArrayDeque<NT>()
                if (node !in layers) {
                    qNodes.add(node)
                    layers[node] = 0
                    while (qNodes.isNotEmpty()) {
                        val u = qNodes.removeFirst()
                        outEdges[u].orEmpty().forEach { edge ->
                            val v = edge.finish
                            if (v !in layers) {
                                layers[v] = layers.getValue(u) + 1
                                qNodes.add(v)
                            }
                        }
                    }
                }
            }
        }

        return layers
    }

    /**
     * Step 2, keeping the previous layout: as [assignLayers] (longest path from the sources), except that a node
     * that was in the previous layout goes no higher than its previous layer. The previous layers are the
     * distinct top edges of the previous nodes. Layers left with no node are then removed.
     */
    private fun assignLayersKeepingPrevious(nodes: List<NT>, edges: List<SugiyamaEdge<NT>>, previous: Map<NT, CompoundRect>): Map<NT, Int> {
        val tolerance = 0.5
        val previousTops = mutableListOf<Double>()
        previous.values.map { it.top }.sorted().forEach { top ->
            if (previousTops.isEmpty() || top - previousTops.last() > tolerance) previousTops.add(top)
        }
        val minimumLayer = previous.mapValues { (_, bounds) -> previousTops.indexOfLast { it <= bounds.top + tolerance }.coerceAtLeast(0) }

        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        edges.forEach { inDegree[it.finish] = inDegree.getValue(it.finish) + 1 }
        val outEdges = edges.groupBy { it.start }
        val required = mutableMapOf<NT, Int>()
        val layers = mutableMapOf<NT, Int>()
        val queue = ArrayDeque(nodes.filter { inDegree[it] == 0 })
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val layer = max(required[u] ?: 0, minimumLayer[u] ?: 0)
            layers[u] = layer
            outEdges[u].orEmpty().forEach { edge ->
                val v = edge.finish
                required[v] = max(required[v] ?: 0, layer + 1)
                inDegree[v] = inDegree.getValue(v) - 1
                if (inDegree.getValue(v) == 0) queue.add(v)
            }
        }
        // The edges are acyclic here, so every node has been reached; this only guards against a cycle left in.
        nodes.forEach { if (it !in layers) layers[it] = max(required[it] ?: 0, minimumLayer[it] ?: 0) }

        val rankOf = layers.values.distinct().sorted().withIndex().associate { (rank, layer) -> layer to rank }
        return layers.mapValues { (_, layer) -> rankOf.getValue(layer) }
    }

    /**
     * Step 3: Creates an intermediate graph representation with dummy nodes for edges spanning multiple layers.
     */
    private fun createLayeredGraph(nodes: List<NT>, edges: List<SugiyamaEdge<NT>>, layers: Map<NT, Int>): Pair<List<MutableList<SNode<NT>>>, List<SEdge<NT>>> {
        val nodeMap = nodes.associateWith { SNode(it, layers.getValue(it), it.toString()) }
        val allSNodes = nodeMap.values.toMutableList()
        val allSEdges = mutableListOf<SEdge<NT>>()

        edges.forEach { edge ->
            val fromNode = nodeMap.getValue(edge.start)
            val toNode = nodeMap.getValue(edge.finish)
            var u = fromNode
            for (i in (fromNode.layer + 1) until toNode.layer) {
                val dummy = SNode<NT>(null, i, nextDummyId())
                allSNodes.add(dummy)
                allSEdges.add(SEdge(u, dummy, edge))
                u = dummy
            }
            allSEdges.add(SEdge(u, toNode, edge))
        }

        val groupedByLayer = allSNodes.groupBy { it.layer }
        val layeredNodes = groupedByLayer.keys.sorted().map {
            groupedByLayer.getValue(it).toMutableList()
        }

        return layeredNodes to allSEdges
    }

    /**
     * Edges between two adjacent layers, grouped once per [layoutGraph] call.
     * [between] has one entry per pair of adjacent layers: entry i holds the edges from layer i to layer i + 1.
     * [upper] and [lower] give, for each node, the other end of each of its edges into the layer above and below
     * (one entry per edge, so parallel edges count more than once).
     */
    private class LayerEdges<NT : Any>(
        val between: List<List<SEdge<NT>>>,
        val upper: Map<SNode<NT>, List<SNode<NT>>>,
        val lower: Map<SNode<NT>, List<SNode<NT>>>
    )

    private fun groupEdgesByLayerPair(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>): LayerEdges<NT> {
        val between = List(max(0, layeredNodes.size - 1)) { mutableListOf<SEdge<NT>>() }
        val upper = mutableMapOf<SNode<NT>, MutableList<SNode<NT>>>()
        val lower = mutableMapOf<SNode<NT>, MutableList<SNode<NT>>>()
        edges.forEach { edge ->
            val i = edge.from.layer
            // Only edges from a layer to the next one take part in crossings (as in counting them).
            if (edge.to.layer == i + 1 && i >= 0 && i < between.size) {
                between[i].add(edge)
                lower.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
                upper.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
            }
        }
        return LayerEdges(between, upper, lower)
    }

    /**
     * Step 4: Improved crossing reduction using multiple heuristics.
     */
    private fun reduceCrossingsImproved(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>) {
        val adj = edges.groupBy { it.from.id }
        val revAdj = edges.groupBy { it.to.id }
        val nodeMap = layeredNodes.flatten().associateBy { it.id }
        val layerEdges = groupEdgesByLayerPair(layeredNodes, edges)

        layeredNodes.forEach { layer ->
            layer.forEachIndexed { index, node -> node.posInLayer = index }
        }

        var bestCrossings = countCrossings(layeredNodes, layerEdges)
        var noImprovementCount = 0
        val maxNoImprovement = 5
        val maxIterations = 24

        for (iteration in 0 until maxIterations) {
            // Alternate between barycenter and median heuristics
            val useMedian = iteration % 2 == 1

            // Sweep down
            for (layerIndex in 1 until layeredNodes.size) {
                val layer = layeredNodes[layerIndex]
                if (useMedian) {
                    sortByMedian(layer, revAdj, nodeMap, isDownward = true)
                } else {
                    sortByBarycenter(layer, revAdj, nodeMap, isDownward = true)
                }
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }

            // Sweep up
            for (layerIndex in layeredNodes.size - 2 downTo 0) {
                val layer = layeredNodes[layerIndex]
                if (useMedian) {
                    sortByMedian(layer, adj, nodeMap, isDownward = false)
                } else {
                    sortByBarycenter(layer, adj, nodeMap, isDownward = false)
                }
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }

            // Apply transpose optimization. Every pass that swaps removes at least one crossing, so this ends;
            // the cap only bounds the time for very large layers.
            var passes = 0
            while (passes < MAX_TRANSPOSE_PASSES && applyTranspose(layeredNodes, layerEdges)) {
                passes++
            }

            val currentCrossings = countCrossings(layeredNodes, layerEdges)
            if (currentCrossings < bestCrossings) {
                bestCrossings = currentCrossings
                noImprovementCount = 0
            } else {
                noImprovementCount++
            }

            if (noImprovementCount >= maxNoImprovement) {
                break
            }
        }
    }

    /**
     * Step 4, keeping the previous layout: each layer starts in the previous left-to-right order (by the previous
     * centres; a new node or dummy node goes at the mean of its neighbours already placed), improved only by
     * swapping neighbours ([applyTranspose]). The full [reduceCrossingsImproved] then runs from that order, and its
     * result is used only if it has fewer crossings.
     */
    private fun reduceCrossingsFromPrevious(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>, previous: Map<NT, CompoundRect>) {
        val key = mutableMapOf<SNode<NT>, Double>()
        layeredNodes.forEach { layer ->
            layer.forEach { sNode -> sNode.originalNode?.let { previous[it] }?.let { key[sNode] = (it.left + it.right) / 2.0 } }
        }
        val upperEdges = edges.groupBy { it.to }
        val lowerEdges = edges.groupBy { it.from }
        fun placeFrom(sNode: SNode<NT>, neighbours: List<SNode<NT>>) {
            val keys = neighbours.mapNotNull { key[it] }
            if (keys.isNotEmpty()) key[sNode] = keys.average()
        }
        repeat(2) {
            layeredNodes.forEach { layer -> layer.forEach { if (it !in key) placeFrom(it, upperEdges[it].orEmpty().map { e -> e.from }) } }
            layeredNodes.asReversed().forEach { layer -> layer.forEach { if (it !in key) placeFrom(it, lowerEdges[it].orEmpty().map { e -> e.to }) } }
        }
        layeredNodes.forEach { layer ->
            layer.sortWith(compareBy<SNode<NT>>({ key[it] ?: Double.MAX_VALUE }, { it.id }))
            layer.forEachIndexed { index, node -> node.posInLayer = index }
        }

        val layerEdges = groupEdgesByLayerPair(layeredNodes, edges)
        var passes = 0
        while (passes < MAX_TRANSPOSE_PASSES && applyTranspose(layeredNodes, layerEdges)) {
            passes++
        }
        val keptCrossings = countCrossings(layeredNodes, layerEdges)
        if (keptCrossings == 0) return
        val kept = layeredNodes.map { it.toList() }
        reduceCrossingsImproved(layeredNodes, edges)
        if (countCrossings(layeredNodes, layerEdges) >= keptCrossings) {
            layeredNodes.forEachIndexed { i, layer ->
                layer.clear()
                layer.addAll(kept[i])
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }
        }
    }

    /**
     * Sorts nodes using barycenter heuristic.
     */
    private fun sortByBarycenter(
        layer: MutableList<SNode<NT>>,
        adjacency: Map<String, List<SEdge<NT>>>,
        nodeMap: Map<String, SNode<NT>>,
        isDownward: Boolean
    ) {
        val barycenters = layer.associate { node ->
            val neighbors = adjacency[node.id]?.mapNotNull { edge ->
                nodeMap[if (isDownward) edge.from.id else edge.to.id]
            } ?: emptyList()
            val center = if (neighbors.isEmpty()) -1.0 else neighbors.map { it.posInLayer }.average()
            node.id to center
        }
        layer.sortWith(compareBy<SNode<NT>>({ barycenters[it.id] }, { it.id }))
    }

    /**
     * Sorts nodes using median heuristic.
     */
    private fun sortByMedian(
        layer: MutableList<SNode<NT>>,
        adjacency: Map<String, List<SEdge<NT>>>,
        nodeMap: Map<String, SNode<NT>>,
        isDownward: Boolean
    ) {
        val medians = layer.associate { node ->
            val neighbors = adjacency[node.id]?.mapNotNull { edge ->
                nodeMap[if (isDownward) edge.from.id else edge.to.id]
            } ?: emptyList()
            val positions = neighbors.map { it.posInLayer }.sorted()
            val median = if (positions.isEmpty()) {
                -1.0
            } else if (positions.size % 2 == 1) {
                positions[positions.size / 2].toDouble()
            } else {
                (positions[positions.size / 2 - 1] + positions[positions.size / 2]) / 2.0
            }
            node.id to median
        }
        layer.sortWith(compareBy<SNode<NT>>({ medians[it.id] }, { it.id }))
    }

    /**
     * Applies transpose optimization: swaps adjacent nodes if it reduces crossings.
     *
     * Swapping two adjacent nodes u, v only changes the crossings between an edge of u and an edge of v
     * (Gansner et al., "A technique for drawing directed graphs"), so the swap is decided from those:
     * it is made when c(v, u) < c(u, v), counted with the layers above and below. That is the same as
     * comparing the crossings of the whole graph before and after the swap.
     */
    private fun applyTranspose(layeredNodes: List<MutableList<SNode<NT>>>, layerEdges: LayerEdges<NT>): Boolean {
        var improved = false
        for (layer in layeredNodes) {
            for (i in 0 until layer.size - 1) {
                val u = layer[i]
                val v = layer[i + 1]
                val (upperUV, upperVU) = pairCrossings(layerEdges.upper[u], layerEdges.upper[v])
                val (lowerUV, lowerVU) = pairCrossings(layerEdges.lower[u], layerEdges.lower[v])
                if (upperVU + lowerVU < upperUV + lowerUV) {
                    layer[i] = v
                    layer[i + 1] = u
                    v.posInLayer = i
                    u.posInLayer = i + 1
                    improved = true
                }
            }
        }
        return improved
    }

    /**
     * For nodes u, v next to each other in a layer (u on the left), with neighbours [uNeighbours] and [vNeighbours]
     * in one adjacent layer: the crossings between u's and v's edges as they are (first) and with u and v swapped (second).
     * Edges that end at the same neighbour do not cross either way.
     */
    private fun pairCrossings(uNeighbours: List<SNode<NT>>?, vNeighbours: List<SNode<NT>>?): Pair<Int, Int> {
        if (uNeighbours.isNullOrEmpty() || vNeighbours.isNullOrEmpty()) return 0 to 0
        val a = IntArray(uNeighbours.size) { uNeighbours[it].posInLayer }.also { it.sort() }
        val b = IntArray(vNeighbours.size) { vNeighbours[it].posInLayer }.also { it.sort() }
        var asIs = 0L // pairs with a > b
        var swapped = 0L // pairs with a < b
        var lessThan = 0 // number of b < current a
        var lessOrEqual = 0 // number of b <= current a
        for (x in a) {
            while (lessThan < b.size && b[lessThan] < x) lessThan++
            if (lessOrEqual < lessThan) lessOrEqual = lessThan
            while (lessOrEqual < b.size && b[lessOrEqual] <= x) lessOrEqual++
            asIs += lessThan
            swapped += b.size - lessOrEqual
        }
        return asIs.toInt() to swapped.toInt()
    }

    /**
     * Counts the total number of edge crossings.
     */
    private fun countCrossings(layeredNodes: List<MutableList<SNode<NT>>>, layerEdges: LayerEdges<NT>): Int {
        var crossings = 0
        for (i in 0 until layeredNodes.size - 1) {
            crossings += countCrossingsBetweenLayers(layerEdges.between[i], layeredNodes[i + 1].size)
        }
        return crossings
    }

    /**
     * Counts crossings between two adjacent layers in O(E log V)
     * (Barth, Juenger, Mutzel, "Simple and efficient bilayer cross counting"):
     * with the edges sorted by their upper end and then their lower end, two edges cross when the later one
     * ends strictly left of the earlier one in the lower layer.
     */
    private fun countCrossingsBetweenLayers(edges: List<SEdge<NT>>, lowerLayerSize: Int): Int {
        if (edges.size < 2) return 0
        val sorted = edges.sortedWith(compareBy<SEdge<NT>>({ it.from.posInLayer }, { it.to.posInLayer }))
        // Fenwick tree over the positions in the lower layer: how many edges seen so far end at each position.
        val size = max(lowerLayerSize, sorted.maxOf { it.to.posInLayer } + 1)
        val tree = IntArray(size + 1)
        var crossings = 0L
        sorted.forEachIndexed { seen, edge ->
            val pos = edge.to.posInLayer
            // edges seen so far that end at or left of pos
            var atOrLeft = 0
            var k = pos + 1
            while (k > 0) {
                atOrLeft += tree[k]; k -= k and -k
            }
            crossings += seen - atOrLeft
            k = pos + 1
            while (k <= size) {
                tree[k]++; k += k and -k
            }
        }
        return crossings.toInt()
    }

    private fun computeNodeBorderIntersection(
        nodeCenter: Pair<Double, Double>,
        nodeSize: Pair<Double, Double>,
        externalPoint: Pair<Double, Double>
    ): Pair<Double, Double> {
        val (nodeCenterX, nodeCenterY) = nodeCenter
        val (nodeWidth, nodeHeight) = nodeSize
        val (externalX, externalY) = externalPoint

        val dx = externalX - nodeCenterX
        val dy = externalY - nodeCenterY

        if (dx == 0.0 && dy == 0.0) return nodeCenter

        val halfWidth = nodeWidth / 2
        val halfHeight = nodeHeight / 2

        if (halfWidth == 0.0 || halfHeight == 0.0) return nodeCenter

        // Calculate the slope of the line from the node center to the external point
        val slope = if (dx != 0.0) dy / dx else Double.POSITIVE_INFINITY

        // Calculate the slope of the diagonals of the node's bounding box
        val diagonalSlope = halfHeight / halfWidth

        val intersectX: Double
        val intersectY: Double

        if (abs(slope) < diagonalSlope) {
            // Intersection is with a vertical edge (left or right)
            intersectX = if (dx > 0) halfWidth else -halfWidth
            intersectY = slope * intersectX
        } else {
            // Intersection is with a horizontal edge (top or bottom)
            intersectY = if (dy > 0) halfHeight else -halfHeight
            intersectX = if (slope.isFinite() && slope != 0.0) intersectY / slope else 0.0
        }

        return Pair(nodeCenterX + intersectX, nodeCenterY + intersectY)
    }

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double)



    private fun getSide(nodePos: Pair<Double, Double>, nodeSize: Pair<Double, Double>, externalPoint: Pair<Double, Double>): ConnectionSide {
        val (nodeX, nodeY) = nodePos
        val (nodeWidth, nodeHeight) = nodeSize
        val (extX, extY) = externalPoint

        val nodeLeft = nodeX
        val nodeRight = nodeX + nodeWidth
        val nodeTop = nodeY
        val nodeBottom = nodeY + nodeHeight

        // Determine connection side based on the external point's position relative to the node's bounding box
        if (extY >= nodeBottom) {
            return ConnectionSide.BOTTOM
        } else if (extY <= nodeTop) {
            return ConnectionSide.TOP
        } else if (extX >= nodeRight) {
            return ConnectionSide.RIGHT
        } else if (extX <= nodeLeft) {
            return ConnectionSide.LEFT
        } else {
            // Fallback for when the external point is within the node's x and y range.
            val dTop = abs(extY - nodeTop)
            val dBottom = abs(extY - nodeBottom)
            val dLeft = abs(extX - nodeLeft)
            val dRight = abs(extX - nodeRight)

            when (minOf(dTop, dBottom, dLeft, dRight)) {
                dTop -> return ConnectionSide.TOP
                dBottom -> return ConnectionSide.BOTTOM
                dLeft -> return ConnectionSide.LEFT
                else -> return ConnectionSide.RIGHT
            }
        }
    }




    /**
     * Finds a path from start to end node through dummy nodes.
     */
    private fun findPath(
        start: SNode<NT>,
        end: SNode<NT>,
        adjacency: Map<SNode<NT>, List<SEdge<NT>>>
    ): List<SNode<NT>> {
        val queue = ArrayDeque<List<SNode<NT>>>()
        queue.add(listOf(start))
        val visited = mutableSetOf(start)

        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val lastNode = currentPath.last()
            if (lastNode == end) {
                return currentPath
            }
            adjacency[lastNode]?.forEach { edge ->
                val neighbor = edge.to
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(currentPath + neighbor)
                }
            }
        }
        return emptyList()
    }

    /**
     * Computes the angle from node center to a point.
     */
    private fun computeAngle(center: Pair<Double, Double>, point: Pair<Double, Double>): Double {
        val dx = point.first - center.first
        val dy = point.second - center.second
        return kotlin.math.atan2(dy, dx)
    }

    /**
     * Assigns ports to all edges based on edge direction, priorities, and strategy.
     */
    private fun assignPorts(
        edgePaths: Map<SugiyamaEdge<NT>, List<SNode<NT>>>,
        nodePositions: Map<NT, Pair<Double, Double>>,
        edges: List<SugiyamaEdge<NT>>,
        layeredEdges: List<SEdge<NT>>,
        sNodePositions: Map<SNode<NT>, Pair<Double, Double>>
    ): Map<SugiyamaEdge<NT>, EdgeRoutingInfo<NT>> {
        val result = mutableMapOf<SugiyamaEdge<NT>, EdgeRoutingInfo<NT>>()

        // Group edges by source and target nodes
        val edgesBySource = edges.groupBy { it.start }
        val edgesByTarget = edges.groupBy { it.finish }

        // Process each node
        val allNodes = nodePositions.keys.toSet()
        allNodes.forEach { node ->
            val outgoingEdges = edgesBySource[node] ?: emptyList()
            val incomingEdges = edgesByTarget[node] ?: emptyList()

            // Assign source ports
            if (outgoingEdges.isNotEmpty()) {
                assignSourcePorts(node, outgoingEdges, edgePaths, nodePositions, sNodePositions, result)
            }

            // Assign target ports
            if (incomingEdges.isNotEmpty()) {
                assignTargetPorts(node, incomingEdges, edgePaths, nodePositions, sNodePositions, result)
            }
        }

        return result
    }

    /**
     * Assigns source ports for all outgoing edges from a node.
     */
    private fun assignSourcePorts(
        node: NT,
        edges: List<SugiyamaEdge<NT>>,
        edgePaths: Map<SugiyamaEdge<NT>, List<SNode<NT>>>,
        nodePositions: Map<NT, Pair<Double, Double>>,
        sNodePositions: Map<SNode<NT>, Pair<Double, Double>>,
        result: MutableMap<SugiyamaEdge<NT>, EdgeRoutingInfo<NT>>
    ) {
        val nodePos = nodePositions[node] ?: return
        val nodeW = nodeWidth(node)
        val nodeH = nodeHeight(node)
        val nodeCenter = Pair(nodePos.first + nodeW / 2, nodePos.second + nodeH / 2)

        // Determine the next point for each edge (either next dummy or target)
        val edgeNextPoints = edges.associateWith { edge ->
            val path = edgePaths[edge] ?: emptyList()
            if (path.size > 1) sNodePositions[path[1]] else null
        }

        // Group edges by side
        val edgesBySide = edges.groupBy { edge ->
            val nextPoint = edgeNextPoints[edge]
            if (nextPoint != null) {
                getSide(nodePos, Pair(nodeW, nodeH), nextPoint)
            } else {
                ConnectionSide.BOTTOM
            }
        }

        // Assign ports for each side
        edgesBySide.forEach { (side, sideEdges) ->
            val ports = generatePortsOnSide(nodePos, Pair(nodeW, nodeH), side, sideEdges.size)

            // Sort edges by angle/direction for better distribution
            val sortedEdges = sideEdges.sortedBy { edge ->
                val nextPoint = edgeNextPoints[edge]
                if (nextPoint != null) computeAngle(nodeCenter, nextPoint) else 0.0
            }

            sortedEdges.forEachIndexed { index, edge ->
                val port = ports[index]
                val existing = result[edge]
                result[edge] = if (existing != null) {
                    existing.copy(sourcePort = port)
                } else {
                    EdgeRoutingInfo(edge, port, Port(Pair(0.0, 0.0), ConnectionSide.TOP, 0.0))
                }
            }
        }
    }

    /**
     * Assigns target ports for all incoming edges to a node.
     */
    private fun assignTargetPorts(
        node: NT,
        edges: List<SugiyamaEdge<NT>>,
        edgePaths: Map<SugiyamaEdge<NT>, List<SNode<NT>>>,
        nodePositions: Map<NT, Pair<Double, Double>>,
        sNodePositions: Map<SNode<NT>, Pair<Double, Double>>,
        result: MutableMap<SugiyamaEdge<NT>, EdgeRoutingInfo<NT>>
    ) {
        val nodePos = nodePositions[node] ?: return
        val nodeW = nodeWidth(node)
        val nodeH = nodeHeight(node)
        val nodeCenter = Pair(nodePos.first + nodeW / 2, nodePos.second + nodeH / 2)

        // Determine the previous point for each edge
        val edgePrevPoints = edges.associateWith { edge ->
            val path = edgePaths[edge] ?: emptyList()
            if (path.size > 1) sNodePositions[path[path.size - 2]] else null
        }

        // Group edges by side
        val edgesBySide = edges.groupBy { edge ->
            val prevPoint = edgePrevPoints[edge]
            if (prevPoint != null) {
                getSide(nodePos, Pair(nodeW, nodeH), prevPoint)
            } else {
                ConnectionSide.TOP
            }
        }

        // Assign ports for each side
        edgesBySide.forEach { (side, sideEdges) ->
            val ports = generatePortsOnSide(nodePos, Pair(nodeW, nodeH), side, sideEdges.size)

            // Sort edges by angle/direction
            val sortedEdges = sideEdges.sortedBy { edge ->
                val prevPoint = edgePrevPoints[edge]
                if (prevPoint != null) computeAngle(nodeCenter, prevPoint) else 0.0
            }

            sortedEdges.forEachIndexed { index, edge ->
                val port = ports[index]
                val existing = result[edge]
                result[edge] = if (existing != null) {
                    existing.copy(targetPort = port)
                } else {
                    EdgeRoutingInfo(edge, Port(Pair(0.0, 0.0), ConnectionSide.TOP, 0.0), port)
                }
            }
        }
    }

    /**
     * Creates an orthogonal route from start to end, avoiding obstacles.
     */
    private fun createOrthogonalRoute(
        centerPath: List<Pair<Double, Double>>,
        nodePositions: Map<NT, Pair<Double, Double>>,
        routingInfo: EdgeRoutingInfo<NT>?
    ): List<Pair<Double, Double>> {
        if (centerPath.size < 2) return centerPath

        val startPoint = centerPath.first()
        val endPoint = centerPath.last()
        val intermediateCenters = centerPath.subList(1, centerPath.size - 1)

        // Build obstacle rectangles with clearance
        val clearance = nodeSpacing * nodeClearance
        val obstacles = nodePositions.map { (node, pos) ->
            val w = nodeWidth(node)
            val h = nodeHeight(node)
            Rect(
                pos.first - clearance,
                pos.second - clearance,
                pos.first + w + clearance,
                pos.second + h + clearance
            )
        }

        val route = mutableListOf<Pair<Double, Double>>()
        route.add(startPoint)

        // Get port sides if available
        val startSide = routingInfo?.sourcePort?.side
        val endSide = routingInfo?.targetPort?.side

        // Add initial segment from port based on its side
        if (intermediateCenters.isNotEmpty()) {
            val firstIntermediate = intermediateCenters.first()
            val initialBend = createInitialBend(startPoint, firstIntermediate, startSide)
            if (initialBend != startPoint && initialBend != firstIntermediate) {
                route.add(initialBend)
            }
        }

        // Route through intermediate points (dummy nodes)
        for (i in 0 until intermediateCenters.size) {
            val current = intermediateCenters[i]
            val next = if (i < intermediateCenters.size - 1) {
                intermediateCenters[i + 1]
            } else {
                endPoint
            }

            // Create orthogonal path from current to next
            val segment = createOrthogonalSegment(route.last(), current, next, obstacles)
            route.addAll(segment)
        }

        // Add final segment to port based on its side
        if (intermediateCenters.isNotEmpty()) {
            val lastIntermediate = intermediateCenters.last()
            val finalBend = createFinalBend(lastIntermediate, endPoint, endSide)
            if (finalBend != lastIntermediate && finalBend != endPoint && finalBend != route.last()) {
                route.add(finalBend)
            }
        } else {
            // Direct connection, create simple orthogonal path
            val midSegment = createSimpleOrthogonalPath(startPoint, endPoint, startSide, endSide)
            route.addAll(midSegment)
        }

        route.add(endPoint)

        // Clean up redundant points
        return cleanupPath(route)
    }

    /**
     * Creates initial bend point based on port side.
     */
    private fun createInitialBend(
        start: Pair<Double, Double>,
        target: Pair<Double, Double>,
        side: ConnectionSide?
    ): Pair<Double, Double> {
        return when (side) {
            ConnectionSide.LEFT, ConnectionSide.RIGHT -> Pair(target.first, start.second)
            ConnectionSide.TOP, ConnectionSide.BOTTOM -> Pair(start.first, target.second)
            null -> Pair(start.first, target.second) // default
        }
    }

    /**
     * Creates final bend point based on port side.
     */
    private fun createFinalBend(
        source: Pair<Double, Double>,
        end: Pair<Double, Double>,
        side: ConnectionSide?
    ): Pair<Double, Double> {
        return when (side) {
            ConnectionSide.LEFT, ConnectionSide.RIGHT -> Pair(source.first, end.second)
            ConnectionSide.TOP, ConnectionSide.BOTTOM -> Pair(end.first, source.second)
            null -> Pair(end.first, source.second) // default
        }
    }

    /**
     * Creates a simple orthogonal path between two points.
     */
    private fun createSimpleOrthogonalPath(
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
        startSide: ConnectionSide?,
        endSide: ConnectionSide?
    ): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()

        // Determine routing based on sides
        when {
            startSide == ConnectionSide.LEFT || startSide == ConnectionSide.RIGHT -> {
                // Exit horizontally, then turn vertically
                result.add(Pair(end.first, start.second))
            }
            startSide == ConnectionSide.TOP || startSide == ConnectionSide.BOTTOM -> {
                // Exit vertically, then turn horizontally
                result.add(Pair(start.first, end.second))
            }
            else -> {
                // Default: vertical then horizontal
                result.add(Pair(start.first, end.second))
            }
        }

        return result
    }

    /**
     * Creates orthogonal segment avoiding obstacles.
     */
    private fun createOrthogonalSegment(
        from: Pair<Double, Double>,
        via: Pair<Double, Double>,
        to: Pair<Double, Double>,
        obstacles: List<Rect>
    ): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()

        // Route through via point with orthogonal turns
        if (from.first != via.first && from.second != via.second) {
            // Need two segments to reach via
            val midY = (from.second + via.second) / 2

            // Check if horizontal path at midY is clear
            if (isHorizontalPathClear(from.first, via.first, midY, obstacles)) {
                result.add(Pair(from.first, midY))
                result.add(Pair(via.first, midY))
            } else {
                // Try routing around obstacles
                result.add(Pair(via.first, from.second))
            }
        }

        result.add(via)

        return result
    }

    /**
     * Checks if a horizontal path is clear of obstacles.
     */
    private fun isHorizontalPathClear(
        x1: Double,
        x2: Double,
        y: Double,
        obstacles: List<Rect>
    ): Boolean {
        val minX = min(x1, x2)
        val maxX = max(x1, x2)
        return obstacles.none { rect ->
            y > rect.top && y < rect.bottom && maxX > rect.left && minX < rect.right
        }
    }

    /**
     * Removes redundant collinear points from a path.
     */
    private fun cleanupPath(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (path.size <= 2) return path

        val cleaned = mutableListOf<Pair<Double, Double>>()
        cleaned.add(path.first())

        for (i in 1 until path.size - 1) {
            val prev = cleaned.last()
            val current = path[i]
            val next = path[i + 1]

            // Check if current point is on the line between prev and next
            val isCollinear = (prev.first == current.first && current.first == next.first) ||
                             (prev.second == current.second && current.second == next.second)

            if (!isCollinear) {
                cleaned.add(current)
            }
        }

        cleaned.add(path.last())
        return cleaned
    }

    /**
     * Generates evenly distributed ports on a given side of a node.
     */
    private fun generatePortsOnSide(
        nodePos: Pair<Double, Double>,
        nodeSize: Pair<Double, Double>,
        side: ConnectionSide,
        count: Int,
        inset: Double = 5.0
    ): List<Port> {
        val (nodeX, nodeY) = nodePos
        val (nodeW, nodeH) = nodeSize
        val ports = mutableListOf<Port>()
        val nodeCenter = Pair(nodeX + nodeW / 2, nodeY + nodeH / 2)

        when (side) {
            ConnectionSide.TOP, ConnectionSide.BOTTOM -> {
                val y = if (side == ConnectionSide.TOP) nodeY else nodeY + nodeH
                val availableWidth = nodeW - 2 * inset
                if (availableWidth <= 0 || count == 0) {
                    ports.add(Port(Pair(nodeX + nodeW / 2, y), side, computeAngle(nodeCenter, Pair(nodeX + nodeW / 2, y))))
                } else {
                    val spacing = availableWidth / (count + 1)
                    for (i in 1..count) {
                        val x = nodeX + inset + i * spacing
                        val pos = Pair(x, y)
                        ports.add(Port(pos, side, computeAngle(nodeCenter, pos)))
                    }
                }
            }
            ConnectionSide.LEFT, ConnectionSide.RIGHT -> {
                val x = if (side == ConnectionSide.LEFT) nodeX else nodeX + nodeW
                val availableHeight = nodeH - 2 * inset
                if (availableHeight <= 0 || count == 0) {
                    ports.add(Port(Pair(x, nodeY + nodeH / 2), side, computeAngle(nodeCenter, Pair(x, nodeY + nodeH / 2))))
                } else {
                    val spacing = availableHeight / (count + 1)
                    for (i in 1..count) {
                        val y = nodeY + inset + i * spacing
                        val pos = Pair(x, y)
                        ports.add(Port(pos, side, computeAngle(nodeCenter, pos)))
                    }
                }
            }
        }

        return ports
    }

    /**
     * Step 5: Assigns final coordinates to nodes and determines edge routes with port assignment.
     */
    private fun computeCentreXs(
        layeredNodes: List<MutableList<SNode<NT>>>,
        layeredEdges: List<SEdge<NT>>,
        layerWidths: List<Double>,
        maxWidth: Double
    ): Map<SNode<NT>, Double> {
        fun width(n: SNode<NT>) = n.originalNode?.let(nodeWidth) ?: 0.0
        val centre = mutableMapOf<SNode<NT>, Double>()
        // Start from the packed, centred placement.
        layeredNodes.forEachIndexed { layerIndex, layer ->
            var x = (maxWidth - layerWidths[layerIndex]) / 2.0
            layer.forEach { n ->
                centre[n] = x + width(n) / 2.0
                x += width(n) + nodeSpacing
            }
        }
        val up = mutableMapOf<SNode<NT>, MutableList<SNode<NT>>>()
        val down = mutableMapOf<SNode<NT>, MutableList<SNode<NT>>>()
        layeredEdges.forEach { e ->
            val (a, b) = if (e.from.layer <= e.to.layer) e.from to e.to else e.to to e.from
            if (a.layer != b.layer) {
                down.getOrPut(a) { mutableListOf() }.add(b)
                up.getOrPut(b) { mutableListOf() }.add(a)
            }
        }

        fun place(layer: List<SNode<NT>>, neighbours: Map<SNode<NT>, List<SNode<NT>>>) {
            if (layer.isEmpty()) return
            fun gap(i: Int) = width(layer[i - 1]) / 2.0 + nodeSpacing + width(layer[i]) / 2.0
            // A node with neighbours wants the barycentre of its neighbours.
            val wanted: List<Double?> = layer.map { n ->
                val ns = neighbours[n].orEmpty()
                if (ns.isEmpty()) null else ns.sumOf { centre.getValue(it) } / ns.size
            }
            // A node without neighbours wants to sit next to its neighbour in the layer (the one to its left,
            // or for those before the first connected node, the one to its right), so it travels with the layer.
            val desired = DoubleArray(layer.size)
            val firstKnown = wanted.indexOfFirst { it != null }
            if (firstKnown < 0) {
                layer.forEachIndexed { i, n -> desired[i] = centre.getValue(n) }
            } else {
                desired[firstKnown] = wanted[firstKnown]!!
                for (i in firstKnown - 1 downTo 0) desired[i] = desired[i + 1] - gap(i + 1)
                for (i in firstKnown + 1 until layer.size) desired[i] = wanted[i] ?: (desired[i - 1] + gap(i))
            }
            // Push right from the left, and push left from the right; the average keeps order and spacing.
            val l = DoubleArray(layer.size)
            for (i in layer.indices) l[i] = if (i == 0) desired[0] else max(desired[i], l[i - 1] + gap(i))
            val r = DoubleArray(layer.size)
            for (i in layer.indices.reversed()) r[i] = if (i == layer.lastIndex) desired[i] else min(desired[i], r[i + 1] - gap(i + 1))
            layer.forEachIndexed { i, n -> centre[n] = (l[i] + r[i]) / 2.0 }
        }

        repeat(4) {
            for (i in 1 until layeredNodes.size) place(layeredNodes[i], up)
            for (i in layeredNodes.size - 2 downTo 0) place(layeredNodes[i], down)
        }

        // Shift so the leftmost node starts at 0.
        val minLeft = centre.entries.minOfOrNull { (n, c) -> c - width(n) / 2.0 } ?: 0.0
        return centre.mapValues { it.value - minLeft }
    }

    private fun assignCoordinates(
        layeredNodes: List<MutableList<SNode<NT>>>,
        layeredEdges: List<SEdge<NT>>,
        nodes: List<NT>,
        edges: List<SugiyamaEdge<NT>>,
        reversedEdges: List<SugiyamaEdge<NT>>,
        originalEdges: List<SugiyamaEdge<NT>>,
        selfLoops: List<SugiyamaEdge<NT>>,
        multiEdges: Map<SugiyamaEdge<NT>, Int>
    ): SugiyamaLayoutData<NT> {
        val nodePositions = mutableMapOf<NT, Pair<Double, Double>>()
        val dummyPositions = mutableMapOf<SNode<NT>, Pair<Double, Double>>()

        // Calculate layer dimensions and positions
        val layerWidths = layeredNodes.map { it.sumOf { sNode -> sNode.originalNode?.let { nodeWidth(it) } ?: 0.0 } + (it.size - 1).coerceAtLeast(0) * nodeSpacing }
        val maxWidth = layerWidths.maxOrNull() ?: 0.0

        val layerHeights = layeredNodes.map { layer -> layer.maxOfOrNull { sNode -> sNode.originalNode?.let { nodeHeight(it) } ?: 0.0 } ?: 0.0 }
        val layerTops = mutableListOf(0.0)
        for (i in 0 until layeredNodes.size - 1) {
            layerTops.add(layerTops[i] + layerHeights[i] + layerSpacing)
        }

        // Position nodes: each node is pulled towards the centres of its neighbours in the
        // adjacent layers (down and up sweeps), keeping the order and at least nodeSpacing between nodes.
        val centreX = computeCentreXs(layeredNodes, layeredEdges, layerWidths, maxWidth)
        layeredNodes.forEachIndexed { layerIndex, layer ->
            val currentLayerTop = layerTops[layerIndex]
            layer.forEach { sNode ->
                val nodeW = sNode.originalNode?.let(nodeWidth) ?: 0.0
                val pos = Pair(centreX.getValue(sNode) - nodeW / 2.0, currentLayerTop) // this is top-left
                if (sNode.isDummy) {
                    dummyPositions[sNode] = pos
                } else {
                    nodePositions[sNode.originalNode!!] = pos
                }
            }
        }

        // Build center positions for all nodes (including dummies)
        val sNodePositions = layeredNodes.flatten().associateWith { sNode ->
            val pos = if (sNode.isDummy) dummyPositions[sNode]!! else nodePositions[sNode.originalNode!!]!!
            val w = sNode.originalNode?.let(nodeWidth) ?: 0.0
            val h = sNode.originalNode?.let(nodeHeight) ?: 0.0
            Pair(pos.first + w / 2, pos.second + h / 2)
        }

        // Build paths through dummy nodes for each original edge
        val nodeToSNode = layeredNodes.flatten().filterNot { it.isDummy }.associateBy { it.originalNode }

        // Build paths through dummy nodes for each acyclic edge
        val edgePaths = mutableMapOf<SugiyamaEdge<NT>, List<SNode<NT>>>()
        // Each edge follows only its own segments: searching all of them could route a long edge through
        // another edge's real node (A->C through B when A->B and B->C exist) instead of its own dummy nodes.
        val sAdjByEdge = layeredEdges.groupBy { it.originalEdge }.mapValues { (_, segments) -> segments.groupBy { it.from } }
        edges.forEach { edge ->
            val startSNode = nodeToSNode.getValue(edge.start)
            val endSNode = nodeToSNode.getValue(edge.finish)
            val path = findPath(startSNode, endSNode, sAdjByEdge[edge] ?: emptyMap())
            edgePaths[edge] = path
        }

        // Assign ports to edges based on priorities and strategies
        val portAssignments = assignPorts(edgePaths, nodePositions, edges, layeredEdges, sNodePositions)

        // Build initial edge routes (using centers for dummy nodes, ports for real nodes)
        val unadjustedAcyclicEdgeRoutes = edges.associateWith { edge ->
            val path = edgePaths[edge] ?: emptyList()
            path.mapIndexed { index, sNode ->
                when {
                    index == 0 -> portAssignments[edge]?.sourcePort?.position ?: sNodePositions.getValue(sNode)
                    index == path.size - 1 -> portAssignments[edge]?.targetPort?.position ?: sNodePositions.getValue(sNode)
                    else -> sNodePositions.getValue(sNode)
                }
            }
        }

        val acyclicEdgeRoutes = if (edgeRouting == EdgeRouting.RECTILINEAR) {
            // Create improved orthogonal routes with obstacle avoidance
            unadjustedAcyclicEdgeRoutes.mapValues { (edge, path) ->
                if (path.size < 2) {
                    path
                } else {
                    createOrthogonalRoute(path, nodePositions, portAssignments[edge])
                }
            }
        } else { // DIRECT
            // For DIRECT routing, use port positions directly (they're already on the node border)
            unadjustedAcyclicEdgeRoutes
        }

        val finalEdgeRoutes = mutableMapOf<SugiyamaEdge<NT>, List<Pair<Double, Double>>>()

        // Add routes for regular edges (including reversed ones)
        val reversedEdgeSet = reversedEdges.toSet()
        originalEdges.forEach { edge ->
            if (edge in reversedEdgeSet) {
                val reversed = SugiyamaEdge<NT>(edge.id,edge.finish, edge.start)
                acyclicEdgeRoutes[reversed]?.let { finalEdgeRoutes[edge] = it.reversed() }
            } else {
                acyclicEdgeRoutes[edge]?.let { finalEdgeRoutes[edge] = it }
            }
        }

        // Add routes for self-loops
        selfLoops.forEach { edge ->
            val route = createSelfLoopRoute(edge.start, nodePositions)
            finalEdgeRoutes[edge] = route
        }

        // Add routes for multi-edges (create parallel/curved routes)
        multiEdges.forEach { (edge, count) ->
            if (edge !in finalEdgeRoutes) {
                // Primary edge already added above, add additional parallel routes
                val baseRoute = finalEdgeRoutes[edge] ?: emptyList()
                for (i in 2..count) {
                    // For now, just use the base route - proper parallel routing would require more work
                    finalEdgeRoutes[edge] = baseRoute
                }
            }
        }

        val totalWidth = nodePositions.entries.maxOfOrNull { (n, p) -> p.first + nodeWidth(n) } ?: 0.0
        val totalHeight = (layerTops.lastOrNull() ?: 0.0) + (layerHeights.lastOrNull() ?: 0.0)
        return SugiyamaLayoutData(totalWidth, totalHeight, nodePositions, finalEdgeRoutes)
    }

    /**
     * Creates a route for a self-loop edge.
     */
    private fun createSelfLoopRoute(
        node: NT,
        nodePositions: Map<NT, Pair<Double, Double>>
    ): List<Pair<Double, Double>> {
        val nodePos = nodePositions[node] ?: return emptyList()
        val nodeW = nodeWidth(node)
        val nodeH = nodeHeight(node)

        // Create a loop on the right side of the node
        val loopWidth = nodeW * 0.5
        val loopHeight = nodeH * 0.5

        val startX = nodePos.first + nodeW
        val startY = nodePos.second + nodeH * 0.25
        val endY = nodePos.second + nodeH * 0.75

        return listOf(
            Pair(startX, startY),
            Pair(startX + loopWidth, startY),
            Pair(startX + loopWidth, startY + loopHeight),
            Pair(startX + loopWidth, endY),
            Pair(startX, endY)
        )
    }
}