---
id: B-86
title: "client + server: review photos open and the histogram filters"
status: done
priority: P3
size: M
stage: stage-10-review
---

# B-86 — client + server: review photos open and the histogram filters

B-71 left two things on the Reviews tab without an action because nothing existed for them to do: a review's
photo tiles (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt` ~375) and the rating histogram's bars (~314). The owner
deferred the decision on 2026-10-10.

- **Decided (owner, 2026-10-12): both, as proposed.** A review's photo opens a viewer over the page (the photos of
  that review, one at a time, closed by «×», Escape or a press outside); a histogram bar filters the reviews by its
  stars with a `load` (pressed again, or «All», the filter goes). The rating shown is computed from the same data as
  the histogram — today the headphones' bars 78/14/4/2/2 average 4.64 under a «4.8» (the v0.4.0…v0.5.1 runs).
- AC: a photo opens its viewer and closes as above, named for assistive tech (B-131's rule); a bar filters, the
  address carries the filter, «All» clears it; the rating and the histogram agree for every product; tests per control.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.

## Findings (2026-10-12)

- **Where the two numbers came from.** The «4.8» was the seed's number on the product (`SampleCatalog`, the canvas's),
  moved by every new review from the stored average (`ExposedReviews.average`); the bars were counted from the rows
  (`rating_counts`, recounted by `SeedReviews` since B-99), which follow `SampleReviews.ratingCounts` — the canvas's
  Product_Reviews, 1,826 / 328 / 94 / 47 / 46 of 2,341 (78 / 14 / 4 / 2 / 2 %), a 4.64. The canvas itself disagrees
  with itself: every artboard that shows the headphones writes 4.8, the one pair with the histogram draws bars
  that average 4.6. The generated catalogue had the same split on a small scale (one review of a drawn «4.5» is a
  five or a four), and a generated product drawn with no review still had a rating.
- **One source: the rows.** `Ratings.average` (feature/reviews/domain) is the histogram's average to a tenth, half
  up, and the rating is written from it wherever either moves: a review stored (`ExposedReviews.write` reads the
  histogram after counting the review in, in the same transaction), the seed's catalogue (`CatalogSeed`: each
  product's histogram, then its rating from it, after generation so the random stream is untouched) and the seed's
  recount of a database (`SeedReviews.RECOUNT`, the same rounding in SQL). Every generated product has at least one
  review now. The tests' seed without the generated rows (B-111) writes the catalogue's histograms, so a review
  written there moves the rating as on the stand.
- **What changes against the canvas (decided here, the item named no number).** One of the canvas's two numbers had
  to move; the bars moved: the seed's headphones are 1,966 / 281 / 47 / 24 / 23 (84 / 12 / 2 / 1 / 1 %), a 4.77, so
  the «4.8» on every card, the product page, search and the tab stays as drawn, and only Product_Reviews,
  Product_Reviews_Phone (and the review dialogs drawn over the tab) draw other bars than the canvas. The other way —
  keep the bars, show 4.6 — would have moved the «4.8» in 16 client bodies and every artboard showing the headphones.
  Parity (Linux box, `viddikDesignParity`, against `main` built the same way): Product_Reviews 7.33 → 7.35 %,
  Product_Reviews_Phone 20.72 → 20.73 %, Product_ReviewDialog 2.76 → 2.78 % (ok), Product_ReviewDialog_Phone
  11.06 → 11.07 %; Product_Reviews_Mine has no artboard. The design references are not changed.
- **A stand seeded before** keeps its database: its next start makes it the database a fresh seed writes. The fill
  compares the generated rows per product and number of stars (not only the totals, which the new bars keep), deletes
  the generated reviews past a target with their «Helpful» votes, writes the missing ones, dates every generated row as
  a fresh seed does (`ON CONFLICT … DO UPDATE created_at`) and recounts counts, bars and ratings. `SeedReviewsTest`
  builds that stand (the canvas's bars, its dates, a vote on a review the new bars drop, a drawn rating) and compares
  the digest with a fresh seed's.
- **Found on the way:** `SeedDigest` ordered each table by its first column, so a key of two — a product's bars in
  `rating_counts` — hashed in whatever order the plan read them; two databases holding the same rows differed once
  one had held other bars before. It orders by the whole primary key now.
- **The histogram filters** (`HistogramBar.action`, `selected`; `ProductReviews.filter`, `allLabel`, `all`): a bar is
  kompot's `load` of `/ui/parts/p/{id}?tab=reviews&stars=N` (the address `…&stars=N`, `push`), answered with the tab's
  node as «Show more» is; «Show more» keeps the filter (`&stars=N&shown=20`). The pressed bar, drawn in bold with the
  others' fill grey and `selected` for a reader, and «All» beside «Showing 4-star reviews» load the tab without it.
  A bar nobody gave has no action. The rating, caption and percentages stay the product's. The viewer's own review
  leads the list only among its stars. `stars` other than 1 to 5 is `400 validation_failed`; the other tabs ignore it.
- **The photo viewer** (`ReviewPhotos.kt`): a tile opens the review's photos over the page in `SheetOverPage` — the
  page's own layer (B-126; a `Dialog` is a second root, which Compose 1.12's mirror keeps after it closes) — the photo
  large, «Daniel R.», «Photo 1 of 2», «‹» and «›» (faded and inert at the ends) and the arrow keys; «×», Escape or a
  press on the dimmed page close it, and a press on the card does not. Names: a tile «Open photo 1 of 2 by Daniel R.»,
  «Close photos», «Previous photo», «Next photo», the photo «Photo 1 of 2 by Daniel R.». The dimmed page is a gesture,
  not a button, so it takes no words into a name. The photos are still their placeholders' tones (B-78). Outside the
  storefront (a screenshot) the tiles press nothing, as before.
- **Tests.** Server: `ReviewPagesTest` «a bar lists the reviews of its stars in place and pressed again or all lists
  every review», «a bar nobody gave presses nothing», «the stars listed are one to five», «a review written moves the
  rating to the histogram's average»; `SeedReviewsTest` «a stand seeded with the canvas's bars gets the seed's and every
  rating its histogram's», «every product of the seed has reviews and the rating they give», and its row check reads
  the rating too; `ReviewCountingTest` the arithmetic. Client: `ReviewControlsTest` (8: a bar, «All», the pressed bar, a
  bar without an action, the viewer's steps and «×», Escape and the arrows, a press outside at 1440 over Aisha's
  «Helpful» and at 390); `NamedPressesTest` opens the viewer as a dialog at both widths.
- **Mutations** (each on the committed change, restored, tree clean): server — the filter not applied, a bar nobody
  gave given an action, `stars` unbounded, the written review not moving the rating, the catalogue's ratings not
  derived: each red on its test (one run); the recount's rating update gone, the past-target delete gone, the
  re-dating gone: the stand test red, each alone; the «at least one review» gone: four seed tests red; the tests' seed
  without histograms: the written-review test, the nobody-gave test and `ReviewRoutesTest`'s verified review red.
  Client — the bars and «All» not pressable: 4 red; `selected` and the tiles' names gone: the bar tests, the photo
  tests and both `NamedPressesTest` cases red; the viewer's buttons unnamed, the dimmed page not closing, the arrows
  not read: `NamedPressesTest` dialogs and the four photo tests red; the viewer in a `Dialog`: the layer test (two
  roots) and the arrow keys red.
- **Goldens**, recorded with `viddikRecord --force` in a copy outside the replica and looked at: Product_Reviews,
  Product_Reviews_Phone, Product_Reviews_Mine (the bars 84 / 12 / 2 / 1 / 1 %; the generated reviews' dates a day
  earlier and Priya's four-star review lower in the list, as the new targets date them), Product_ReviewDialog and
  _Phone (the same tab under the dialog). The 163 others are byte-identical. No golden draws the viewer or a filter:
  neither has an artboard, and outside the storefront nothing opens over the page.
