---
id: B-145
title: "fix: a facet ticked in the filter sheet that fails says so over the sheet"
status: open
priority: P2
size: S
stage: stage-12-qa-run
---

# B-145 — fix: a facet ticked in the filter sheet that fails says so over the sheet

Found by the v0.6.0 run, TC-ERR-03 at 390 ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B on the stand and D locally, each independently). On a phone a
facet is only reached through «Filters», whose sheet covers the screen. Ticked offline, `GET /ui/parts/…?brand=Sony`
fails, the tick falls back («0 applied», «Show 15 items») and «The page didn’t update» with «Retry» is drawn beneath
the sheet — four frames over 2.4 s show only the sheet. Its «Retry» sits where the sheet's «Show 15 items» is, so the
press meant for it closes the sheet; only then is the notice seen. 1440 behaves as the case expects.

- **A notice the shopper must act on is drawn above whatever is open** (the sheet, the phone menu, a dialog — B-126's
  `OverlayHost` layers), or the sheet itself says the tick did not go through with its own Retry; pick the one the
  shell's message frame (B-75, B-92, B-136) already supports and write down why.

- AC: at 390, offline, a tick in the sheet shows «The page didn’t update» with «Retry» while the sheet is open; Retry
  online applies the tick in place; 1440 unchanged; a client test fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Messages.kt`.
