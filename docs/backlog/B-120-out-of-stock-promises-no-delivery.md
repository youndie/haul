---
id: B-120
title: "fix: a product with nothing in stock promises no delivery date"
status: open
priority: P2
size: S
stage: stage-12-qa-run
---

# B-120 — fix: a product with nothing in stock promises no delivery date

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)): `p-073-13`, every SKU
out of stock, shows «Courier · Tomorrow · Free» on its page and «TOMORROW» on its card. Nothing can be bought, so
nothing arrives tomorrow; the date comes from `DeliveryCalendar` regardless of stock.

- **No stock, no date.** A product (or the chosen SKU) with nothing to sell draws no delivery promise on its page
  or card; the buy box says it is out of stock, as it does. Rejected: a later date — there is no restock date
  to promise from.

- AC: an all-out-of-stock product's page and card show no delivery line; a product with one SKU in stock shows
  the date for that SKU and none when an out-of-stock SKU is chosen.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/DeliveryCalendar.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`.
