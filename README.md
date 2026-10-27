[![Maven Central](https://img.shields.io/maven-central/v/net.akehurst.kotlin.compose/code-editor.svg)](https://search.maven.org/artifact/net.akehurst.kotlin.compose/code-editor)
![Kotlin Version](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdhakehurst%2Fnet.akehurst.kotlin.compose%2Frefs%2Fheads%2Fmain%2Fnet.akehurst.kotlin.compose%2Fgradle%2Flibs.versions.toml&search=kotlin%20%3D%20%22(%5B%5E%22%5D%2B)%22&replace=%241&label=Kotlin&color=green&link=https%3A%2F%2Fkotlinlang.org%2F)


A selection of Kotlin Multiplatform Compose components, for JVM (desktop), JS and Wasm.

## Modules

| Module | Contents |
|---|---|
| `code-editor-api` | API for the code editor and viewer: editor interfaces, segment styles, margin items, autocomplete, find/replace state, text decorations |
| `code-editor` | DSL code editor and read-only code viewer, with autocomplete and find/replace |
| `components` | Splitter, Table, Tree, Vertical Tabs, Combo, Titled Box, Text Input dialog, alert dialog with issues, mutable state-flow holders |
| `layout-multipane` | Multipane layout: tabbed panes in nested splits, with drag-and-drop between panes |
| `layout-graph` | Graph layout: Sugiyama layout and compound (nested) graph layout with a pluggable child layout algorithm |

These modules are demos only and are not published: `code-editor-demo-js` and `layout-graph-demo`.

## Building

The Gradle project is in the `net.akehurst.kotlin.compose/` folder, so run Gradle from there.

It depends on `net.akehurst.kotlinx` (`nak-kotlinx` in `gradle/libs.versions.toml`). When that is a `-SNAPSHOT` version, build it from
[dhakehurst/net.akehurst.kotlinx](https://github.com/dhakehurst/net.akehurst.kotlinx) with `publishToMavenLocal` first.

```sh
cd net.akehurst.kotlin.compose
./gradlew assemble
./gradlew jvmTest      # JVM tests (use jvmTest, not test)
```

## Demos

The JVM window demos live in each module's `src/jvmDemo` source set and run with a `run<Name>Demo` task:

```sh
./gradlew :code-editor:runCodeEditorDemo
./gradlew :components:runTableViewDemo
./gradlew :components:runTreeViewDemo
./gradlew :layout-multipane:runMultiPaneLayoutDemo
./gradlew :layout-graph-demo:run                     # compound graph layout scenarios
./gradlew :code-editor-demo-js:jsBrowserDevelopmentRun   # code editor in the browser, then open the printed URL
```

## Documentation

Design notes for the graph layout are in `net.akehurst.kotlin.compose/5_Documentation/graph-layout`.
