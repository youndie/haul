---
id: B-149
title: "fix: every product tile draws its picture, and the sample products their own"
status: done
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
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewPhotos.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/seed/SeedPhotos.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductPhoto.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.

## Done (2026-10-13)

Research D8 has «Decided in B-149», D9 «Measured in B-149» (the first frame does not move).

- **The sample products' stored photos are their own pictures.** `SeedPhotos` stores `SeedArt`'s drawing of each
  sample product — its category's object in its first SKU's colour over its tile tone, the composition its id picks —
  as SVG (`products/<id>/<hash>.svg`, `image/svg+xml`; the photo route already sends a stored SVG with the pictures'
  `Content-Security-Policy`). Chosen over a `SeedPhotos` that rasterises the object to PNG: the JVM has no SVG
  rasteriser without a library, and an SVG is sharp at every size, which a 640 px PNG at 768 px on a 2× screen is not.
  A store seeded before (B-30's tinted boxes) gets today's photos on its next start: a key the seed stored
  (`products/<id>/<16 hex>.png|svg`) that is not today's is replaced; any other key is left alone.
- **Every tile draws the product's picture.** The wire names one where it named none: `FormProduct.image` (the review
  and question dialogs — the photo the product's page draws for the chosen SKU), `ActiveOrder.images` and
  `HistoryRow.images` (one per line, `null` for a product no longer sold — the order's page's photos), and
  `Review.images` (one per photo tone: the reviewed product's object, plain, over the photo's tone, in another of the
  three compositions than the product's own picture and another for each photo — `Illustrations.reviewPhoto`). The
  client draws each over its tile through `LineTile`, the viewer too, large.
- **Art is sharp at every width.** The cause was not a fixed size of the server's: the client asked Coil for the
  picture with no size, and Coil's SVG decoder then rasterises at the SVG's own size, 240 px — the product page drew
  that bitmap at ≈768 px (at 1440, ≈500 px). `coilPhotoLoader` now asks for the box's size in pixels once the box is
  measured, and again — the picture on screen kept until the sharper one is in (`useExistingImageAsPlaceholder`) —
  whenever the box outgrows it (rounded up to 64 px, so a dragged window asks every few steps); never for a smaller
  one. A stored PNG photo is unaffected beyond being asked for its box's size.
- **Tests.** Server: `PhotoRoutesTest` «a sample product's stored photo is its own picture» (each of the fifteen's
  stored photo is what its page serves without storage; the headphones, mug and duvet differ), «the review and
  question dialogs and a review's photos carry pictures», the history row's photos added to «the cart, checkout, an
  order and the suggest panel carry the photo the card carries», «the photo address serves the stored bytes for
  good» now SVG with the CSP; `SeedPhotosTest` (a B-30 key replaced, an owner's kept, a second start stores nothing);
  `S3PhotoStoreTest` with an SVG; the fixture tests regenerated the bodies (`account_*`, `order_delivered*`,
  `order_partly_returned`, `product_reviews*`, `product_questions`) and `ArtFixturesTest` the three new pictures.
  Client: `PhotoFallbackTest` «a picture whose box grows is decoded again at its new size»; `LinePhotosTest` «the
  account's order cards and history rows draw their pictures», «the review and question dialogs draw their product's
  picture»; `ReviewControlsTest` «a review's photos are drawn as their pictures on the tiles and in the viewer». QA:
  TC-PDP-17 (step 5, sharpness), TC-PDP-19 and TC-ACC-01 extended.
- **Mutations**, on the committed change, each red, restored, tree clean: `SeedPhotos` drawing a category it does not
  know (the «own picture» test); the old seed key not replaced (`SeedPhotosTest`); `FormProduct.image` dropped (the
  dialogs test); the history row's images dropped (the B-108 test); `Review.images` dropped and the cards' images
  dropped (the dialogs test, `ReviewFixturesTest`, `AccountFixturesTest`); the client's account tiles, the dialogs'
  tile and the review tiles drawn without the address (the three client tests); the loader as before
  (`PhotoFallbackTest`: «decoded at 24 px for a 60 dp box» — the evidence of the cause above).
- **Goldens**: `viddikRecord --force` on the Linux box in a copy outside the mutagen replica: 15 changed. 13 brought
  back, each looked at: `Account_Content`, `_NotMember`, `_Orders` (and each `_Phone`) — the cards' and rows' tiles
  draw the lines' pictures; `Product_ReviewDialog`, `Product_QuestionDialog` (and `_Phone`) — the product tile;
  `Product_Reviews`, `_Mine`, `_Phone` — Daniel's two photos. Two not brought back: `Search_Results` (901 px) and
  `Search_Autocomplete` (188 px), the chips' glyph edges only, which `viddikVerify` accepts as they are (see Findings).
- **Parity** (`viddikDesignParity`, the merge base's client against this branch, same box): 49/49 within 5 % on both;
  the artboards that moved stay inside — `Account_Content` 1.44 → 1.74 %, `_Phone` 3.35 → 4.06 %, `Account_NotMember`
  1.86 → 1.90 %, `_Phone` 3.58 → 3.71 %, `Account_Orders` 1.41 → 1.78 %, `_Phone` 3.38 → 4.46 %. The product ones
  were retired in B-85. Nothing retired here.
- **Walked in headless Chrome** (149, the Linux box) on a throwaway local stack of this branch's image, PostgreSQL,
  shildik and SeaweedFS: with storage («sample photos stored: 15», 0 on a restart) `/c/electronics`, home, the empty
  cart's «Picked for you» and the product page draw the sample products as headphones, earbuds, mug, duvet, each
  `/images/products/<id>/<hash>.svg` 200 `image/svg+xml`; Maya (a test password set in the local realm) placed an
  order — the overview's card and the history row at 1440 and 390 draw the headphones, the duvet and the mug; the
  review and question dialogs draw the product. Without storage («no object storage: seed pictures only»): every
  picture `/images/art/…svg`; Daniel's photos are two different headphones pictures, the viewer draws photo 2 at
  584 px, crisp. The product page at 800 against v0.6.0's image on the same stack: the headphones' edges are blurred
  there and crisp here (the same picture name).


## Findings (2026-10-13)

- **A forced recording also rewrites `Search_Results` (901 px) and `Search_Autocomplete` (188 px)** — the chips'
  glyph edges only, nothing this item draws; left as they are (B-84's kind of staleness).
- **Two sample products' stored photos are byte-identical** (`p-bose-qc-ultra`, `p-sony-mdr-7506`: same composition,
  colour and tone) — B-78's set allows it; their keys differ by folder.
- **With object storage the product page's photo does not follow the SKU's colour** (one stored photo per product,
  as before); the seed's pictures do.
- **Headless Chrome builds no accessible mirror here**, so the walk pressed by coordinates read off screenshots.

## Where the checks ran (2026-10-13)

On the Linux build box (WSL2, Ubuntu 24.04), on this branch rebased on B-145 and B-146 (#182):
`:composeApp:wasmJsBrowserDistribution` green; `check :server:installDist` green — server 538 tests, client 464, `viddikVerify` 170, 0 failed, ktlint;
`scripts/e2e.sh haul/server:b149` green (WholePathTest, ShoppersConfigTest 6, SyntheticShopperTest 3). `viddikVerify`
accepts `Search_Results` and `Search_Autocomplete` as they are, so the two goldens a forced recording rewrites (chip
glyph edges only) were left unrecorded. Goldens, parity, the Chrome walk and the first-load campaign (D9, «Measured in
B-149»: first frame and pictures frame unmoved against B-150's image) ran there too. On the Mac: `make check` and
`make docs-against BASE=origin/main` clean, every anchor found.
