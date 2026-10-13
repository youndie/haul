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

## Protocol, fixed before the first measured run (2026-10-13)

Written before any number of the comparison below was taken and not edited after (one smoke run of this branch's
bundle as static files, B-28's `main` arm, was taken first — only to see that each font is fetched once).

- **What changes.** `index.html` preloads the four `.ttf` (`<link rel="preload" as="fetch" crossorigin>`, the
  request Compose's resources make, so the read takes the preloaded answer), and the entry point reads their bytes
  before it mounts the storefront (`loadHaulFonts`), so the store's first composition is laid out in its faces. A
  file that cannot be read mounts the store as before (Compose's own loading, the fallback until it lands).
- **Arms.** B-34's `image` arm of B-28's harness (`scripts/measure-first-load.sh`, `ARMS=image`), each image beside
  a seeded PostgreSQL so the page draws Home from the server, a guest at 1440 × 900: **before** — the image of
  `origin/main` at `0802a3f4` (`haul/server:b150-before`); **after** — this branch (`haul/server:b150`). Seven rounds,
  round 1 discarded, median (min–max) of six; profiles none, Fast 4G and Slow 4G, interleaved; `STOP_AFTER=8` (the
  storefront never goes quiet; on Slow 4G the fonts landed 3.2 s after the first frame in B-78).
- **Numbers.** The first frame (Compose's first GL frame, which removes the static frame); the fonts' last byte and
  the first frame drawn after it, «faces frame» (`measure-first-load.py`, new in this item); the transferred bytes.
- **The budget (B-28's).** Passes when, in the medians on Fast 4G and on Slow 4G, (1) after's first frame is no later
  than before's faces frame + 5 % — the shopper sees the store in its faces no later than before; and (2) after's
  first frame is later than before's by no more than the fonts' brotli bytes ÷ the profile's bandwidth + 5 % of
  before's first frame (B-80's tolerance) — the frame pays at most the fonts' transfer. Either failing makes the
  item a `question` with the numbers.
- **The faces at the first frame.** `scripts/first-frame-faces.py` (new): three cold loads per image under Fast 4G
  (fresh profile, cache off), a screenshot every 0.15 s; the logo's crop of the first shot taken after the static
  frame's removal is compared with the last shot's. After must draw its first store shot in the faces in every
  load; before is the control and must fail.
- **Controls** as in B-28/B-34 (blocked `.wasm`, known order, the floors); a run with a failed request is void —
  if `/favicon.ico`'s 404 (B-152's line) voids runs, it is noted and worked around in the measurement only.
