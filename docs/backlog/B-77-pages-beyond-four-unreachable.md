---
id: B-77
title: "client + server: every page of results can be reached"
status: wip
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
