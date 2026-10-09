---
id: B-64
title: "client: back after a filter draws the address's page with kompot's own reset"
status: wip
priority: P2
size: S
stage: stage-9-ship
blocked_by: [B-63]
---

# B-64 — client: back after a filter draws the address's page with kompot's own reset

B-63 found that kompot kept an `update`'s overrides when the tree that arrived was equal to the one the
screen started from — back after a facet tick drew the tick's results at the old address — and worked
around it by giving the override store a new instance on every page that arrives (`arrivals` in
`Storefront.kt`). kompot 0.40.0.215 (kompot B-84) fixes it: a tree resets the overrides when it arrives,
not when it differs; `KompotScreenLoader` counts its loads, and a screen drawn without it takes `arrival`.

- Bump kompot to 0.40.0.215 and remove the workaround; the shell relies on the loader's arrivals.
- AC: back after a facet tick draws the address's page (B-63's test, unchanged, green without the
  workaround); the mutation that brings one store per screen back is no longer red — record why; the e2e
  green.
- Anchors: `gradle/libs.versions.toml`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
