---
id: B-133
title: "fix: the small lines the v0.5.0 run noticed"
status: open
priority: P3
size: S
stage: stage-12-qa-run
---

# B-133 — fix: the small lines the v0.5.0 run noticed

Found outside the catalogue by its v0.5.0 run ([run](../qa/runs/2026-10-12-v0.5.0.md)), each small:

- «1 items»: a category or filter result with one product titles «1 items», and the sheet's button would read «Show
  1 items» (`CatalogScreen.kt`); the cart already writes «1 item».
- A press on the words «from» / «to» inside a price box does not focus its field (the field is only the box's right
  two thirds); the keys typed after it are lost.
- At machine speed, leaving «from» for «to» and pressing blank space within one frame wait still sends the same
  price load twice, the first aborted (B-118 meant one).
- An order's shipment line reads «TOMORROW, OCT 13 · 12:00 – 15:00» while packed and «TOMORROW · 12:00 – 15:00» in
  transit — the date drops mid-order.
- At 390 a returned line's «Midnight Black · Headphones only · 1 × $349» breaks between «1» and «× $349».
- The cold `/sign-in` page's phone menu lists the ten row categories, not the 32 (its picker presses nothing — as
  decided in B-66; the menu's list is worth the same answer as the other pages').

- AC: each behaves as its line says, or the line is recorded as decided otherwise with its reason; a test per line
  where it is code's logic.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`.
