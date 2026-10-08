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
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/search
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

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/search/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/server/search/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/search?q=` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** header with «running shoes», placeholder chips and cards
- [ ] **Results:** «Running shoes», 14,870 results, category chips with counts, 10 cards; no panel
- [ ] **Autocomplete:** Results with the panel open: suggestions, «In categories», «Recent», «Top products», «All 14,870 results ↵»
- [ ] **NoResults:** «Nothing found for "xqzt"», suggestions to try, popular categories
- [ ] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1288 / 1984; Results 1359 / 2196; Autocomplete 1359 / 2196; NoResults 869 / 1010; Error 900 / 692.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-search | [endpoint-search](../api/endpoint-search.md) |

## 5. Navigation (summary)

- suggestion → Results for it
- category → screen-catalog
- top product → screen-product
- «Clear» → empties Recent
