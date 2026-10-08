---
id: screen-search
title: Search
type: client_screen
platform: [web]
status: draft
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
- [x] **Results:** «14,870 results» above «“Running shoes”» (`PageTitle.quoted`), category chips with counts, 10 cards; no panel. The artboard draws no pagination, and neither does its fixture body; the server's tree has `HaulPagination` under the grid
- [x] **Autocomplete:** Results with the panel open over a scrim: suggestions, «In categories», «Recent», «Top products», «All 14,870 results ↵». «Recent» is drawn only when the panel carries recent searches, which the server fills for a signed-in customer only (B-12); its «Clear» is drawn and does nothing until then
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

The server builds every target as a `NavigateAction`; no client loader follows them yet — the app
has no navigation, and nothing fetches `/ui/search` or `/ui/search/suggest` as the shopper types (B-10, «Not done here»).

- suggestion → Results for it (`/search?q=`)
- category chip on Results → the same query narrowed (`/search?q=&category=`)
- category in the panel → screen-catalog (`/c/{slug}`)
- top product → screen-product (`/p/{productId}`)
- «All N results» → Results for the query
- «Clear» → empties Recent (B-12; no action is set until then)
