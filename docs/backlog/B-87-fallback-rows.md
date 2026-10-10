---
id: B-87
title: "server: what home and the empty cart show when no deal is live"
status: open
priority: P3
size: S
stage: stage-10-review
---

# B-87 — server: what home and the empty cart show when no deal is live

B-70 chose «Popular on Haul» (the six most-reviewed products in stock) for home while no deal of the day is live;
the item only asked for «at least one row of products». The empty cart's «Picked for you» still shows only
deals, so it is empty in the seconds after midnight and on a store without deals.

- AC: the owner confirms or replaces the home row; the empty cart's picks fall back the same way; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`.
