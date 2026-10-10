---
id: B-69
title: "client + server: the price filter can be used"
status: open
priority: P1
size: M
stage: stage-10-review
---

# B-69 — client + server: the price filter can be used

The price facet draws «from» and «to» fields and a slider with two thumbs. None of them works: the fields are not
editable, the thumbs cannot be dragged (`feature/catalog/FacetPanelView.kt:121-180`), and the server sends no action
for the range facet (`CatalogScreen.kt:234-245`); `price_min`/`price_max` can only be set through the address.

- AC: typing a bound and confirming (Enter or leaving the field), or releasing a thumb, applies the range in place
  with a `load` (B-63); the applied range shows as a removable chip; the phone filter sheet has the same; client and
  server tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`.
