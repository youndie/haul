---
id: B-02
title: "scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check"
status: wip
priority: P1
size: M
stage: stage-1-skeleton
---

# B-02 — scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check

Nothing else can start before the four modules exist, the settings plugin pins the stack and CI builds the server, the wasm bundle and the documentation gate.

- Not covered: any feature code; the PostgreSQL access layer is chosen here and recorded in research D3.

- AC: CI builds the server, the wasm bundle and the docs check green on an empty feature set.
- Anchors (planned): `settings.gradle.kts`, `shared/build.gradle.kts`, `server/build.gradle.kts`, `composeApp/build.gradle.kts`, `.github/workflows/check.yaml`.
