---
id: B-54
title: "client: the filter sheet stays open while filtering"
status: done
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-49]
---

# B-54 — client: the filter sheet stays open while filtering

On a phone every press inside the filter sheet — a facet tick, «Show N more» — is a `navigate`, and the shell
keys the page on its address, so the sheet closes after each one; the sheet's «Show N items» button does
nothing. Choosing three filters means opening the sheet three times. Found by B-49.

Decided as product owner: the sheet stays open across the navigations its own presses cause (the page under
it redraws with the new results), and «Show N items» closes it, showing the results; «×» still closes it
(B-49). N is the result count the tree already carries.

- AC: on `Catalog_FiltersSheet_Phone`, ticking two facets keeps the sheet open with both ticked and the
  results behind it updated; «Show N items» closes it; client wiring tests; the filter-sheet golden unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRenderers.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt`.

## Findings (2026-10-09)

- **The level.** The sheet's open state moved from `FilteredResultsRenderer`'s `remember` to the shell,
  above `key(address)`: `FiltersSheetState`, provided as `LocalFiltersSheet`, drawn by
  `FiltersSheetOverlay` beside the keyed page. A flag carried into the next page's `remember` was the
  smaller change, but the page under the sheet is replaced by its loading placeholder while the next tree
  is on its way, so the sheet would close and open again on every tick, its scroll back at the top.
  Held by the shell, the sheet stays drawn through the load, keeps its scroll, and each page drawn hands
  it its results (`drawn`), so «N applied», the ticks and «Show N items» are the new page's.
- **Only its own presses keep it.** A press in the sheet records the address it opens; the shell's
  arrival at any other address — back, forward, a link outside the sheet — closes it (`arrived`), and a
  page drawn while the sheet is closed does not open it.
- **Between a tick and its page the facets follow nothing.** The old page's addresses lack the tick just
  made, so a second tick in that window would open the page without the first; «×» still closes the
  sheet. A page that fails to arrive leaves the sheet open and inert over the error, «×» away.
- **«Show N items»** closes the sheet (`FiltersSheet`'s `onShow`); N is `FilteredResults.showLabel`,
  already the result count (`CatalogScreen`). No server change.
