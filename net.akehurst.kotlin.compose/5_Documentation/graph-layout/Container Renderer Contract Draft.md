# Container Renderer Contract (Draft for Review)

Status: draft
Date: 2026-05-13

## Decision Snapshot

1. Contract choice: **C** (both)
   - Container renderers should fill the allocated node area.
   - `NodeContent` also enforces a fill wrapper so custom content cannot accidentally shrink measured bounds.
2. `minWidthToContent` behavior: **unchanged**
   - Current logic may exceed incoming max constraints by design.

## Why this draft exists

`minWidthToContent(30.dp)` is useful for visual minimums and content-driven sizing, but container visuals must still fully cover the layout node allocated by the compound engine. Otherwise children can appear outside the drawn container border.

## Proposed contract

- Leaf renderer: may use content-size/min-width behavior.
- Container renderer: must fill its allocated node bounds and may also apply content/min-width floors.
- Layout/view layer should not rely on container content to do this correctly; wrapper enforcement remains in place.

## Implemented in code (current draft state)

- `layout-graph/src/commonMain/kotlin/CompoundGraphLayoutView.kt`
  - Container and leaf wrapper `Box` now use `fillMaxSize()` before `onGloballyPositioned`.
- `layout-graph-demo/src/commonMain/kotlin/DemoScenarios.kt`
  - Added `Modifier.containerNodeFrame(hardMinWidth)` = `fillMaxSize().minWidthToContent(hardMinWidth)`.
  - Applied to container-style renderers:
    - `SimpleBoxContainer`
    - `Package`
    - `CompoundState`
    - `DeploymentNode`
    - `Component`
    - `Region`

## Review checklist

- [ ] Confirm this contract should be documented in public KDoc for `GraphLayoutCompound` rendering APIs.
- [ ] Confirm `Component` should always be treated as container-style, including when currently empty.
- [ ] Decide if `Package` header tab should keep full-width behavior or use width-to-header style with inner host fill.
- [ ] Decide if any additional demo renderer should move to `containerNodeFrame`.

## Open question

Should we add a dedicated regression test at view level (Compose UI test) that verifies container border bounds always enclose child layout bounds for `single_container_two_nodes` with non-zero padding?

