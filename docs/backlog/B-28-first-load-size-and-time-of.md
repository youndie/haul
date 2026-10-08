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
