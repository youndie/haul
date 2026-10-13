---
id: B-142
title: "fix: a search's chips row reaches every scope on a desktop"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-142 — fix: a search's chips row reaches every scope on a desktop

Found by the v0.6.0 run, TC-SRCH-11 ([run](../qa/runs/2026-10-13-v0.6.0.md), walker A on the stand). From 1280 up a search's category chips are one line
clipped at the sort: `classic` has 111 chips and shows «All 600» … «Books · Classics 1» with its count cut under the
sort at 1440. The row does not move for a horizontal wheel, a shift-wheel, a mouse drag or 25 Tabs from the field; at
1024 and 390 the same input scrolls it (B-82 decided «a search's kinds scrolling sideways» there). So on a desktop the
case's «Party · Classics» has nothing to press, and a chosen chip past the line
(`/search?q=classic&category=party-essentials-classics`) is not visible at all.

- **Every chip is reachable and the chosen one is in view.** The desktop row scrolls sideways like the narrower bands
  (wheel, shift-wheel, drag, keyboard focus), or wraps — whichever the code's own layout rules make cheaper; the chosen
  chip is scrolled into view when the page arrives. Rejected: hiding chips past the line — they are results.

- AC: at 1280 and 1440 `classic` reaches «Party · Classics» by wheel, drag and Tab, pressing it scopes the results, and
  arriving at a chip past the line shows it chosen; 1024 and 390 unchanged; a client test per band.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/SearchViews.kt`.
