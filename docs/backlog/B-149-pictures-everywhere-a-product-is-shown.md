---
id: B-149
title: "fix: every product tile draws its picture, and the sample products their own"
status: open
priority: P2
size: M
stage: stage-12-qa-run
---

# B-149 — fix: every product tile draws its picture, and the sample products their own

Found by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers A, C, D, E):

- **The 15 sample products' stored photos are one generic object.** With object storage, `SeedPhotos` draws the same
  rounded box with an inner frame, only tinted — the headphones, earbuds, mug and duvet look alike, `/c/electronics`'
  first row and the empty cart's «Popular on Haul» are six identical blobs — while every other product gets B-78's
  recognisable category drawing (better than the sample's own «photo»).
- **Tiles with no picture yet:** the review and question dialogs' product tile, `/account/orders` history thumbnails and
  the account's order cards are plain tones (the order page draws the photo); review photos and the photo viewer are
  empty tone tiles. B-78's findings named some of them; their wire has no image field.
- **Art drawn large is soft:** the headphones' picture on the product page at 800 (≈768 px wide) is blurred while the
  same art at 1440 (≈500 px) is crisp — rasterised at a fixed size and scaled up.

- AC: the sample products draw a picture of their own kind (`SeedArt` or a `SeedPhotos` that draws the category's
  object); every tile above draws the product's picture; art is sharp at every width it is drawn; goldens recorded
  with --force and looked at.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SeedArt.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewPhotos.kt`.
