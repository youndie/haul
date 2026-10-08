---
id: endpoint-catalog
title: Home, deals, category, product, photos
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared FilteredResults
  - haul:shared ProductDetails
  - haul:shared ErrorCode
parent_feature: feature-browse
---

# API: Home, deals, category, product, photos

> Drafted from the product brief; the three browse routes exist since B-05 and their trees took
> their current shape in B-07 and B-08; photos arrived with B-30, the controls' actions and `/ui/deals`
> with B-37. What a customer would see differently (the member's Plus block, «Picked for you», a
> recorded view) is still *target*, which keeps this document a draft. There are no route classes in
> `shared`: the paths are strings in `CatalogRouting.kt` (and `Frame.DEALS`, `ProductPhotos.PATH`),
> and the contract is the components the routes answer and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: —; answers tree: Home (with recommendations and the member's Plus block for a customer — *target*: today a customer gets the guest's page without the Plus offer) |
| `GET` `/ui/deals` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: `page`; answers tree: Deals ([screen-deals](../screens/screen-deals.md)) |
| `GET` `/ui/c/{categoryPath}` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: filters, sort, page; answers tree: Catalog |
| `GET` `/ui/p/{productId}` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: `sku`, `tab` (description / specifications / reviews / questions); answers tree: Product; records a view for a customer (*target*, see Quirks) |
| `GET` `/images/{key...}` | haul-server | public (optional bearer, as every catalog route) | no — bytes, not a tree | request: —; answers the stored photo under `products/` with its image media type, `Cache-Control: public, max-age=31536000, immutable` and `X-Content-Type-Options: nosniff` |

The screen routes read the caller (`Viewers` in `server/src/main/kotlin/io/github/youndie/haul/shell/Viewers.kt`)
for the header — the first name, the cart's count, `/sign-in` or `/account` — and for each card's «+»,
which depends on what the viewer's cart already holds.

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/home` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt` |
| `GET` `/ui/deals` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt` |
| `GET` `/ui/c/{categoryPath}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` (`catalogRequest`) → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt` |
| `GET` `/ui/p/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt` |
| `GET` `/images/{key...}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `ProductPhotos.read` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Photos.kt`) over `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/S3PhotoStore.kt` |
| cards, «+», pages | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt` (`card`, `addToCart`, `dealCards`, `pagination`) |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request parameters

* `/ui/c/{categoryPath}` — the path's last segment is the category slug (`/ui/c/electronics/audio/headphones`
  and `/ui/c/headphones` are the same page). Filters, each refused by name when malformed: `brand`,
  `feature`, `colour` (repeatable), `price_min`, `price_max` (whole dollars), `kind`,
  `delivery=tomorrow`, `rating` (`4.5` or `4.0`); `sort` (`popular` by default, `price-asc`,
  `price-desc`, `rating`, `newest`); `page` from 1 (`catalogRequest` in `CatalogRouting.kt`).
* `/ui/p/{productId}` — `sku` (one of this product's; the cheapest in stock when absent), `tab`.
* `/ui/deals` — `page` from 1; below 1 or not a number is `400 validation_failed` (field `page`).
* `/images/{key...}` — the object's key: under `products/`, lower-case segments, no `..`
  (`ProductPhotos.servable`).

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` (`BrowseComponents.kt`,
`ProductComponents.kt`, `HaulComponents.kt`); not copied here. What changed on the wire in B-07 and
B-08, and what the server fills:

* **The header** (`server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, B-12 and B-37) —
  `HaulHeader.account` (`/sign-in` for a guest, `/account` for a customer), `catalog` (every top-level
  category as a `Link`: «Catalog» opens them as a menu, the first ten are the category row), `deals`
  (`/deals`), `cart` (`/cart`), and `customerName` for a customer.
* **Home** — `CampaignRow` (the campaign with its two banners, one component because the two widths
  arrange them differently), `SectionHeader` + `CategoryGrid` (eight tiles; `compactLinkLabel` «All N»
  is what the link reads on a phone), `SectionHeader` «Deals of the day» with the deals'
  `countdownEndsAt` and «View all deals» → `/deals`, a `ProductGrid` with `scroll = true`, and — for
  a guest only — the `PlusBlock` offering the trial.
* **Deals** — `PageTitle` «Deals» / «N items on sale»; on page 1 only, `SectionHeader` «Deals of the
  day» with the countdown and the day's deals; `SectionHeader` «On sale» and a `ProductGrid` of every
  product whose shown price is under its old one, deepest discount first (then the most reviews),
  24 a page; `HaulPagination`; the footer.
* **Category** — `Breadcrumbs`, `PageTitle`, the kinds as `FilterChips`, then one `FilteredResults`
  holding the `FacetPanel`, the `AppliedFilters` and either the grid with its `HaulPagination` or an
  `EmptyState`; `showLabel` is the phone sheet's «Show N items». `AppliedFilters.sorts` is one `Link`
  per order (filters kept, page reset), `clearAction` the category without filters (sort kept).
* **Cards** — `ProductCard.image` is `/images/products/…` when a photo is stored (and a store is
  configured), else absent and the tile is the placeholder. `ProductCard.add` is a `LineCommand`:
  `PUT /api/v1/cart/lines/{skuId}` ([endpoint-cart](endpoint-cart.md)) with the line's **next**
  quantity for the SKU whose price the card shows (a deal card's deal SKU) — absent when the cart
  holds ten, or the stock, or the SKU is out of stock; a press sent twice adds one.
* **Pagination** — `HaulPagination.moreAction` («Show 24 more») opens the next page, `links` every
  page number but the current one; the address keeps filters and sort, and page 1 has no `page=`. The price facet carries
  `rangeStart` / `rangeEnd`, the selection as fractions of a track from $0 to the category's dearest
  price, rounded up to the next $100 above it. A facet lists every value the category has, with 0 where the
  other filters rule it out, so the facets of an empty result are still there to undo it with
  (`Browse.counts`).
* **Product** — `Breadcrumbs`, `ProductDetails`, `ProductTabs`, and the open tab's
  `ProductDescription` or `SpecificationList`. `ProductDetails` gained `gallery`, `morePhotos`,
  `photoTotal`, `haulPayStrong`, `photo` (the stored photo's address, B-30) and, for a SKU out of
  stock, `stockAdvice` in place of the delivery lines; the courier line reads the cut-off while the SKU is in stock. `TabLabel.compactTitle` is
  the phone's «Specs». `?tab=reviews` and `?tab=questions` answer `ProductReviews` and
  `ProductQuestions` from storage, and the tab row lists four tabs with the reviews' and questions'
  counts (B-22, [endpoint-reviews](endpoint-reviews.md)); their buttons carry the dialogs' `present`
  for a customer and `navigate` to `/sign-in` for a guest. `ProductDescription` carries the product's headline as `title` and
  `accent` since B-33 (`products.headline`, `products.headline_accent`,
  `server/src/main/resources/db/migration/V6__product_headline.sql`); a catalogue seeded before V6
  has the product's title as its headline and no accent.
* **`accent`** — on `CampaignHero`, `PromoBanner`, `SectionHeader`, `PlusBlock`, `EmptyState` and
  `ProductDetails`: the words of the title drawn in Bodoni Moda's italic; it occurs in the title.
* **`Chip.count`** — the count a chip stands for; the search's category chips carry it, the catalog's
  kind chips do not.

## Quirks

* `tab` takes `description`, `specifications`, `reviews` and `questions`; anything else is `400 validation_failed` (field `tab`).
* No view is recorded: nothing in `server/` stores a `ProductView` yet.
* `/ui/home` answers a customer the guest's page without the Plus offer: no «Picked for you», no member's Plus block.
* The product page's «Add to cart» and «Buy now», home's «All N categories», the brand facet's «Show N more», the header strip's links and the footer carry no action (B-37's findings).
* `/ui/deals` reads the whole catalog per request, as a top-level category page reads its descendants.
* With no object storage configured (`HAUL_S3_ENDPOINT` unset) no `image` or `photo` is sent and every `/images/…` is `404`.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` | none of its own |
| `GET` `/ui/deals` | `400` validation_failed (field `page`) |
| `GET` `/ui/c/{categoryPath}` | `400` validation_failed (field named), `404` category_not_found |
| `GET` `/ui/p/{productId}` | `400` validation_failed (field `tab` or `sku`), `404` product_not_found |
| `GET` `/images/{key...}` | `404` with no body — no store, a key outside `products/`, or no such object |

On every route, from the catch-all (`server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`,
B-32): a request Ktor itself cannot decode is `400` validation_failed («Malformed request», no field);
a database that cannot be reached is `503` unavailable; any other unhandled failure is `500` internal
(«Something went wrong», nothing of the exception in the body), reported to katcher when it is on.
`ErrorAnswersTest.a database that cannot be reached makes a screen 503 unavailable` runs the `503`
through `/ui/home`.

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt` (header,
pages, sort, «Clear all», «+», the deals page), `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PhotoRoutesTest.kt`
(the photo's address and its answer), `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/data/S3PhotoStoreTest.kt`.
