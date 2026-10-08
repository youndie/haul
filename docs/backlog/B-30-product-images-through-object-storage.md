---
id: B-30
title: "product images through object storage"
status: done
priority: P3
size: M
stage: stage-9-ship
blocked_by: [B-08]
---

# B-30 — product images through object storage

Product photography through object storage; the placeholder tiles remain the fallback (research D8).

Feature: `feature-product` — its scenarios are this item's acceptance where it names them.

- Not covered in v1.

- AC: cards and the product page show stored images; the placeholder tiles remain the fallback.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Photos.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/S3PhotoStore.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/seed/SeedPhotos.kt`,
  `server/src/main/resources/db/migration/V7__product_images.sql`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductPhoto.kt`.

## Findings (2026-10-08)

- **Built:** `products.image_key` (V7); `ProductCard.image` and `ProductDetails.photo` on the wire,
  absent unless the server has object storage *and* the product has a key; `GET /images/{key}` serves
  the photo from the server's origin (bucket private, no CORS, `Cache-Control: immutable` because the
  key carries the content's SHA-256), and only keys under `products/`. Configuration
  `HAUL_S3_ENDPOINT` / `_BUCKET` / `_ACCESS_KEY` / `_SECRET_KEY` / `_REGION` (default `us-east-1`):
  no endpoint is no photos and the server starts as before; an endpoint without the rest refuses the
  start. The client draws the photo over the placeholder tile in the card, the product photo and its
  first thumbnail; the tile stays while the photo is absent, loading or failed.
- **The S3 client is the JDK's `HttpClient` plus a hand-written SigV4** (`SigV4.kt`, ~100 lines): the
  server makes two calls on one bucket. AWS SDK v2 would have brought a dozen jars and its own HTTP
  stack into the image and its AOT cache; Ktor's client would have added a dependency for what the
  JDK already has; s3kn (the portfolio's S3 client) is Kotlin/Native-first and exists only as a stale
  `0.1.0-SNAPSHOT` in the private reposilite, which a public repository should not pin. The signer is
  held to AWS's own «GET Object» example (`SigV4Test`), and the store to a SeaweedFS that checks
  signatures — without an identity SeaweedFS lets anonymous requests through, and the round trip still
  passed (mutation M4 below), so the wrong-secret test is the control.
- **The client loads with Coil 3.6.3** (`coil-compose`, `coil-network-ktor3`; it publishes wasmJs and
  was built with Kotlin 2.4.10) over a Ktor client the entry point gives it (`ktor-client-js` in the
  bundle). Two traps found in the tests: Coil registers the network fetcher it finds by service
  loader, over a default `HttpClient()` of its own, *ahead* of the one handed to it — so it is turned
  off; and its disk cache (in `/tmp/coil3_disk_cache` on the desktop target) kept a 404 between test
  runs — off too, the browser's HTTP cache does that job for an immutable photo.
- **Bundle cost** (measured on the WSL box, `wasmJsBrowserDistribution`, main at `382f643` against
  this branch): the app `.wasm` grows from 1,598 to 1,881 KiB raw (+283 KiB), 530 to 628 KiB under
  `gzip -9` (+99 KiB); skiko's `.wasm` is byte-identical. That is Coil and Ktor's client, reachable from
  `main()` already, though no screen is loaded yet (B-28's «main» arm grows by this much).
- **Seed:** the 15 sample products of research §6 get a photo drawn in code with `java.awt` at start
  (`HAUL_SEED=true` and a store configured); nothing is downloaded. Checked in the real image: the JRE
  draws headless, `sample photos stored: 15`, the page names `/images/products/p-sony-wh-1000xm6/<hash>.png`
  and it answers `200 image/png`, 640×640. A store that fails at seed time is logged and the server
  starts anyway. The generated two thousand keep their tiles.
- **Question for the owner — real product photography.** The repository ships none, and none was
  fetched (that needs the owner's permission). The choices: (a) keep the drawn still lifes; (b) the
  owner supplies licensed photos for the sample products (CC0 / own shots), uploaded to the bucket
  and keyed in the seed; (c) generated images. Whoever decides also decides whether the generated
  catalog gets photos at all — today it shows the fallback on purpose.
- **Not wired here:** the Helm chart (B-27) passes no `HAUL_S3_*`, so the stand shows placeholders
  until it does and has a bucket; cart lines and the search suggestions' product tiles still draw tone
  tiles (not cards; the cart's «Picked for you» cards do carry photos). No upload path exists — photos
  enter through the seed only.
- **Migration number:** V7, not V5. By the time B-30 was rebased, main had V4 (the cart) and V6 (the
  product headline); Flyway runs with `validateOnMigrate` and without `outOfOrder`, so a V5 would have
  refused to start every database already migrated to V6. V6's comment reserves V5 for a parallel
  branch; that reservation is the risk, not a plan.
- **Parity unchanged:** 43/43 artboards within tolerance with the same percentages as main to the
  hundredth (the fixtures name no photo and draw through a loader that loads nothing); one new golden,
  `ProductCard_Photo`, through a painted fake loader. The image check: 499 of 499 classes from the AOT
  cache.
- **Mutation-checked:** the card without `photos.url` (M1), a photo address without a store (M2), the
  route without the `products/` prefix (M3), SeaweedFS without an identity (M4), the signing key from
  the access key (M5), an optional bucket (M6), the placeholder only while loading (C1) or always (C2),
  the card ignoring its image (C3), no origin resolution (C4) — each failed the test written for it.
