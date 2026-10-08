---
id: B-25
title: "server: recommendations from views in the Home tree"
status: wip
priority: P2
size: M
stage: stage-8-loyalty
blocked_by: [B-07, B-19]
---

# B-25 — server: recommendations from views in the Home tree

«Picked for you» from recent views, inside the home tree.

Feature: `feature-recommendations` — its scenarios are this item's acceptance where it names them.

- Not covered: a recommendations service of its own.

- AC: feature-recommendations scenarios pass.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/`.

## Done (2026-10-09)

- **The decisions** are research D6, «Decided in B-25»: a view is a customer opening a product page, recorded by the
  product tree's `GET` beside the page's reads, a failed write logged and never answered; a guest's views are not
  kept; one view per product, the newest 20 kept; the categories by how many views they hold (a tie to the one viewed
  last, then the slug), from each the top-rated in stock (rating, reviews, id) neither viewed nor bought (an order not
  cancelled), two of a category, six in all, the rest of the row filled from the popular row under the same rules;
  fewer than three views is the popular row, «Popular right now»; a failure inside the block drops the block; the
  empty cart keeps the day's deals; Maya's views are seeded.
- **The server** (`feature/recommendations/`): `product_views` (`V20__product_views.sql`), `ExposedProductViews`
  (`record` writes nothing for a product the catalog does not have, then trims; `recent` joins the category);
  `ExposedPickSources` (`bought`, `popular`); `PickedForYou`, the rule; `RecordView`, called from
  `GET /ui/p/{productId}` (`CatalogRouting.kt`) in a `launch` beside `ProductScreen.build`; `PickedSection`, the
  title, subtitle and six-card `ProductGrid` (`picked-title`, `picked`) at the end of `HomeScreen`'s sections for a
  customer, nothing for a guest. `recommendationsModule` in `HaulModule.kt`. Maya's five views in
  `seed/SampleViews.kt`, inserted by `Seeder` and covered by `SeedDigest`. No contract change: the block is drawn
  with `SectionHeader` and `ProductGrid`, and the client draws it from `home_content.json` already.
- **Scenarios** (feature-recommendations): «From views» — `RecommendationsRoutesTest.a customer who viewed three
  headphones is picked at most two and none of them` over HTTP (the three best-rated headphones opened, two others
  picked), and at the rule `PickedForYouTest.three headphones viewed give at most two headphones and none of the
  three`; «Guest» — `RecommendationsRoutesTest.a guest's home has no picked block` (with a guest id and with none) and
  `PickedSectionTest.a guest gets no block and nothing is read for them` (no read of views for a guest: there is no
  request to make, the block is part of `/ui/home`).
- **Tests written**: `PickedForYouTest` (the scenario, categories by frequency with the tie to the latest viewed,
  fewer than three views, bought never picked, a category run dry filled from the popular row, the same views the
  same picks); `ProductViewsTest` against PostgreSQL (a product viewed again is one view, the newest twenty kept and
  the rest deleted, one customer's views are not another's, an unknown product is not recorded, bought is the orders
  not cancelled, popular is the most reviewed in stock); `PickedSectionTest` (the guest, a failing block left out, the
  `Home_Content` body's header field for field the server's, a view recorded for a customer only and a failed write
  swallowed); `RecommendationsRoutesTest` (the two scenarios, the popular row under three products — four tabs of one
  product are one view — and the seeded Maya's two headphones, two duvet covers and two mugs); `SchemaTest` for
  `product_views`, `KoinGraphTest`.
- **Mutations**, each seen failing and restored: viewed products not left out (four tests — after viewing the
  best-rated three instead of the first three by id, which the first run showed passed without the exclusion); three
  of a category; a guest's views read; no trim; a failed write thrown to the page; the tie to the oldest view;
  cancelled orders counted as bought; no fill from the popular row; two views enough; a failing block failing the
  page; the top-rated order reversed; the views read without their customer filter (four tests).
- **Goldens and parity**: no client body and no golden changed — `home_content.json` already draws «Picked for
  you — Based on your recent views», now the server's header (`PickedSectionTest` holds it), and the empty cart's
  picks stay the deals. `viddikDesignParity` on the Linux box, tolerance untouched: `Home*` 8/8 within 5 % (Loading
  0.27 / 0.22, Content 1.83 / 2.96, Guest 1.74 / 3.01, Error 1.45 / 2.61, wide / phone), `Cart*` 16/16 (Empty 1.90 /
  3.16, as B-13 recorded).

