---
id: endpoint-catalog
title: Home, category, product
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

# API: Home, category, product

> Drafted from the product brief; the three routes exist since B-05 and their trees took their
> current shape in B-07 and B-08. What a customer would see differently (the member's Plus block,
> «Picked for you», a recorded view) is still *target*. There are no route classes in `shared`: the
> paths are strings in `CatalogRouting.kt`, and the contract is the components the routes answer and
> `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` | haul-server | public (none; `X-Haul-Guest` is not read here yet, B-12) | yes | request: —; answers tree: Home (with recommendations and the Plus block for a customer — *target*: today every viewer gets the guest's page) |
| `GET` `/ui/c/{categoryPath}` | haul-server | public (none; `X-Haul-Guest` is not read here yet, B-12) | yes | request: filters, sort, page; answers tree: Catalog |
| `GET` `/ui/p/{productId}` | haul-server | public (none; `X-Haul-Guest` is not read here yet, B-12) | yes | request: `sku`, `tab` (description / specifications / reviews / questions); answers tree: Product; records a view for a customer (*target*, see Quirks) |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/home` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt` |
| `GET` `/ui/c/{categoryPath}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` (`catalogRequest`) → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt` |
| `GET` `/ui/p/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request parameters

* `/ui/c/{categoryPath}` — the path's last segment is the category slug (`/ui/c/electronics/audio/headphones`
  and `/ui/c/headphones` are the same page). Filters, each refused by name when malformed: `brand`,
  `feature`, `colour` (repeatable), `price_min`, `price_max` (whole dollars), `kind`,
  `delivery=tomorrow`, `rating` (`4.5` or `4.0`); `sort` (`popular` by default, `price-asc`,
  `price-desc`, `rating`, `newest`); `page` from 1 (`catalogRequest` in `CatalogRouting.kt`).
* `/ui/p/{productId}` — `sku` (one of this product's; the cheapest in stock when absent), `tab`.

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` (`BrowseComponents.kt`,
`ProductComponents.kt`, `HaulComponents.kt`); not copied here. What changed on the wire in B-07 and
B-08, and what the server fills:

* **Home** — `CampaignRow` (the campaign with its two banners, one component because the two widths
  arrange them differently), `SectionHeader` + `CategoryGrid` (eight tiles; `compactLinkLabel` «All N»
  is what the link reads on a phone), `SectionHeader` with the deals' `countdownEndsAt` + a
  `ProductGrid` with `scroll = true`, and the guest's `PlusBlock`.
* **Category** — `Breadcrumbs`, `PageTitle`, the kinds as `FilterChips`, then one `FilteredResults`
  holding the `FacetPanel`, the `AppliedFilters` and either the grid with its `HaulPagination` or an
  `EmptyState`; `showLabel` is the phone sheet's «Show N items». The price facet carries
  `rangeStart` / `rangeEnd`, the selection as fractions of a track from $0 to the category's dearest
  price, rounded up to the next $100 above it. A facet lists every value the category has, with 0 where the
  other filters rule it out, so the facets of an empty result are still there to undo it with
  (`Browse.counts`).
* **Product** — `Breadcrumbs`, `ProductDetails`, `ProductTabs`, and the open tab's
  `ProductDescription` or `SpecificationList`. `ProductDetails` gained `gallery`, `morePhotos`,
  `photoTotal`, `haulPayStrong` and, for a SKU out of stock, `stockAdvice` in place of the delivery
  lines; the courier line reads the cut-off while the SKU is in stock. `TabLabel.compactTitle` is
  the phone's «Specs». `ProductReviews` and `ProductQuestions` exist in the contract and no route
  answers them yet (B-22). `ProductDescription` carries the product's headline as `title` and
  `accent` since B-33 (`products.headline`, `products.headline_accent`,
  `server/src/main/resources/db/migration/V6__product_headline.sql`); a catalogue seeded before V6
  has the product's title as its headline and no accent.
* **`accent`** — on `CampaignHero`, `PromoBanner`, `SectionHeader`, `PlusBlock`, `EmptyState` and
  `ProductDetails`: the words of the title drawn in Bodoni Moda's italic; it occurs in the title.
* **`Chip.count`** — the count a chip stands for; the search's category chips carry it, the catalog's
  kind chips do not.

## Quirks

* `tab` takes `description` and `specifications` only; `reviews` and `questions` answer `400 validation_failed` (field `tab`) until feature-reviews lands (B-22), and the tab row lists only those two.
* No view is recorded: the route builds the page for a `Viewer()` with no customer, and nothing in `server/` stores a `ProductView` yet.
* `/ui/home` answers every viewer the guest's page: no «Picked for you», and the Plus block offering the trial.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` | none of its own |
| `GET` `/ui/c/{categoryPath}` | `400` validation_failed (field named), `404` category_not_found |
| `GET` `/ui/p/{productId}` | `400` validation_failed (field `tab` or `sku`), `404` product_not_found |

On every route, from the catch-all (`server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`,
B-32): a request Ktor itself cannot decode is `400` validation_failed («Malformed request», no field);
a database that cannot be reached is `503` unavailable; any other unhandled failure is `500` internal
(«Something went wrong», nothing of the exception in the body), reported to katcher when it is on.
`ErrorAnswersTest.a database that cannot be reached makes a screen 503 unavailable` runs the `503`
through `/ui/home`.
