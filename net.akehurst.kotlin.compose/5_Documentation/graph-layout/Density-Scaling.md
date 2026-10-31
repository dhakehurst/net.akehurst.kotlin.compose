# Layout spacing does not scale with screen density

**Status:** Known limitation. Deferred for now (David, 2026-10-31).
**Found:** 2026-10-30, while fixing edges that didn't meet nodes on a laptop screen (PR #5).

## Problem

The layout engine works in pixels. Node content is Compose content, measured in pixels after
density is applied, so it grows with screen density. The spacing and default sizes below are plain
numbers that the engine uses as pixels, so they do not grow. At 2x density, nodes are twice as big
but the gaps between them are the same, and diagrams look tighter than at 1x. Example 11 in the demo
shows it most.

Edges still meet their nodes at any density. This is only about spacing.

## Values that do not scale

| Where | Value | Default |
|---|---|---|
| `SugiyamaChildLayout` (`ChildLayoutAlgorithm.kt`) | `layerSpacing`, `nodeSpacing` | 100 / 100 (`DEFAULT`); the presets `HIERARCHY_BIASED`, `ORTHOGONAL_BIASED`, `COMPACT` and `CHANNEL_BIASED` use 70 to 130 |
| `SugiyamaLayout` (`SugiyamaLayout.kt`) | `layerSpacing`, `nodeSpacing` | 100 / 100 |
| `OrthogonalEdgeRouter` (`OrthogonalEdgeRouter.kt`) | `margin`, `edgeSpacing` | 12 / 8 |
| `CompoundLayoutConfig` (`CompoundLayoutEngine.kt`) | `defaultNodeWidth`, `defaultNodeHeight` | 100 / 56 |
| `CompoundLayoutConfig` | `defaultPortWidth`, `defaultPortHeight` | 20 / 20 |
| `CompoundLayoutConfig` | `defaultContainerChildHostMetrics` (fallback insets) | 12, 24, 12, 12 |
| `CompoundLayoutEngine` | `borderNodeGap` (space between ports on a side) | 8 |

Not checked yet: whether caller-given width/height hints and `PaddingHint` are treated as pixels too.

## How to see it

`test_RenderScenarios` in `layout-graph-demo` renders every scenario at 1x, 1.5x and 2x density:

```sh
./gradlew :layout-graph-demo:jvmTest --tests '*test_RenderScenarios*'
```

The PNGs are written to `.gradle-build/layout-graph-demo/scenario-renders`.

## Options for later (none chosen)

- Treat all these values as dp and multiply by `LocalDensity` in `CompoundGraphLayoutView` before
  calling the engine.
- Change the engine's API to take `Dp` values.
- Leave the values in pixels and let callers scale them.
