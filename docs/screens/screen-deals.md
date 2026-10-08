---
id: screen-deals
title: Deals
type: client_screen
platform: [web]
status: draft
entry:
  web: "/deals"
parent_feature: feature-browse
calls_api:
  - endpoint-catalog
  - endpoint-cart
source: haul/server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen
---

# Screen: Deals

> Built in B-37 as the place «Deals», «View all deals», «Shop the sale» and the empty cart's picks
> lead to (research D2, «Decided in B-37»). **No artboard draws it**: it is assembled from components
> other screens draw, so it has no `design` block, no parity reference and no golden. It stays a
> draft until the canvas draws it or the owner accepts it as built.

## 0a. Code anchors

| What | File |
|---|---|
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt` (cards and pages from `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`) |
| Renderers | none of its own: `PageTitle`, `SectionHeader`, `ProductGrid`, `HaulPagination` and the footer are the home and catalog renderers (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRenderers.kt`) |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` — `/deals` is `PageKind.Other` |
| The address list | `shared/src/commonMain/kotlin/io/github/youndie/haul/StorefrontPage.kt` (`Deals`: a reloaded or shared `/deals` answers the page) |

## 0. Entry point and visibility

- **Entry point:** `/deals` in the browser, `?page=` from 2 on; tree at `GET /ui/deals`.
- **Shown when:** always; guests included.

## 1. Screen states

No artboard, so the states are the tree's and the shell's:

- [x] **Loading:** the shell's generic placeholder — the header pending, nothing below (`PageKind.Other`)
- [x] **Content, page 1:** «Deals» over «N items on sale», «Deals of the *day*» with the countdown to midnight and the day's deal cards (six across), «On *sale*» and 24 cards of everything whose shown price is under its old one, deepest discount first, the pages, the footer
- [x] **Content, page 2 on:** the same without the day's deals
- [x] **Error:** «This page didn’t *load*» with Retry (`ErrorShell`); the reason reads «We couldn’t reach Haul…» when nothing answered

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| `GET /ui/deals` | [endpoint-catalog](../api/endpoint-catalog.md) |
| a card's «+» | [endpoint-cart](../api/endpoint-cart.md) |

## 5. Navigation (summary)

- card → screen-product (`/p/{productId}`)
- «+» on a card → `PUT /api/v1/cart/lines/{skuId}` with the line's next quantity (a deal card's deal SKU), then the page drawn again in place
- page number, «Show 24 more» → `/deals?page=n`
- the header → as on every screen (screen-home)

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt` (`the
deals page shows today's deals and everything on sale deepest first`, `page zero of the deals is
refused`), `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt` (`view
all deals opens the deals page`).
