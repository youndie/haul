---
id: B-52
title: "server: «bought this month» on the product page"
status: done
priority: P3
size: S
stage: stage-2-browse
blocked_by: [B-18]
---

# B-52 — server: «bought this month» on the product page

The product page artboards draw «12K bought this month» under the rating and the contract has
`ProductDetails.bought`, but the server never sends it — only the client's fixture bodies carry it. Found by
the PR #2 docs sync after B-25.

Decided as product owner: the count is the units of the product in orders placed in the last 30 store days
and not cancelled, shown rounded the way the canvas writes it («12K», «840»), and absent below 50 so a
quiet product does not advertise its quietness. The seed's sample products carry a base so the stand reads
like the canvas.

- AC: the product tree sends `bought` by that rule (route test with orders inside and outside the window,
  cancelled ones not counted, below the threshold absent); the product goldens unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/BoughtThisMonth.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/ExposedProductSales.kt`,
  `server/src/main/resources/db/migration/V23__bought_this_month.sql`.

## Findings

Decided while building it (research D6, «Decided in B-52», carries the reasons):

- **Placed means `placed`**: an order still `placing` may yet be declined, so it is not counted any more than a
  `cancelled` one. A returned order still counts — it was bought.
- **The window** is `placed_at` after the store clock's now minus 30 days (days in the clock's zone) and up to now.
- **The abbreviation** truncates: «840», «1.2K», «12K», «600K», «1.2M»; 12,999 is «12K», 999 is «999».
- **The base** is `products.bought_base` (V23), added to the live count: 12,340 for the headphones, 2,180 for the
  duvet cover set, 840 for the mug, 0 elsewhere. V23 also writes them into a catalogue seeded before it, so the
  running stand reads «12K» without a reseed.
- **The cost** is one `SUM` per product page over the product's lines through its SKUs, on V23's
  `order_lines_sku_id`; cards do not show the line.
