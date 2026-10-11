---
id: B-103
title: "server: the search panel keeps the query and the scope"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-103 — server: the search panel keeps the query and the scope

Found in the QA pass of 2026-10-10 (v0.3.0), code audit:
- «In categories · Sports › Running shoes 12» counts the query's matches but opens the whole category without the
  query (`server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt:94`) — it could open `/search?q=…&category=`;
- the panel ignores the picker's scope (B-72): «All N results» and the counts are unscoped and a suggestion drops the
  scope (`SearchScreen.kt:84, 107-110`);
- the sort defaults to «Popular» and drops the database's relevance order; there is no relevance sort
  (`SearchScreen.kt:145`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Browse.kt:128`).

- AC: category suggestions keep the query; the panel follows the scope; a «Relevance» sort is the default for a
  search; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt`.

## Findings

- **Category suggestions keep the query**: «Sports › Running shoes 12» opens `/search?q=…&category=running-shoes`, whose
  chosen chip counts the same 12 (`SearchScreen.suggest`).
- **The panel follows the scope**: the client asks for `/ui/search/suggest?q=…&category=<slug>` in the picker's scope
  (`Address.suggest`, `Storefront`), and again when the picker changes it. The products, the categories with their
  counts and «All N results» are the matches under the scope; a query suggestion, a recent search and «All N results»
  open their search in it. A recent search keeps the scope too: the row is a search the field runs, and the field's
  searches are in its scope. A scope the catalog does not have is `404 category_not_found`, as on the results, and the
  client shows no panel. The query suggestions themselves (category, brand and kind names) stay unscoped.
- **Relevance**: the search's own order, «Relevance», is the first sort and the default, the order
  `PostgresSearchRepository.matching` returns (`ts_rank`, then the most reviewed, then the id). It is not a `Sort`:
  a category has no relevance, so the catalog's sorts and default are untouched; on a search a `null` sort is
  relevance, its address has no `sort` (`sort=relevance` is taken too), and «Popular» is now `sort=popular`. On the
  seed the two orders mostly agree — the ranks tie and both break ties by reviews — and differ where `ts_rank` does
  («pro», «classic»).
- **Inert chips** (B-102's rule, from its findings): the search's chosen chip — «All», a leaf, the picker's scope —
  and the sort shown carry no action.
- Recorded in research-architecture (the B-77 sort bullet and a B-103 bullet under «Decided in B-72»).
- Left as it was: the results' title counts every match of the query («120 results») while a scoped page's grid and
  its scope chip count the scope — so «All 30 results» in a scoped panel opens a page titled with the larger number.
  The title was B-72's choice and is outside this item's criteria. The client's `search_results.json` fixture (and
  the Search_Results goldens) still draw «Popular» as the sort shown: a state the shopper can choose, so nothing was
  re-recorded.
- Tests: `SearchPanelScopeTest` (five), `DrawnActionsTest` (server: the sort list starts with «Relevance»; client: the
  picker's test now asks for the scoped panel), `AddressTest`. Mutation: the category suggestion back to
  `categoryLink`, the scope filter dropped from the panel, «Popular» as the order without a sort, the chosen chip's
  action kept, the shown sort's action kept, and the client asking for the panel without the scope each turned its
  test red.
