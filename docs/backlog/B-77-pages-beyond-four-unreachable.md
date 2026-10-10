---
id: B-77
title: "client + server: every page of results can be reached"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-77 — client + server: every page of results can be reached

Pagination draws 1, 2, 3, «…» and the last page only (`Cards.kt:156-182`, `CatalogViews.kt:510-535`): from page 5
on the current page and its neighbours are not drawn, so the only way forward is «Show 24 more» — which replaces
the grid with the next page instead of appending, as its label suggests (`Cards.kt:167-168`).
Search has no sort control though the server parses `sort` (`SearchRouting.kt:47`). The no-results tips
(«01 Check the spelling», …) are drawn like the pressable suggestion pills (`SearchViews.kt:96-111`). Arrow keys do
not move the suggestion highlight (`Storefront.kt:232`).

- **Decided as product owner:** the pagination shows the current page with its neighbours; «Show N more» appends.
- AC: tests for page 7 of a long listing; «Show more» keeps the earlier cards; search has the sort menu; tips read as
  text; arrow keys and Enter pick a suggestion.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/CatalogViews.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/SearchViews.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt`.

## Findings (2026-10-10)

- **How «Show N more» appends, decided here.** kompot's `update` replaces a node by its id and has no «append»,
  so the append is an address, not an action: `?page=<next>&from=<first>` draws every page from `from` to `page`
  in one grid (`Page.of`, `Browse.page`), and «Show N more» under a grid of pages `m..n` goes to `page=n+1&from=m`.
  The press stays the screen's `load` (B-63); the answer's grid holds the earlier cards and the new ones, so the
  page drawn after the `update` is still the whole page at its address (`PartsRoutesTest` follows every such
  load), and a reload or a shared link draws the same grid. A page number opens its page alone; a filter, a sort
  or a category starts from the first page; the brand facet's «Show N more» keeps the pages. Considered and not
  taken: a chain of per-page grids where each `update` fills an empty «next» slot — constant bytes per press,
  but a contract change for `FilteredResults` and every grid renderer, and the catalog's parts are the whole
  `results` node anyway. Cost taken instead: each press sends every card the grid holds (the seed's «everyday»
  ends at 192); no cap. `from` outside `1..page` is `400 validation_failed` on the category, the search and the
  deals. «N» is what the next page holds (24, fewer before the last).
- **The deals' first page.** A grid that starts at the first page has «Deals of the day» above it, which an
  `update` cannot add or remove, so «Show N more» there is `navigate` to `/deals?page=2&from=1`: same path, so the
  shell keeps the page and its scroll while it loads (B-62) and the cards above stay. Grids that start past the
  first page load in place as before.
- **Page numbers** (`pageNumbers(current, pages)`): first, last, the current with one neighbour each side, «…»
  per gap, a one-page gap drawn as its number, three pages at either end — page 1 still reads «1 2 3 … N», so no
  golden of the catalog changed. At most seven numbers. The client drew whatever labels the server sent; two «…»
  needed nothing there. The Saved list takes the same numbers.
- **The search's sort** is `FilterChips.sortLabel` / `sorts` (new optional fields, default empty, so the
  catalog's kinds row is unchanged): the catalog's sort control and menu at the end of the chips' row (over it
  on a phone). Category chips keep the sort now — they reset it to Popular before, which a visible sort would
  have made a surprise. Its press is a `load` of the existing `categories`, `grid`, `pagination` parts.
- **Tips as text**: numbered lines without the white pill; the queries to try keep their pills.
- **Keys**: `SearchInput` (the field's owner) holds the highlight over the queries the shell offers from the
  panel; ↓/↑ move it (clamped, ↑ above the first is none), Enter picks the highlighted query's action, otherwise
  submits the text; typing resets it. Only the suggestion rows take the highlight, as the panel draws it.
  `Storefront.kt` changed in three lines (the `onPick`, the offer, the highlight passed to the overlay).
- **Goldens changed**, each looked at: `Search_Results`, `Search_Autocomplete` and their phone twins (the sort
  control; the grid moves down by the control on a phone), `Search_NoResults` and its twin (tips as text). The
  wire body `search_results.json` carries the sort the server now sends. No other golden moved.
- **Tests.** Server: `PaginationTest` (4: page 7 of 517 draws «1 … 6 7 8 … 517» with every number a press; the
  ends; every page of 517 reachable from the one before, at most seven numbers; «Show N more» from a held grid
  and its count), `DrawnActionsTest` — «Show 24 more» is page 1 then page 2 with the filters kept and page
  numbers open one page; page 7 of the search's eight with its neighbours and «Show 24 more» = pages 7 and 8;
  the search's sort orders and chips keeping it; `from` outside its pages refused; the deals' «Show 24 more»
  keeps today's deals and appends. `PartsRoutesTest`/`UniqueIdsTest` exercise the new loads unchanged. Client:
  `ReachablePagesTest` (3: page 7 draws its neighbours and both ends and «8» loads its parts; «Show 24 more»
  keeps page 7's cards, the scroll and no placeholder; the search's sort opens and loads), `StorefrontTest` (2:
  ↓↓↓↑↓ + Enter opens the second query; ↓↑↑ + Enter opens the typed text).
- **Mutations** (each on the committed change, restored, `git status` clean): server — page numbers as before
  (the first three) and no search sort — 5 red (`PaginationTest` 3, `DrawnActionsTest` 2); «Show N more» to the
  next page alone — 6 red; `from` ignored by `Browse.page` — 2 red. Client — Enter ignoring the highlight — 1
  red; the chips' row without its sort — 1 red. The tips: `viddikVerify` failed on `Search_NoResults` (12 %) and
  its twin before they were re-recorded.
