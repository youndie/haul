---
id: B-28
title: "measure: first-load size and time of the wasm bundle"
status: wip
priority: P2
size: S
stage: stage-9-ship
blocked_by: [B-07]
---

# B-28 — measure: first-load size and time of the wasm bundle

A Compose canvas page's first load is dominated by the runtime (research D9); the number is measured, not assumed.

- Not covered: optimising it.

- AC: the numbers are in the research document with how they were taken.
- Anchors (planned): `composeApp/build.gradle.kts`.

## Protocol, fixed before the first run (2026-10-08)

The acceptance criteria are this measurement's pre-registration; what follows is written before any
number was taken and is not edited after the first raw file exists.

- **What is measured.** The production bundle, `./gradlew :composeApp:wasmJsBrowserDistribution`
  (`composeApp/build/dist/wasmJs/productionExecutable`), served as static files. On `main` the app's
  root draws the theme with an empty body and calls no server (`App.kt`), so the page needs no
  server and no PostgreSQL: what is measured is the shell plus the bundle. Because nothing reaches
  the renderers from `main()`, the compiler's dead-code elimination may leave them out, and the
  "app" share would then be a lower bound. A second arm, **wired**, builds a copy of the tree in which
  the root renders the Home body of the screenshot fixtures (`home_content.json`) through
  `haulRegistry()`, so every renderer is reachable — the bundle the storefront will ship once it
  draws screens. The arm is a measurement copy only; nothing of it is committed to `composeApp`.
- **Sizes**, per file the page requests and per bucket (the app's `.wasm`, skiko's `.wasm`, the
  `.mjs`/`.js` glue, fonts, other resources): raw, gzip -9, brotli q11. Which files the page
  requests is read from the browser's network log, not guessed from the directory.
- **Time**, in headless Chromium (Playwright's build on the WSL box, WebGL through SwiftShader), a
  fresh browser process and profile per run, cache disabled, the files served compressed by what
  the browser negotiates (brotli or gzip, recorded per response) by a local static server. Three
  network profiles through CDP `Network.emulateNetworkConditions`, DevTools' own numbers: **none**
  (loopback), **Fast 4G** (165 ms, 9 Mbit/s × 0.9 down), **Slow 4G** (562.5 ms, 1.6 Mbit/s × 0.9
  down). Seven rounds per profile and arm, profiles interleaved within a round, round 1 discarded;
  median, min and max of the six counted rounds reported.
- **The probe for «first frame».** A script injected before any page script wraps the WebGL
  (1 and 2) `clear` and `draw*` calls; the first such call is the first time Compose draws, and the
  first `requestAnimationFrame` after it is the frame that carries it to the screen (an upper bound
  within one vsync). **First frame** = that rAF's time since navigation start. **Settled** = the
  same for the last GL frame before the page has been quiet (no request in flight, no GL call) for
  3 s. Beside them, from Resource Timing: when the last byte of the bundle arrived, so the time
  splits into transfer and compile + instantiate + first composition.
- **Controls, in the same log.** (1) The probe must not fire when every `.wasm` is blocked
  (`Network.setBlockedURLs`) — a probe that reports a frame there measures nothing, and the run is
  void. (2) The known order: none < Fast 4G < Slow 4G in first frame, and under Slow 4G first
  frame ≥ the compressed bytes ÷ 180 000 B/s; a violation voids the profile's numbers.
- **The stand**, recorded per run: `nproc`, the load average and `MemAvailable`, because the box is
  shared with other builds; quiet moments are preferred and the document says the box was shared.
- **Not covered**: making it smaller or faster — levers seen on the way go to the findings.

## Done (2026-10-08)

- Measured and written into research, D9, «Measured in B-28»: the method, the stand, the sizes per
  file and bucket (raw, gzip, brotli), the time per profile with its spread, the raw values per run and
  what they say about D9 (confirmed: skiko's wasm is 65.8 % of the wired first load, the runtime at
  least 78 %; on a throttled link the wait is the bundle crossing it — first frame 5.0 s on Fast 4G,
  22.3 s on Slow 4G, 0.6–1.0 s on loopback).
- The harness: `scripts/measure-first-load.sh` builds the two arms and calls
  `scripts/measure-first-load.py`, which serves the distribution, drives headless Chromium over CDP
  and writes the raw runs (JSON lines), a summary per arm and a screenshot per run to `$OUT`. Standard
  library only; brotli through the `libbrotlienc` that ships with Chromium.
- Ran on the shared Linux build machine with other agents' builds on it (load average 3.7–11.0); the
  throttled numbers are bounded by the link, the loopback ones by the CPU and are no better than a
  factor of two.
- Anchors: `scripts/measure-first-load.sh`, `scripts/measure-first-load.py`,
  `docs/research/research-architecture.md`.

## Findings (2026-10-08)

- **`main` ships no renderers.** The root draws the theme with an empty body (`App.kt`), so nothing
  reaches `haulRegistry()` from `main()` and dead-code elimination drops every renderer: main's app
  `.wasm` is 1,597 KiB against 3,348 KiB once the Home page is drawn. Measuring only `main` would have
  confirmed D9 for an app that draws nothing; the **wired** arm (a throwaway copy whose root renders the
  Home fixture body) is the number to quote until the root draws screens, and the measurement should be
  repeated then (the script's `ARMS=main` alone will do).
- **A pre-registered control failed, for a reason.** «Slow 4G first frame ≥ all its bytes ÷
  bandwidth» fails on main (19,506 < 19,862 ms): the fonts are fetched after the wasm runs and the
  first frame does not wait for them. The floor over the non-font bytes passes in both arms and is
  reported beside it; the pre-registered one is reported as it failed.
- **The first frame draws text before the fonts arrive**, in a fallback face carried by the bundle
  (the fonts-blocked control drew the whole Home page with no other font requested); the canvas faces
  swap in 0.7 s (loopback) to 3.8 s (Slow 4G) later. Screenshot goldens never see that frame.
- **Levers, not taken (optimising is out of scope):**
  - *Compression where the bundle is served.* Nothing in the repository serves the bundle yet (the
    server does not; the stand is B-27). Uncompressed the first load is 13.5 MB, gzip 4.9 MB, brotli
    3.9 MB (wired); brotli saves 21 % over gzip. `application/wasm` is not in common default
    compression lists (nginx's `gzip_types`), so it has to be named.
  - *The fonts.* `archivo.ttf` is the whole variable font, 643 KiB raw / 192 KiB brotli; a Latin subset
    (the storefront is English and USD) would cut most of it. Whether Compose's web font loading takes
    WOFF2 was not checked. Preloading the fonts from `index.html` would remove the face swap but, on a
    link that is the bottleneck, delay the first frame by their 432 KiB (≈ 2.4 s on Slow 4G) — a trade.
  - *Repeat visits.* The two `.wasm` files are content-hashed (`composeApp.js`, `index.html` and the
    fonts are not); long-lived `Cache-Control` on them would make a second visit skip skiko's 2.5 MB.
    Only the first load was measured.
  - *The distribution carries `composeApp.js.map`* (1,451 KiB), never requested by the page; a server
    that publishes the directory as is publishes it too.
