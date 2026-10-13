---
id: B-152
title: "fix: the small lines the v0.6.0 run noticed"
status: open
priority: P3
size: S
stage: stage-12-qa-run
---

# B-152 — fix: the small lines the v0.6.0 run noticed

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md)), each small:

- `/favicon.ico` answers 404 on every first load, a console error each time (the page links no icon); it may be what
  voids `scripts/measure-first-load.py --url` runs («no static frame on the page» while screenshots show one).
- A home page left open across the store's midnight: its countdown reads 00:00:00 one second early and stays there
  over the ended deals' cards until a reload, while the server has the new day's deals.
- A cart line at quantity 10 with a struck-through price cuts «$31.49 each» to «$31.4» at 390.
- The card fact on an order flips its wording: «$462.00 charged» when delivered, «… charged as the shipments shipped»
  once a return is requested.
- Checkout with Haul Pay chosen still says «Your card is charged when the order ships.»
- Generated descriptions lowercase the leaf name: «Travel tvs by Northwind.» (`CatalogSeed.kt`).
- A press on the variant already chosen reloads the product (checkout's chosen method sends nothing for the same press).
- «Placing order…» leaves «Back to cart» in link blue though inert.
- A scoped search that finds nothing anywhere resets the picker to «All categories» while the address names the scope.
- The phone's category row at rest shows no cut word at 375, 390 and 800 (the next word starts just past the edge),
  so nothing says there is more — TC-HDR-05 expects the cut; its golden shows the uncut row. Decide the layout or the
  case.

- AC: each behaves as its line says, or is recorded as decided otherwise with its reason; a test per line where it is
  code's logic.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`, `server/src/main/kotlin/io/github/youndie/haul/seed/CatalogSeed.kt`.
