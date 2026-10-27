# layout-graph-demo

Compose Desktop demo of the compound graph layout in `layout-graph`. Not published.

## Features

- Scenario picker with fixed scenario IDs (flat chain, nested and sibling containers, collapsed containers, UML-like and state-like diagrams).
- Live layout using `CompoundGraphLayoutView`.
- Debug overlay toggles for rendered bounds and endpoints, ports, edge IDs and content origins.
- Collapse and expand of containers.

## Run

From the `net.akehurst.kotlin.compose/` folder (the Gradle project root):

```sh
./gradlew :layout-graph-demo:run
```

The scenario tests are in `src/commonTest` and run with `./gradlew :layout-graph-demo:jvmTest`.
