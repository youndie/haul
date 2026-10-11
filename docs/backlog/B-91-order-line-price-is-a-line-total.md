---
id: B-91
title: "server: an order line's price is a unit price, and a return refunds what was paid"
status: wip
priority: P0
size: S
stage: stage-11-qa
---

# B-91 — server: an order line's price is a unit price, and a return refunds what was paid

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk. Maya's order HL-48303 held Sony ×2 at $349. The order page drew «2 × $698», and returning
only that line offered «Refund $770.97 + 2,220 points back» — more than the $698 paid for it — while the duvet
($139) got $76.76 and the mug ($24) $13.27. The refunds still add up to the order's total, but they are split wrongly.

Cause, from the code: `PricedLine.priceCents` is already price × quantity (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/Totals.kt:29`).
It is stored as `OrderLine.priceCents`, multiplied by the quantity again in `server/src/main/kotlin/io/github/youndie/haul/feature/returns/domain/Returns.kt:124`,
and drawn as a unit price by `OrderScreen.details()` (~566).

- AC: an order line stores and shows its unit price (or its total, consistently named); a return of any subset of
  lines, with and without points, refunds exactly what those lines cost; a test with quantity > 1 and points, red
  before; existing orders on the stand are read correctly (a migration or a read-side fix — record which).
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/Totals.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/returns/domain/Returns.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`.
