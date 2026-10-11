---
id: B-104
title: "client + server: the product page keeps what the shopper chose"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-104 — client + server: the product page keeps what the shopper chose

Found in the QA pass of 2026-10-10 (v0.3.0):
- picking a colour or bundle on the Reviews, Questions or Specs tab jumps back to Description: the variant link drops
  `tab` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt:304`);
- the loading skeleton still draws the gallery's thumbnail column B-71 removed — the page jumps when it arrives;
- the description tiles cut their titles («Noise ca», «Connect»);
- share gives no sign when the clipboard refuses (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt:210`) — no fallback.

- AC: per line; tests; goldens where the look changes.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt`.

## Findings

- **A variant keeps the tab**: on the specifications, reviews and questions tabs a colour or bundle opens
  `/p/{id}?sku=…&tab=<tab>` (`ProductScreen.variants`); on the description it stays `/p/{id}?sku=…`. The buy box an
  «Add to cart» answers with (`update`, B-63) is the same tab's: the command's address carries `&tab=` beside
  `answer=details` (`LineAnswers.detailsAnswer`), so the redrawn variants do not fall back to the description.
  `shown` (B-71) is not kept: a «Show more» loads in place and the buy box's links were drawn before it, so a variant
  picked then opens the tab's first ten. Tests: `ProductRoutesTest` «a variant picked on a tab keeps the tab» opens
  each one; `LineAnswersTest` «add to cart answers the header and the buy box» holds the answered variants on the
  reviews.
- **The skeleton**: at 1440 the loading photo takes the gallery's width (1.3 fr), as the page draws it since B-71;
  the thumbnail column is gone. `ProductPageLayoutTest` holds the photo's left edge and width equal before and
  after the page arrives, at 1440 and 390 (the phone had no column; its test is a guard). Golden `Product_Loading`
  re-recorded; `Product_Loading_Phone` did not move.
- **Fact tiles**: the server sends the product's first four specifications as the tiles, so a tile's title is a
  specification's name («Noise cancelling», «Connectivity»), drawn at 56 / 44 sp on one line and cut. The title now
  wraps between words, and shrinks (in half-sp steps, not below 16 sp) until its widest word fits the tile; a title
  that fits — the canvas's «40 h», «3 min», «12», «250 g» — keeps the display size, and `Product_Description` /
  `_Phone` did not move. The size only shrinks while the tile is shown: a measurement asked twice (the tiles' row is
  as tall as its taller tile, an intrinsic measurement) settles instead of swinging; widening the window afterwards
  keeps the smaller size until the page is drawn again. Tests: `ProductPageLayoutTest` (no visual overflow, no line
  broken inside a word, at 1440 and 390; the canvas's facts at their display size). New goldens, no artboard:
  `Product_Description_SpecFacts` (1440 × 1560) and `_Phone` (390 × 2720).
- **Share, refused**: `ClipboardLinks` tries `navigator.clipboard`, then a selected text area and
  `document.execCommand('copy')`, which pages without a secure context still allow. Refused by both, the photo shows
  under its buttons «Couldn't copy the link. Copy it from here:» over the whole link (`LinkCopier.address`: origin and
  path), selectable, with a close button; a press that copies after all says «Link copied» and takes it away. Test:
  `ProductPageLinksTest` «share the browser refuses shows the link to copy by hand». New golden, no artboard:
  `Product_ShareRefused` (390 × 180). Neither copy path was exercised in a real browser here — the desktop tests
  drive the `LinkCopier` seam; the JavaScript compiles into the bundle.
- **Goldens** recorded on the Linux build machine in a copy outside the mutagen replica; a full `viddikRecord` there
  changed no other golden.
