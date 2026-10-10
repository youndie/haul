---
id: B-80
title: "client: the first load shows something before the app starts"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-80 — client: the first load shows something before the app starts

Opening any address cold shows a blank page for a few seconds while the wasm bundle downloads and starts; the
skeletons appear only once the app runs. On a slow connection the shopper sees nothing at all.

- AC: `index.html` draws a static frame (the header's shape and a progress mark) that the app replaces; measured
  on the stand: time to first paint before and after.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`.

## Protocol, fixed before the first counted run (2026-10-10)

- **What is measured.** Two images from one server build that differ only in their `web/`: **before**, the bundle
  of `main` at `d07ddf3` (B-72 and B-79 merged); **after**, this branch's. Each started beside a seeded
  PostgreSQL and measured through B-28's harness with B-34's `image` arm, so every byte is what the server sends:
  `ARMS=image STOP_AFTER=3 IMAGE=haul/server:b80-<arm> scripts/measure-first-load.sh 7`. Seven rounds per
  profile (none, Fast 4G, Slow 4G), interleaved, round 1 discarded; median, min and max of six.
- **«Time to first paint»** is the browser's own first contentful paint (Paint Timing): a page whose only paint
  is its background colour reports none, so before it is Compose's first canvas frame, after it is the static
  frame. Beside it, B-28's first frame (the first animation frame after Compose's first GL call) in both arms.
  «Settled» is not measured: the storefront drawn from a live server never goes quiet (the deals count down), so
  each run ends 3 s after the first frame (`--stop-after`, new here).
- **Expected.** After, the first contentful paint arrives with the HTML — under a second on every profile; before,
  with the first frame — seconds on the throttled ones. The app's first frame does not move: after's median within
  5 % of before's on Fast 4G and Slow 4G (the page grows by about a kilobyte compressed).
- **The swap, by a probe.** The probe counts animation frames and stamps the tick of Compose's first GL call and
  of the frame element's `remove()`: «no flash of both, no gap» is the two in the same tick and the frame never
  hidden before it, in every counted run of the after arm.
- **Controls.** (1) With every `.wasm` blocked the probe sees no GL call (both arms), and after's frame is painted
  and stays. (2) The profiles in their known order. (3) The swap check can fail: a copy of the after bundle whose
  page removes the frame on a timer, 300 ms after the HTML is parsed, must fail it. (4) B-28's floor over the
  bundle's non-font bytes on Slow 4G.
