# Ladder layout

Package `net.akehurst.kotlin.compose.layout.ladder`, in the `layout-graph` module.

A ladder diagram draws things shaped like UML sequence diagrams, which do not lay out well as a node/edge graph.
The names are deliberately not UML names:

| Ladder            | Typical UML meaning   |
|-------------------|-----------------------|
| `LadderRail`      | lifeline              |
| `LadderRung`      | message               |
| `LadderBar`       | execution bar         |
| `LadderGroup`     | combined fragment     |
| `LadderGroupPart` | operand               |

## Model

```kotlin
LadderModel(
    rails = listOf(LadderRail("a"), LadderRail("b")),   // left to right
    events = listOf(                                    // top to bottom
        LadderRung("call", from = "a", to = "b"),       // from == to is a self-rung
        LadderBarStart("busy", rail = "b"),             // starts on the line of the rung before it
        LadderGroup("g", rails = listOf("a", "b"), parts = listOf(
            LadderGroupPart("p1", events = listOf(/* ... */)),
            LadderGroupPart("p2", events = listOf(/* ... */)),
        )),
        LadderRung("reply", from = "b", to = "a"),
        LadderBarEnd("busy"),                           // ends on the line of the rung before it
    )
)
```

- Bars on one rail may nest; a nested bar is moved a little to the right. Rungs end on the edge of the innermost bar at their height.
- A group spans from the leftmost to the rightmost of its rails. Groups may nest; an outer group always reaches past the groups inside it.
- `LadderModel.validate()` reports unknown rails, duplicate ids, bars that end without a start or never end, and groups without rails or parts. The layout still draws what it can.

## Content

`LadderLayoutState(id, model)` holds the content by id, like `GraphLayoutCompoundGraphState`:

- `addRailHeader(railId) { ... }`: the header box at the top of the rail (the caller draws the box).
- `addRungContent(rungId, GraphLayoutEdgeContent(...))`: the same type as graph edges, so the same end symbols, texts at START/MIDDLE/END, line colour, width and dash work unchanged. Texts are drawn above the line.
- `addGroupLabel(groupId) { ... }`: the label inside the group's tab ("alt", "loop"). The view draws the tab outline.
- `addGroupPartLabel(partId) { ... }`: optional label at the top left of a part (for example a condition).

Missing content is shown as a red placeholder, except part labels.

## View

```kotlin
LadderLayoutView(state, viewState, updateView, modifier, colors = LadderLayoutColors(), style = LadderLayoutStyle(), config = LadderLayoutConfig(), initialView = InitialViewPlacement.AS_GIVEN)
```

Pan and zoom work as in `CompoundGraphLayoutView`, with the same `GraphLayoutViewState` and `InitialViewPlacement`.
`LadderLayoutConfig` holds the spacing, `LadderLayoutStyle` the line widths and dashes the view draws itself, and
`LadderLayoutColors` their colours. All lengths are in dp.

The engine, `LadderLayoutEngine`, is UI independent: it takes the model and the measured sizes of the caller's content (in dp)
and returns positions for everything.

## Not yet done

Gates, lost and found messages, timing, collapsing groups.
