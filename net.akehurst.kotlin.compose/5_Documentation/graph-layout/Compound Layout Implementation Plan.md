# Compound Layout Implementation Plan

## Status
- Target runtime for live review: JVM
- Demo delivery mode: dedicated demo module (`layout-graph-demo`)
- Scenario set: drafted below

## Implementation checklist
- [x] Step 0: Contracts + demo scaffold
- [x] Step 1: Compound model + flat adapter
- [ ] Step 2: Recursive container placement
- [ ] Step 3: Compound-aware per-level layout integration
- [ ] Step 4: Boundary ports + cross-container routing
- [ ] Step 5: Collapse/expand behavior
- [ ] Step 6: Determinism + performance hardening

## Working agreements for implementation and review
- Deliver in small, reviewable slices.
- After each step: provide changed files, acceptance checklist, and visual review notes.
- Keep layout core model in `commonMain` UI-agnostic.
- Keep route output keyed by stable edge IDs (`edgeRoutesByEdgeId`).
- Ensure deterministic output for identical input.
- **Every step must update `DemoApp.kt` / `LiveLayoutCanvas` so the live JVM demo reflects the new layout output for the relevant scenarios.**
- Validate JVM live output after each step by running `./gradlew :layout-graph-demo:run`.

## Step plan

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
- Adapter from existing flat graph API to compound model.

Live demo update:
- `DemoApp.kt`: no visible rendering change expected; demo continues to render flat scenarios via adapter to confirm no regression.

Acceptance:
- Existing flat examples render via adapter with no behavioral regression.
- Model invariants validated (single parent per node, acyclic containment).

### Step 2: Recursive container placement
Goal: place nested graphs with correct bounds and transforms.

Deliverables:
- Leaf-first recursive layout orchestration.
- Parent bounds from child bounds + padding/header rules.
- Local/global coordinate transform contract implemented in result.

Live demo update:
- `DemoApp.kt`: `LiveLayoutCanvas` updated to consume `CompoundLayoutResult` and render containers with their children positioned inside them using global coordinates.
- Target scenarios for review: `single_container_two_nodes`, `deep_nesting`, `state_like_regions`.

Acceptance:
- Nested scenarios show children inside container bounds in the live demo.
- Global coordinates are consistent with local + accumulated transforms.

### Step 3: Compound-aware per-level layout integration
Goal: integrate level layout while treating child graphs as atomic nodes.

Deliverables:
- Parent-level graph built from atomic child placeholders.
- Existing Sugiyama-style steps reused per level where possible.
- Stable ordering tie-breakers by ID.

Live demo update:
- `DemoApp.kt`: directional flow visible between containers as well as within them.
- Target scenarios for review: `sibling_containers_cross_edges`, `deep_nesting`, `uml_like_inheritance_association`.

Acceptance:
- Directional flow and layer order are stable across reruns in the live demo.
- Crossing count trends improve vs naive placement on target scenarios.

### Step 4: Boundary ports + cross-container routing
Goal: route external edges through container boundaries.

Deliverables:
- Boundary port selection rules.
- `edgeRoutesByEdgeId` routing output in global coordinates.
- Multi-edge separation and basic self-loop handling.

Live demo update:
- `DemoApp.kt`: edges between nodes in different containers visually attach at container boundaries rather than passing through them.
- Target scenarios for review: `sibling_containers_cross_edges`, `state_like_regions`, `single_container_two_nodes`.

Acceptance:
- Cross-container edges attach at boundaries in the live demo, not through unrelated containers.
- Route output uses stable edge IDs.

### Step 5: Collapse/expand behavior
Goal: support runtime visibility changes with stable layout behavior.

Deliverables:
- Collapse policy default + runtime state handling.
- External connectivity aggregation for collapsed descendants.
- Deterministic rerouting and stable relative ordering where possible.

Live demo update:
- `DemoApp.kt`: add a collapse/expand toggle per container in the sidebar.
- Collapsed containers render as a single summary box; internal nodes and edges are hidden.
- Target scenarios for review: `collapsed_container_external_links`, `mixed_collapsed_expanded_siblings`.

Acceptance:
- Collapsed containers hide internal nodes/edges in the live demo.
- Expand/collapse cycles remain deterministic for unaffected regions.

### Step 6: Determinism + performance hardening
Goal: lock reliability and regression safety.

Deliverables:
- Determinism tests with repeated-run snapshots.
- Baseline performance metrics for representative scenario sizes.
- Review notes on known trade-offs (compactness vs readability).

Live demo update:
- `DemoApp.kt`: add a "re-run layout" button that triggers a repeated layout pass and confirms the output is visually identical.
- Display layout computation time in the status bar.

Acceptance:
- Identical input yields identical output on repeated JVM runs, visible in the demo.
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

## Definition of done
- Compound layout supports nested containers and cross-container routing.
- Collapse/expand behavior is deterministic and reviewable live.
- Output API is stable (`edgeRoutesByEdgeId`, local/global coordinates).
- Existing flat usage remains supported through adapter during migration.
- Documentation and scenario corpus remain aligned with implementation.

