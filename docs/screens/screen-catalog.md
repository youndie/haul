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

- **Entry point:** `/c/{categoryPath}` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** breadcrumbs and title, placeholder facet column and 12 card placeholders
- [x] **Content:** Headphones, 12,408 items, facets with Sony + Bose + $80–$400 + Noise cancelling applied, chips, sort «Popular», 12 cards, «Show 24 more», pages 1 2 3 … 517
- [x] **Empty:** same filters plus Marshall + Pink: «No items match these filters», Clear all, facets still visible
- [x] **FiltersSheet:** **phone only**: the facet column as a full-height sheet with «Show 48 items»
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
- the brand facet's «Show N more», the filter sheet's × → nothing yet (B-37's findings; B-49)
