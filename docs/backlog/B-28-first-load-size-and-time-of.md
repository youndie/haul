---
id: B-28
title: "measure: first-load size and time of the wasm bundle"
status: open
priority: P2
size: S
stage: stage-9-ship
blocked_by: [B-07]
---

# B-28 — measure: first-load size and time of the wasm bundle

A Compose canvas page's first load is dominated by the runtime (research D9); the number is measured, not assumed.

- Not covered: optimising it.

- AC: the numbers are in the research document with how they were taken.
- Anchors (planned): `composeApp/build.gradle.kts`.
