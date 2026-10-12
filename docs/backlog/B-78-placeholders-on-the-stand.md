---
id: B-78
title: "server + client: the stand shows pictures, not placeholder labels"
status: done
priority: P3
size: M
stage: stage-10-review
---

# B-78 — server + client: the stand shows pictures, not placeholder labels

Every product, category tile and campaign is a tinted box with a caption: «STUDIO», «HEADPHONES», «ELECTRONICS»,
«CAMPAIGN IMAGE» on the hero (`HomeScreen.kt:99`), «PRODUCT PHOTO · 1 / 8» on the product page
(`ProductScreen.kt:171-175`). On a public stand this reads as unfinished.

- **Question for the product owner:** where the pictures come from (licensed stock, generated, the seed's own
  illustrations) and how they are served (`ProductPhotos` exists on the server). Until decided, the captions that
  only describe a missing picture are not drawn.
- AC: per the decision; the first-load numbers (B-28) re-measured with images.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/seed/SeedArt.kt`, `server/src/main/kotlin/io/github/youndie/haul/seed/SeedMotifs.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Photos.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductPhoto.kt`, `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/FixtureHarness.kt`, `scripts/measure-first-load.py`.

## Deferred (2026-10-10)

The owner put the pictures off. The item stays open, last in the queue; nothing here is taken until the owner
says where the pictures come from.

## Decision (2026-10-12)

The owner chose **the seed's own illustrations**: flat SVG pictures drawn per category (and for the campaigns and the
hero) in the canvas's style, generated as part of the seed, served the way `ProductPhotos` serves stored photos — no
licence, no third-party host (B-128's rule: nothing off the store's origin). Products of one category share its
picture set, varied by colour where the product has one. The captions that only describe a missing picture go.
The item is open for the loop.

## Done (2026-10-13)

Per the decision: the seed's own pictures, drawn and served by the server, no storage needed; research D8 has «Decided
in B-78», D9 «Measured in B-78».

- **The pictures** (`seed/SeedArt.kt`, the objects in `seed/SeedMotifs.kt`, the SVG writer in `seed/Svg.kt`): a flat
  object per category — the sample catalog's twelve leaves each their own, every other top-level category one, found by
  walking up the seed's tree, a parcel for a category the seed does not know — in the canvas's roles (Ink, Cobalt, Acid,
  Hot, white) over the product's tile tone and the canvas's hatching. A product is its category's object in its SKU's
  colour (the seed's ten colours have swatches), in one of three compositions chosen by its id: one category's products
  share the set and differ by colour and composition, and the product page's picture follows the chosen SKU. A category
  tile is its object, plain. The Autumn mega sale's hero is falling leaves in Cobalt's shades, white and Hot (the hero's
  Acid figure runs across it); Tech week's banner a laptop in the tile the canvas keeps for it. Free delivery weekend's
  banner has no picture place on the canvas and draws none.
- **Served from the store's own origin, without object storage** — the stand has none: `ProductPhotos.url` gives the
  stored photo when there is one (it still wins) and the product's picture otherwise; `/images/art/<name>.svg` is drawn on
  request, `image/svg+xml`, a year's `immutable`, `nosniff`, `Content-Security-Policy: default-src 'none'`. The name
  carries the content's hash (`p_headphones_1_silver_e6e4ff.3f1c9a0e5b.svg`); a name whose drawing changed, or that the
  seed never drew, is a 404, and the client keeps its tile. Only names the screens hand out are kept in memory; a
  requested one is drawn and compared, never kept.
- **The wire**: `image` on `CategoryTile`, `CampaignHero` and `PromoBanner`; `ProductCard`, `CartLine`, `SummaryItem`,
  `ProductSuggestion` and `ProductDetails` (`photo`) carry the picture where they carried nothing. The captions that only
  described a missing picture went, with their fields: `label` on `ProductCard`, `CategoryTile`, `CampaignHero`,
  `PromoBanner`, `CartLine`, `SummaryItem`, `ProductSuggestion`, and `ProductDetails.photoLabel`; `HaulColors.tileLabel`
  with them. The placeholder tile stays (tone and hatching) while a picture is absent, loading or failed.
- **The client** draws the pictures with Coil's SVG decoder (`coil-svg`, Skia's own SVG renderer, which skiko's wasm
  already carries) in the photos' loader. SVG rather than a PNG the server rasterises: on the home page the sixteen
  pictures are 24 KiB as SVG against 143 KiB as PNG at their own size, and the decoder adds 2.3 KiB brotli to the bundle —
  the numbers below.
- **The goldens draw the pictures**: the fixtures draw every `/images/art/…` a body names, at once, from
  `composeApp/src/desktopTest/resources/art` (`ArtLoader` in `FixtureHarness.kt`), and `ArtFixturesTest` holds that
  directory to what the server draws under each name — and on a difference writes the new names (`renamed.txt`) and
  files to `server/build/art-fixtures`. The canvas's bodies name the pictures their products' categories would get; the
  server-built ones were regenerated by their fixture tests (the orders of canvas products pass their category).
- **The log line** without storage reads «no object storage: seed pictures only».
- **Tests.** Server: `SeedArtTest` (the object by walking up the tree, every seeded leaf and top level has one; one
  category's products share a set and differ by colour; every name answers its picture, the same each time, well-formed and
  under 6 KB; a name the art did not hand out answers nothing), `PhotoRoutesTest` (a product without a stored photo
  carries its picture; without object storage every product carries its picture and its address answers the SVG; a
  picture address serves the drawn SVG for good with its headers — the hero's, a tile's, Tech week's banner's, and none
  for Free delivery weekend's; a picture that is not the seed's is 404), `ArtFixturesTest`. Client: `PhotoFallbackTest`
  «a picture sent as SVG replaces the placeholder tile» (the placeholder is told by `PLACEHOLDER_TAG` now that it says
  nothing); `LinePhotosTest` keeps the results under the suggest panel out of its count. QA catalogue: TC-PDP-17
  rewritten for the pictures, TC-HOME-08 new.
- **Mutations**, on the committed change, each red, restored, tree clean: the SKU's colour ignored (`SeedArtTest`
  colour test); the CSP header dropped (`PhotoRoutesTest` serving test); a kept SVG edited (`ArtFixturesTest`); the
  client's `SvgDecoder` removed (`PhotoFallbackTest` SVG test); the hash check dropped from `SeedArt.svg` (`SeedArtTest`
  «did not hand out»); the route answering no picture (three `PhotoRoutesTest` tests).
- **Goldens**: 90 re-recorded with `viddikRecord --force` on the Linux box in a copy outside the mutagen replica — every
  page whose tiles now draw a picture (home, the catalog, search and its panel, the product page and its tabs and dialogs,
  the cart, checkout, every order state and the return dialog, Saved, the cards); the loading, error, header and shell
  goldens did not move, nor `ProductCard_Photo` (a stored photo over the tile, as before). Each kind looked at; the
  pictures sit where the canvas draws its striped tiles, nothing else moved.

- **The first load, re-measured** (B-28's harness, B-34's image arm; research D9, «Measured in B-78»): the pictures are
  asked for after the first frame, so it does not move beyond the shared machine's spread (Slow 4G 24.27 s → 24.39 s,
  Fast 4G 4.78 s → 4.92 s, ranges overlapping; the bundle grew 2.1 KiB brotli). The home page's sixteen pictures are on
  screen at 28.5 s on Slow 4G, 6.0 s on Fast 4G, 0.86 s on loopback — behind the fonts, which share the link. As PNG at
  their own size they would be 143 KiB instead of 24 KiB and land 0.35 s later on Slow 4G; the SVG decoder costs 2.3 KiB
  brotli before the first frame. The harness reports the pictures in every run now, relays through a proxy of its own
  for the PNG arm (`--art pass|png`, `ART=` in `measure-first-load.sh`), and waits for Chromium's first page (one launch
  in this campaign listed none and voided an arm, which was run again).
- **Parity**, every artboard that moved (`viddikDesignParity` on the Linux box, `origin/main` against this branch): the
  canvas draws a striped, captioned placeholder wherever a picture now stands, so every page with tiles moves away from
  it by the pictures — the owner's decision, not a defect, and no tolerance was touched. Fourteen artboards cross 5 %
  for it: `Home_Content` 1.90 → 7.01 %, `_Phone` 3.28 → 9.47 %, `Home_Guest` 1.98 → 6.47 %, `_Phone` 3.55 → 7.57 %,
  `Catalog_Content` 3.17 → 9.90 %, `_Phone` 3.46 → 12.79 %, `Saved_Content` 1.80 → 7.46 %, `_Phone` 3.60 → 11.44 %,
  `Saved_PriceDrops` 1.68 → 6.43 %, `_Phone` 3.68 → 11.50 %, `Search_Autocomplete` 3.91 → 6.24 %, `Cart_Empty_Phone`
  3.95 → 12.21 %, `Order_InTransit_Phone` 4.46 → 5.22 %, `Product_QuestionDialog` 4.44 → 5.36 %; the rest of the
  pages with tiles move by 0.2–2.3 points and stay inside (cart 1.6 → 2.5 %, checkout 1.9 → 2.1 %, order 1.9 → 2.1 %);
  those already over move by the same (`Product_Description` 6.25 → 7.76 %, `Search_Results` 5.42 → 13.92 %). The
  full table is in the pull request.

## Findings (2026-10-13)

- **Tiles still without a picture**, not in the decision's list: the account overview's order cards and history rows
  (`AccountComponents` carry `tones`, no addresses), the review dialog's product tile (`FormProduct.tone`), and the
  reviews' photos, which are shoppers' photos drawn as tones (B-86). Each would need its wire to name an image.
- **Tech week's banner is Lavender on the stand and Acid on the canvas** (`SampleCatalog.campaigns` names `#E6E4FF`; the
  canvas's body `#DFFF3A`), before this item as after; the picture takes whichever tone the server sends.
- **The stand's tiles were never captioned by the canvas's words**: the server sent each product's leaf slug
  («fashion-essentials-classics») where the canvas wrote «SWEATER», and the generic categories' tiles their own slugs.
  Gone with the captions.
- **The SVGs go out uncompressed** — the server compresses only the bundle's precompressed files (B-34); a `gzip`
  of the pictures would cut their 24 KiB on the home page to about a third. Not taken: optimising is not this item.
- **Order fixtures of canvas products** (`OrderFixturesTest.bought`) name the category their picture is drawn for: the
  canvas's yoga mat, sweater, serum, blocks, food bags and cable are in no seeded category.
