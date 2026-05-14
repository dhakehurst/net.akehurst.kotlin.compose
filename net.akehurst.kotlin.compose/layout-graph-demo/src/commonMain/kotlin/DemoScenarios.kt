package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.components.layout.graph.ChildLayout
import net.akehurst.kotlin.components.layout.graph.EdgeContentPosition
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeContent
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeSymbol
import net.akehurst.kotlin.components.layout.graph.GraphLayoutEdgeText

object DemoScenarios {

    private val umlFill = Color.White
    private val umlStroke = Color.Black
    private val umlPadding = 5.dp
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
            DemoNode("A", content = { SimpleBox("A") }),
            DemoNode("B", content = { SimpleBox("B") }),
            DemoNode("C", content = { SimpleBox("C") }),
            DemoNode("D", content = { SimpleBox("D") }),
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
            DemoNode("Container1", content = { children -> SimpleBoxContainer("Container1", 0.dp, children) }),
            DemoNode("InsideA", containerId = "Container1", content = { SimpleBox("InsideA") }),
            DemoNode("InsideB", containerId = "Container1", content = { SimpleBox("InsideB") }),
            DemoNode("Outside", content = { SimpleBox("Outside") }),
        ),
        edges = listOf(
            DemoEdge("e_single_1", "InsideA", "InsideB"),
            DemoEdge("e_single_2", "InsideB", "Outside")
        )
    )

    val singleContainerTwoNodesWithPadding = DemoScenario(
        id = "single_container_two_nodes_withPadding",
        title = "Single container two nodes with padding",
        nodes = listOf(
            DemoNode("Container1", content = { children -> SimpleBoxContainer("Container1", 20.dp,children) }),
            DemoNode("InsideA", containerId = "Container1", content = { SimpleBox("InsideA") }),
            DemoNode("InsideB", containerId = "Container1", content = { SimpleBox("InsideB") }),
            DemoNode("Outside", content = { SimpleBox("Outside") }),
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
            DemoNode("ContainerL", content = { children -> SimpleBoxContainer("ContainerL", 0.dp,children) }),
            DemoNode("ContainerR", content = { children -> SimpleBoxContainer("ContainerR", 0.dp,children) }),
            DemoNode("L1", containerId = "ContainerL", content = { SimpleBox("L1") }),
            DemoNode("L2", containerId = "ContainerL", content = { SimpleBox("L2") }),
            DemoNode("R1", containerId = "ContainerR", content = { SimpleBox("R1") }),
            DemoNode("R2", containerId = "ContainerR", content = { SimpleBox("R2") }),
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
            DemoNode("RootContainer", content = { children -> SimpleBoxContainer("RootContainer", 0.dp,children) }),
            DemoNode("Level1", containerId = "RootContainer", content = { children -> SimpleBoxContainer("Level1", 0.dp,children) }),
            DemoNode("Level2", containerId = "Level1", content = { children -> SimpleBoxContainer("Level2", 0.dp,children) }),
            DemoNode("LeafA", containerId = "Level2", content = { SimpleBox("LeafA") }),
            DemoNode("LeafB", containerId = "Level2", content = { SimpleBox("LeafB") }),
            DemoNode("External", containerId = "Level1", content = { SimpleBox("External") }),
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
            DemoNode("Upstream", content = { SimpleBox("Upstream") }),
            DemoNode("CollapsedMid", defaultCollapsed = true, content = { children -> SimpleBoxContainer("CollapsedMid", 0.dp,children) }),
            DemoNode("InnerX", containerId = "CollapsedMid", content = { SimpleBox("InnerX") }),
            DemoNode("InnerY", containerId = "CollapsedMid", content = { SimpleBox("InnerY") }),
            DemoNode("Downstream", content = { SimpleBox("Downstream") }),
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
            DemoNode("CollapsedSib", defaultCollapsed = true, content = { children -> SimpleBoxContainer("CollapsedSib", 0.dp,children) }),
            DemoNode("C1", containerId = "CollapsedSib", content = { SimpleBox("C1") }),
            DemoNode("C2", containerId = "CollapsedSib", content = { SimpleBox("C2") }),
            DemoNode("ExpandedSib", content = { children -> SimpleBoxContainer("ExpandedSib", 0.dp,children) }),
            DemoNode("Ex1", containerId = "ExpandedSib", content = { SimpleBox("Ex1") }),
            DemoNode("Ex2", containerId = "ExpandedSib", content = { SimpleBox("Ex2") }),
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
            DemoNode("Base", content = { Class("Base") }),
            DemoNode("DerivedA", content = { Class("DerivedA") }),
            DemoNode("DerivedB", content = { Class("DerivedB") }),
            DemoNode("Service", content = { Class("Service") })
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
            DemoNode("StateMachine", childLayout = ChildLayout.TESSELLATE, content = { children -> CompoundState("StateMachine", children) }),
            DemoNode("RegionA", containerId = "StateMachine", childLayout = ChildLayout.GRAPH, content = { children -> Region("RegionA", children) }),
            DemoNode("RegionB", containerId = "StateMachine", childLayout = ChildLayout.GRAPH, content = { children -> Region("RegionB", children) }),
            DemoNode("A1", containerId = "RegionA", content = { SimpleState("A1") }),
            DemoNode("A2", containerId = "RegionA", content = { SimpleState("A2") }),
            DemoNode("B1", containerId = "RegionB", content = { SimpleState("B1") }),
            DemoNode("B2", containerId = "RegionB", content = { SimpleState("B2") }),
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
            DemoNode("NamedElement", content = { Class("NamedElement") }),
            DemoNode("Person", content = { Class("Person") }),
            DemoNode("Company", content = { Class("Company") }),
            DemoNode("Project", content = { Class("Project") }),
            DemoNode("Address", content = { Class("Address") })
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
            DemoNode("Machine", childLayout = ChildLayout.TESSELLATE, content = { children -> CompoundState("Machine", children) }),
            DemoNode(
                "ParallelA",
                containerId = "Machine",
                childLayout = ChildLayout.TESSELLATE,
                content = { children -> Region("ParallelA", children) }),
            DemoNode("ParallelB", containerId = "Machine", childLayout = ChildLayout.GRAPH, content = { children -> Region("ParallelB", children) }),
            DemoNode(
                "SubRegionA1",
                containerId = "ParallelA",
                childLayout = ChildLayout.GRAPH,
                content = { children -> Region("SubRegionA1", children) }),
            DemoNode(
                "SubRegionA2",
                containerId = "ParallelA",
                childLayout = ChildLayout.GRAPH,
                content = { children -> Region("SubRegionA2", children) }),
            DemoNode("Idle", containerId = "SubRegionA1", content = { SimpleState("Idle") }),
            DemoNode("Active", containerId = "SubRegionA2", content = { SimpleState("Active") }),
            DemoNode("Wait", containerId = "ParallelB", content = { SimpleState("Wait") }),
            DemoNode("Done", containerId = "ParallelB", content = { SimpleState("Done") })
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
            DemoNode("System", content = { children -> Component("System", children) }),
            DemoNode("User", content = { Actor("User") }),
            DemoNode("Admin", content = { Actor("Admin") }),
            DemoNode("Login", containerId = "System", content = { UseCase("Login") }),
            DemoNode("Browse", containerId = "System", content = { UseCase("Browse") }),
            DemoNode("ManageUsers", containerId = "System", content = { UseCase("ManageUsers") })
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
            DemoNode("Controller", content = { children -> Component("Controller", children) }),
            DemoNode("InputPort", containerId = "Controller", content = { Interface("InputPort") }),
            DemoNode("Core", containerId = "Controller", content = { children -> Component("Core", children) }),
            DemoNode("OutputPort", containerId = "Controller", content = { Interface("OutputPort") }),
            DemoNode("Sensor", content = { children -> Component("Sensor", children) }),
            DemoNode("Actuator", content = { children -> Component("Actuator", children) })
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
            DemoNode("Cloud", content = { children -> DeploymentNode("Cloud", children) }),
            DemoNode("WebNode", containerId = "Cloud", content = { children -> DeploymentNode("WebNode", children) }),
            DemoNode("DbNode", containerId = "Cloud", content = { children -> DeploymentNode("DbNode", children) }),
            DemoNode("WebApp", containerId = "WebNode", content = { children -> Component("WebApp", children) }),
            DemoNode("Database", containerId = "DbNode", content = { children -> Component("Database", children) }),
            DemoNode("Client", content = { Actor("Client") })
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
            DemoNode("PackageDomain", content = { children -> Package("Domain", children) }),
            DemoNode("PackageInfra", content = { children -> Package("Infra", children) }),
            DemoNode("Order", containerId = "PackageDomain", content = { Class("Order") }),
            DemoNode("Customer", containerId = "PackageDomain", content = { Class("Customer") }),
            DemoNode("OrderRepo", containerId = "PackageInfra", content = { Class("OrderRepo") }),
            DemoNode("EventBus", containerId = "PackageInfra", content = { Class("EventBus") }),
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
        singleContainerTwoNodesWithPadding,
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
     * simple nnode
     */
    @Composable
    fun SimpleBox(name: String) = Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()//.minWidthToContent(30.dp)
            .background(Color(0xFFEFF8EF))
            .border(1.5.dp, Color.Black)
            .padding(10.dp)
    ) {
        Text(text = name, style = MaterialTheme.typography.bodySmall, color = Color.Black, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }

    /**
     * simple named container
     */
    @Composable
    fun SimpleBoxContainer(name: String, padding:Dp = Dp.Unspecified, children: @Composable () -> Unit) = Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()//.containerNodeFrame(30.dp)
            .background(Color(0xFFE8F0FE))
            .border(1.5.dp, Color(0xFF3F7ACC))
            .padding(padding)
    ) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, color = Color.Black, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xccb4c0FE))
                .weight(1f)
        ) {
            children()
        }
    }

    /**
     * Composable that looks like a UML Package. A Box with a tab at top left containing the name.
     */
    @Composable
    fun Package(name: String, children: @Composable () -> Unit) = Column(
        modifier = Modifier
            .fillMaxSize()//.containerNodeFrame(30.dp)
            .padding(umlPadding)
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
            .fillMaxSize()//.minWidthToContent(30.dp)
            .background(umlFill)
            .border(1.5.dp, umlStroke)
            .padding(umlPadding)
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
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()//.minWidthToContent(30.dp)
            .background(umlFill, RoundedCornerShape(12.dp))
            .border(1.5.dp, umlStroke, RoundedCornerShape(5.dp))
            .padding(umlPadding)
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
    fun CompoundState(name: String, children: @Composable () -> Unit) = Column(
        modifier = Modifier
            .fillMaxSize()//.containerNodeFrame(30.dp)
            .background(umlFill, RoundedCornerShape(compoundStateCornerRadiusDp.dp))
            .border(1.5.dp, umlStroke, RoundedCornerShape(compoundStateCornerRadiusDp.dp))
            .padding(umlPadding)
    ) {
        Text(
            text = name,
            modifier = Modifier
                .fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = umlStroke,
            textAlign = TextAlign.Center
        )
        // Declared after Column in Box z-order → renders on top of children
        HorizontalDivider(
            modifier = Modifier,
            thickness = 1.5.dp,
            color = umlStroke
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = compoundStateCornerRadiusDp.dp)
        ) {
            children()
        }
    }

    @Composable
    fun UseCase(name: String) = Box(
        modifier = Modifier
            .minWidthToContent(30.dp)
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
            .minWidthToContent(30.dp)
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
    fun DeploymentNode(name: String, children: @Composable () -> Unit) = Box(
        modifier = Modifier.containerNodeFrame(30.dp)
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
    fun Component(name: String, children: @Composable () -> Unit) = Column(
        modifier = Modifier
            .containerNodeFrame(30.dp)
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
            .minWidthToContent(30.dp)
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
    fun Region(name: String, children: @Composable () -> Unit) = Column(
        modifier = Modifier
            .fillMaxSize()//.containerNodeFrame(30.dp)
            .background(umlFill)
            .padding(umlPadding)
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.CenterHorizontally)
        ) {
            children()
        }

    }

    // Container renderers must fill allocated node bounds; min-width still applies when unconstrained.
    fun Modifier.containerNodeFrame(hardMinWidth: Dp = Dp.Unspecified) =
        this.fillMaxSize().minWidthToContent(hardMinWidth)

    fun Modifier.minWidthToContent(hardMinWidth: Dp = Dp.Unspecified) = this.layout { measurable, constraints ->
        // Convert the optional Dp to pixels, defaulting to 0 if not specified
        val hardMinPx = if (hardMinWidth != Dp.Unspecified) hardMinWidth.roundToPx() else 0

        // 1. Query the content for its natural width
        val contentWidth = measurable.maxIntrinsicWidth(constraints.maxHeight)

        // 2. The target width is the largest of:
        // - The parent's minimum constraint
        // - The content's natural width
        // - The custom hard minimum width (if provided)
        val targetWidth = maxOf(constraints.minWidth, contentWidth, hardMinPx)

        // 3. Create new constraints that force this target width, overriding parent max limits if necessary
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = targetWidth,
                maxWidth = maxOf(constraints.maxWidth, targetWidth)
            )
        )

        // 4. Place the content
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
