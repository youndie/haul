---
id: B-55
title: "server: the order page names what the card gets back"
status: done
priority: P3
size: S
stage: stage-6-account
blocked_by: [B-50]
---

# B-55 — server: the order page names what the card gets back

Since B-50 the return dialog of an order paid partly with points reads the card amount and «+ N points
back», but the order page's return heading («$349.00 goes back to your card … once the seller has it») and
its «$x refunded» fact still use the lines' full value — after the return the page promises more money than
the card gets. Found by B-50.

Decided as product owner: the page uses the same split as the dialog — the card amount in the heading and
the refunded fact, and «+ N points back» beside it — for a return in flight and a refunded one; Haul Pay reads
«to your Haul Pay plan» as the dialog does. An order paid without points reads as today.

- AC: route tests over a points-paid order at requested, picked up and refunded, the numbers equal to what
  the card and the ledger get; `Order_Returned*` goldens unchanged (the sample is paid without points).
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`.

## Done (2026-10-09)

- **One number for the page and the refund.** `ReturnRefunds.moneyBack(order, return)` — the return's value less
  its lines' B-50 shares of the points (`pointsBack` of the returned positions) — is what `ReturnSimulator` pays
  and what the page names; the simulator's inline subtraction moved there, so the two cannot drift.
- **What the page says** (`OrderScreen.kt`), for Maya's order paid with 2,480 points and the duvet cover and the
  mugs sent back ($163.00 of lines, 790 points of shares):
  - requested: «A courier picks it up for free. $155.10 goes back to your card ···· 4821 once the seller has it
    · + 790 points back»; picked up: the same without the first sentence;
  - refunded: «$155.10 is back on your card ···· 4821 · + 790 points back», the payment fact «$155.10 refunded
    Oct 12 · + 790 points back», and «−N points» for the earned points reversed, as before;
  - Haul Pay: «$332.10 goes back to your Haul Pay plan …» / «is back on your Haul Pay plan», the one amount the
    plan and the processor give back together, as the dialog names it;
  - an order paid without points has no points back: the money is the lines' value and the sentences end with
    a full stop, as before — #HL-44019's `order_returned.json` is the same bytes.
- **The summary** follows the split: «Refunded −$x» is the money given back, and «Paid» is the total less it — what
  was paid in money and kept. The rows add up to it ($652.00 − $140.00 − $24.80 − $155.10 = $332.10); the points
  that came back are not money and stand in the payment fact. Before, «Refunded −$163.00» and «Paid $324.20»
  added up too, but to $7.90 less than the card kept.
- **Tests**: `ReturnRoutesTest.a points-paid order's page names what the card and the points get back at every
  step of its return` — over HTTP at requested, picked up and refunded; the refunded page's numbers are built
  from what `payment_refunds` holds for the order ($155.10) and the ledger's `returned` and `reversed` rows (790,
  and the «−N points»). `HaulPayPlanTest.a points-paid return gives back through the plan what the dialog said`
  now also reads the page before and after the refund: $332.10 to the plan, «+ 1,690 points back», «Paid $155.10».
- **Mutations**, each restored: the heading back on the lines' value → both tests; no «+ N points back» → both;
  the «Refunded» row on the lines' value → the route test; «Paid» on it → both; the payment fact on it → the route
  test; `moneyBack` without the points' shares (the refund paying the lines' value) → both, and B-50's dialog
  route test. Each time the test after the failing one in `ReturnRoutesTest` failed with B-21's closed-pool
  cascade (`HikariDataSource … has been closed`), not on an assertion.
- **Goldens**: no client body moved (`OrderFixturesTest` green against the checked-in bodies), no client code
  changed; `viddikVerify` green.
- **Where it ran**: the Linux build machine (WSL), on the branch rebased onto `18536bf`:
  `:composeApp:wasmJsBrowserDistribution` alone, then `check :server:installDist` — `:server:test` 325 tests,
  `:composeApp:desktopTest` 173 (from the build cache, its inputs unchanged), `viddikVerify`, ktlint. No migration.
