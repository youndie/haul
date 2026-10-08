---
id: B-02
title: "scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check"
status: done
priority: P1
size: M
stage: stage-1-skeleton
---

# B-02 — scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check

Nothing else can start before the four modules exist, the settings plugin pins the stack and CI builds the server, the wasm bundle and the documentation gate.

- Not covered: any feature code; the PostgreSQL access layer is chosen here and recorded in research D3.

- AC: CI builds the server, the wasm bundle and the docs check green on an empty feature set.
- Anchors (planned): `settings.gradle.kts`, `shared/build.gradle.kts`, `server/build.gradle.kts`, `composeApp/build.gradle.kts`, `.github/workflows/check.yaml`.

## Done (2026-10-08)

- Modules `shared` (jvm, wasmJs; kompot-core as `api`), `server` (Ktor CIO, `application`),
  `composeApp` (wasmJs executable + `jvm("desktop")`), `e2e` (JVM tests against `shared`); the
  settings plugin 0.5.0.113, Gradle 9.8.0, toolchain 25.
- CI: a `gradle` job beside the documentation gate runs `./gradlew check :server:installDist
  :composeApp:wasmJsBrowserDistribution`.
- Decided here and written into research: Exposed + `petich-postgres`, the CIO engine (D3), one root
  package with `feature/<name>/` (D3a); the planned anchors of every item were rewritten to it.
- Not done here: zavarnik and probes arrive with the image (B-03); the KSP registry with the first
  components (B-04).
- Anchors: `settings.gradle.kts`, `server/build.gradle.kts`, `composeApp/build.gradle.kts`,
  `.github/workflows/check.yaml`.
