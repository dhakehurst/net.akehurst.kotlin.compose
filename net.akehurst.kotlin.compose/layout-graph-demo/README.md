# layout-graph-demo

Step 0 demo scaffold for compound graph layout review.

## Included in this slice

- JVM Compose Desktop entrypoint.
- Scenario picker with deterministic scenario IDs.
- Debug overlay toggles for bounds, ports, and edge IDs.
- Placeholder/static canvas rendering path (no edge routing yet).

## Run

From the repo root:

```zsh
./gradlew :layout-graph-demo:run
```

## Notes

- This is a static baseline to validate review workflow before layout integration.
- Later steps will add compound-aware layout and routed edges.

