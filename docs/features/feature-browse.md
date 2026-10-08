---
id: feature-browse
title: Home and category catalog
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-home
  - screen-catalog
api:
  - endpoint-catalog
  - endpoint-membership
  - endpoint-recommendations
tags: []
---

# Home and category catalog

## 1. Overview

The home page sells: a campaign, two side banners, categories, deals of the day with a countdown, Haul Plus, and recommendations. A category page lists products with facet filters, sort and pages.

## 2. Business rules

* deals of the day end at local midnight in the store's time zone (`America/New_York`); the countdown is computed by the client from the server's `endsAt`, and an ended deal disappears on the next load;
* a Plus member sees a campaign's prices from its early-access start (24 hours before the public start);
* the Haul Plus block offers the trial to guests and non-members, and shows a member their delivery savings this year and the renewal date;
* facet counts are computed over the current filter set minus the facet itself (the usual «what you would get if you ticked this»);
* 24 products per page; sort: popular (default), price ascending, price descending, rating, newest;
* a product card shows the cheapest in-stock `Sku`'s price, its old price and discount, rating, reviews count and the earliest delivery day to the customer's default address (guest: the store's default ZIP).
* a card's «+» puts one more of the card's `Sku` into the cart — the line's next quantity, so a press sent twice adds one — and is not offered at ten, at the stock or out of stock (B-37; `Cards.kt`);
* a product with a stored photo shows it on its card and its page; without one, or while it loads or after it fails, the placeholder tile stays (B-30, research D8);
* `/deals` lists the day's deals (on its first page) and then every product whose shown price is under its old one, the deepest discount first, 24 a page (B-37, research D2).

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductPhoto.kt` — the photo over the placeholder tile |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Facets narrow the list
* **Given:** the `headphones` category
* **When:** the client asks for brands Sony and Bose, price 80…400 and feature «noise cancelling»
* **Then:** every product returned matches all four and the total is the count shown above the grid.
* **Automated:** `CatalogRoutesTest.facets narrow the list`

### Scenario: A filter set with no products
* **Given:** the same category
* **When:** the client asks for brand Marshall and colour «Pink» together
* **Then:** the server returns `200` with an empty list and total 0, and the facets still list their values.
* **Automated:** `CatalogRoutesTest.a filter set with no products answers an empty page with its facets`

### Scenario: An unknown category
* **When:** the client opens `/ui/c/no-such-thing`
* **Then:** the server returns `404` with `category_not_found`.
* **Automated:** `CatalogRoutesTest.an unknown category is 404 category_not_found`

The controls' actions are covered by `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt`
and `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt`; photos and their
fallback by `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PhotoRoutesTest.kt` and
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/PhotoFallbackTest.kt`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
