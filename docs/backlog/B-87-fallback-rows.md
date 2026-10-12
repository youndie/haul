---
id: B-87
title: "server: what home and the empty cart show when no deal is live"
status: wip
priority: P3
size: S
stage: stage-10-review
---

# B-87 — server: what home and the empty cart show when no deal is live

B-70 chose «Popular on Haul» (the six most-reviewed products in stock) for home while no deal of the day is live;
the item only asked for «at least one row of products». The empty cart's «Picked for you» still shows only
deals, so it is empty in the seconds after midnight and on a store without deals.

- **Decided (owner, 2026-10-12): «Popular on Haul» stays on home**, and the empty cart's «Picked for you» falls back to
  the same row while no deal is live.
- AC: with no live deal, home shows «Popular on Haul» and the empty cart shows the same six; with deals, both show the
  deals as today; tests for both states.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`.
