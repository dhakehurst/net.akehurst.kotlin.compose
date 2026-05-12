package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.components.layout.graph.EdgeContentPosition
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeContent
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeSymbol
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeText

object DemoScenarios {

    private val umlFill = Color(0xFFE8F0FE)
    private val umlStroke = Color(0xFF3F7ACC)
    private const val compoundStateCornerRadiusDp = 12

    private fun edgeLabel(text: String, position: EdgeContentPosition = EdgeContentPosition.MIDDLE) =
        GraphLayoutEdgeText(position = position) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF222222),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, Color(0x55444444))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

    private fun labelledEdge(text: String) = GraphLayoutEdgeContent(
        texts = listOf(edgeLabel(text))
    )

    private fun transitionEdge(text: String) = GraphLayoutEdgeContent(
        endSymbol = arrowHead(umlStroke),
        texts = listOf(edgeLabel(text))
    )

    private fun arrowHead(color: Color) = GraphLayoutEdgeSymbol(
        pathPoints = listOf(
            Offset(0f, 0f),
            Offset(-12f, 6f),
            Offset(-12f, -6f)
        ),
        isClosed = true,
        fillColor = color,
        strokeColor = color,
        strokeWidth = 1.5f
    )

    private fun openTriangleHead(color: Color) = GraphLayoutEdgeSymbol(
        pathPoints = listOf(
            Offset(0f, 0f),
            Offset(-14f, 7f),
            Offset(-14f, -7f)
        ),
        isClosed = true,
        fillColor = Color.White,
        strokeColor = color,
        strokeWidth = 1.5f
    )

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
            DemoNode("CollapsedMid", x = 240f, y = 100f, width = 240f, height = 140f, defaultCollapsed = true),
            DemoNode("InnerX", x = 270f, y = 148f, width = 80f, height = 44f, containerId = "CollapsedMid"),
            DemoNode("InnerY", x = 370f, y = 148f, width = 80f, height = 44f, containerId = "CollapsedMid"),
            DemoNode("Downstream", x = 540f, y = 140f, width = 120f, height = 56f)
        ),
        edges = listOf(
            DemoEdge("e_collapsed_1", "Upstream", "InnerX"),
            DemoEdge("e_collapsed_2", "InnerX", "InnerY"),
            DemoEdge("e_collapsed_3", "InnerY", "Downstream")
        )
    )

    val mixedCollapsedExpandedSiblings = DemoScenario(
        id = "mixed_collapsed_expanded_siblings",
        title = "Mixed collapsed/expanded siblings",
        nodes = listOf(
            DemoNode("CollapsedSib", x = 80f, y = 90f, width = 200f, height = 140f, defaultCollapsed = true),
            DemoNode("C1", x = 110f, y = 148f, width = 80f, height = 44f, containerId = "CollapsedSib"),
            DemoNode("C2", x = 195f, y = 148f, width = 80f, height = 44f, containerId = "CollapsedSib"),
            DemoNode("ExpandedSib", x = 330f, y = 70f, width = 320f, height = 250f),
            DemoNode("Ex1", x = 380f, y = 140f, width = 90f, height = 48f, containerId = "ExpandedSib"),
            DemoNode("Ex2", x = 500f, y = 220f, width = 90f, height = 48f, containerId = "ExpandedSib")
        ),
        edges = listOf(
            DemoEdge("e_mixed_1", "C2", "Ex1"),
            DemoEdge("e_mixed_2", "Ex1", "Ex2")
        )
    )

    val umlLikeInheritanceAssociation = DemoScenario(
        id = "uml_like_inheritance_association",
        title = "UML-like inheritance and association",
        nodes = listOf(
            DemoNode("Base", x = 300f, y = 60f, width = 120f, height = 56f, content = { Class("Base") }),
            DemoNode("DerivedA", x = 170f, y = 190f, width = 130f, height = 56f, content = { Class("DerivedA") }),
            DemoNode("DerivedB", x = 430f, y = 190f, width = 130f, height = 56f, content = { Class("DerivedB") }),
            DemoNode("Service", x = 300f, y = 300f, width = 120f, height = 56f, content = { Class("Service") })
        ),
        edges = listOf(
            DemoEdge("e_uml_1", "DerivedA", "Base", content = GraphLayoutEdgeContent(endSymbol = openTriangleHead(umlStroke))),
            DemoEdge("e_uml_2", "DerivedB", "Base", content = GraphLayoutEdgeContent(endSymbol = openTriangleHead(umlStroke))),
            DemoEdge("e_uml_3", "Service", "DerivedA", content = labelledEdge("uses")),
            DemoEdge("e_uml_4", "Service", "DerivedB", content = labelledEdge("uses"))
        )
    )

    val stateLikeRegions = DemoScenario(
        id = "state_like_regions",
        title = "State-like regions",
        nodes = listOf(
            DemoNode("StateMachine", x = 50f, y = 40f, width = 640f, height = 360f, content = { children -> CompoundState("StateMachine", children) }),
            DemoNode("RegionA", x = 90f, y = 100f, width = 260f, height = 250f, containerId = "StateMachine", role = DemoNodeRole.REGION, content = { children -> Region("RegionA", children) }),
            DemoNode("RegionB", x = 390f, y = 100f, width = 260f, height = 250f, containerId = "StateMachine", role = DemoNodeRole.REGION, content = { children -> Region("RegionB", children) }),
            DemoNode("A1", x = 130f, y = 150f, width = 90f, height = 48f, containerId = "RegionA", content = { SimpleState("A1") }),
            DemoNode("A2", x = 230f, y = 240f, width = 90f, height = 48f, containerId = "RegionA", content = { SimpleState("A2") }),
            DemoNode("B1", x = 430f, y = 150f, width = 90f, height = 48f, containerId = "RegionB", content = { SimpleState("B1") }),
            DemoNode("B2", x = 530f, y = 240f, width = 90f, height = 48f, containerId = "RegionB", content = { SimpleState("B2") }),
        ),
        edges = listOf(
            DemoEdge("e_state_1", "A1", "A2", content = transitionEdge("next")),
            DemoEdge("e_state_2", "A2", "B1", content = transitionEdge("cross")),
            DemoEdge("e_state_3", "B1", "B2", content = transitionEdge("done"))
        )
    )

    val umlClassAssociationsGeneralisation = DemoScenario(
        id = "uml_class_associations_generalisation",
        title = "UML class: associations + generalisation",
        nodes = listOf(
            DemoNode("NamedElement", x = 290f, y = 40f, width = 140f, height = 56f, content = { Class("NamedElement") }),
            DemoNode("Person", x = 120f, y = 150f, width = 110f, height = 56f, content = { Class("Person") }),
            DemoNode("Company", x = 320f, y = 150f, width = 120f, height = 56f, content = { Class("Company") }),
            DemoNode("Project", x = 520f, y = 150f, width = 110f, height = 56f, content = { Class("Project") }),
            DemoNode("Address", x = 320f, y = 280f, width = 120f, height = 56f, content = { Class("Address") })
        ),
        edges = listOf(
            DemoEdge("e_cls_gen_person", "Person", "NamedElement", content = GraphLayoutEdgeContent(endSymbol = openTriangleHead(umlStroke))),
            DemoEdge("e_cls_gen_company", "Company", "NamedElement", content = GraphLayoutEdgeContent(endSymbol = openTriangleHead(umlStroke))),
            DemoEdge(
                "e_cls_assoc_works_on",
                "Person",
                "Project",
                content = GraphLayoutEdgeContent(
                    texts = listOf(
                        edgeLabel("1", position = EdgeContentPosition.START),
                        edgeLabel("worksOn", position = EdgeContentPosition.MIDDLE),
                        edgeLabel("*", position = EdgeContentPosition.END)
                    )
                )
            ),
            DemoEdge("e_cls_assoc_owns", "Company", "Project", content = labelledEdge("owns")),
            DemoEdge("e_cls_assoc_located_at", "Company", "Address", content = labelledEdge("locatedAt"))
        )
    )

    val umlStatechartNestedRegions = DemoScenario(
        id = "uml_statechart_nested_regions",
        title = "UML statechart with nested regions",
        nodes = listOf(
            DemoNode("Machine", x = 40f, y = 30f, width = 700f, height = 420f, content = { children -> CompoundState("Machine", children) }),
            DemoNode("ParallelA", x = 130f, y = 130f, width = 250f, height = 250f, containerId = "Machine", role = DemoNodeRole.REGION, content = { children -> Region("ParallelA", children) }),
            DemoNode("ParallelB", x = 420f, y = 130f, width = 230f, height = 250f, containerId = "Machine", role = DemoNodeRole.REGION, content = { children -> Region("ParallelB", children) }),
            DemoNode("SubRegionA1", x = 160f, y = 170f, width = 190f, height = 150f, containerId = "ParallelA", role = DemoNodeRole.REGION, content = { children -> Region("SubRegionA1", children) }),
            DemoNode("Idle", x = 190f, y = 205f, width = 100f, height = 48f, containerId = "SubRegionA1", content = { SimpleState("Idle") }),
            DemoNode("Active", x = 190f, y = 265f, width = 100f, height = 48f, containerId = "SubRegionA1", content = { SimpleState("Active") }),
            DemoNode("Wait", x = 470f, y = 190f, width = 100f, height = 48f, containerId = "ParallelB", content = { SimpleState("Wait") }),
            DemoNode("Done", x = 470f, y = 270f, width = 100f, height = 48f, containerId = "ParallelB", content = { SimpleState("Done") })
        ),
        edges = listOf(
            DemoEdge("e_state_nested_1", "Idle", "Active", content = transitionEdge("activate")),
            DemoEdge("e_state_nested_2", "Active", "Wait", content = transitionEdge("handoff")),
            DemoEdge("e_state_nested_3", "Wait", "Done", content = transitionEdge("complete")),
            DemoEdge("e_state_nested_4", "Done", "Idle", content = transitionEdge("reset"))
        )
    )

    val umlUseCaseDiagram = DemoScenario(
        id = "uml_use_case_diagram",
        title = "UML use case diagram",
        nodes = listOf(
            DemoNode("System", x = 180f, y = 60f, width = 420f, height = 300f, childContentOffsetX = 0f, childContentOffsetY = 28f, content = { children -> Component("System", children) }),
            DemoNode("User", x = 40f, y = 150f, width = 100f, height = 56f, content = { Actor("User") }),
            DemoNode("Admin", x = 40f, y = 250f, width = 100f, height = 56f, content = { Actor("Admin") }),
            DemoNode("Login", x = 270f, y = 120f, width = 110f, height = 56f, containerId = "System", content = { UseCase("Login") }),
            DemoNode("Browse", x = 430f, y = 120f, width = 120f, height = 56f, containerId = "System", content = { UseCase("Browse") }),
            DemoNode("ManageUsers", x = 340f, y = 230f, width = 150f, height = 56f, containerId = "System", content = { UseCase("ManageUsers") })
        ),
        edges = listOf(
            DemoEdge("e_uc_1", "User", "Login"),
            DemoEdge("e_uc_2", "User", "Browse"),
            DemoEdge("e_uc_3", "Admin", "Login"),
            DemoEdge("e_uc_4", "Admin", "ManageUsers"),
            DemoEdge("e_uc_5", "ManageUsers", "Login")
        )
    )

    val umlCompositeStructureDiagram = DemoScenario(
        id = "uml_composite_structure_diagram",
        title = "UML composite structure diagram",
        nodes = listOf(
            DemoNode("Controller", x = 80f, y = 70f, width = 560f, height = 300f, childContentOffsetX = 0f, childContentOffsetY = 28f, content = { children -> Component("Controller", children) }),
            DemoNode("InputPort", x = 130f, y = 180f, width = 100f, height = 48f, containerId = "Controller", content = { Interface("InputPort") }),
            DemoNode("Core", x = 280f, y = 130f, width = 140f, height = 56f, containerId = "Controller", content = { Component("Core") }),
            DemoNode("OutputPort", x = 490f, y = 180f, width = 110f, height = 48f, containerId = "Controller", content = { Interface("OutputPort") }),
            DemoNode("Sensor", x = 80f, y = 410f, width = 100f, height = 56f, content = { Component("Sensor") }),
            DemoNode("Actuator", x = 540f, y = 410f, width = 110f, height = 56f, content = { Component("Actuator") })
        ),
        edges = listOf(
            DemoEdge("e_comp_1", "Sensor", "InputPort"),
            DemoEdge("e_comp_2", "InputPort", "Core"),
            DemoEdge("e_comp_3", "Core", "OutputPort"),
            DemoEdge("e_comp_4", "OutputPort", "Actuator")
        )
    )

    val umlDeploymentDiagram = DemoScenario(
        id = "uml_deployment_diagram",
        title = "UML deployment diagram",
        nodes = listOf(
            DemoNode("Cloud", x = 60f, y = 40f, width = 620f, height = 340f, childContentOffsetX = 0f, childContentOffsetY = 28f, content = { children -> DeploymentNode("Cloud", children) }),
            DemoNode(
                "WebNode",
                x = 120f,
                y = 110f,
                width = 180f,
                height = 220f,
                containerId = "Cloud",
                childContentOffsetX = 0f,
                childContentOffsetY = 28f,
                content = { children -> DeploymentNode("WebNode", children) }),
            DemoNode(
                "DbNode",
                x = 360f,
                y = 110f,
                width = 180f,
                height = 220f,
                containerId = "Cloud",
                childContentOffsetX = 0f,
                childContentOffsetY = 28f,
                content = { children -> DeploymentNode("DbNode", children) }),
            DemoNode("WebApp", x = 150f, y = 170f, width = 120f, height = 56f, containerId = "WebNode", content = { Component("WebApp") }),
            DemoNode("Database", x = 390f, y = 170f, width = 120f, height = 56f, containerId = "DbNode", content = { Component("Database") }),
            DemoNode("Client", x = 80f, y = 430f, width = 110f, height = 56f, content = { Actor("Client") })
        ),
        edges = listOf(
            DemoEdge("e_dep_1", "Client", "WebApp"),
            DemoEdge("e_dep_2", "WebApp", "Database"),
            DemoEdge("e_dep_3", "Database", "WebApp")
        )
    )

    val umlClassPackageCrossingRelations = DemoScenario(
        id = "uml_class_package_crossing_relations",
        title = "UML class/package crossing relations",
        nodes = listOf(
            DemoNode("PackageDomain", x = 60f, y = 60f, width = 290f, height = 280f, childContentOffsetX = 0f, childContentOffsetY = 18f, content = { children -> Package("Domain", children) }),
            DemoNode("PackageInfra", x = 390f, y = 60f, width = 290f, height = 280f, childContentOffsetX = 0f, childContentOffsetY = 18f, content = { children -> Package("Infra", children) }),
            DemoNode("Order", x = 110f, y = 130f, width = 100f, height = 56f, containerId = "PackageDomain", content = { Class("Order") }),
            DemoNode("Customer", x = 220f, y = 230f, width = 110f, height = 56f, containerId = "PackageDomain", content = { Class("Customer") }),
            DemoNode("OrderRepo", x = 440f, y = 130f, width = 120f, height = 56f, containerId = "PackageInfra", content = { Class("OrderRepo") }),
            DemoNode("EventBus", x = 560f, y = 230f, width = 100f, height = 56f, containerId = "PackageInfra", content = { Class("EventBus") }),
        ),
        edges = listOf(
            DemoEdge("e_pkg_1", "Order", "Customer"),
            DemoEdge("e_pkg_2", "Order", "OrderRepo"),
            DemoEdge("e_pkg_3", "OrderRepo", "EventBus"),
            DemoEdge("e_pkg_4", "EventBus", "Order")
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
        stateLikeRegions,
        umlClassAssociationsGeneralisation,
        umlStatechartNestedRegions,
        umlUseCaseDiagram,
        umlCompositeStructureDiagram,
        umlDeploymentDiagram,
        umlClassPackageCrossingRelations
    )

    /**
     * Composable that looks like a UML Package. A Box with a tab at top left containing the name.
     */
    @Composable
    fun Package(name: String, children: @Composable () -> Unit = {}) = Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .background(umlFill)
                .border(1.5.dp, umlStroke)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = umlStroke
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(umlFill)
                .border(1.5.dp, umlStroke)
        ) {
            children()
        }
    }

    /**
     * Composable that looks like a UML Class. A Box with the name centered at the top.
     */
    @Composable
    fun Class(name: String) = Column(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill)
            .border(1.5.dp, umlStroke)
    ) {
        Text(
            text = name,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke
        )
        Text(
            text = "attributes...",
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke
        )
    }

    /**
     * Composable that looks like a UML State. A rounded corner Box with the name centered.
     */
    @Composable
    fun SimpleState(name: String) = Box(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill, RoundedCornerShape(12.dp))
            .border(1.5.dp, umlStroke, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke
        )
    }

    /**
     * Composable that looks like a UML State.
     * A rounded corner Box with the name centered at the top.
     * The partition line under the header is rendered on top via declaration order in Box.
     * Contained regions go in the lower compartment, rendered via [children].
     */
    @Composable
    fun CompoundState(name: String, children: @Composable () -> Unit = {}) = Column(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill, RoundedCornerShape(compoundStateCornerRadiusDp.dp))
            .border(1.5.dp, umlStroke, RoundedCornerShape(6.dp))
    ) {
        Text(
            text = name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke,
            textAlign = TextAlign.Center
        )
        // Declared after Column in Box z-order → renders on top of children
        HorizontalDivider(
            modifier = Modifier
                .padding(top = (4 + compoundStateCornerRadiusDp).dp)
                .padding(horizontal = compoundStateCornerRadiusDp.dp),
            thickness = 1.5.dp,
            color = umlStroke
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(compoundStateCornerRadiusDp.dp)
        ) {
            children()
        }
    }

    @Composable
    fun UseCase(name: String) = Box(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill, CircleShape)
            .border(1.5.dp, umlStroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke,
            textAlign = TextAlign.Center
        )
    }

    @Composable
    fun Actor(name: String) = Box(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill)
            .border(1.5.dp, umlStroke),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(1.5.dp, umlStroke, CircleShape)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(10.dp)
                    .background(umlStroke)
            )
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(1.5.dp)
                    .background(umlStroke)
            )
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(8.dp)
                    .background(umlStroke)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = umlStroke,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun DeploymentNode(name: String, children: @Composable () -> Unit = {}) = Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, top = 6.dp)
                .border(1.5.dp, umlStroke)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 6.dp, bottom = 6.dp)
                .background(umlFill)
                .border(1.5.dp, umlStroke),
            contentAlignment = Alignment.TopCenter
        ) {
            children()
            Text(
                text = name,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = umlStroke,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun Component(name: String, children: @Composable () -> Unit = {}) = Column(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill)
            .border(1.5.dp, umlStroke)
    ) {
        Box {
            Text(
                text = name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 4.dp, start = 6.dp, end = 20.dp),
                style = MaterialTheme.typography.labelSmall,
                color = umlStroke,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(10.dp)
                    .border(1.dp, umlStroke)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 17.dp, end = 5.dp)
                    .size(10.dp)
                    .border(1.dp, umlStroke)
            )
        }
        Box {
            children()
        }
    }

    @Composable
    fun Interface(name: String) = Column(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill)
            .border(1.5.dp, umlStroke),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .aspectRatio(1f)
                .border(1.5.dp, umlStroke, CircleShape)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke,
            textAlign = TextAlign.Center
        )
    }

    /**
     * Region in a statechart. Renders its name label on top of contained child nodes.
     */
    @Composable
    fun Region(name: String, children: @Composable () -> Unit = {}) = Column(
        modifier = Modifier
            .fillMaxSize()
            .background(umlFill)
    ) {
        // Label on top
        Text(
            text = name,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke
        )
        Box {
            children()
        }

    }
}
