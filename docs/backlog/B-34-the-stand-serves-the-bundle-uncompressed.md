---
id: B-34
title: "ops: the stand serves the wasm bundle uncompressed"
status: wip
priority: P2
size: S
stage: stage-9-ship
blocked_by: [B-27]
---

# B-34 — ops: the stand serves the wasm bundle uncompressed

B-28 measured the first load (research D9, «Measured in B-28»): served with brotli the first frame
arrives 19.5 s in on Slow 4G, served raw 62 s — three times longer. Since B-27 the server serves the
bundle with `staticFiles` and no `Compression` plugin, and the chart's Traefik route has no `compress`
middleware, so the stand would hand every visitor the raw 11.7 MB. Whether the cluster's Traefik
compresses on its own was not checked.

Make the stand send the bundle compressed — precompressed `.br`/`.gz` files served by the server
(compressing 8 MB of wasm per request is CPU the server does not need to spend) or compression at the
ingress, whichever the measurement favours — and stop publishing `composeApp.js.map` (1.45 MB) with
the distribution. Long-lived `Cache-Control` on the hashed `.wasm` files belongs here too.

- AC: `scripts/image-check.sh` asserts that the module answers with `Content-Encoding: br` (or `gzip`)
  to a browser's `Accept-Encoding`, and that the source map is not served; the B-28 script re-run
  against the image shows the compressed first-load numbers.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`, `server/build.gradle.kts`, `scripts/image-check.sh`.
