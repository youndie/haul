---
id: B-119
title: "fix: a search's title, chips and picker say what the grid shows"
status: open
priority: P2
size: S/M
stage: stage-12-qa-run
epic: feature-search
---

# B-119 — fix: a search's title, chips and picker say what the grid shows

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)) on the stand:

- the title counts every result while a chip scopes the grid: «30 results» with «Running shoes 15» chosen,
  «600 results» with one «Classics 15» chosen;
- chips repeat leaf names: `classic` draws ten «Classics 15» chips, `everyday` «Limited 5», «Limited 4», «New in
  4», «New in 4» — leaves of different parents (`party-premium-limited`, `beauty-premium-limited`, …) that read
  the same;
- the picker reads «All categories» while a subcategory chip scopes the results.

- **What the page names is what the grid holds.** The title counts the chosen chip's results (and says of
  how many, if that is worth keeping); a chip whose leaf name repeats carries its parent («Party · Limited»);
  the picker names the chip's category. Rejected: hiding repeated chips — they are different results.

- AC: `classic` with one chip chosen titles the chip's count; no two chips on one search read the same; the
  picker names the scope a chip chose. Server tests on the search tree hold all three.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt`.
