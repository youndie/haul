---
id: B-08
title: "client: renderers for the Product components (without the dialogs)"
status: done
priority: P1
size: L
stage: stage-2-browse
blocked_by: [B-05, B-06]
---

# B-08 — client: renderers for the Product components (without the dialogs)

The product page is the densest screen and the one most components appear on.

Feature: `feature-product` — its scenarios are this item's acceptance where it names them.

- Not covered: the review and question dialogs (B-22).

- AC: parity for `Product_*` except the two dialogs.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`.

## Done (2026-10-08)

`viddikDesignParity` after three rounds, all 16 artboards within tolerance (5 % of pixels, ±16 per
channel, untouched):

| Artboard | Mismatch | | Artboard | Mismatch |
|---|---|---|---|---|
| Product_Loading | 0.68 % | | Product_Questions | 2.80 % |
| Product_Loading_Phone | 0.29 % | | Product_Questions_Phone | 3.52 % |
| Product_Description | 2.78 % | | Product_OutOfStock | 2.72 % |
| Product_Description_Phone | 4.05 % | | Product_OutOfStock_Phone | 4.02 % |
| Product_Specifications | 2.92 % | | Product_NotFound | 1.43 % |
| Product_Specifications_Phone | 1.98 % | | Product_NotFound_Phone | 4.53 % |
| Product_Reviews | 2.43 % | | Product_Error | 1.40 % |
| Product_Reviews_Phone | 3.69 % | | Product_Error_Phone | 2.67 % |

Rounds: (1) first measurement, 9 of 16; (2) a text's cropped line boxes measured with a text
measurer, so a row made as tall as its tallest cell (the fact tiles) crops too — 11 of 16; (3) the
first line box placed where CSS places it relative to the baseline — 16 of 16 (research, «Found in
B-08»). The Home and Catalog artboards stay within tolerance — most moved down, by up to 0.9 points, and
Catalog_Empty up by 0.07 to 2.96 % — and their goldens were re-recorded with this change.

- Contract (`shared/.../ui/ProductComponents.kt`): `ProductReviews` (the rating, its histogram, «Write
  a review», the reviews) and `ProductQuestions` (the count, who answers, «Ask a question», the
  questions, «Not answered yet»); `ProductDetails` gained `accent`, the gallery's tones,
  `morePhotos`, `photoTotal`, `haulPayStrong` and `stockAdvice` (what Product_OutOfStock says instead
  of the delivery lines); `TabLabel.compactTitle` («Specs»); `ProductDescription.title` and `accent`.
  The server keeps answering `400` for the reviews and questions tabs (B-22) and builds the new
  fields of the details and tabs; its courier line now reads the cut-off, as the canvas does.
- Client: `feature/product/` — the details at both widths (gallery, identity and choices, buy box,
  the out-of-stock buy box), the tab row, the four tabs; `ProductNotFound` over a new `NotFoundShell`
  and `ProductLoading` in `shell/`; the Error shell reused («This product didn’t *load*»). Bodoni Moda
  700 added (review titles). On a phone a path deeper than four crumbs shows its last two after «…».
- Fixtures: `composeApp/src/desktopTest/.../ProductFixtures.kt`, one per artboard; the tabs and the
  out-of-stock state decode wire bodies with the canvas's copy (`resources/bodies/product_*.json`).
  Goldens recorded on Linux.
- Scenarios: the page half of «Out of stock» now also asserts what the buy box says instead
  (`ProductRoutesTest`); mutation: dropping `stockAdvice` fails exactly that test.
- Not done here: the review and question dialogs and the trees of those two tabs (B-22); the server's
  tab row lists only the two tabs it serves until then; NotFound's header is the one the client last
  drew, which the screen loader has to keep.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.
