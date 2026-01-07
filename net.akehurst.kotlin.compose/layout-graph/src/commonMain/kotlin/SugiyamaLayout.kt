package net.akehurst.kotlin.components.layout.graph

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
    RECTILINEAR
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
    private val nodeClearance: Double = 0.25 // as fraction of nodeSpacing
) {

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
        val layers = assignLayers(nodes, newEdges)

        // 3. Add dummy nodes for long edges
        val (layeredNodes, layeredEdges) = createLayeredGraph(nodes, newEdges, layers)

        // 4. Crossing Reduction (improved with multiple heuristics)
        reduceCrossingsImproved(layeredNodes, layeredEdges)

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

        val queueNodes = ArrayDeque<NT>()
        nodes.filter { inDegree[it] == 0 }.forEach {
            queueNodes.add(it)
            layers[it] = 0
        }

        while (queueNodes.isNotEmpty()) {
            val u = queueNodes.removeFirst()
            edges.filter { it.start == u }.forEach { edge ->
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
                        edges.filter { it.start == u }.forEach { edge ->
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
     * Step 4: Improved crossing reduction using multiple heuristics.
     */
    private fun reduceCrossingsImproved(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>) {
        val adj = edges.groupBy { it.from.id }
        val revAdj = edges.groupBy { it.to.id }
        val nodeMap = layeredNodes.flatten().associateBy { it.id }

        layeredNodes.forEach { layer ->
            layer.forEachIndexed { index, node -> node.posInLayer = index }
        }

        var bestCrossings = countCrossings(layeredNodes, edges)
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

            // Apply transpose optimization
            var improved = true
            while (improved) {
                improved = applyTranspose(layeredNodes, edges)
            }

            val currentCrossings = countCrossings(layeredNodes, edges)
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
        layer.sortBy { barycenters[it.id] }
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
        layer.sortBy { medians[it.id] }
    }

    /**
     * Applies transpose optimization: swaps adjacent nodes if it reduces crossings.
     */
    private fun applyTranspose(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>): Boolean {
        var improved = false
        for (layer in layeredNodes) {
            for (i in 0 until layer.size - 1) {
                val before = countCrossings(layeredNodes, edges)
                // Swap adjacent nodes
                val temp = layer[i]
                layer[i] = layer[i + 1]
                layer[i + 1] = temp
                layer.forEachIndexed { index, node -> node.posInLayer = index }

                val after = countCrossings(layeredNodes, edges)
                if (after < before) {
                    improved = true
                } else {
                    // Swap back
                    val temp2 = layer[i]
                    layer[i] = layer[i + 1]
                    layer[i + 1] = temp2
                    layer.forEachIndexed { index, node -> node.posInLayer = index }
                }
            }
        }
        return improved
    }

    /**
     * Counts the total number of edge crossings.
     */
    private fun countCrossings(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>): Int {
        var crossings = 0
        for (i in 0 until layeredNodes.size - 1) {
            val upperLayer = layeredNodes[i]
            val lowerLayer = layeredNodes[i + 1]
            val edgesBetween = edges.filter { edge ->
                edge.from.layer == i && edge.to.layer == i + 1
            }
            crossings += countCrossingsBetweenLayers(edgesBetween, upperLayer, lowerLayer)
        }
        return crossings
    }

    /**
     * Counts crossings between two adjacent layers.
     */
    private fun countCrossingsBetweenLayers(
        edges: List<SEdge<NT>>,
        upperLayer: List<SNode<NT>>,
        lowerLayer: List<SNode<NT>>
    ): Int {
        var crossings = 0
        for (i in edges.indices) {
            for (j in i + 1 until edges.size) {
                val e1 = edges[i]
                val e2 = edges[j]
                val e1FromPos = e1.from.posInLayer
                val e1ToPos = e1.to.posInLayer
                val e2FromPos = e2.from.posInLayer
                val e2ToPos = e2.to.posInLayer

                // Check if edges cross
                if ((e1FromPos < e2FromPos && e1ToPos > e2ToPos) ||
                    (e1FromPos > e2FromPos && e1ToPos < e2ToPos)) {
                    crossings++
                }
            }
        }
        return crossings
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

        // Position nodes
        layeredNodes.forEachIndexed { layerIndex, layer ->
            val layerWidth = layerWidths[layerIndex]
            val currentLayerTop = layerTops[layerIndex]
            var x = (maxWidth - layerWidth) / 2.0

            layer.forEach { sNode ->
                val nodeW = sNode.originalNode?.let(nodeWidth) ?: 0.0
                val pos = Pair(x, currentLayerTop) // this is top-left
                if (sNode.isDummy) {
                    dummyPositions[sNode] = pos
                } else {
                    nodePositions[sNode.originalNode!!] = pos
                }
                x += nodeW + nodeSpacing
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
        val sAdj = layeredEdges.groupBy { it.from }

        // Build paths through dummy nodes for each acyclic edge
        val edgePaths = mutableMapOf<SugiyamaEdge<NT>, List<SNode<NT>>>()
        edges.forEach { edge ->
            val startSNode = nodeToSNode.getValue(edge.start)
            val endSNode = nodeToSNode.getValue(edge.finish)
            val path = findPath(startSNode, endSNode, sAdj)
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
        originalEdges.forEach { edge ->
            if (edge in reversedEdges) {
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

        val totalWidth = maxWidth
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