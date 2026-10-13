---
id: B-150
title: "fix: the display face is there at the store's first frame"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
---

# B-150 — fix: the display face is there at the store's first frame

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers A and B independently). On a cold load (a fresh profile,
cache off, Fast 4G) the store replaces the static frame with the page laid out in a sans fallback — «Haul», «Up to
−70%», «Laptops from $399», «Mugs» — and redraws it in Bodoni Moda 0.4 s to a few seconds later, reflowing the text
(«Laptops from» changes its line breaks): a second visible swap after the frame's. The four `.ttf` are requested
only once the bundle starts (5.2 s), beside `/ui/home`.

- **The first frame the store draws is in its own faces**: the fonts are fetched early (preloaded by the static page)
  and the store's first composition waits for them, within B-28's first-load budget; re-measure B-34's first frame.
- AC: on a cold load under Fast 4G the store's first frame shows the display face (screenshots every 0.15 s); the
  first-frame time measured before and after, in the item.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`, `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`.
