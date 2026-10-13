---
id: B-150
title: "fix: the display face is there at the store's first frame"
status: done
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

## Done (2026-10-13)

- **The store's first frame is drawn in its faces.** `index.html` preloads the four `.ttf` as `fetch` with CORS — what
  Compose's resources send, so their read takes the preloaded answer (each file is fetched once, seen in every
  measured run) — and `Main.kt` mounts the store only once `loadHaulFonts`
  (`composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/theme/PreloadedFonts.kt`) has read their bytes and built
  the faces from them; `App` takes them (`fonts`). The families, weights and optical sizes are built in one place for
  both ways of loading (`haulFonts`, `HaulFontResources.kt`). A file that cannot be read mounts the store as before:
  Compose's own loading, the fallback until it lands (the fonts-blocked control draws so).
- **Measured** as pre-registered, research D9 «Measured in B-150», two campaigns. First frame, median, before → after:
  Fast 4G 5,179 → 5,388 and 4,837 → 5,341 ms; Slow 4G 24,780 → 26,408 and 24,341 → 26,422 ms. Before drew that frame
  in a fallback in every run and its faces at 5,841 / 5,532 ms (Fast 4G) and 28,096 / 27,563 ms (Slow 4G); after draws
  its first frame in them. **The budget holds**: after's first frame is earlier than before's faces frame (−0.45 /
  −0.19 s on Fast 4G, −1.7 / −1.1 s on Slow 4G), and later than before's first frame by +0.21 / +0.50 s and +1.6 /
  +2.1 s — under the fonts' 432 KiB brotli ÷ bandwidth + 5 % (0.68–0.70 s and 3.67–3.69 s). All controls passed, no
  void run; B-28's all-bytes floor, which failed because the first frame did not wait for the fonts, now passes.
- **Seen**: `scripts/first-frame-faces.py`, three cold loads per image under Fast 4G, a screenshot every 0.15 s: after
  drew its first store shot in the faces in 3 of 3 loads and never a fallback; before (the control) drew its first in
  a fallback in 3 of 3, for 1.5–1.8 s.
- Tests: `StaticFrameTest` «the page preloads every bundled font the way the entry point reads it» (red with the
  preloads as `font`), «the page asks for nothing but the bundle's loader and the fonts' preloads». QA: TC-ERR-02 says
  what the first frame looks like and names the new test and script.

## Findings (2026-10-13)

- **The `/favicon.ico` 404 (B-152's line) voids no measured run**: the harness counts a failed request
  (`Network.loadingFailed`), and a 404 is an answer. Nothing worked around.
- **The screenshots cannot be taken every 0.15 s throughout**: while the page compiles the module and composes its
  first frame, `Page.captureScreenshot` waits for the next frame, so the interval stretches to 0.3–0.6 s there — when
  no new frame is drawn either. Recorded per shot (the file name carries its time).
- **The preloads take the link before the bundle** (high priority, found with the page): the fonts land at 1.0 s
  (Fast 4G) and 4.4 s (Slow 4G), skiko's module 1.8 s later than before on Slow 4G. A lower `fetchpriority` might let
  the module compile while the fonts cross — a few hundred milliseconds at most; not measured, not taken.
- The wired arm of B-28 (`MeasureWired.kt`, generated by `measure-first-load.sh`) still loads the fonts through
  `rememberHaulFonts`, so it measures the old first frame; it only serves size comparisons now.
