---
id: screen-catalog
title: Category
type: client_screen
platform: [web]
status: active
entry:
  web: "/c/{categoryPath}"
parent_feature: feature-browse
calls_api:
  - endpoint-catalog
  - endpoint-cart
  - endpoint-saved
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Catalog)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Catalog_Loading
    Content: Catalog_Content
    Empty: Catalog_Empty
    FiltersSheet: Catalog_FiltersSheet_Phone
    Error: Catalog_Error
---

# Screen: Category

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/` |
| Client shell: Loading and Error, navigation | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` (`Storefront.kt`, `Shell.kt`); the sort menu in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/LinkMenu.kt` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |
| Parity fixtures, one per artboard | `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/ScreenFixtures.kt` |

## 0. Entry point and visibility

- **Entry point:** `/c/{categoryPath}` in the browser. The catalog's root `/c` (`StorefrontPage.Categories`, `GET /ui/c`, B-49) — every top-level category as home's tiles — is a page of its own with no artboard, drawn as `PageKind.Other`; it has no states of this document's.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** breadcrumbs and title, placeholder facet column and 12 card placeholders
- [x] **Content:** Headphones, 12,408 items, facets with Sony + Bose + $80–$400 + Noise cancelling applied, chips, sort «Popular», 12 cards (each writing its listing name, B-45), «Show 24 more», pages 1 2 3 … 517
- [x] **Empty:** same filters plus Marshall + Pink: «No items match these filters», Clear all, facets still visible
- [x] **FiltersSheet:** **phone only**: the facet column as a full-height sheet with «Show 48 items». «Filters» opens it; it is the client's own state, held by the shell above the keyed page (`FiltersSheetState`, `LocalFiltersSheet`, `FiltersSheetOverlay`, B-54), so it stays open across the navigations its own presses cause and is drawn from each new page («N applied», the ticks, «Show N items»)
- [x] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1717 / 2356; Content 1940 / 2789; Empty 1646 / 852; Error 900 / 692; FiltersSheet — / 1467.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-catalog | [endpoint-catalog](../api/endpoint-catalog.md) |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |

## 5. Navigation (summary)

Every control below is an action the server puts in the tree; the shell follows it to a new address
(B-35, B-37) and loads that page — nothing is filtered in the client.

- facet tick → the same category with the filter added or removed (`/c/{slug}?…`)
- chip × → the same category without that filter
- sort → a menu of the five orders (`AppliedFilters.sorts`); the one picked opens with the filters kept and the page reset
- «Clear all» → the category without filters, the sort kept (wide, phone and the filter sheet)
- page number → that page; «Show 24 more» → the next page (an address of its own, not appended in place)
- card → screen-product (`/p/{productId}`)
- «+» on a card → `PUT /api/v1/cart/lines/{skuId}` with the line's next quantity; absent at ten, at the stock limit and out of stock; the page is drawn again in place
- crumb → that category or home
- heart → `PUT` / `DELETE /api/v1/me/saved/{productId}`, then the page drawn again; a guest's → `/sign-in` (B-20)
- the brand facet's «Show N more» → the same page with `expand=brand` (`Facet.moreAction`), filters, sort and page kept; the expansion stays on every address the page builds, so the list does not fold after a tick (B-49)
- «Filters» (phone) → opens the filter sheet, in the client, with no request (B-49)
- in the sheet: a facet tick, «Show N more», «Clear all» → their pages as above, **the sheet staying open** over the new results; until the page a tick opened arrives the facets follow nothing (the old page's addresses lack that tick), «×» still works (B-54)
- the sheet's «Show N items» (`FilteredResults.showLabel`), «×» and the scrim → close the sheet over the results, with no request and the address unchanged (B-49, B-54)
- back, forward, a link outside the sheet → the sheet closes; a page drawn while it is closed never opens it (B-54)

Tests: `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt` — `show more on the brand facet opens the page with the facet expanded`, `the filter sheet's close closes it without asking the server`, `ticking two facets keeps the filter sheet open over the new results`, `show N items closes the filter sheet over its results`, `a page not opened from the filter sheet leaves it closed`, `the filter sheet follows nothing while the page its tick opened is on its way`; the server's `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt` — `show more lists every brand with the filters the sort and the page kept`, `a facet other than the brand's does not expand`.

## 6. Quirks

- A page that fails to load after a tick leaves the sheet open and inert over the error until «×» closes it (B-54).
