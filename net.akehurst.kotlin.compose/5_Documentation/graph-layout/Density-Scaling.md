# Layout units: dp

**Status:** Fixed on 2026-11-04. The layout works in dp, so a diagram looks the same at every screen density.
**Found:** 2026-10-30, while fixing edges that didn't meet nodes on a laptop screen (PR #5).

## What was wrong

The layout engine used to work in pixels. Node content is Compose content, so it grows with screen
density, but the spacing and default sizes were plain numbers used as pixels, so they did not.
At 2x density nodes were twice as big but the gaps between them were the same, and diagrams looked
cramped (example 11 in the demo showed it most).

## How it works now

- `CompoundLayoutEngine`, `SugiyamaLayout` and `OrthogonalEdgeRouter` work in dp. Every length they take
  (spacing, margins, default sizes, width/height hints, `PaddingHint`, measured sizes and container
  metrics) and every position and size they return is in dp.
- `CompoundGraphLayoutView` and `GraphLayoutView` do the conversion. They divide measured pixel sizes
  by the density before calling the layout, and multiply the result by the density before placing
  and drawing.
- Measured sizes are rounded to whole dp before layout (leaf sizes round up, so content always fits).
  Without this, at 1.5x tiny fractions of a dp could tip a layout choice, such as which side an edge
  is routed on.
- What the views draw is in dp too: edge line widths, `GraphLayoutEdgeSymbol` points and stroke
  widths, `GraphLayoutEdge.width`, the offset of start and end edge labels, and the debug overlay.

At 1x density nothing changed: every demo render is pixel-identical to before.

## Values that are in dp

| Where | Value | Default |
|---|---|---|
| `SugiyamaChildLayout` (`ChildLayoutAlgorithm.kt`) | `layerSpacing`, `nodeSpacing` | 100 / 100 (`DEFAULT`); the presets use 70 to 130 |
| `SugiyamaLayout` (`SugiyamaLayout.kt`) | `layerSpacing`, `nodeSpacing` | 100 / 100 |
| `OrthogonalEdgeRouter` (`OrthogonalEdgeRouter.kt`) | `margin`, `edgeSpacing` | 12 / 8 |
| `CompoundLayoutConfig` (`CompoundLayoutEngine.kt`) | `defaultNodeWidth`, `defaultNodeHeight` | 100 / 56 |
| `CompoundLayoutConfig` | `defaultPortWidth`, `defaultPortHeight` | 20 / 20 |
| `CompoundLayoutConfig` | `defaultContainerChildHostMetrics` (fallback insets) | 12, 24, 12, 12 |
| `CompoundLayoutEngine` | `borderNodeGap` (space between ports on a side) | 8 |

## Tests

- `test_DensityIndependence` (layout-graph-demo) renders every demo scenario at 1x, 1.5x and 2x and
  checks that every node's bounds, in dp, match 1x. Measured: within 1 dp at 2x and within 5 dp at 1.5x
  (at 1.5x text is measured in whole pixels, a third of a dp each, and lines of text come out up to a
  pixel taller; that adds up through nested containers). The test allows 2 and 6.
- `test_GraphLayoutViewDensity` checks the same for the simple `GraphLayoutView` at 1x and 2x.
- Both tests fail on the code before this change.
- `test_RenderScenarios` writes the renders, including `density-1.5/` and `density-2.0/`:

```sh
./gradlew :layout-graph-demo:jvmTest --tests '*test_RenderScenarios*'
```

The PNGs are written to `.gradle-build/layout-graph-demo/scenario-renders`.

## Known, not changed

`GraphLayoutView` (the simple view) reports the diagram's own size to Compose even when that is larger
or smaller than the space it gets, so Compose centres it. `CompoundGraphLayoutView` had the same
problem and it was fixed in PR #5.
