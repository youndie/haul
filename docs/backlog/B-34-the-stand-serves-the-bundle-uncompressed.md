---
id: B-34
title: "ops: the stand serves the wasm bundle uncompressed"
status: done
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
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt`, `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`, `server/build.gradle.kts`, `scripts/image-check.sh`, `scripts/measure-first-load.sh`.

## Done (2026-10-08)

- **Precompressed at build time, served by the server.** `docker/Dockerfile`'s throw-away train stage
  installs `brotli` and writes `<file>.br` (quality 11) and `<file>.gz` (-9 -n) beside every `.wasm`,
  `.js`, `.html`, font and the like in `web/` (33 s, mostly skiko's wasm); `WebBundle.kt` serves `web/`
  with `staticFiles { preCompressed(BROTLI, GZIP) }`, so a request costs a file lookup, not a
  compression. Chosen over Traefik's `compress` middleware because that compresses every response as it
  passes, at the ingress only — a port-forward or a second ingress would get
  raw bytes — and over a Gradle task because brotli has no pure-JVM encoder (brotli4j carries a native
  library per platform) and the build machines cannot be assumed to have the `brotli` CLI (the WSL box
  has none); the image build is the one place an encoder can be installed and thrown away. The chart is unchanged.
- **Headers.** The original `Content-Type` survives (`application/wasm`); `Vary: Accept-Encoding` on
  compressed answers (Ktor's) and on the uncompressed answer of a file that has variants (ours — Ktor
  omits it there); `public, max-age=31536000, immutable` only on the two `.wasm`, whose names are
  webpack's content hash (verified: the skiko module keeps `bfa5198fb2fe683c613a.wasm` across builds
  with equal bytes, the app module's name changed with its bytes); `index.html`, `composeApp.js` and
  the fonts keep their names across releases and get `no-cache`.
- **No source map.** `server/build.gradle.kts` excludes `**/*.map` from the distribution, and the
  server answers 404 to a `.map` even if a hand-built `web/` carries one. B-27's no-fallback rule holds
  (`/ui/nowhere` and `/no-such-file.js` are 404).
- **Tests.** `WebBundleTest` (10 cases): brotli to a browser's Accept-Encoding with its body, gzip to
  gzip-only, the raw file with Vary to no Accept-Encoding, `application/wasm` on every branch, the cache
  headers per name, the map 404, the screen route beside the bundle, the 404s. Mutation-checked on WSL,
  each mutant red: no `preCompressed` (2 failing), no Vary on the raw answer (1), never immutable (1),
  always immutable (1), no map filter (1). `scripts/image-check.sh` asserts the same on the image —
  brotli and gzip on the module, type, Vary, immutable, `no-cache` on the page, map 404, no map in the
  distribution — and fails on an image built without the precompression step (control run on WSL).
- **Measured** (research D9, «Measured in B-34»): the image's first frame on Slow 4G is 19.4 s against
  62.2 s for the same `web/` served raw, 4.0 s against 11.8 s on Fast 4G, 0.4 s against 1.0 s on
  loopback; 3,486 KiB on the wire against 11,708. `scripts/measure-first-load.sh` gained the `image`
  and `image-identity` arms and `measure-first-load.py` a `--url` mode for that.
- Ran on the shared WSL box: `./gradlew check :server:installDist :composeApp:wasmJsBrowserDistribution`,
  `scripts/image-check.sh`, `scripts/chart-check.sh`; `make check` on the Mac.

## Findings (2026-10-08)

- **Ktor's `preCompressed` leaves Vary off the uncompressed answer.** It adds `Vary: Accept-Encoding`
  only to the precompressed response (`PreCompressed.kt`, `preCompressedHeaders`), so a shared cache
  that first stores a `curl`'s raw answer would hand 11.7 MB to every browser after it. `WebBundle.kt`
  adds it through `modify` when the call was not compressed (`isCompressionSuppressed` is set before
  `modify` on the compressed branch, which the test pins).
- **`no-cache` files have no validator.** No `ETag` or `Last-Modified` is sent and the server has no
  `ConditionalHeaders`, so `index.html`, `composeApp.js` and the four fonts (≈ 510 KiB brotli) are
  re-sent in full on every visit instead of answering 304. `etag(ETagProvider.StrongSha256)` plus the
  plugin would do it (the provider hashes each file once and caches it); not taken — the item asked for
  the hashed `.wasm` only.
- **A direct request for `<file>.br` or `<file>.gz` is answered** with the compressed bytes under their
  own name and type, not refused. Harmless (they are public bytes of a public bundle), left as is.
- **The base image moved under the Dockerfile.** `eclipse-temurin:25.0.4_7-jre` is now Ubuntu 26.04
  («resolute»); `brotli` 1.2.0 comes from its universe. The tag is not pinned by digest, so the
  encoder's version can change with a rebuild; the sizes would move by a few KiB, not the encoding.
- **Traefik passes the encoding through**: with no `compress` middleware on the route there is nothing
  to double-compress, and if one is added later Traefik skips a response that already carries
  `Content-Encoding`. Whether the cluster's entrypoint compresses on its own is still unchecked and no
  longer matters for the bundle.
