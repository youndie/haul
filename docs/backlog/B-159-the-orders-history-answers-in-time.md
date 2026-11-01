---
id: B-159
title: "fix: the orders' history answers in time, however many orders the customer has"
status: open
priority: P0
size: S
stage: stage-15-showcase
---

# B-159 — fix: the orders' history answers in time, however many orders the customer has

On the stand (v0.7.2) `GET /ui/account/orders` takes 6.4–8.3 s over a day, and no other span of haul-server is
slower than a second (tracy of the stand, 2026-11-01). The synthetic shoppers (B-31) place orders around the clock,
so their customers' histories only grow, and the page is drawn from all of them: `AccountScreen.history` filters and
counts `view.orders` in memory, every order with its lines and images, and draws a row for each.

- **Read a page, count in SQL.** The history shows the newest orders (those on their way first) a page at a time with a
  «Show more» that loads the next part in place (kompot `load`, as the chips do, B-63); the chips' counts come from one
  grouped query, not from the rows. The overview's «Order history» reads its four rows the same way.
- Rejected: capping the shoppers' history (hides the defect from the next real customer) or caching the tree (the
  history changes with every order the saga moves).
- Not covered: the order page itself (`/ui/account/orders/{id}`), which is not slow.

- AC: the history of a customer with 5 000 orders answers within 300 ms on the server (a test that seeds them and
  times the route, and the span on the stand after the release); the chips' counts are unchanged; «Show more»
  appends the next rows; the goldens of the account's pages do not move.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/data/ExposedOrders.kt`.
