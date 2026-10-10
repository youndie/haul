---
id: screen-search
title: Search
type: client_screen
platform: [web]
status: active
entry:
  web: "/search?q="
parent_feature: feature-search
calls_api:
  - endpoint-search
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Search)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Search_Loading
    Results: Search_Results
    Autocomplete: Search_Autocomplete
    NoResults: Search_NoResults
    Error: Search_Error
---

# Screen: Search

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/SearchViews.kt` (the no-results page; the suggest panel, which `SearchSuggestOverlay` draws over the page rather than the registry); `SearchNoResultsRenderer` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRenderers.kt`; the results page is the catalog's renderers |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`SearchLoading`, `SearchError`) |
| The search field, suggest requests, «Clear» | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |
| Parity fixtures, one per artboard | `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/SearchFixtures.kt` |

## 0. Entry point and visibility

- **Entry point:** `/search?q=` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** header with «running shoes» in the field, placeholders for the count, the title, six chips and ten cards
- [x] **Results:** «14,870 results» above «“Running shoes”» (`PageTitle.quoted`), category chips with counts, 10 cards (each writing its listing name, B-45); no panel. The artboard draws no pagination, and neither does its fixture body; the server's tree has `HaulPagination` under the grid, whose numbers and «Show 24 more» open their pages
- [x] **Autocomplete:** Results with the panel open over a scrim: suggestions, «In categories», «Recent», «Top products», «All 14,870 results ↵». «Recent» is drawn only when the panel carries recent searches, which the server fills for a signed-in customer only; its «Clear» sends `DELETE` to `SearchSuggestPanel.clearUrl` and asks for the panel again
- [x] **NoResults:** «0 results» above «Nothing found for “xqzt”», the three tips, «Popular *categories*» with eight tiles. The tips are shown when there are no query suggestions; when there are, the queries to try take their place (no artboard draws that form)
- [x] **Error:** header with the query still in the field, «Search didn’t *respond*», Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1288 / 1984; Results 1359 / 2196; Autocomplete 1359 / 2196; NoResults 869 / 1010; Error 900 / 692.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-search | [endpoint-search](../api/endpoint-search.md) |

## 5. Navigation (summary)

The server builds every target as a `NavigateAction` and the shell follows it (B-35). Typing in the
header's field asks for `/ui/search/suggest` after a 250 ms pause; Enter or the button opens
`/search?q=` — the one address the client builds itself. The panel stays open when the field loses
focus and closes on the scrim, an emptied field or a new page.

- suggestion → Results for it (`/search?q=`)
- category chip on Results → the same query narrowed (`/search?q=&category=`)
- category in the panel → screen-catalog (`/c/{slug}`)
- top product → screen-product (`/p/{productId}`)
- «All N results» → Results for the query
- page number, «Show 24 more» → the same query at that page (`/search?q=&page=`), category and sort kept
- a card's «+» → `PUT /api/v1/cart/lines/{skuId}` with the line's next quantity, then the page drawn again in place
- «Clear» → `DELETE /api/v1/me/recent-searches` (customer only; a guest is offered none), then the panel fetched again
- a recent search's own row → Results for that search: `SearchSuggestPanel.recent` carries each as a `Link` with its encoded `/search?q=` (B-49; `RecentSearchesRoutesTest.a recent search's row runs that search again`, the client's `DrawnActionsTest.a recent search's row runs that search`)

## 6. Quirks

- Between 768 and about 1,150 px the wide header's field is narrower than the panel's products
  column, and the panel's suggestions column collapses (B-35's findings); the canvas draws 1440 and
  390 only.
- Back loads the page again (no tree is kept per address) and opens it at the top.
