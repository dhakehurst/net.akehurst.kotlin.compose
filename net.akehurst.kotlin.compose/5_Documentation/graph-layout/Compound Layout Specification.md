# Compound Graph Layout Specification

## Implementation tracking

Implementation steps and review checkpoints are tracked in:
- `5_Documentation/graph-layout/Compound Layout Implementation Plan.md`

## Checklist
- [x] Capture the current layout limitations
- [x] Summarize the research-driven layout goals
- [x] Specify the compound/recursive layout architecture
- [x] Define routing, ports, and container behavior
- [x] Record implementation phases and open questions

## 1. Purpose

The current layout implementation is a flat Sugiyama-style graph layout. It is adequate for simple graphs, but it does not handle compound graphs well and produces weak results for UML-like diagrams with nested packages, nested statecharts, containment regions, or edges that cross hierarchy boundaries.

This document defines a better layout plan based on:

- `A New Approach for Visualizing UML Class Diagrams`
- `Automatic Layout and Structure-Based Editing of UML Diagrams`
- Sugiyama-style layered layout ideas
- compound/layout concepts similar to yFiles hierarchical and series-parallel layouts

## 2. Design Goals

### Primary goals
- Support graphs nested inside other graphs.
- Support different child-layout strategies per container.
- Each container may optionally specify the layout used for its immediate children.
- If a container does not specify a child layout, it inherits the effective child layout from its parent.
- The default child layout for most diagrams is `GRAPH`.
- Support at least these child layout options:
  - `GRAPH` for general graph-style layout
  - `TESSELLATE` for deterministic tiling layouts, for example state regions
- Layout multiple packages, regions, or statecharts cleanly.
- For region-based containers, tile regions to fill available content area (top-to-bottom, left-to-right) with no unused gaps.
- Render region separators as single shared divider lines (no double-stroked adjacent borders).
- Keep hierarchies visually consistent and readable.
- Improve crossing minimization and orthogonal routing.
- Preserve clear separation between containment and ordinary adjacency edges.
- Keep the core algorithm domain-agnostic (no UML/statechart/package-specific behavior in core steps).
- Support collapsing/expanding contained graphs with configurable default visibility.

### Secondary goals
- Keep the implementation deterministic.
- Keep layout output consistent across supported Compose targets for identical input.
- Preserve the Compose rendering model.
- Make the layout extensible for diagram-specific presets.

## 3. Research-driven layout principles

The cited papers are inspiration sources for constraints and techniques. They do **not** define domain-specific hard-coding in the algorithm. The implementation must remain generic and only consume abstract graph semantics.

### From the UML class-diagram paper
Use these ideas:
- mixed hierarchical + non-hierarchical graph handling
- uniform direction within each hierarchy
- avoidance of hierarchy nesting
- orthogonal routing
- merged/clean inheritance rendering
- planarization with dummy vertices for crossings
- cluster-aware processing to keep hierarchies separated

### From the compound-graph paper
Use these ideas:
- model the diagram as a compound graph
- maintain an inclusion tree for nested structures
- recurse from inner graphs to outer graphs
- treat cross-hierarchy edges explicitly
- support diagrams where edges connect different hierarchy levels

### From Sugiyama / yFiles-style layout concepts
Use these ideas:
- layered assignment
- crossing reduction by barycenter/median heuristics
- dummy nodes for long edges
- port-aware routing
- hierarchical layout as a preset
- series-parallel handling for tree-like subregions where useful

## 4. Problems in the current implementation

The current `layout-graph/src/commonMain/kotlin/SugiyamaLayout.kt` implementation:
- assumes a single flat graph
- has no representation for containment or child graphs
- treats all edges as ordinary adjacency edges
- cannot compute bounds for nested packages or regions
- has no recursive layout step
- routes edges without respecting container boundaries
- cannot make nested diagrams visually compact or stable

The current `layout-graph/src/commonMain/kotlin/GraphLayout.kt` rendering code also assumes a flat coordinate space.

## 5. Proposed architecture

### 5.1 Graph model
Introduce a compound graph model with:
- nodes
- child graphs / nested regions
- inclusion relationships
- adjacency edges
- edge semantics, such as:
  - hierarchy / inheritance
  - ordinary association
  - self-loop
  - cross-boundary edge

Containment/inclusion is structural data (parent-child ownership), not a routable edge type.

### 5.2 Recursive layout pipeline
For each graph level:
1. Layout child graphs first.
2. Compute each child’s bounding box.
3. Treat each child graph as an atomic compound node in the parent graph.
4. Run layered layout on the parent level.
5. Place children inside parent containers.
6. Route edges that enter/leave containers through boundary ports.

### 5.3 Layout strategy per level
Each local graph level should use a Sugiyama-style pipeline:
- cycle removal
- layer assignment
- dummy node insertion
- crossing reduction
- coordinate assignment
- edge routing

But all steps must be compound-aware.

### 5.4 Proposed Kotlin model changes

This is the minimum model shape the implementation should aim for.

```kotlin
data class GraphLayoutGraphState(
    val id: String,
    val routing: EdgeRouting = EdgeRouting.DIRECT,
    val root: GraphLayoutCompoundGraph = GraphLayoutCompoundGraph("root")
)

data class GraphLayoutCompoundGraph(
    val id: String,
    val kind: CompoundGraphKind = CompoundGraphKind.GENERIC,
    val childLayout: ChildLayout? = null,
    val nodes: MutableMap<String, GraphLayoutNode> = mutableMapOf(),
    val edges: MutableMap<String, GraphLayoutEdge> = mutableMapOf(),
    val children: MutableMap<String, GraphLayoutCompoundGraph> = mutableMapOf(),
    val collapsePolicy: CollapsePolicy = CollapsePolicy.EXPANDED_BY_DEFAULT,
    var isCollapsed: Boolean = false
)

data class GraphLayoutNode(
    val id: String,
    val kind: NodeKind = NodeKind.NORMAL,
    val widthHint: Double? = null,
    val heightHint: Double? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class GraphLayoutEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val kind: EdgeKind = EdgeKind.ADJACENCY
)
```

### 5.5 Proposed enums

```kotlin
enum class CompoundGraphKind {
    GENERIC,
    TREE_LIKE,
    SERIES_PARALLEL
}

enum class ChildLayout {
    GRAPH,
    TESSELLATE
}

enum class NodeKind {
    NORMAL,
    CONTAINER,
    PORTAL,
    DUMMY
}

enum class EdgeKind {
    ADJACENCY,
    HIERARCHY,
    SELF_LOOP,
    CROSS_BOUNDARY
}

enum class CollapsePolicy {
    EXPANDED_BY_DEFAULT,
    COLLAPSED_BY_DEFAULT
}
```

Rendering concerns stay outside the core layout model. Compose-specific content is mapped in the rendering layer using stable node/edge IDs.

### 5.6 Proposed layout result shape

The layout result should return stable IDs and both local/global coordinates.

```kotlin
data class CompoundLayoutResult(
    val graphId: String,
    val localBounds: Rect,
    val globalBounds: Rect,
    val localNodePositions: Map<String, Offset>,
    val globalNodePositions: Map<String, Offset>,
    val edgeRoutesByEdgeId: Map<String, List<Offset>>,
    val childResults: Map<String, CompoundLayoutResult> = emptyMap()
)
```

This keeps the result API independent of a specific internal algorithm type and allows rendering without recomputing layout.

### 5.6.1 Child-layout selection contract

- Each container may declare `childLayout` to control how its immediate children are arranged.
- If `childLayout == null`, the container inherits the effective child layout from its parent.
- The root graph defaults to `ChildLayout.GRAPH` unless explicitly configured otherwise.
- `ChildLayout.GRAPH` uses the standard graph-oriented local pipeline.
- `ChildLayout.TESSELLATE` uses deterministic tiling for the container's immediate children and is intended for structures such as state regions.
- Child-layout selection applies only to direct children of that container; nested containers resolve their own effective child layout independently.

### 5.7 Coordinate and transform contract

- Each graph level computes positions in local coordinates relative to its own `(0,0)`.
- Parent placement defines a translation transform for each child graph.
- Global coordinates are derived by accumulating ancestor transforms.
- `edgeRoutesByEdgeId` is in global coordinates for direct rendering.
- Container rendering order is parent background -> child content -> edges/labels.

### 5.8 Domain-agnostic semantic inputs

The core algorithm should only consume abstract semantics:

- containment relation (inclusion tree)
- directed-priority edges (preferred flow direction)
- undirected/general adjacency edges
- boundary-crossing permissions
- routing constraints (bend penalty, crossing penalty, orthogonality preference)

Domain concepts (UML/statechart/package) must be mapped to these generic semantics outside the core layout engine.

### 5.9 Compose content and the UI/algorithm boundary

The layout *algorithm* is UI-agnostic: `GraphLayoutCompoundNode`, `GraphLayoutCompoundEdge`, and `GraphLayoutCompoundGraph` carry no Compose dependencies and can be used in any context.

The *caller-facing state class* `GraphLayoutCompoundGraphState` is the integration point for Compose UIs. It holds:

- `nodeContentById: Map<String, @Composable (@Composable () -> Unit) -> Unit>` — Compose content for each node, keyed by stable node ID. The lambda receives a `children` composable so container-like nodes can render their nested content within their own visual chrome, while leaf nodes may ignore it.
- `edgeContentById: Map<String, GraphLayoutEdgeContent>` — structured rendering information for each edge, keyed by stable edge ID.

Minimum caller-facing edge-rendering shape:

```kotlin
enum class EdgeContentPosition {
    START,
    MIDDLE,
    END
}

data class GraphLayoutEdgeSymbol(
    val pathPoints: List<Offset>,
    val isClosed: Boolean = true,
    val fillColor: Color = Color.Transparent,
    val strokeColor: Color = Color(0xFF444444),
    val strokeWidth: Float = 1.5f
)

data class GraphLayoutEdgeText(
    val position: EdgeContentPosition = EdgeContentPosition.MIDDLE,
    val content: @Composable () -> Unit
)

data class GraphLayoutEdgeContent(
    val startSymbol: GraphLayoutEdgeSymbol? = null,
    val endSymbol: GraphLayoutEdgeSymbol? = null,
    val texts: List<GraphLayoutEdgeText> = emptyList()
)
```

This mirrors the pattern of the flat `GraphLayoutGraphState`, while extending it so both node visuals and edge visuals are externally supplied and remain stable across recomputations. The algorithm receives only `root: GraphLayoutCompoundGraph`; the renderer looks up node composables and edge rendering entries by the same stable IDs present in the layout result and applies them to the computed node bounds and edge routes. This keeps the boundary clean without preventing the library from being used as a Compose layout engine.

Edge rendering contract:
- the layout result remains responsible for geometry only (`edgeRoutesByEdgeId` and endpoint positions)
- edge rendering metadata is resolved separately via `edgeContentById`
- edge content must be attachable by stable edge ID so routing can change without losing the associated visual decorators
- endpoint symbols are defined by local path points and attached to the start/end route tangents
- text labels declare whether they appear at the `START`, `MIDDLE`, or `END` of the route
- an edge with no registered content still renders with the default route stroke

## 6. Compound-graph behavior

### 6.1 Containers
A container/package/statechart region should:
- have its own bounds
- allow the Compose layer to decide whether a distinct visual boundary is rendered
- optionally have a header area if the Compose presentation requires one

Container visuals are not part of the core layout contract:

- a container does not need to have a distinct visual boundary, although many diagram styles will choose to render one
- visualization is provided by the Compose-specific rendering layer
- padding and margins are determined by Compose-facing presentation code and then supplied to layout as sizing constraints when needed

### 6.2 Child graphs
A child graph should:
- be laid out in its own local coordinate system
- be positioned relative to its parent container
- contribute to the parent container’s size

### 6.6 Region-based layout contract

When a container's effective `childLayout` is `TESSELLATE`, the local level should use tessellated placement semantics:

- children are arranged as a deterministic row-major tiling (`left->right`, then `top->bottom`)
- tiles fill the full available content area inside the container (after header allocation)
- any padding or margin affecting the tiling area is supplied from Compose-facing presentation configuration rather than hard-coded in core layout
- region child bounds are normalized to tile bounds before parent-level placement to avoid residual gaps from author-provided hints
- divider rendering is single-pass at shared boundaries; adjacent regions must not each paint their own border on the same seam

`TESSELLATE` is typically used for region-like children, but it remains a generic layout option rather than a UML/statechart-specific rule.

This keeps region diagrams visually stable and prevents "double wall" artifacts.

### 6.5 Collapse/expand behavior

Containers must support two states:
- expanded: children and internal edges are visible and laid out normally
- collapsed: container is represented as a single summary node

Rules:
- initial state is determined by `collapsePolicy`
- runtime state is held in `isCollapsed`
- collapsed containers do not render internal nodes/edges
- collapsed containers still participate in parent layout as atomic nodes
- edges from/to hidden descendants are rerouted to the collapsed container boundary
- expanding a container restores child layout with stable relative ordering where possible

### 6.3 Cross-container edges
Edges between nodes in different containers should:
- attach to boundary ports
- avoid passing through unrelated containers
- use dummy points if needed
- preserve readability over compactness when those conflict

### 6.4 Containment semantics
Containment must be explicit and acyclic.

- Every node belongs to exactly one direct parent graph.
- Child graphs may contain nodes and subgraphs recursively.
- Inclusion relationships define structure, not ordinary graph connectivity.
- Layout must never infer containment from adjacency edges alone.
- Containment is represented by parent-child ownership, not by `EdgeKind`.

## 7. Routing and ports

### Routing modes
- `DIRECT`
- `RECTILINEAR`
- compound-aware rectilinear routing

Endpoint boundary attachment contract:
- All routed edges terminate at node/container boundaries, never at visual centers.
- For `DIRECT`, compute each endpoint as the intersection of the straight center-to-center ray with the source/target boundary.
- For `RECTILINEAR` (including compound-aware rectilinear), choose boundary touch points that reduce overlap and ambiguity; default to the center of the closest border side when no stronger separation heuristic applies.

### Port rules
- ordinary nodes use node-side ports
- containers use boundary ports
- port side should depend on edge direction and layer placement
- multi-edges should be separated instead of stacked on top of each other
- collapsed containers must expose boundary ports for all external connectivity represented by hidden descendants
- endpoint anchor selection should prefer distinct boundary positions for parallel/near-parallel routes to improve readability

Region routing refinement:
- region containers inside a larger state container may disable boundary-routing participation (`routeBoundary = false`) so sibling-region transitions do not produce redundant boundary hops.
- this avoids zig-zag/clipped routes caused by repeatedly intersecting intermediate region boxes when source/target are already resolved at the enclosing level.

### Edge handling rules
- self-loops should be local and readable
- long edges should use dummy nodes
- internal edges should stay inside the container if possible
- external edges should leave and enter via container boundaries
- edges may be rerouted around a container if that reduces crossings or bends

### 7.5 Deterministic endpoint geometry notes

To keep endpoint clipping deterministic across JVM/JS/Wasm, use the following rules:

- **Boundary epsilon**: use a fixed epsilon (`1e-6`) for boundary membership/intersection checks.
- **Shape model**: treat routable node/container bounds as axis-aligned rectangles for endpoint clipping.
- **Direct endpoint rule**: for edge `u -> v`, compute the center-to-center ray and clip at the first boundary hit from each side.
- **Degenerate direct case**: if source and target centers are equal, choose anchor side by deterministic priority `RIGHT, BOTTOM, LEFT, TOP`.
- **Corner hits**: if intersection lies within epsilon of a corner, resolve to a single side by deterministic priority `RIGHT, BOTTOM, LEFT, TOP`.
- **Rectilinear default anchor**: choose the closest border side center to the first/last orthogonal segment direction.
- **Rectilinear ties**: if equal distance/score, break ties by side priority `RIGHT, BOTTOM, LEFT, TOP`, then by stable node ID.
- **Parallel-edge separation**: apply deterministic slot offsets indexed by stable edge ID ordering to avoid stacked endpoints.
- **Output normalization**: snap values with absolute magnitude `< epsilon` to `0.0` to reduce floating-point drift in snapshots.

### 7.4 Routing preferences by generic profile

- `DEFAULT`: balanced crossing and bend minimization.
- `HIERARCHY_BIASED`: stronger directional alignment and layer stability.
- `ORTHOGONAL_BIASED`: stronger bend/right-angle constraints.
- `COMPACT`: smaller area with acceptable crossing increase.
- `CHANNEL_BIASED`: straighter parallel channels for near series-parallel graphs.

## 8. Layout quality criteria

The result should make it easy to see:
- which nodes belong to which package or region
- which edges are internal vs external
- which hierarchy direction is being followed
- which containers are nested inside others
- where cross-hierarchy interactions happen

The layout should reduce:
- edge crossings
- unnecessary bends
- hierarchy nesting confusion
- edge routes passing through unrelated boxes

When collapse state changes, the layout should preserve:
- stable positioning of unaffected regions
- deterministic rerouting for external edges
- minimal visual jump for nearby nodes where possible

## 9. Implementation phases

### Phase 0: Contracts and compatibility
- define input/output contracts (semantic model, internal model, render result)
- document coordinate/transform guarantees and ID stability
- add adapter from flat API to compound API for incremental migration

### Phase 1: Model refactor
- add compound graph structures
- represent nested graphs and boundary metadata
- classify edge semantics

### Phase 2: Recursive layout engine
- layout leaves first
- compute container bounds
- build parent-level layouts from child bounds

### Phase 3: Compound-aware Sugiyama
- adapt layer assignment and crossing reduction
- preserve hierarchy direction
- keep child graphs atomic at parent levels

### Phase 4: Routing
- boundary port routing
- orthogonal routes around containers
- clearer multi-edge and self-loop handling

### Phase 5: Rendering
- update Compose rendering for nested graphs
- render containers before children
- draw edge routes in global coordinates

### Phase 6: Refinement
- add presets for UML class diagrams, packages, and statecharts
- tune spacing and alignment
- validate against representative diagrams

### Phase 7: Determinism and performance hardening
- enforce stable tie-breakers in every ordering step (ID-based)
- add incremental recompute boundaries for collapse/expand changes
- track baseline performance and memory budgets on representative graphs

## 10. Suggested algorithm outline

1. Build a compound graph from the semantic model.
2. Validate the inclusion tree.
3. Resolve effective collapse state for each container (`collapsePolicy` + runtime override).
4. Resolve the effective `childLayout` for each container by inheriting from the parent when unspecified.
5. Identify visible leaf graphs under the current collapse configuration.
6. Layout visible leaves using the local strategy selected by the effective `childLayout`.
   - `GRAPH` -> graph-oriented local layout pipeline
   - `TESSELLATE` -> deterministic tiling of immediate children
7. Compute child bounds and inflate by Compose-supplied sizing constraints where applicable.
8. Replace each child graph with an atomic compound node in the parent graph.
9. For collapsed children, aggregate descendant external connections into container boundary ports.
10. Run the selected parent-level layout for the container's immediate children.
11. Route edges that cross graph boundaries using boundary ports.
12. Lift child coordinates into the parent coordinate system.
13. Merge all local results into one global result.

## 11. Proposed testing strategy

### Structural tests
- empty graph
- single node
- one container with two children
- two sibling containers with cross edges
- deeply nested container tree
- cyclic adjacency graph with acyclic containment tree
- collapsed container with descendants
- mixed collapsed and expanded siblings

### Visual/layout tests
- UML class diagram with inheritance and associations
- package tree with cross-package dependencies
- statechart with nested composite states
- mixed diagram with both local and cross-boundary edges

### Assertions
- child bounds are inside parent bounds
- no node is placed outside its container
- tessellated region children fill the container content area without internal gaps (except configured header area)
- region separators render as single lines on shared seams (no doubled adjacent borders)
- cross-boundary edges attach to boundary ports
- all edge endpoints lie on source/target boundaries (within epsilon), not node centers
- direct-routing endpoints match center-ray-to-boundary intersections
- rectilinear-routing endpoints use suitable boundary sides and default to closest-border centers when ties/constraints do not dictate otherwise
- containment tree remains acyclic
- layout output is deterministic for identical input
- layout output is identical across supported targets (JVM/JS/Wasm) for identical input
- collapsed container hides all descendants from rendering output
- collapsed/expanded default state follows `collapsePolicy`
- expanding then collapsing preserves deterministic external edge routing
- edge routes are keyed by stable edge IDs (`edgeRoutesByEdgeId`)

### Platform coverage
- run deterministic layout assertions in `commonTest`
- run cross-target parity snapshots in `jvmTest` and JS/Wasm test targets

## 12. Suggested generic presets

- `genericCompound`
- `hierarchyBiased`
- `orthogonalBiased`
- `compact`
- `treeLikeSeriesParallel`

These are algorithmic profiles, not domain profiles. They are orthogonal to `ChildLayout`; for example, a diagram may use the `hierarchyBiased` profile overall while a specific container uses `TESSELLATE` for its immediate children.

## 13. Open questions

1. Should containers be explicit graph objects or inferred from input data?
2. Should cross-boundary edges always use boundary ports?
3. Should profile selection be static or auto-selected from graph metrics?
4. Should orthogonal readability always win over compactness?
5. How should optional container headers affect sizing in a domain-neutral way?
6. Should collapsed containers show summary metadata (for example, descendant count, edge count)?

## 14. Recommended first implementation scope

To reduce risk, implement in this order:

1. Add compound graph data structures and a flat-API adapter without breaking existing callers.
2. Implement recursive leaf-first layout for nested containers.
3. Keep the existing Sugiyama implementation for individual levels.
4. Add boundary-port routing for edges that cross containers, keyed by edge ID.
5. Update `GraphLayoutView` to consume recursive layout results.
6. Add tests for nested compound graphs and cross-boundary edge cases before optimizing aesthetics.

## 15. Acceptance criteria

The layout is successful if it can:
- lay out nested compound graphs cleanly
- keep children inside their containers
- route cross-container edges clearly
- preserve hierarchy direction consistency
- reduce crossings compared with the current implementation
- remain stable as the graph changes incrementally
- support both default-expanded and default-collapsed containers
- support runtime toggle between collapsed and expanded states
- return deterministic edge routes keyed by stable edge IDs
- produce the same layout output on supported platforms for identical inputs

Additionally, the same core algorithm implementation must work unchanged across at least two different domain mappings (for example, class-like graphs and state-like graphs) by changing only input mapping/profile configuration.

## 16. Notes for future iteration

This specification is intentionally implementation-oriented, so it can be refined into a concrete design doc or directly converted into code tasks. If needed, the next iteration can break this into:
- API changes
- data model changes
- algorithm changes
- rendering changes
- test scenarios

