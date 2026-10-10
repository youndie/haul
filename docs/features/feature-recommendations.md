---
id: feature-recommendations
title: Picked for you
type: feature
status: active
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-home
api:
  - endpoint-recommendations
tags: []
---

# Picked for you

## 1. Overview

Six products the customer is likely to want, from what they viewed recently.

> Described as built (B-25, research D6 «Decided in B-25»). The block is drawn by the home page's tree
> with components that already existed (`SectionHeader`, `ProductGrid`): no contract or client change.

## 2. Business rules

* **a view** is a customer opening a product page: the product tree's `GET /ui/p/{productId}` records
  it beside the page's own reads; a failed write is logged and never fails the page; a product the
  catalog does not have is not recorded; a guest's views are not kept;
* one view per (customer, product) with the latest time — a tab, a variant or a refresh only moves the
  same view to now — and the newest 20 are kept, older rows deleted by the write that pushes them out;
* **the pick**: the viewed categories ranked by how many views they hold (a tie to the category viewed
  last, then its slug); from each, the top-rated in-stock products (rating, reviews, id) that were
  neither viewed nor bought (in an order not cancelled; the cart is not «bought»), at most 2 per
  category, 6 in total; when the viewed categories run dry, the row is filled from the popular products
  under the same rules, and the subtitle stays «Based on your recent views» while at least one pick
  came from views;
* fewer than 3 viewed products, or nothing left in the viewed categories → the popular products (the
  catalog's «Popular» order, in stock, minus viewed and bought, 2 per category) and the subtitle «Popular
  right now»;
* the block comes after the Plus block on the home page, for a customer only: a guest gets no block, and
  nothing is read for them;
* a failure inside the block drops the block (logged); the home page still answers;
* the empty cart keeps «From today's deals» (feature-cart): this feature names only the home page;
* the seed gives Maya five views (three headphones, her cart's duvet cover set and mugs), so the stand
  draws `Home_Content`'s two headphones, two duvet covers and two mugs.

Numbers in these rules (20 views, 2 per category, 6 in all, 3 views) are decisions of the brief,
recorded in [research-architecture](../research/research-architecture.md) D6, and checked against the
code and the tests below.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt` — `SectionHeader` and `ProductGrid`, which draw the block |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/` — the rule (`domain/Recommendations.kt`: `PickedForYou`, `RecordView`), the views and the sources (`data/`), the block (`screen/PickedSection.kt`); `server/src/main/resources/db/migration/V20__product_views.sql`; Maya's views `server/src/main/kotlin/io/github/youndie/haul/seed/SampleViews.kt` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/` — draws the block like any section |

## 5. Scenarios (BDD / test cases)

### Scenario: From views
* **Given:** Maya viewed three headphones
* **When:** she opens the home page
* **Then:** «Picked for you» holds at most two headphones and none of the three she viewed.
* **Automated:** `RecommendationsRoutesTest.a customer who viewed three headphones is picked at most two and none of them` (`server/src/test/kotlin/io/github/youndie/haul/feature/recommendations/RecommendationsRoutesTest.kt`); the rule alone, `PickedForYouTest.three headphones viewed give at most two headphones and none of the three` (`server/src/test/kotlin/io/github/youndie/haul/feature/recommendations/PickedForYouTest.kt`)

### Scenario: Guest
* **Given:** no bearer
* **When:** the client opens `/ui/home`
* **Then:** the tree holds no «Picked for you» block and no views are read for the guest (there is no separate request: the block is part of `/ui/home`).
* **Automated:** `RecommendationsRoutesTest.a guest's home has no picked block`; `PickedSectionTest.a guest gets no block and nothing is read for them` (`server/src/test/kotlin/io/github/youndie/haul/feature/recommendations/PickedSectionTest.kt`)

### Scenario: Too few views
* **Given:** a customer who opened fewer than three products (four tabs of one product are one view)
* **Then:** the block is the popular row, «Popular right now».
* **Automated:** `RecommendationsRoutesTest.fewer than three products viewed is the popular row`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1 — a
  recommendations service of its own.

## 7. Quirks

* A guest's views are not kept, so a guest who signs in starts with none.
* Views recorded in one store instant tie and are ordered by product id (the tests' clock); on the stand
  the clock moves.
* The empty cart's canvas subtitle is «Based on your recent views» too (`Cart_Empty`); the server says
  «From today’s deals» there.
* The home bodies' picked cards are the canvas's; only the header and the grid's shape are held to the
  server (`PickedSectionTest.the home bodies draw the block the server builds`).
