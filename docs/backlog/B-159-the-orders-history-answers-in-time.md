---
id: B-159
title: "fix: the orders' history answers in time, however many orders the customer has"
status: done
priority: P0
size: S
stage: stage-15-showcase
---

# B-159 — fix: the orders' history answers in time, however many orders the customer has

On the stand (v0.7.2) `GET /ui/account/orders` takes 6.4–8.3 s over a day, and no other span of haul-server is
slower than a second (tracy of the stand, 2026-11-01). The synthetic shoppers (B-31) place orders around the clock,
so their customers' histories only grow, and the page is drawn from all of them: `AccountScreen.history` filters and
counts `view.orders` in memory, every order with its lines and images, and draws a row for each.

- **Track what is drawn, page the history.** Only the orders a page draws are tracked — the overview's three cards and
  four rows, the history's twenty to a page; the rest are counted from the order alone (its row, shipments and return
  status, four queries whatever the count). The history pages as the Saved list does — page numbers under the rows,
  `?page=N`, each loading the account's body in place (kompot `load`, B-63) — rather than a «Show more», which the
  store draws nowhere else for a list of rows.
- Rejected: capping the shoppers' history (hides the defect from the next real customer) or caching the tree (the
  history changes with every order the saga moves).
- Not covered: the order page itself (`/ui/account/orders/{id}`), which is not slow.

- AC: a page of the history of a customer with 5 000 orders sends a bounded number of statements, whatever the
  count (a test that seeds them and counts; under two seconds as a loose bound), and the span on the stand is under
  a second after the release; the chips' counts are unchanged; page 2 is the
  next twenty; the goldens of the account's pages do not move.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/data/ExposedOrders.kt`.

## Findings

- **Where the time went.** `AccountScreen.build` called `OrderTracking.of` for every order: the shipments' history
  and the captured payments are a query each per order, and a return or an instalment plan one more. The span on the
  stand had no children (the queries are not spans), so the cause was read from the code and confirmed by the test:
  5 001 orders took 4.2 s on the WSL box tracking all of them, 176 ms tracking the page (best of three, warm).
- **Counting is not in SQL.** An order's state comes from its shipments' statuses and its return, which is
  `OrderProgress.of(order)` in Kotlin; a grouped query would be a second copy of that rule. Reading every order's
  row, lines and shipments is four queries and stays well inside the budget at 5 000; the budget, not the rule,
  is what to revisit if a customer ever has a hundred thousand.
- **A count, not a time.** The first test held the route to 300 ms; it passed at 176 ms on the idle box and failed
  at 489 ms on the CI runner, which was building the push and the pull request at once. The test now counts the
  statements a page sends — 10 035 tracking every order, under a hundred now — and keeps only a loose bound on time.
