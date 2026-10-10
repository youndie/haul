---
id: B-80
title: "client: the first load shows something before the app starts"
status: done
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

## Done (2026-10-10)

- `index.html` draws the header's shape — strip, logo's place and dot, «Catalog», the search field, the shortcuts,
  the cart, the category row — and the sweeping progress line under it, inline HTML and CSS at the header's
  measures (185 px at 1440, 206 below 768), shapes only: no words, so no font is needed. No new request; 4,726
  bytes, 1,175 brotli. `role="progressbar"` with a label, and no motion under `prefers-reduced-motion`.
- The app replaces it: `Main.kt` puts the frame back over Compose's mount (which replaces the body's children)
  and removes it from a `drawWithContent` around the app, in the animation frame of Compose's first picture.
  `STATIC_FRAME_ID` (`shell/StaticFrame.kt`) is the id both sides name.
- Measured, before and after, on images that differ only in the bundle (research D9, «Measured in B-80»):
  first contentful paint 4.8 s → 0.3 s on Fast 4G, 24.0 s → 0.7 s on Slow 4G, 0.67 s → 0.33 s on loopback;
  Compose's first frame unchanged (±0.1 %); the frame removed in the animation frame of Compose's first GL call
  in every run the probe saw it (17 of 18), and a page that removes it on a timer fails that check.
- CSP and headers: none restricts the page — `WebBundle.kt` sets cache and encoding headers only, the chart's
  `IngressRoute` only the HTTPS redirect; the frame uses inline styles and no inline script.
- `StaticFrameTest` holds the page to the id, the frame before the loader's script, the loader as its one request
  and the size under a first TCP window; `desktopTest` now has the page as an input, without which a changed page
  left the test up to date.
- The harness: `scripts/measure-first-load.py` reports the first contentful paint and the frame's swap, and
  `--stop-after` (`STOP_AFTER` in the shell script) ends a run after the first frame for a page that never goes
  quiet.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/StaticFrame.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/shell/StaticFrameTest.kt`,
  `scripts/measure-first-load.py`.

## Findings (2026-10-10)

- **Compose replaces the body's children when it mounts.** `ComposeViewport(document.body)` (Compose
  Multiplatform 1.12.1) leaves only its own host `<div>` in the body — the frame and the loader's `<script>` go
  with it — and its canvas sits in that div's shadow root. A frame left where it was would vanish at the mount,
  a composition before the first picture: on loopback a 180 ms blank between the two. Hence the put-back.
- **The probe misses a frame that is never painted.** On loopback the bundle can run and draw before the
  browser's second animation frame; the probe only follows the frame once a tick has seen it, so one counted run
  reports no frame at all (its first contentful paint is the canvas). Stamping the frame in the `remove` wrapper
  too would close it; the numbers were taken without that.
- **B-28's `wired` arm no longer builds**: its patch asserts `{ App() }` in `Main.kt`, which has taken arguments
  since the root started drawing screens. The `image` arm measures the storefront as it ships, and is what this
  item used; `main` served as static files now draws the error page (no server) and never goes quiet, so its
  «settled» would time out without `--stop-after`.
- **One frame for every address**: a cold `/checkout` shows the storefront's header shape, then the checkout's
  own header. Not in the item; the page cannot know the address's header without script.
