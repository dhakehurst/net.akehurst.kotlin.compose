package demo

object DemoScenarios {

    val flatChain = DemoScenario(
        id = "flat_chain",
        title = "Flat chain",
        nodes = listOf(
            DemoNode("A", x = 80f, y = 120f, width = 90f, height = 56f),
            DemoNode("B", x = 220f, y = 120f, width = 90f, height = 56f),
            DemoNode("C", x = 360f, y = 120f, width = 90f, height = 56f),
            DemoNode("D", x = 500f, y = 120f, width = 90f, height = 56f)
        ),
        edges = listOf(
            DemoEdge("e_flat_1", "A", "B"),
            DemoEdge("e_flat_2", "B", "C"),
            DemoEdge("e_flat_3", "C", "D")
        )
    )

    val singleContainerTwoNodes = DemoScenario(
        id = "single_container_two_nodes",
        title = "Single container two nodes",
        nodes = listOf(
            DemoNode("Container1", x = 90f, y = 70f, width = 360f, height = 240f),
            DemoNode("InsideA", x = 140f, y = 130f, width = 100f, height = 56f, containerId = "Container1"),
            DemoNode("InsideB", x = 300f, y = 130f, width = 100f, height = 56f, containerId = "Container1"),
            DemoNode("Outside", x = 510f, y = 130f, width = 110f, height = 56f)
        ),
        edges = listOf(
            DemoEdge("e_single_1", "InsideA", "InsideB"),
            DemoEdge("e_single_2", "InsideB", "Outside")
        )
    )

    val siblingContainersCrossEdges = DemoScenario(
        id = "sibling_containers_cross_edges",
        title = "Sibling containers cross edges",
        nodes = listOf(
            DemoNode("ContainerL", x = 60f, y = 70f, width = 280f, height = 240f),
            DemoNode("ContainerR", x = 380f, y = 70f, width = 280f, height = 240f),
            DemoNode("L1", x = 110f, y = 130f, width = 90f, height = 50f, containerId = "ContainerL"),
            DemoNode("L2", x = 220f, y = 200f, width = 90f, height = 50f, containerId = "ContainerL"),
            DemoNode("R1", x = 430f, y = 130f, width = 90f, height = 50f, containerId = "ContainerR"),
            DemoNode("R2", x = 540f, y = 200f, width = 90f, height = 50f, containerId = "ContainerR")
        ),
        edges = listOf(
            DemoEdge("e_sibling_1", "L1", "R1"),
            DemoEdge("e_sibling_2", "L2", "R2"),
            DemoEdge("e_sibling_3", "R1", "L2")
        )
    )

    val deepNesting = DemoScenario(
        id = "deep_nesting",
        title = "Deep nesting",
        nodes = listOf(
            DemoNode("RootContainer", x = 60f, y = 40f, width = 620f, height = 340f),
            DemoNode("Level1", x = 120f, y = 90f, width = 500f, height = 250f, containerId = "RootContainer"),
            DemoNode("Level2", x = 180f, y = 140f, width = 260f, height = 140f, containerId = "Level1"),
            DemoNode("LeafA", x = 210f, y = 180f, width = 90f, height = 48f, containerId = "Level2"),
            DemoNode("LeafB", x = 330f, y = 180f, width = 90f, height = 48f, containerId = "Level2"),
            DemoNode("External", x = 520f, y = 180f, width = 90f, height = 48f, containerId = "Level1")
        ),
        edges = listOf(
            DemoEdge("e_deep_1", "LeafA", "LeafB"),
            DemoEdge("e_deep_2", "LeafB", "External")
        )
    )

    val collapsedContainerExternalLinks = DemoScenario(
        id = "collapsed_container_external_links",
        title = "Collapsed container external links",
        nodes = listOf(
            DemoNode("Upstream", x = 70f, y = 140f, width = 110f, height = 56f),
            DemoNode("CollapsedMid", x = 250f, y = 110f, width = 200f, height = 120f),
            DemoNode("Downstream", x = 520f, y = 140f, width = 120f, height = 56f)
        ),
        edges = listOf(
            DemoEdge("e_collapsed_1", "Upstream", "CollapsedMid"),
            DemoEdge("e_collapsed_2", "CollapsedMid", "Downstream")
        )
    )

    val mixedCollapsedExpandedSiblings = DemoScenario(
        id = "mixed_collapsed_expanded_siblings",
        title = "Mixed collapsed/expanded siblings",
        nodes = listOf(
            DemoNode("CollapsedSib", x = 80f, y = 90f, width = 180f, height = 120f),
            DemoNode("ExpandedSib", x = 320f, y = 70f, width = 320f, height = 250f),
            DemoNode("Ex1", x = 380f, y = 140f, width = 90f, height = 48f, containerId = "ExpandedSib"),
            DemoNode("Ex2", x = 500f, y = 220f, width = 90f, height = 48f, containerId = "ExpandedSib")
        ),
        edges = listOf(
            DemoEdge("e_mixed_1", "CollapsedSib", "Ex1"),
            DemoEdge("e_mixed_2", "Ex1", "Ex2")
        )
    )

    val umlLikeInheritanceAssociation = DemoScenario(
        id = "uml_like_inheritance_association",
        title = "UML-like inheritance and association",
        nodes = listOf(
            DemoNode("Base", x = 300f, y = 60f, width = 120f, height = 56f),
            DemoNode("DerivedA", x = 170f, y = 190f, width = 130f, height = 56f),
            DemoNode("DerivedB", x = 430f, y = 190f, width = 130f, height = 56f),
            DemoNode("Service", x = 300f, y = 300f, width = 120f, height = 56f)
        ),
        edges = listOf(
            DemoEdge("e_uml_1", "DerivedA", "Base"),
            DemoEdge("e_uml_2", "DerivedB", "Base"),
            DemoEdge("e_uml_3", "Service", "DerivedA"),
            DemoEdge("e_uml_4", "Service", "DerivedB")
        )
    )

    val stateLikeRegions = DemoScenario(
        id = "state_like_regions",
        title = "State-like regions",
        nodes = listOf(
            DemoNode("StateMachine", x = 50f, y = 40f, width = 640f, height = 360f),
            DemoNode("RegionA", x = 90f, y = 100f, width = 260f, height = 250f, containerId = "StateMachine"),
            DemoNode("RegionB", x = 390f, y = 100f, width = 260f, height = 250f, containerId = "StateMachine"),
            DemoNode("A1", x = 130f, y = 150f, width = 90f, height = 48f, containerId = "RegionA"),
            DemoNode("A2", x = 230f, y = 240f, width = 90f, height = 48f, containerId = "RegionA"),
            DemoNode("B1", x = 430f, y = 150f, width = 90f, height = 48f, containerId = "RegionB"),
            DemoNode("B2", x = 530f, y = 240f, width = 90f, height = 48f, containerId = "RegionB")
        ),
        edges = listOf(
            DemoEdge("e_state_1", "A1", "A2"),
            DemoEdge("e_state_2", "A2", "B1"),
            DemoEdge("e_state_3", "B1", "B2")
        )
    )

    val all: List<DemoScenario> = listOf(
        flatChain,
        singleContainerTwoNodes,
        siblingContainersCrossEdges,
        deepNesting,
        collapsedContainerExternalLinks,
        mixedCollapsedExpandedSiblings,
        umlLikeInheritanceAssociation,
        stateLikeRegions
    )

}


