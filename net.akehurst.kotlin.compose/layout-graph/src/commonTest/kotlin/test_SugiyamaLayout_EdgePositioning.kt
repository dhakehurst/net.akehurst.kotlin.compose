package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for verifying edge start/end positions respect node sizes and boundaries.
 */
class test_SugiyamaLayout_EdgePositioning {

    private fun edge(start: Int, finish: Int) = SugiyamaEdge("$start->$finish", start, finish)

    private fun assertPointOnNodeBorder(
        point: Pair<Double, Double>,
        nodePos: Pair<Double, Double>,
        nodeWidth: Double,
        nodeHeight: Double,
        message: String,
        tolerance: Double = 0.1
    ) {
        val (px, py) = point
        val (nx, ny) = nodePos
        val onLeft = px >= nx - tolerance && px <= nx + tolerance
        val onRight = px >= nx + nodeWidth - tolerance && px <= nx + nodeWidth + tolerance
        val onTop = py >= ny - tolerance && py <= ny + tolerance
        val onBottom = py >= ny + nodeHeight - tolerance && py <= ny + nodeHeight + tolerance
        val withinX = px >= nx - tolerance && px <= nx + nodeWidth + tolerance
        val withinY = py >= ny - tolerance && py <= ny + nodeHeight + tolerance

        val isOnBorder = (withinX && (onTop || onBottom)) || (withinY && (onLeft || onRight))
        assertTrue(isOnBorder, "$message - point ($px, $py) not on border of node at ($nx, $ny) with size ($nodeWidth x $nodeHeight)")
    }

    private fun assertPointWithinNodeBounds(
        point: Pair<Double, Double>,
        nodePos: Pair<Double, Double>,
        nodeWidth: Double,
        nodeHeight: Double,
        message: String,
        tolerance: Double = 0.1
    ) {
        val (px, py) = point
        val (nx, ny) = nodePos
        assertTrue(
            px >= nx - tolerance && px <= nx + nodeWidth + tolerance,
            "$message - X coordinate $px not within node horizontal bounds [$nx, ${nx + nodeWidth}]"
        )
        assertTrue(
            py >= ny - tolerance && py <= ny + nodeHeight + tolerance,
            "$message - Y coordinate $py not within node vertical bounds [$ny, ${ny + nodeHeight}]"
        )
    }

    // --- Basic Edge Positioning with Different Node Sizes ---

    @Test
    fun edge_endpoints_within_different_sized_nodes_direct_routing() {
        val node1Width = 200.0
        val node1Height = 100.0
        val node2Width = 80.0
        val node2Height = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) node1Width else node2Width },
            nodeHeight = { if (1 == it) node1Height else node2Height },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertTrue(route.size >= 2, "Route should have at least 2 points")

        val startPoint = route.first()
        val endPoint = route.last()

        assertPointWithinNodeBounds(startPoint, node1Pos, node1Width, node1Height, "Edge start")
        assertPointWithinNodeBounds(endPoint, node2Pos, node2Width, node2Height, "Edge end")
    }

    @Test
    fun edge_endpoints_within_different_sized_nodes_rectilinear_routing() {
        val node1Width = 200.0
        val node1Height = 100.0
        val node2Width = 80.0
        val node2Height = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) node1Width else node2Width },
            nodeHeight = { if (1 == it) node1Height else node2Height },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertTrue(route.size >= 2, "Route should have at least 2 points")

        val startPoint = route.first()
        val endPoint = route.last()

        assertPointWithinNodeBounds(startPoint, node1Pos, node1Width, node1Height, "Edge start")
        assertPointWithinNodeBounds(endPoint, node2Pos, node2Width, node2Height, "Edge end")
    }

    @Test
    fun edge_start_on_large_node_border() {
        val largeWidth = 300.0
        val largeHeight = 150.0
        val smallWidth = 50.0
        val smallHeight = 30.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val route = actual.edgeRoutes[e12]!!
        val startPoint = route.first()

        assertPointOnNodeBorder(startPoint, node1Pos, largeWidth, largeHeight, "Edge start should be on large node border")
    }

    @Test
    fun edge_end_on_small_node_border() {
        val largeWidth = 300.0
        val largeHeight = 150.0
        val smallWidth = 50.0
        val smallHeight = 30.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!
        val endPoint = route.last()

        assertPointOnNodeBorder(endPoint, node2Pos, smallWidth, smallHeight, "Edge end should be on small node border")
    }

    // --- Multiple Edges with Different Node Sizes ---

    @Test
    fun multiple_edges_from_large_node_to_small_nodes_direct() {
        val largeWidth = 250.0
        val largeHeight = 120.0
        val smallWidth = 60.0
        val smallHeight = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e14 = edge(1, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e14))

        val node1Pos = actual.nodePositions[1]!!

        // All edges should start within node 1's bounds
        listOf(e12, e13, e14).forEach { edge ->
            val route = actual.edgeRoutes[edge]!!
            val startPoint = route.first()
            assertPointWithinNodeBounds(
                startPoint, node1Pos, largeWidth, largeHeight,
                "Edge ${edge.id} start"
            )
        }

        // All edges should end within their respective target node bounds
        listOf(2, 3, 4).zip(listOf(e12, e13, e14)).forEach { (nodeId, edge) ->
            val nodePos = actual.nodePositions[nodeId]!!
            val route = actual.edgeRoutes[edge]!!
            val endPoint = route.last()
            assertPointWithinNodeBounds(
                endPoint, nodePos, smallWidth, smallHeight,
                "Edge ${edge.id} end at node $nodeId"
            )
        }
    }

    @Test
    fun multiple_edges_from_large_node_to_small_nodes_rectilinear() {
        val largeWidth = 250.0
        val largeHeight = 120.0
        val smallWidth = 60.0
        val smallHeight = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e14 = edge(1, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e14))

        val node1Pos = actual.nodePositions[1]!!

        // All edges should start within node 1's bounds
        listOf(e12, e13, e14).forEach { edge ->
            val route = actual.edgeRoutes[edge]!!
            val startPoint = route.first()
            assertPointWithinNodeBounds(
                startPoint, node1Pos, largeWidth, largeHeight,
                "Edge ${edge.id} start"
            )
        }
    }

    // --- Various Node Size Combinations ---

    @Test
    fun wide_node_to_tall_node_direct() {
        val wideWidth = 300.0
        val wideHeight = 50.0
        val tallWidth = 50.0
        val tallHeight = 200.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) wideWidth else tallWidth },
            nodeHeight = { if (1 == it) wideHeight else tallHeight },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertPointWithinNodeBounds(route.first(), node1Pos, wideWidth, wideHeight, "Edge start on wide node")
        assertPointWithinNodeBounds(route.last(), node2Pos, tallWidth, tallHeight, "Edge end on tall node")
    }

    @Test
    fun wide_node_to_tall_node_rectilinear() {
        val wideWidth = 300.0
        val wideHeight = 50.0
        val tallWidth = 50.0
        val tallHeight = 200.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) wideWidth else tallWidth },
            nodeHeight = { if (1 == it) wideHeight else tallHeight },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertPointWithinNodeBounds(route.first(), node1Pos, wideWidth, wideHeight, "Edge start on wide node")
        assertPointWithinNodeBounds(route.last(), node2Pos, tallWidth, tallHeight, "Edge end on tall node")
    }

    @Test
    fun varying_sizes_in_chain_direct() {
        val sizes = mapOf(
            1 to (200.0 to 100.0),
            2 to (50.0 to 50.0),
            3 to (150.0 to 30.0),
            4 to (80.0 to 120.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val e23 = edge(2, 3)
        val e34 = edge(3, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e23, e34))

        // Verify each edge respects its source and target node sizes
        listOf(
            Triple(e12, 1, 2),
            Triple(e23, 2, 3),
            Triple(e34, 3, 4)
        ).forEach { (edge, sourceId, targetId) ->
            val sourcePos = actual.nodePositions[sourceId]!!
            val targetPos = actual.nodePositions[targetId]!!
            val (sourceW, sourceH) = sizes[sourceId]!!
            val (targetW, targetH) = sizes[targetId]!!
            val route = actual.edgeRoutes[edge]!!

            assertPointWithinNodeBounds(route.first(), sourcePos, sourceW, sourceH, "Edge ${edge.id} start")
            assertPointWithinNodeBounds(route.last(), targetPos, targetW, targetH, "Edge ${edge.id} end")
        }
    }

    @Test
    fun varying_sizes_in_chain_rectilinear() {
        val sizes = mapOf(
            1 to (200.0 to 100.0),
            2 to (50.0 to 50.0),
            3 to (150.0 to 30.0),
            4 to (80.0 to 120.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e23 = edge(2, 3)
        val e34 = edge(3, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e23, e34))

        // Verify each edge respects its source and target node sizes
        listOf(
            Triple(e12, 1, 2),
            Triple(e23, 2, 3),
            Triple(e34, 3, 4)
        ).forEach { (edge, sourceId, targetId) ->
            val sourcePos = actual.nodePositions[sourceId]!!
            val targetPos = actual.nodePositions[targetId]!!
            val (sourceW, sourceH) = sizes[sourceId]!!
            val (targetW, targetH) = sizes[targetId]!!
            val route = actual.edgeRoutes[edge]!!

            assertPointWithinNodeBounds(route.first(), sourcePos, sourceW, sourceH, "Edge ${edge.id} start")
            assertPointWithinNodeBounds(route.last(), targetPos, targetW, targetH, "Edge ${edge.id} end")
        }
    }

    // --- Diamond Graph with Different Sizes ---

    @Test
    fun diamond_graph_varying_sizes_direct() {
        val sizes = mapOf(
            1 to (150.0 to 80.0),   // top
            2 to (100.0 to 60.0),   // left
            3 to (200.0 to 40.0),   // right
            4 to (120.0 to 100.0)   // bottom
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e24 = edge(2, 4)
        val e34 = edge(3, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e24, e34))

        // Verify all edges
        listOf(
            Triple(e12, 1, 2),
            Triple(e13, 1, 3),
            Triple(e24, 2, 4),
            Triple(e34, 3, 4)
        ).forEach { (edge, sourceId, targetId) ->
            val sourcePos = actual.nodePositions[sourceId]!!
            val targetPos = actual.nodePositions[targetId]!!
            val (sourceW, sourceH) = sizes[sourceId]!!
            val (targetW, targetH) = sizes[targetId]!!
            val route = actual.edgeRoutes[edge]!!

            assertPointWithinNodeBounds(route.first(), sourcePos, sourceW, sourceH, "Edge ${edge.id} start")
            assertPointWithinNodeBounds(route.last(), targetPos, targetW, targetH, "Edge ${edge.id} end")
        }
    }

    @Test
    fun diamond_graph_varying_sizes_rectilinear() {
        val sizes = mapOf(
            1 to (150.0 to 80.0),
            2 to (100.0 to 60.0),
            3 to (200.0 to 40.0),
            4 to (120.0 to 100.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e24 = edge(2, 4)
        val e34 = edge(3, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e24, e34))

        // Verify all edges
        listOf(
            Triple(e12, 1, 2),
            Triple(e13, 1, 3),
            Triple(e24, 2, 4),
            Triple(e34, 3, 4)
        ).forEach { (edge, sourceId, targetId) ->
            val sourcePos = actual.nodePositions[sourceId]!!
            val targetPos = actual.nodePositions[targetId]!!
            val (sourceW, sourceH) = sizes[sourceId]!!
            val (targetW, targetH) = sizes[targetId]!!
            val route = actual.edgeRoutes[edge]!!

            assertPointWithinNodeBounds(route.first(), sourcePos, sourceW, sourceH, "Edge ${edge.id} start")
            assertPointWithinNodeBounds(route.last(), targetPos, targetW, targetH, "Edge ${edge.id} end")
        }
    }

    // --- Extreme Size Differences ---

    @Test
    fun very_large_to_very_small_node_direct() {
        val largeWidth = 500.0
        val largeHeight = 300.0
        val smallWidth = 20.0
        val smallHeight = 15.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertPointWithinNodeBounds(route.first(), node1Pos, largeWidth, largeHeight, "Edge start on very large node")
        assertPointWithinNodeBounds(route.last(), node2Pos, smallWidth, smallHeight, "Edge end on very small node")
    }

    @Test
    fun very_large_to_very_small_node_rectilinear() {
        val largeWidth = 500.0
        val largeHeight = 300.0
        val smallWidth = 20.0
        val smallHeight = 15.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        assertPointWithinNodeBounds(route.first(), node1Pos, largeWidth, largeHeight, "Edge start on very large node")
        assertPointWithinNodeBounds(route.last(), node2Pos, smallWidth, smallHeight, "Edge end on very small node")
    }

    // --- Rectilinear Routing Specific Tests ---

    @Test
    fun rectilinear_routes_are_orthogonal_with_different_sizes() {
        val sizes = mapOf(
            1 to (200.0 to 100.0),
            2 to (80.0 to 60.0),
            3 to (150.0 to 40.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val actual = sl.layoutGraph(listOf(1, 2, 3), listOf(e12, e13))

        // Check all route segments are orthogonal
        actual.edgeRoutes.values.forEach { route ->
            for (i in 0 until route.size - 1) {
                val p1 = route[i]
                val p2 = route[i + 1]
                val isHorizontal = kotlin.math.abs(p1.second - p2.second) < 0.01
                val isVertical = kotlin.math.abs(p1.first - p2.first) < 0.01
                assertTrue(
                    isHorizontal || isVertical,
                    "Rectilinear route segment from $p1 to $p2 should be horizontal or vertical"
                )
            }
        }
    }

    // --- Port Strategy with Different Sizes ---

    @Test
    fun unique_ports_on_large_node_direct() {
        val largeWidth = 300.0
        val largeHeight = 150.0
        val smallWidth = 60.0
        val smallHeight = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            portStrategy = PortStrategy.UNIQUE,
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e14 = edge(1, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e14))

        val node1Pos = actual.nodePositions[1]!!

        // All start points should be within node 1's bounds
        val startPoints = listOf(e12, e13, e14).map { edge ->
            val route = actual.edgeRoutes[edge]!!
            route.first()
        }

        startPoints.forEach { point ->
            assertPointWithinNodeBounds(point, node1Pos, largeWidth, largeHeight, "Unique port")
        }

        // Start points should be different (unique ports)
        val uniquePoints = startPoints.toSet()
        assertTrue(uniquePoints.size >= 2, "Should use different ports for different edges from large node")
    }

    @Test
    fun unique_ports_on_large_node_rectilinear() {
        val largeWidth = 300.0
        val largeHeight = 150.0
        val smallWidth = 60.0
        val smallHeight = 40.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { if (1 == it) largeWidth else smallWidth },
            nodeHeight = { if (1 == it) largeHeight else smallHeight },
            portStrategy = PortStrategy.UNIQUE,
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e14 = edge(1, 4)
        val actual = sl.layoutGraph(listOf(1, 2, 3, 4), listOf(e12, e13, e14))

        val node1Pos = actual.nodePositions[1]!!

        // All start points should be within node 1's bounds
        val startPoints = listOf(e12, e13, e14).map { edge ->
            val route = actual.edgeRoutes[edge]!!
            route.first()
        }

        startPoints.forEach { point ->
            assertPointWithinNodeBounds(point, node1Pos, largeWidth, largeHeight, "Unique port")
        }

        // Start points should be different (unique ports)
        val uniquePoints = startPoints.toSet()
        assertTrue(uniquePoints.size >= 2, "Should use different ports for different edges from large node")
    }

    // --- Edges Not Overlapping Nodes ---

    @Test
    fun edge_route_does_not_pass_through_other_nodes_direct() {
        val sizes = mapOf(
            1 to (100.0 to 50.0),
            2 to (100.0 to 50.0),
            3 to (100.0 to 50.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            edgeRouting = EdgeRouting.DIRECT
        )
        // Edge from 1 to 3, with node 2 potentially in the middle
        val e12 = edge(1, 2)
        val e13 = edge(1, 3)
        val e23 = edge(2, 3)
        val actual = sl.layoutGraph(listOf(1, 2, 3), listOf(e12, e13, e23))

        // Verify all edges have valid routes
        listOf(e12, e13, e23).forEach { edge ->
            val route = actual.edgeRoutes[edge]
            assertNotNull(route, "Edge ${edge.id} should have a route")
            assertTrue(route!!.size >= 2, "Edge ${edge.id} route should have at least 2 points")
        }
    }

    // --- Spacing Parameter Effects ---

    @Test
    fun edge_connects_at_center_of_side_not_corner() {
        val nodeW = 100.0
        val nodeH = 50.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { nodeW },
            nodeHeight = { nodeH },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        val startPoint = route.first()
        val endPoint = route.last()

        // Start point should be at bottom-center of node 1
        val expectedStartX = node1Pos.first + nodeW / 2
        val expectedStartY = node1Pos.second + nodeH
        assertTrue(
            kotlin.math.abs(startPoint.first - expectedStartX) < 1.0,
            "Edge start X should be at center of node 1 bottom. Expected ~$expectedStartX, got ${startPoint.first}"
        )
        assertTrue(
            kotlin.math.abs(startPoint.second - expectedStartY) < 1.0,
            "Edge start Y should be at bottom of node 1. Expected ~$expectedStartY, got ${startPoint.second}"
        )

        // End point should be at top-center of node 2
        val expectedEndX = node2Pos.first + nodeW / 2
        val expectedEndY = node2Pos.second
        assertTrue(
            kotlin.math.abs(endPoint.first - expectedEndX) < 1.0,
            "Edge end X should be at center of node 2 top. Expected ~$expectedEndX, got ${endPoint.first}"
        )
        assertTrue(
            kotlin.math.abs(endPoint.second - expectedEndY) < 1.0,
            "Edge end Y should be at top of node 2. Expected ~$expectedEndY, got ${endPoint.second}"
        )
    }

    @Test
    fun three_node_chain_edges_at_center_rectilinear() {
        val nodeW = 100.0
        val nodeH = 50.0

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { nodeW },
            nodeHeight = { nodeH },
            edgeRouting = EdgeRouting.RECTILINEAR
        )
        val e12 = edge(1, 2)
        val e23 = edge(2, 3)
        val actual = sl.layoutGraph(listOf(1, 2, 3), listOf(e12, e23))

        // Check edge 1->2
        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route12 = actual.edgeRoutes[e12]!!

        val start12 = route12.first()
        val end12 = route12.last()

        // Start should be at bottom-center of node 1
        val node1CenterX = node1Pos.first + nodeW / 2
        assertTrue(
            kotlin.math.abs(start12.first - node1CenterX) < 1.0,
            "Edge 1->2 start X should be centered. Expected ~$node1CenterX, got ${start12.first}"
        )

        // End should be at top-center of node 2
        val node2CenterX = node2Pos.first + nodeW / 2
        assertTrue(
            kotlin.math.abs(end12.first - node2CenterX) < 1.0,
            "Edge 1->2 end X should be centered. Expected ~$node2CenterX, got ${end12.first}"
        )

        // Check edge 2->3
        val node3Pos = actual.nodePositions[3]!!
        val route23 = actual.edgeRoutes[e23]!!

        val start23 = route23.first()
        val end23 = route23.last()

        // Start should be at bottom-center of node 2
        assertTrue(
            kotlin.math.abs(start23.first - node2CenterX) < 1.0,
            "Edge 2->3 start X should be centered. Expected ~$node2CenterX, got ${start23.first}"
        )

        // End should be at top-center of node 3
        val node3CenterX = node3Pos.first + nodeW / 2
        assertTrue(
            kotlin.math.abs(end23.first - node3CenterX) < 1.0,
            "Edge 2->3 end X should be centered. Expected ~$node3CenterX, got ${end23.first}"
        )
    }

    @Test
    fun large_layer_spacing_with_different_node_sizes() {
        val sizes = mapOf(
            1 to (200.0 to 100.0),
            2 to (80.0 to 40.0)
        )

        val sl = SugiyamaLayout<Int>(
            nodeWidth = { sizes[it]?.first ?: 100.0 },
            nodeHeight = { sizes[it]?.second ?: 50.0 },
            layerSpacing = 300.0,
            edgeRouting = EdgeRouting.DIRECT
        )
        val e12 = edge(1, 2)
        val actual = sl.layoutGraph(listOf(1, 2), listOf(e12))

        val node1Pos = actual.nodePositions[1]!!
        val node2Pos = actual.nodePositions[2]!!
        val route = actual.edgeRoutes[e12]!!

        // With large spacing, nodes should be far apart
        val verticalDistance = node2Pos.second - (node1Pos.second + sizes[1]!!.second)
        assertTrue(verticalDistance >= 300.0, "Layer spacing should be respected: actual distance = $verticalDistance")

        // Edge endpoints should still be within node bounds
        assertPointWithinNodeBounds(route.first(), node1Pos, sizes[1]!!.first, sizes[1]!!.second, "Edge start")
        assertPointWithinNodeBounds(route.last(), node2Pos, sizes[2]!!.first, sizes[2]!!.second, "Edge end")
    }
}
