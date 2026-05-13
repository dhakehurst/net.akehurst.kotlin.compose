# Compound Layout Implementation Plan

## Status
- Target runtime for live review: JVM
- Demo delivery mode: dedicated demo module (`layout-graph-demo`)
- Scenario set: drafted below

## Implementation checklist
- [x] Step 0: Contracts + demo scaffold
- [x] Step 1: Compound model + flat adapter
- [x] Step 2: Recursive container placement
- [x] Step 3: Compound-aware per-level layout integration
- [x] Step 4: Boundary ports + cross-container routing
- [x] Step 5: Collapse/expand behavior
- [ ] Step 6: Refinement + profile tuning
- [ ] Step 7: Determinism + performance hardening

## Specification alignment snapshot
- Phase mapping now matches `Compound Layout Specification.md` phases 0-7.
- Step-level acceptance includes: local/global transform contract, stable edge-route IDs, collapse policy semantics, and cross-target parity.
- Remaining high-risk items: cross-hierarchy edge routing quality, profile tuning trade-offs (compactness vs readability), and incremental recompute boundaries.
- Region-layout refinements now captured: tessellated region fill behavior, single-divider border rendering, reduced boundary-hop routing for sibling-region transitions, and per-container child-layout inheritance.
- Container presentation intent captured: child-content origin and insets are measured from Compose child-host placement; legacy model geometry fields (`childContentOffsetX/Y`, `padding`, `headerHeight`) are removed.
- **Node position/size contract clarified (implemented):** Caller-supplied `x`/`y` fields are removed from the demo node DTO — position is exclusively algorithm output. `widthHint`/`heightHint` on `GraphLayoutCompoundNode` are optional; the engine falls back to `CompoundLayoutConfig.defaultNodeWidth/Height` when not provided. Container size is computed from child bounds + Compose-measured insets, with `widthHint`/`heightHint` acting as a lower-bound floor. `DemoNode.x`, `DemoNode.y`, `DemoNode.width`, and `DemoNode.height` are replaced by `DemoNode.widthHint: Float?` and `DemoNode.heightHint: Float?`. The adapter-side `normalizeRegionTiles()` pre-computation (which depended on explicit container dimensions) is removed; tessellated tiling is owned entirely by the engine's `TESSELLATE` layout strategy.
- **Deterministic first-pass container metrics implemented:** when Compose has not yet measured a container child host, the engine now uses fallback child-host metrics `(originX=12, originY=24, insetRight=12, insetBottom=12)` for containers with visible children. This prevents first-pass layouts from collapsing child content flush against container boundaries and stabilizes demo scenarios such as `single_container_two_nodes` and nested state-like regions until measured metrics arrive.

## Research and yFiles-inspired constraints (applies to all steps)
- Keep core layout domain-agnostic; map UML/statechart/package semantics before entering layout.
- Treat containment as inclusion-tree structure only; never infer containment from adjacency edges.
- Let each container optionally choose the layout for its immediate children; inherit the effective child layout from the parent when unspecified.
- Support at least `GRAPH` (default) and `TESSELLATE` child-layout modes.
- Preserve hierarchy direction per local level; apply stable tie-breakers by ID in every ordering phase.
- Use Sugiyama-style per-level flow (`cycle removal -> layering -> dummy insertion -> crossing reduction -> coordinate assignment -> routing`) and keep child graphs atomic at parent levels.
- Incorporate yFiles-like profile behavior: hierarchical default, orthogonal-biased routing, compact mode, and tree/series-parallel bias where graph metrics indicate suitability.
- Prefer readability when in conflict with compactness for cross-boundary and hierarchy-critical routes.

## Working agreements for implementation and review
- Deliver in small, reviewable slices.
- After each step: provide changed files, acceptance checklist, and visual review notes.
- Keep layout core model in `commonMain` UI-agnostic.
- Keep route output keyed by stable edge IDs (`edgeRoutesByEdgeId`).
- Keep caller-supplied rendering content keyed by stable IDs for both nodes and edges, outside the core layout algorithm; edge rendering content includes endpoint symbols and positioned text labels.
- Treat container visuals and insets as Compose-facing concerns; pass measured child-host metrics to layout rather than storing geometry fields on the core model.
- Ensure deterministic output for identical input.
- **Every step must update `DemoApp.kt` / `LiveLayoutCanvas` so the live JVM demo reflects the new layout output for the relevant scenarios.**
- Validate JVM live output after each step by running `./gradlew :layout-graph-demo:run`.

## Step plan

## Specification traceability (spec -> plan)
| Specification section | Required capability | Plan step(s) |
| --- | --- | --- |
| 5.1 Graph model | Compound graph structures + inclusion ownership | Step 1 |
| 5.2 Recursive layout pipeline | Leaf-first recursion + child-as-atomic parent layout + inherited child-layout resolution | Step 2, Step 3 |
| 5.3 Layout strategy per level | Sugiyama-style per-level flow | Step 3 |
| 5.6 Layout result shape | Stable IDs, local/global coordinates, edge routes by ID | Step 2, Step 4 |
| 5.7 Coordinate/transform contract | Local-to-global transform accumulation | Step 2 |
| 5.8 Domain-agnostic semantics | No UML/statechart hard-coding in core layout | Step 1, Step 3, Step 6 |
| 6.3 Cross-container edges | Boundary-attached, readable cross-container routing | Step 4 |
| 6.5 Collapse/expand behavior | Default policy + runtime state + hidden descendant handling | Step 5 |
| 7 Routing and ports | Routing modes, boundary ports, multi-edge separation | Step 4, Step 6 |
| 8 Layout quality criteria | Readability, hierarchy clarity, crossing/bend reduction | Step 3, Step 4, Step 6 |
| 9 Phase 6 refinement | Presets + spacing/alignment tuning | Step 6 |
| 9 Phase 7 hardening | Determinism, incremental recompute, perf/memory baselines | Step 7 |
| 10 Algorithm outline | End-to-end orchestration and merge strategy | Step 2 to Step 5 |
| 11 Testing strategy | Structural, visual, assertions, parity coverage | Step 7 (+ tests listed below) |
| 15 Acceptance criteria | Nested layout, routing, collapse, determinism, parity | Step 2 to Step 7 |

### Step 0: Contracts + demo scaffold
Goal: establish a runnable review loop before algorithm changes.

Deliverables:
- Demo module scaffold with JVM entrypoint and scenario picker.
- Scenario DTOs and deterministic IDs.
- Debug overlay hooks (bounds, ports, edge IDs) as toggleable UI.
- Live layout rendering wired to `SugiyamaLayout` (replacing static placeholder).

Live demo update:
- `DemoApp.kt`: `LiveLayoutCanvas` renders computed node positions and edge routes from `SugiyamaLayout`.
- All 8 scenarios selectable and visually rendered with live layout.

Acceptance:
- Demo starts on JVM.
- All scenarios can be selected and rendered with computed layout.
- Debug overlay toggle works (bounds, ports, edge IDs).

### Step 1: Compound model + flat adapter
Goal: introduce data model without breaking current callers.

Deliverables:
- Compound graph structures in `commonMain`.
- Containment represented as ownership/inclusion tree (not routable edge kind).
- Per-container child-layout field with inheritance semantics (`GRAPH` default at root, `TESSELLATE` optional per container).
- Core compound model excludes container geometry hint fields (`childContentOffsetX/Y`, `padding`, `headerHeight`).
- Adapter from existing flat graph API to compound model.
- Caller-facing compound state preserves Compose rendering content for nodes and edges, keyed by stable IDs; node content may render nested children, and edge content may define start/end symbols plus text labels positioned at the start, middle, or end of a route.

Live demo update:
- `DemoApp.kt`: no visible rendering change expected; demo continues to render flat scenarios via adapter to confirm no regression.

Acceptance:
- Existing flat examples render via adapter with no behavioral regression.
- Model invariants validated (single parent per node, acyclic containment).
- Unspecified container child layout inherits from the parent; root defaults to `GRAPH`.
- Node and edge rendering content remain available after adapting flat scenarios into the compound model.
- Endpoint symbols and positioned edge text remain bound by stable edge ID.

### Step 2: Recursive container placement
Goal: place nested graphs with correct bounds and transforms.

Deliverables:
- Leaf-first recursive layout orchestration.
- Inclusion-tree validation (acyclic, single direct parent per node).
- Effective child-layout resolution per container.
- Parent bounds from child bounds + Compose-measured child-host metrics.
- Child-content origin and insets captured from Compose measurement with deterministic first-pass fallback and automatic relayout.
- Local/global coordinate transform contract implemented in result.

Live demo update:
- `DemoApp.kt`: `LiveLayoutCanvas` updated to consume `CompoundLayoutResult` and render containers with their children positioned inside them using global coordinates.
- Target scenarios for review: `single_container_two_nodes`, `deep_nesting`, `state_like_regions`.

Acceptance:
- Nested scenarios show children inside container bounds in the live demo.
- Global coordinates are consistent with local + accumulated transforms.
- Containers without explicit child-layout selection use the same effective child layout as their parent.
- Container child origin is derived from Compose placement of the `children` host; no per-container offset fields are required.
- Child/global bounds and node placements are stable for identical input.

### Step 3: Compound-aware per-level layout integration
Goal: integrate level layout while treating child graphs as atomic nodes.

Deliverables:
- Parent-level graph built from atomic child placeholders.
- Existing Sugiyama-style steps reused per level where possible.
- `GRAPH` child layout uses the graph-oriented local pipeline; `TESSELLATE` child layout uses deterministic tiling for immediate children.
- Crossing reduction uses deterministic barycenter/median-style ordering with ID tie-breakers.
- Long edges are expanded with dummy nodes at each local level.
- Per-level algorithm/profile selection supported: inherit parent profile by default, allow explicit override for child graphs.
- Stable ordering tie-breakers by ID.

Live demo update:
- `DemoApp.kt`: directional flow visible between containers as well as within them.
- Target scenarios for review: `sibling_containers_cross_edges`, `deep_nesting`, `uml_like_inheritance_association`.

Acceptance:
- Directional flow and layer order are stable across reruns in the live demo.
- Crossing count trends improve vs naive placement on target scenarios.
- Child-layout overrides affect only the container's immediate children and remain deterministic.
- Child-level profile override works while preserving deterministic output.

### Step 4: Boundary ports + cross-container routing
Goal: route external edges through container boundaries.

Deliverables:
- Boundary port selection rules.
- `edgeRoutesByEdgeId` routing output in global coordinates.
- Edge rendering content remains bound by stable edge ID so route geometry, endpoint symbols, and text labels can evolve independently.
- Multi-edge separation and basic self-loop handling.
- Compound-aware rectilinear routing mode and direct mode parity checks.
- Endpoint boundary-attachment contract:
  - Direct routing endpoints are computed as center-ray boundary intersections (edge would continue to center, but is clipped at boundary).
  - Rectilinear routing endpoints attach at suitable boundary points to reduce overlap; default anchor is the center of the closest border side.
- Deterministic geometry/tie-break contract for cross-target parity:
  - Fixed boundary epsilon (`1e-6`) and normalized near-zero output.
  - Corner/degenerate tie-break side order: `RIGHT, BOTTOM, LEFT, TOP`.
  - Parallel-edge endpoint slotting based on stable edge ID ordering.
- Routing profile presets (`DEFAULT`, `HIERARCHY_BIASED`, `ORTHOGONAL_BIASED`, `COMPACT`, `CHANNEL_BIASED`) with deterministic fallback behavior.

Live demo update:
- `DemoApp.kt`: edges between nodes in different containers visually attach at container boundaries rather than passing through them.
- Target scenarios for review: `sibling_containers_cross_edges`, `state_like_regions`, `single_container_two_nodes`.

Acceptance:
- Cross-container edges attach at boundaries in the live demo, not through unrelated containers.
- Route output uses stable edge IDs.
- External routes avoid unrelated containers unless no valid alternative exists.
- Edge endpoints visually stop at source/target boundaries (not centers) in both direct and rectilinear modes.
- Direct mode endpoint clipping matches center-to-center ray intersections with node/container boundaries.
- Rectilinear mode endpoint anchoring reduces overlap for parallel edges, with closest-border-center fallback.
- Tie-break outcomes (corner hits, degenerate centers, anchor ties) are deterministic and consistent across repeated JVM runs.

### Step 5: Collapse/expand behavior
Goal: support runtime visibility changes with stable layout behavior.

Deliverables:
- Collapse policy default + runtime state handling.
- External connectivity aggregation for collapsed descendants.
- Deterministic rerouting and stable relative ordering where possible.
- Collapsed containers expose boundary ports for hidden-descendant connectivity.

Live demo update:
- `DemoApp.kt`: add a collapse/expand toggle per container in the sidebar.
- Collapsed containers render as a single summary box; internal nodes and edges are hidden.
- Target scenarios for review: `collapsed_container_external_links`, `mixed_collapsed_expanded_siblings`.

Acceptance:
- Collapsed containers hide internal nodes/edges in the live demo.
- Expand/collapse cycles remain deterministic for unaffected regions.
- Default collapsed/expanded state follows `collapsePolicy`.

### Step 6: Refinement + profile tuning
Goal: improve readability/compactness trade-offs and validate profile behavior on representative diagrams.

Deliverables:
- Profile presets wired and tuned: `genericCompound`, `hierarchyBiased`, `orthogonalBiased`, `compact`, `treeLikeSeriesParallel`.
- Spacing/alignment tuning guided by UML-like and state-like scenarios.
- Region-based tessellation tuning:
  - when a container selects `TESSELLATE`, use deterministic row-major tiling that fills container content area
  - normalize region child bounds to tile bounds (author hints are advisory)
  - render shared region seams as single divider lines
- Routing refinement discovered during live review:
  - for nested region containers, disable intermediate boundary participation where appropriate (`routeBoundary = false`) to prevent redundant boundary intersections and zig-zag routes.
- Review notes documenting where readability intentionally wins over compactness.

Live demo update:
- `DemoApp.kt`: profile selector and per-scenario profile defaults exposed in sidebar.
- Visual comparison mode for profile A/B review on the same scenario.

Acceptance:
- Each preset produces distinct, documented behavior across target scenarios.
- At least two domain mappings (class-like and state-like) use same core algorithm with only mapping/profile changes.
- In region scenarios, tessellated regions fill available area and separators appear as single borders.
- Region-crossing transitions avoid unnecessary boundary hops while preserving deterministic output.

### Region-based layout notes (captured from implementation)
- Region containers should be selectable for tessellated layout explicitly via the container's child-layout setting; state-region mappings are the main motivating example.
- Region tiling area follows measured child-host bounds from Compose container content.
- To avoid double borders, region node content should not paint per-node outer borders when shared seams are drawn centrally by the view.
- Boundary routing through intermediate region containers can reduce readability; routing should prioritize enclosure-level clarity for sibling-region transitions.

### Compose/presentation boundary notes
- A container may or may not render a visible boundary; that decision belongs to Compose-specific node content.
- Child-content origin/insets are measured from Compose child-host placement and supplied to layout at render time.
- Core compound model fields do not carry container geometry hints (`childContentOffsetX/Y`, `padding`, `headerHeight`).
- First-pass layout may use deterministic fallback insets, then re-layout automatically once measurements are available.

### Step 7: Determinism + performance hardening
Goal: lock reliability and regression safety.

Deliverables:
- Determinism tests with repeated-run snapshots.
- Cross-target parity checks for JVM/JS/Wasm on identical inputs.
- Incremental recompute boundaries for collapse/expand and localized edits.
- Baseline performance and memory metrics for representative scenario sizes.

Live demo update:
- `DemoApp.kt`: add a "re-run layout" button that triggers repeated layout and highlights any divergence.
- Display layout computation time in the status bar.

Acceptance:
- Identical input yields identical output on repeated JVM runs, visible in the demo.
- Cross-target parity tests pass for selected snapshots.
- Performance baseline recorded and tracked.

## Scenario corpus (draft)
- `flat_chain`: linear A -> B -> C -> D.
- `single_container_two_nodes`: one container, two internal nodes, one external edge.
- `sibling_containers_cross_edges`: two sibling containers with cross links.
- `deep_nesting`: three-level containment tree with mixed internal/external edges.
- `collapsed_container_external_links`: collapsed middle container with aggregated links.
- `mixed_collapsed_expanded_siblings`: one collapsed sibling and one expanded sibling.
- `uml_like_inheritance_association`: hierarchy-like plus association-like mix.
- `state_like_regions`: region-like nested structure with boundary-crossing transitions.
- `package_like_dependencies`: package tree with cross-package dependencies.
- `series_parallel_subregion`: tree-like/series-parallel inner region inside a hierarchical parent.
- `multi_edge_channel`: multiple cross-container edges between same endpoints to validate channel separation.

## Per-step review template
Use this template for each implementation slice:

1. Scope completed
- What changed and why.

2. Files touched
- List of paths.

3. Acceptance checks
- Invariants and expected behavior.

4. Live demo review notes
- Scenario(s) to inspect and expected visual result.

5. Test evidence
- Tests run and outcomes.

## Proposed acceptance test IDs

Naming convention:
- Common deterministic/structural tests: `CompoundLayout<Capability>Test`
- Platform parity snapshots: `CompoundLayoutParity<Scenario>Test`

`commonTest` candidates (structural + deterministic assertions):
- `CompoundLayoutEmptyGraphTest`
- `CompoundLayoutSingleNodeTest`
- `CompoundLayoutSingleContainerTwoNodesTest`
- `CompoundLayoutSiblingContainersCrossEdgesTest`
- `CompoundLayoutDeepNestingTest`
- `CompoundLayoutCyclicAdjacencyAcyclicContainmentTest`
- `CompoundLayoutCollapsedContainerVisibilityTest`
- `CompoundLayoutMixedCollapsedExpandedSiblingsTest`
- `CompoundLayoutBoundsContainmentInvariantTest`
- `CompoundLayoutBoundaryPortAttachmentInvariantTest`
- `CompoundLayoutEdgeEndpointOnBoundaryInvariantTest`
- `CompoundLayoutDirectEndpointCenterRayIntersectionTest`
- `CompoundLayoutRectilinearEndpointClosestBorderFallbackTest`
- `CompoundLayoutStableEdgeRouteIdsTest`
- `CompoundLayoutCollapsePolicyDefaultStateTest`
- `CompoundLayoutExpandCollapseDeterminismTest`
- `CompoundLayoutDeterministicRepeatRunTest`

`jvmTest` candidates (visual/snapshot + profile behavior):
- `CompoundLayoutSnapshotUmlLikeInheritanceAssociationTest`
- `CompoundLayoutSnapshotPackageLikeDependenciesTest`
- `CompoundLayoutSnapshotStateLikeRegionsTest`
- `CompoundLayoutSnapshotSeriesParallelSubregionTest`
- `CompoundLayoutProfileHierarchyBiasedTest`
- `CompoundLayoutProfileOrthogonalBiasedTest`
- `CompoundLayoutProfileCompactTest`
- `CompoundLayoutProfileChannelBiasedTest`

JS/Wasm parity snapshot candidates (target-specific test source sets):
- `CompoundLayoutParityUmlLikeInheritanceAssociationTest`
- `CompoundLayoutParityStateLikeRegionsTest`
- `CompoundLayoutParityDeepNestingTest`
- `CompoundLayoutParityCollapsedContainerExternalLinksTest`

## Definition of done
- Compound layout supports nested containers and cross-container routing.
- Collapse/expand behavior is deterministic and reviewable live.
- Output API is stable (`edgeRoutesByEdgeId`, local/global coordinates).
- Existing flat usage remains supported through adapter during migration.
- Profile presets are available and validated against scenario corpus.
- Determinism and cross-target parity are verified on representative snapshots.
- Documentation and scenario corpus remain aligned with implementation.

