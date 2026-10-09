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

> Built: the home page's Plus block (B-23), «Picked for you» (B-25), the cards' hearts (B-20), the
> catalog's root and the last drawn controls (B-49), the filter sheet that stays open (B-54), listing
> names (B-45) and a Plus member's early access to campaign prices (B-53). Still planned, which keeps
> this document a draft: **a deal's price is what the cart charges** (B-57, in progress — today a deal
> card draws the deal's price while «+» puts the SKU into the cart at its own price) and **campaigns and
> deals end** (B-58 — today nothing reads `ends_at`).

## 2. Business rules

* the deals' countdown runs to the store's next local midnight (`America/New_York`, `calendar.midnight()` sent as `SectionHeader.countdownEndsAt`), computed by the client against one ticking «now»; **an ended deal does not disappear today** — nothing reads `deals.ends_at`, so every seeded deal is drawn on the stand whatever its date; *planned* (B-58): a deal's card and price end at its `ends_at`, and the seed dates its windows from the day it seeds;
* a deal card draws the deal's price (`deals.price_cents`) over the SKU's, but its «+» puts the SKU into the cart at the SKU's own price — the five generated deals advertise 70 % of what the cart charges (the Sony deal matches, $349); *planned* (B-57): a deal is a price of its SKU for its window, drawn and charged everywhere through the same pricing rule, the lower of a deal and a campaign winning;
* **a Plus member sees a campaign's prices early** (B-53, research D6 «Decided in B-53»): a SKU names its campaign (`skus.campaign_slug`, V26) and is stored at the campaign's price with the regular one as its old price; one rule (`CampaignPricing.priced`) prices every SKU on every read — cards, the product page, search, Saved, the cart, the quote and placement: until the campaign opens for the viewer the SKU is at its regular price with nothing struck through; it opens at `plus_early_access_at` for a member, a trial included (the Autumn mega sale's is 24 hours before its start), and at `starts_at` for everyone else; every read names whose prices it draws (`PriceList.Public` or `Plus`, no default); nothing is cached between viewers; a campaign's prices do not end yet (B-58);
* the Haul Plus block offers the trial to guests and non-members — «Try 30 days free» presents the trial dialog to a customer and sends a guest to `/sign-in` — and shows a member their delivery savings this year and the renewal date (B-23, [feature-membership](feature-membership.md));
* a customer's home page ends with «Picked for you» ([feature-recommendations](feature-recommendations.md)); a guest's has none;
* facet counts are computed over the current filter set minus the facet itself (the usual «what you would get if you ticked this»);
* 24 products per page; sort: popular (default), price ascending, price descending, rating, newest;
* a product card shows the product's **listing name** — what cards, cart lines and order lines write, its title unless it has one (`products.listing_name`, V25, B-45; only the headphones have one: «Sony WH-1000XM6 Wireless Noise Cancelling Headphones») — the cheapest in-stock `Sku`'s price, its old price and discount, rating, reviews count and the earliest delivery day to the customer's default address (guest: the store's default ZIP);
* a card's «+» puts one more of the card's `Sku` into the cart — the line's next quantity, so a press sent twice adds one — and is not offered at ten, at the stock or out of stock (B-37; `Cards.kt`);
* a card's heart keeps the product in the customer's Saved list or lets it go, and is drawn filled when it is there; a guest's leads to `/sign-in` (B-20, [feature-account](feature-account.md));
* a product with a stored photo shows it on its card and its page; without one, or while it loads or after it fails, the placeholder tile stays (B-30, research D8);
* `/deals` lists the day's deals (on its first page) and then every product whose shown price is under its old one, the deepest discount first, 24 a page (B-37, research D2);
* **the catalog's root** `/c` (B-49, research D2 «Decided in B-49»): «Catalog · 32 categories», every top-level category as home's tiles in the header's order — where home's «All N categories» leads;
* the brand facet lists its first brands and «Show N more» opens the same page with `expand=brand`, the filters, sort and page kept, the expansion kept on every address the page builds; only the brand facet expands (`400 validation_failed` otherwise);
* **the phone's filter sheet** is the one control wired in the client alone: «Filters» opens it, it stays open across the navigations its own presses cause and is redrawn from each new page, «Show N items», «×» and the scrim close it, and arriving at any other address closes it (B-49, B-54);
* a control with no page behind it — «Help», «Sell on HAUL», the language, the footer's links — is drawn as plain text, not as a link (B-49); the header's «HAUL PLUS» pill offers the trial, the account or sign-in ([feature-membership](feature-membership.md)).

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

### Scenario: The filter sheet stays open while filtering
* **Given:** the headphones category on a phone, the filter sheet open
* **When:** the shopper ticks two facets, then presses «Show N items»
* **Then:** the sheet stays open with «1 applied» then «2 applied» and the new results behind it; «Show N items» closes it with no request.
* **Automated:** `DrawnActionsTest.ticking two facets keeps the filter sheet open over the new results`, `DrawnActionsTest.show N items closes the filter sheet over its results`, `DrawnActionsTest.a page not opened from the filter sheet leaves it closed` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt`)

### Scenario: A member sees the sale early
* **Given:** Oct 6 at noon, a day before the Autumn mega sale starts
* **When:** Maya (a member), a guest and Sam open the headphones
* **Then:** Maya sees $349 under $449 on the product page and the card, the guest and Sam $449 with nothing struck through; from the sale's start everyone sees $349.
* **Automated:** `PlusEarlyAccessTest.inside the early window a member sees the sale price and a non-member the regular one`, `PlusEarlyAccessTest.from the start a non-member gets the sale price through to the order` (`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PlusEarlyAccessTest.kt`); the rule at its edges, `CampaignPricingTest.in the early window only a member has the campaign price` and the rest of `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/CampaignPricingTest.kt`

### Scenario: A card writes the listing name
* **Given:** the headphones, listed as «Sony WH-1000XM6 Wireless Noise Cancelling Headphones»
* **Then:** their card writes the listing name while the product page writes «Sony» above the title; a product with no listing name is listed by its title.
* **Automated:** `ListingNameTest.a card writes the listing name where the product page writes brand above title`, `ListingNameTest.a product with no listing name is listed by its title` (`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/ListingNameTest.kt`)

### Scenario: An unknown category
* **When:** the client opens `/ui/c/no-such-thing`
* **Then:** the server returns `404` with `category_not_found`.
* **Automated:** `CatalogRoutesTest.an unknown category is 404 category_not_found`

The catalog's root, «Show N more» and the plain-text controls are in the same `DrawnActionsTest`s
(`all categories on the home page opens the catalog root`, `show more lists every brand with the filters
the sort and the page kept`, `help sell on haul the language and the footer are plain text`). The controls' actions are covered by `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt`
and `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt`; photos and their
fallback by `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PhotoRoutesTest.kt` and
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/PhotoFallbackTest.kt`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
