---
id: B-90
title: "client + server: the small leftovers of the review loop"
status: wip
priority: P3
size: S
stage: stage-10-review
---

# B-90 — client + server: the small leftovers of the review loop

Recorded in the findings of B-69, B-72, B-77 and B-80, each too small for an item of its own:
- before the first tree, the shell's header has no search scopes, so the picker is drawn but opens nothing (B-72);
- a search scope with no matches shows its chip at 0 over an empty grid (B-72);
- «Show N more» has no cap on how many pages one grid accumulates (B-77; at most 192 cards on the seed);
- a slider thumb at the end of the track can be grabbed only by its inner half (B-69);
- a cold `/checkout` shows the storefront header's shape first, then checkout's own (B-80);
- the B-28 measurement's `wired` arm no longer builds — its patch expects `{ App() }` in `Main.kt` (B-80).

- AC: each line fixed or answered in the findings with why not; a test per fix.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`,
  `composeApp/src/wasmJsMain/resources/index.html`.
