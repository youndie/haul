---
id: endpoint-search
title: Search
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared SearchSuggestPanel
  - haul:shared SearchNoResults
  - haul:shared ErrorCode
parent_feature: feature-search
---

# API: Search

> Drafted from the product brief; the two screen routes exist since B-09 and are described as the
> code has them. The `DELETE` route is still *target* (B-12). There are no route classes in
> `shared`: the paths are strings in `SearchRouting.kt`, and the contract is the components the
> routes answer and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/search` | haul-server | public (none; `X-Haul-Guest` is not read yet, B-11) | yes | request: `q`, `category`, `sort`, `page`; answers tree: the results page, or the no-results page |
| `GET` `/ui/search/suggest` | haul-server | public (none; `X-Haul-Guest` is not read yet, B-11) | yes | request: `q`; answers tree: `SearchSuggestPanel` |
| `DELETE` `/api/v1/me/recent-searches` | haul-server | customer (shildik bearer) | yes | *planned, B-12*: request: —; answers action: refresh the panel |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/search` | `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt` → `screen/SearchScreen.kt` (`results`) |
| `GET` `/ui/search/suggest` | `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt` → `screen/SearchScreen.kt` (`suggest`) |
| `DELETE` `/api/v1/me/recent-searches` | not built (B-12); the storage it will clear is `server/src/main/kotlin/io/github/youndie/haul/feature/search/data/ExposedRecentSearches.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SearchComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request parameters

* `q` — the query on both routes. It is trimmed, lower-cased and its inner spaces collapsed before
  anything else (`Query.of` in `feature/search/domain/Search.kt`); fewer than two characters after
  that, a missing `q` included, is `query_too_short`.
* `category` — `/ui/search` only: a category slug that narrows the grid to it; the chips and the
  count stay those of the whole query.
* `sort` — `/ui/search` only: the catalog's keys `popular` (the default), `price-asc`, `price-desc`,
  `rating`, `newest` (`Sort` in `feature/catalog/domain/Browse.kt`).
* `page` — `/ui/search` only: from 1.

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SearchComponents.kt`
and, for the parts the results page shares with the catalog,
`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`; not copied here. What
the server puts in them (`feature/search/screen/SearchScreen.kt`):

* **`/ui/search`, something matched** — the frame (`HaulHeader` with the query in its field) around
  `PageTitle` with `quoted = true` (the query, first letter upper-cased, under «N results»),
  `FilterChips` whose chips carry `Chip.count` («All» first, then the leaf categories, the most
  results first), a `ProductGrid` of the catalog's cards five across, and `HaulPagination`.
* **`/ui/search`, nothing matched** — `SearchNoResults` with `title` «Nothing found for “q”»,
  `accent` (the quoted query, drawn in the italic), `count` «0 results», `suggestions` (the queries
  to try; may be empty) and three `tips`; then `SectionHeader` «Popular categories» and a
  `CategoryGrid` of eight top-level categories.
* **`/ui/search/suggest`** — `SearchSuggestPanel`: up to five `suggestions`, each a
  `QuerySuggestion` split into `prefix` (what the term adds before the typed part), `typed` and
  `completion` («running sh» + «oes», «running » + «shoes»; a corrected spelling is all
  `completion`); up to three `categories` with counts, labelled «Sports › Running shoes»; up to three
  `products`; `allResultsLabel` «All N results» and its action. `recent` is filled for a signed-in
  customer only, and nothing signs one in until B-12, so it is empty today; `clearAction` is not set.

## Quirks

* A `category` that is not a category is `404 category_not_found` — but only when the query matched
  something; a query that matched nothing answers the no-results page first.
* A search is recorded in recent searches only for a signed-in customer (`Viewer.customerId`), which
  nothing sets until B-12.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/search` | `400` query_too_short (field `q`), `400` validation_failed (field `sort` or `page`), `404` category_not_found |
| `GET` `/ui/search/suggest` | `400` query_too_short (field `q`) |
| `DELETE` `/api/v1/me/recent-searches` | `401` (planned) |
