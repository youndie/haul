---
id: B-164
title: "feat: a public feed of live orders shows the saga's steps and its compensations"
status: wip
priority: P1
size: M
stage: stage-15-showcase
---

# B-164 — feat: a public feed of live orders shows the saga's steps and its compensations

The synthetic shoppers (B-31) place, pay, ship, receive and return orders on the stand all day, some of them
failing on purpose so the petich saga compensates — and nobody can see it except in tracy. That is the most
convincing thing the stand does, and it is invisible.

- **«Live orders» page, linked from the demo strip and the About page:** the last orders across the store, each a
  row of saga steps (reserve, pay, ship per seller, …) lighting up as they happen over the existing SSE bus, failed
  steps red and their compensations shown undoing them; a row opens a read-only view of that order's trail
  (`OrderTrail`). Anonymised: no names, no addresses — order number, sellers, totals, steps, times.
- Rejected: a public link into tracy (another product's UI, and a log is not a story); showing only the visitor's
  own orders (the demo shopper of B-162 has one order; the feed has hundreds).

- AC: with the shoppers running on a local stand, the page shows orders moving step by step without a reload; a
  compensated order shows the failed step and the undoing ones; no personal data in the tree (a test over it); the
  page's goldens recorded.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ops/OrderTrail.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt`.
