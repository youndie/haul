---
id: B-54
title: "client: the filter sheet stays open while filtering"
status: open
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
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/`.
