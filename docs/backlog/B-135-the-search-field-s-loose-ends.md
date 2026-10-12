---
id: B-135
title: "fix: the search field's loose ends — one letter, Escape, the empty page's notice"
status: open
priority: P3
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-135 — fix: the search field's loose ends — one letter, Escape, the empty page's notice

Found outside the catalogue by its v0.5.1 run ([run](../qa/runs/2026-10-12-v0.5.1.md)):

- Typing one character sends `GET /ui/search/suggest?q=a`, which answers `400 query_too_short` and logs a console
  error each time. The client knows the minimum: under two characters it asks nothing (or recall, B-124, for an empty
  field).
- Escape does not close the suggest panel, and the next press outside it is swallowed (it only closes the panel).
  Escape closes it, as it closes the menus (B-75); a press outside closes it and still reaches what it pressed — the
  panel is a suggestion over the page, not a menu whose scrim B-126 made absorb the press.
- `/search` with nothing typed draws «Search needs at least two characters.» above B-132's «Start typing» — two
  notices for one state; only B-132's stays.

- AC: each of the three behaves as its line says; a client test per line.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/SearchViews.kt`.
