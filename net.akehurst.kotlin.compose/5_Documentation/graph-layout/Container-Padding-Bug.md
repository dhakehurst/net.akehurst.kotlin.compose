# Container Padding Bug - Issue Capture

**Status:** Investigating  
**Last Updated:** 2026-05-13  
**Scenario:** `single_container_two_nodes_withPadding`

## Problem Statement

When a container composable (e.g., `SimpleBoxContainer`) has padding applied, the container size computed by the layout engine does not grow large enough to visually contain its children inside the padded border. Children appear to overflow or be positioned outside the visual container bounds.

### Reproduction

- **Scenario:** `single_container_two_nodes_withPadding` in `DemoScenarios.kt`
- **Container:** `SimpleBoxContainer("Container1", 20.dp, children)`
  - Applies `.padding(20.dp)` to the root Column
  - Contains two child nodes: `InsideA` and `InsideB`
- **Expected:** Container visual border (with 20dp padding) fully encloses both children
- **Actual:** Container visual border is too small; children appear to overflow or sit at the edge

### Current Errors Encountered

1. **IllegalStateException: LayoutCoordinates not attached**
   - Occurs when trying to call `localBoundingBoxOf()` to compute measured child-host insets
   - Happens in `NodeContent` when container and child-host coordinates are captured asynchronously
   - Timing issue: child-host coordinates can become detached before reporter lambda executes

## Architecture Overview

### Key Components

1. **Layout Engine** (`CompoundLayoutEngine.kt`):
   - Computes child node positions and container bounds
   - Uses `ContainerChildHostMetrics` to account for padding/header offsets
   - Container size = `childContent + originX + insetRight + originY + insetBottom`

2. **View/Render Layer** (`CompoundGraphLayoutView.kt`):
   - `NodeContent()` composable wraps each node in a `Box`
   - Container nodes receive a `children` lambda that internally:
     - Captures child-host `Box` coordinates via `onGloballyPositioned`
     - Calls `reportContainerMetrics()` to compute `ContainerChildHostMetrics`
     - Passes metrics back to layout engine via `onChildHostMeasured` callback

3. **Demo Renderers** (`DemoScenarios.kt`):
   - `SimpleBoxContainer(name, padding, children)`:
     - Root: `Column` with `Modifier.fillMaxSize()` + `.padding(padding)`
     - Header: `Text` with name
     - Content host: `Box` with `weight(1f)` + `fillMaxWidth()`
     - Receives `children` composable and renders it

### Data Flow

```
Layout Engine (computes sizes)
    ↓
NodeContent render (fills allocated bounds)
    ├→ Container Box (fillMaxSize)
    │   ├→ onGloballyPositioned { containerCoordinates = ... }
    │   ├→ Container Composable (SimpleBoxContainer)
    │   │   ├→ Header (Text)
    │   │   ├→ Child Host Box (the 'children' lambda output)
    │   │   │   ├→ onGloballyPositioned { childHostCoordinates = ... }
    │   │   │   └→ Runs: reportContainerMetrics()
    │   │   └→ [children rendered here]
```

## Root Cause Analysis

### Theory 1: Padding Not Measured Into Child-Host Metrics ✓ (Partially Fixed)

**What we know:**
- `SimpleBoxContainer` applies padding via `.padding(20.dp)` on the root `Column`
- This shifts the child-host `Box` inward by 20dp in each direction
- When `localBoundingBoxOf(childHostCoordinates)` is called, it should report:
  - `left = 20.dp` (padding)
  - `top = 20.dp` (padding)
  - `right = containerWidth - 20.dp`
  - `bottom = containerHeight - 20.dp`

**What was wrong:**
- Old code discarded measured `insetRight/insetBottom` when child content exceeded host box size
- This meant if children overflowed, container padding was treated as "shrinkable"
- Fixed by preserving measured insets (`insetRight = containerWidth - measuredHostRight`)

**Current status:** Padding insets should now be preserved in metrics, but...

### Theory 2: Detached Coordinates Block Metric Reporting ✓ (Partially Fixed)

**What happens:**
1. Container `onGloballyPositioned` fires → captures `containerCoordinates`
2. Child host box is being laid out → its coordinates may momentarily become detached
3. `reportContainerMetrics()` tries to call `localBoundingBoxOf(childHostCoordinates)`
4. Throws `IllegalStateException` because child coords are detached
5. Metrics are never reported → layout engine uses fallback metrics

**Fix applied:** Wrapped `localBoundingBoxOf` call in try-catch so it silently skips on detached error and retries when next callback fires.

**Current status:** Crash should be prevented, but metrics may be delayed or incomplete.

### Theory 3: First-Pass Measurement Doesn't Account for Padding

**What we suspect:**
- On first layout pass, container may be measured **before** child-host box is fully positioned
- Fallback metrics are used:
  ```kotlin
  ContainerChildHostMetrics(
      originX = 12.0,
      originY = 24.0,
      insetRight = 12.0,
      insetBottom = 12.0
  )
  ```
- These hardcoded fallbacks do not match your actual padding (20.dp)
- Container size is computed from fallback metrics, not measured metrics
- Measured metrics arrive later (second recompose), but container size doesn't update

**Current status:** Not yet validated. Would require logging metric values to confirm.

## What Has Been Attempted

### Changes to `CompoundGraphLayoutView.kt`

1. ✅ **Enforce node wrapper `fillMaxSize()`:** Ensures node composables cannot shrink below allocated bounds
2. ✅ **Cache both container and child-host coordinates:** Allow reporting in any order
3. ✅ **Preserve measured padding insets:** Keep `insetRight/insetBottom` stable from measured geometry
4. ✅ **Guard against detached coordinates:** Wrap `localBoundingBoxOf()` in try-catch
5. ⚠️  **Metric reporting race condition:** Still has timing sensitivity around coordinate attachment

### Changes to `DemoScenarios.kt`

1. ✅ **Added `padding` parameter to `SimpleBoxContainer`:** Now `SimpleBoxContainer(name, padding, children)`
2. ✅ **Created padded scenario:** `singleContainerTwoNodesWithPadding` with 20.dp padding
3. ✅ **Created unpadded scenario:** `singleContainerTwoNodes` with 0.dp padding for comparison

### Test Coverage

- ✅ Added `measured_container_metrics_preserve_padding_when_child_content_exceeds_host_size()` in `test_CompoundGraphLayoutViewMetrics.kt`

## Open Questions

1. **Are measured metrics being reported?**
   - Need logging to verify `onChildHostMeasured` callback is invoked both times (with/without padding)
   - Need to capture actual metric values: `originX`, `originY`, `insetRight`, `insetBottom`

2. **Does the layout engine use reported metrics on second pass?**
   - Is `measuredContainerMetrics` map being populated?
   - Does `CompoundLayoutEngine.layout()` use these metrics for second layout?

3. **What is the fallback metric vs. measured metric gap?**
   - Fallback: `{ originX: 12, originY: 24, insetRight: 12, insetBottom: 12 }`
   - Actual (with 20.dp padding): `{ originX: 20, originY: 20+header, insetRight: 20, insetBottom: 20 }`
   - This mismatch could cause undersizing on first pass

4. **Why doesn't container size expand on second recomposition?**
   - After measured metrics arrive, does the layout engine re-run?
   - Does `CompoundGraphLayoutView` invalidate layout when metrics change?

## Next Steps for Investigation

1. **Add instrumentation:**
   - Log `reportContainerMetrics()` invocations (with timestamp, nodeId, coordinates validity)
   - Log metric values passed to `onChildHostMeasured` (originX, originY, insetRight, insetBottom)
   - Log when `measuredContainerMetrics` map is updated

2. **Verify metric flow:**
   - Check if metrics are reported for padded scenario
   - Compare reported values vs. container size computation

3. **Consider alternative approaches:**
   - If first-pass metrics are the bottleneck, pre-compute padding from composable structure
   - If timing is the issue, defer child Layout rendering until both coordinates are stable

## References

- Spec: `5_Documentation/graph-layout/Compound Layout Specification.md`
- Implementation Plan: `5_Documentation/graph-layout/Compound Layout Implementation Plan.md`
- Container Renderer Contract: `5_Documentation/graph-layout/Container Renderer Contract Draft.md`

