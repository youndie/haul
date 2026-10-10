---
id: endpoint-catalog
title: Home, deals, category, product, photos
type: api_endpoints
status: active
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
> with B-37; a customer's Plus block with B-23, the hearts with B-20, the product page's «Add to
> cart» and «Buy now» with B-48, «Picked for you» and the recorded view with B-25; the catalog's root
> `/ui/c`, `expand=brand` and the header's «HAUL PLUS» pill with B-49, «bought this month» with B-52,
> listing names with B-45, a member's early campaign prices with B-53. There are no route classes in
> `shared`: the paths are strings in `CatalogRouting.kt` (and `Frame.DEALS`, `ProductPhotos.PATH`),
> and the contract is the components the routes answer and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: —; answers tree: Home — the Plus block per viewer, and «Picked for you» for a customer ([endpoint-recommendations](endpoint-recommendations.md)) |
| `GET` `/ui/deals` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: `page`; answers tree: Deals ([screen-deals](../screens/screen-deals.md)) |
| `GET` `/ui/c` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: —; answers tree: the catalog's root — «Home / Catalog», «Catalog · 32 categories», every top-level category as home's tiles in the header's order, the footer (B-49; the storefront's address is `/c`, `StorefrontPage.Categories`) |
| `GET` `/ui/c/{categoryPath}` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: filters, sort, page, `expand`; answers tree: Catalog |
| `GET` `/ui/p/{productId}` | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: `sku`, `tab` (description / specifications / reviews / questions); answers tree: Product; records a view for a customer, beside the page's reads (B-25, [endpoint-recommendations](endpoint-recommendations.md)) |
| `GET` `/images/{key...}` | haul-server | public (optional bearer, as every catalog route) | no — bytes, not a tree | request: —; answers the stored photo under the `products/<key>` prefix with its image media type, `Cache-Control: public, max-age=31536000, immutable` and `X-Content-Type-Options: nosniff` |

The screen routes read the caller (`Viewers` in `server/src/main/kotlin/io/github/youndie/haul/shell/Viewers.kt`)
for the header — the first name, the cart's count, `/sign-in` or `/account`, the «HAUL PLUS» pill — for
each card's «+», which depends on what the viewer's cart already holds, and for the prices: a member's
(`Viewer.prices`, `PriceList.Plus`, a trial included) open a campaign at its early-access time,
everyone else's (`PriceList.Public`) at its start (B-53). Nothing is cached between viewers: every tree
is built per request and the `/ui/*` answers carry no `Cache-Control`.

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/home` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt` |
| `GET` `/ui/deals` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt` |
| `GET` `/ui/c` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `CatalogScreen.root` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`) |
| `GET` `/ui/c/{categoryPath}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` (`catalogRequest`) → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt` |
| `GET` `/ui/p/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`; «bought this month» from `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/BoughtThisMonth.kt` over `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/ExposedProductSales.kt` |
| prices | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/CampaignPricing.kt` (`PriceList`, `CampaignPricing.priced`), applied by `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/ExposedCatalogRepository.kt` to every read that returns SKUs; `server/src/main/resources/db/migration/V26__sku_campaigns.sql` |
| `GET` `/images/{key...}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `ProductPhotos.read` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Photos.kt`) over `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/S3PhotoStore.kt` |
| cards, «+», pages | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt` (`card`, `addToCart`, `dealCards`, `pagination`) |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request parameters

* `/ui/c/{categoryPath}` — the path's last segment is the category slug (`/ui/c/electronics/audio/headphones`
  and `/ui/c/headphones` are the same page). Filters, each refused by name when malformed: `brand`,
  `feature`, `colour` (repeatable), `price_min`, `price_max` (whole dollars), `kind`,
  `delivery=tomorrow`, `rating` (`4.5` or `4.0`); `sort` (`popular` by default, `price-asc`,
  `price-desc`, `rating`, `newest`); `page` from 1; `expand=brand` lists every brand in the brand facet
  (any other value is `400 validation_failed`, field `expand`) (`catalogRequest` in `CatalogRouting.kt`).
* `/ui/p/{productId}` — `sku` (one of this product's; the cheapest in stock when absent), `tab`.
* `/ui/deals` — `page` from 1; below 1 or not a number is `400 validation_failed` (field `page`).
* `/images/{key...}` — the object's key: under the `products/<key>` prefix, lower-case segments, no `..`
  (`ProductPhotos.servable`).

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` (`BrowseComponents.kt`,
`ProductComponents.kt`, `HaulComponents.kt`); not copied here. What changed on the wire in B-07 and
B-08, and what the server fills:

* **The header** (`server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, B-12 and B-37) —
  `HaulHeader.account` (`/sign-in` for a guest, `/account` for a customer), `catalog` (every top-level
  category as a `Link`: «Catalog» opens them as a menu, the first ten are the category row), `deals`
  (`/deals`), `cart` (`/cart`), `customerName` for a customer, and `plus`, the «HAUL PLUS» pill (B-49):
  the trial dialog's `present` for a customer who is not a member, `/account` for a member, `/sign-in`
  for a guest. «Help», «Sell on HAUL», the language and the footer's links carry nothing and are drawn
  as plain text until a page exists for them (research D2, «Decided in B-49»).
* **Home** — `CampaignRow` (the campaign with its two banners, one component because the two widths
  arrange them differently), `SectionHeader` + `CategoryGrid` (eight tiles; «All N categories», on a
  phone `compactLinkLabel` «All N», → `/c`, B-49), `SectionHeader` «Deals of the day» with
  `countdownEndsAt` — the store's next local midnight, not a deal's own end — and «View all deals» →
  `/deals`, a `ProductGrid` with `scroll = true`, the
  `PlusBlock` (B-23, `server/src/main/kotlin/io/github/youndie/haul/feature/membership/screen/PlusOffer.kt`)
  — the offer to a guest and a non-member, whose «Try 30 days free» is `navigate /sign-in` for a guest
  and kompot's `present` of the `PlusTrialDialog` for a customer, or a member's savings this year and
  renewal («You saved $186 on delivery this year», «Renews Nov 2») — and, for a customer, «Picked for
  you» ([endpoint-recommendations](endpoint-recommendations.md)).
* **Deals** — `PageTitle` «Deals» / «N items on sale»; on page 1 only, `SectionHeader` «Deals of the
  day» with the countdown and the day's deals; `SectionHeader` «On sale» and a `ProductGrid` of every
  product whose shown price is under its old one, deepest discount first (then the most reviews),
  24 a page; `HaulPagination`; the footer. Every seeded deal is drawn whatever its `ends_at` — nothing
  reads it until B-58 — and a deal card draws the deal's price (`deals.price_cents`) while its «+» puts
  the SKU into the cart at the SKU's own price, which B-57 makes one price.
* **Category** — `Breadcrumbs`, `PageTitle`, the kinds as `FilterChips`, then one `FilteredResults`
  holding the `FacetPanel`, the `AppliedFilters` and either the grid with its `HaulPagination` or an
  `EmptyState`; `showLabel` is the phone sheet's «Show N items». `AppliedFilters.sorts` is one `Link`
  per order (filters kept, page reset), `clearAction` the category without filters (sort kept). The
  brand facet's `Facet.moreAction` («Show N more») is the same page with `expand=brand`, filters, sort
  and page kept; the expansion stays on every address the page builds (a ticked brand, the sort, a
  page, «Clear all»), and an expanded facet has neither label nor action (B-49).
* **Cards** — a card writes the product's **listing name** (`products.listing_name`, the title when
  null, B-45: «Sony WH-1000XM6 Wireless Noise Cancelling Headphones»), on home, catalog, search, Saved,
  deals, «Picked for you» and the empty cart's picks; the product page keeps brand above title. Its
  price is the viewer's (above). `ProductCard.image` is `/images/products/…` when a photo is stored (and a store is
  configured), else absent and the tile is the placeholder. `ProductCard.add` is a `LineCommand`:
  `PUT /api/v1/cart/lines/{skuId}` ([endpoint-cart](endpoint-cart.md)) with the line's **next**
  quantity for the SKU whose price the card shows (a deal card's deal SKU) — absent when the cart
  holds ten, or the stock, or the SKU is out of stock; a press sent twice adds one. The heart is
  `ProductCard.heartCommand` — a `SaveCommand`, `PUT` or `DELETE /api/v1/me/saved/{productId}`
  ([endpoint-saved](endpoint-saved.md)), drawn filled (`saved`) when the product is in the viewer's
  list — or, for a guest, `heartAction`, a `navigate` to `/sign-in` (B-20).
* **Pagination** — `HaulPagination.moreAction` («Show 24 more») opens the next page, `links` every
  page number but the current one; the address keeps filters and sort, and page 1 has no `page=`. The price facet carries
  `rangeStart` / `rangeEnd`, the selection as fractions of a track from $0 to the category's dearest
  price, rounded up to the next $100 above it. A facet lists every value the category has, with 0 where the
  other filters rule it out, so the facets of an empty result are still there to undo it with
  (`Browse.counts`).
* **Product** — `Breadcrumbs`, `ProductDetails`, `ProductTabs`, and the open tab's
  `ProductDescription` or `SpecificationList`. `ProductDetails` gained `gallery`, `morePhotos`,
  `photoTotal`, `haulPayStrong`, `photo` (the stored photo's address, B-30), `bought` («12K bought this
  month», B-52: the product's units across its SKUs in `placed` orders of the last 30 store days, a
  returned order still counted, plus the seed's `products.bought_base` (V23); absent below 50; truncated
  as «840», «1.2K», «12K», «1.2M» by `compactCount`) and, for a SKU out of
  stock, `stockAdvice` in place of the delivery lines; the courier line reads the cut-off while the SKU is in stock. `TabLabel.compactTitle` is
  the phone's «Specs». `?tab=reviews` and `?tab=questions` answer `ProductReviews` and
  `ProductQuestions` from storage, and the tab row lists four tabs with the reviews' and questions'
  counts (B-22, [endpoint-reviews](endpoint-reviews.md)); their buttons carry the dialogs' `present`
  for a customer and `navigate` to `/sign-in` for a guest; each review carries «Helpful»'s
  `helpfulCommand` for a customer (B-43). `ProductDetails.add` and `.buy` are «Add to cart» and «Buy
  now» for the SKU shown (B-48, `LineCommand`s, `buy` with `next`: `/checkout`, or
  `/sign-in?next=%2Fcheckout` for a guest; both absent out of stock, `add` absent at the line's limit),
  and `heartCommand` / `heartAction` the heart and «Save». `ProductDescription` carries the product's headline as `title` and
  `accent` since B-33 (`products.headline`, `products.headline_accent`,
  `server/src/main/resources/db/migration/V6__product_headline.sql`); a catalogue seeded before V6
  has the product's title as its headline and no accent.
* **`accent`** — on `CampaignHero`, `PromoBanner`, `SectionHeader`, `PlusBlock`, `EmptyState` and
  `ProductDetails`: the words of the title drawn in Bodoni Moda's italic; it occurs in the title.
* **`Chip.count`** — the count a chip stands for; the search's category chips carry it, the catalog's
  kind chips do not.

## Quirks

* `tab` takes `description`, `specifications`, `reviews` and `questions`; anything else is `400 validation_failed` (field `tab`).
* The filter sheet's «×» and «Show N items» are the client's alone: the sheet is drawn over facets already in the tree, so nothing is asked of the server (B-49, B-54).
* Nothing reads `ends_at` of a deal or a campaign: ended deals are still drawn and a campaign's prices never end (B-58).
* `/ui/deals` reads the whole catalog per request, as a top-level category page reads its descendants.
* With no object storage configured (`HAUL_S3_ENDPOINT` unset) no `image` or `photo` is sent and every `/images/…` is `404`.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` | none of its own |
| `GET` `/ui/deals` | `400` validation_failed (field `page`) |
| `GET` `/ui/c` | none of its own |
| `GET` `/ui/c/{categoryPath}` | `400` validation_failed (field named; `expand` other than `brand`), `404` category_not_found |
| `GET` `/ui/p/{productId}` | `400` validation_failed (field `tab` or `sku`), `404` product_not_found |
| `GET` `/images/{key...}` | `404` with no body — no store, a key outside the `products/<key>` prefix, or no such object |

On every route, from the catch-all (`server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`,
B-32): a request Ktor itself cannot decode is `400` validation_failed («Malformed request», no field);
a database that cannot be reached is `503` unavailable; any other unhandled failure is `500` internal
(«Something went wrong», nothing of the exception in the body), reported to katcher when it is on.
`ErrorAnswersTest.a database that cannot be reached makes a screen 503 unavailable` runs the `503`
through `/ui/home`.

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt` (header,
pages, sort, «Clear all», «+», the deals page, «All N categories», «Show N more», the pill),
`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/BoughtThisMonthTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/ListingNameTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PlusEarlyAccessTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/PhotoRoutesTest.kt`
(the photo's address and its answer), `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/data/S3PhotoStoreTest.kt`.
