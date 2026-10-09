---
id: B-64
title: "client: back after a filter draws the address's page with kompot's own reset"
status: done
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

## Findings (2026-10-09)

- **Removed**: `arrivals` and the store remembered on it (`Storefront.kt`). The screen has one override store again
  (`remember { KompotNodeOverrides() }` under `key(screen)`), which kompot 0.40.0.215 starts over on every load the
  loader completes, an equal tree included: the loader provides the load's number around the tree it draws, and the
  `KompotScreen` inside `Shown` — under `KompotRealtimeProvider` too — takes it as the tree's arrival.
- **Haul draws a tree without the loader in one place**: `Shown`'s refresh fetches the screen itself and sets the tree
  (every answer still `refresh` — the cart's own lines, a dialog's `close` + `refresh`, B-63's list — and a sign-in
  that returns to the same page). It now counts the trees it brought (`refreshed`) and passes the count as
  `arrival`, so a refresh that brings the page drawn drops what an `update` or the order's channel wrote over it. Without it — before B-64 too — an equal tree from a refresh kept them;
  `mutableStateOf` does not even recompose for an equal tree. The dialog a `present` shows writes nothing into a store
  and takes no `arrival`.
- **Mutation C2 is the code now, and green**: B-63's C2 was «one override store for the screen», red 2 on 0.40.0.213
  (`InPlaceAnswersTest`'s back after a tick, `DrawnActionsTest`'s «a page not opened from the filter sheet leaves it closed»). On 0.40.0.215 that
  store is what the shell has, and the full suite is green: the back after a tick loads the page before it, which is
  an arrival, so the tick's `update` goes whatever the tree. **Control**: the same code on 0.40.0.213 (without
  `arrival`, which that version lacks) — the same 2 red again, plus the new refresh test: 3 of 188. **Mutation**: on
  0.40.0.215 without `arrival = refreshed` — 1 red, the new refresh test (`InPlaceAnswersTest`, «7» stays after the
  refreshed page arrived).
- **Tests**: B-63's back-after-tick test unchanged but for its KDoc, which no longer says kompot takes the tree for
  no news; new in `InPlaceAnswersTest`: a refresh that brings the page drawn drops what an update wrote over it.
- **Where it ran** (the Linux box, `MemoryMax=8G`, two runs): `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` — `:server:test` 385, `:composeApp:desktopTest` 188, `viddikVerify`, ktlint, all green;
  `scripts/e2e.sh` against the branch's image — `WholePathTest` green. The Mac: `ktlintFormat`, `make check`,
  `make docs-against BASE=origin/main`.
