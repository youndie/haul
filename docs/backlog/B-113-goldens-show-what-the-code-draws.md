---
id: B-113
title: "test: every golden shows what the code draws now"
status: open
priority: P3
size: XS
stage: stage-11-qa
---

# B-113 — test: every golden shows what the code draws now

Found by B-84: 24 goldens on main show what the code no longer draws — 18 the old «Search 2.4 million products»
placeholder (B-107), 6 the display face's quotation marks before B-106's cut. `viddikVerify` accepts them because
each difference is under its tolerance (about 0.05 % of a page), and `viddikRecord` without `--force` keeps any
golden that verification accepts, which is how B-107's re-record left them behind.

- AC: a forced re-record on Linux (outside the mutagen replica) changes exactly the stale goldens and nothing else
  — list them and look at each; `CLAUDE.md`'s «Screenshot goldens» section says to record with `--force` after a
  change of copy, glyphs or fonts; `viddikVerify` green.
- Anchors: `composeApp/src/desktopTest/snapshots`, `CLAUDE.md`.
