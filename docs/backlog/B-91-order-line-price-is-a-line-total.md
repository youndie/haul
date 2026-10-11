---
id: B-91
title: "server: an order line's price is a unit price, and a return refunds what was paid"
status: done
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

## Done (2026-10-11)

- **Decision: a read-side fix, no migration.** An order line keeps what the whole line cost, as placement has
  written it since V10 (`price_cents` = price × quantity, `list_cents` = list price × quantity); the fields now say
  so — `OrderLine.totalCents` and `listTotalCents`, with `unitCents` (total ÷ quantity, exact) for the price of one;
  the cart's `PricedLine.priceCents`/`listCents`, where the totals come from, are `totalCents`/`listTotalCents`
  too. The columns keep their names; `OrderTables.kt` says what they hold. Why not a Flyway migration rewriting
  them to unit prices:
  - every row ever written is already in this one shape, so there is nothing to convert, and the order's own
    numbers agree with it (`orders.items_cents` = Σ `list_cents`);
  - a rewrite is not idempotent against a row written by the previous image during a deploy, and leaves a
    rolled-back image reading unit prices as totals;
  - a saga stored before the deploy carries the same totals in its payload; `OrderPayload.Line` keeps the serial
    names `priceCents`/`listCents` for the renamed fields, so an order placed across the deploy reads the same.
- **The stand, read before deciding** (one read-only `SELECT` on `haul-postgres`, 2026-10-11): 73 order lines,
  none with quantity > 1, every order's `items_cents` equal to its lines' `list_cents`; one return requested and
  50 refunded, none on an order with a quantity > 1. No stored order or return was mispriced by the bug, and the
  read-side fix reads every row as it is.
- **Every money path that reads an order line**, checked:
  - fixed — `ReturnRefunds.of` (`Returns.kt`) multiplied the total by the quantity: the line's refund, and through
    it the return dialog's amounts, the stored return (`RequestReturn`), the points shares (`pointsBack`), the money
    paid back (`moneyBack`, `ReturnSimulator`) and the order page's «Refunded» row;
  - fixed — `ShipmentShares.of` (`Fulfilment.kt`) multiplied too: each shipment's capture
    (`FulfilmentSimulator`), the points it credits on arrival, and the tracked share (`OrderTracking`) were split
    by the quantity counted twice — a different split of the same total;
  - fixed — `OrderScreen.details` drew the total as the price of one: «2 × $698» on the order page and in the
    return dialog's lines, now «2 × $349»;
  - correct as they were — the cart line's price (the line total, with «$349 each» under it) and `Totals.of`; the
    checkout summary's item price (the line total, the quantity beside it); placement and the saga payload (totals
    copied); the order summary («Items (n)», order-level sums); the account's history (`placed.totalCents`);
    reorder (quantities, at today's catalog prices); «bought this month» (units); `ReturnRefunds.points`
    (order-level).
  - fixtures — `SampleOrders` (every line quantity 1, its items now summed as totals), `BoughtThisMonthTest` (its
    lines were unit-priced with quantities up to 3; now totals), `PointsTest`, `ReturnRefundsTest`; the client's
    order bodies all have quantity 1 and did not move; the e2e and the synthetic shoppers buy quantity 1.
- **Tests**: `LineOfSeveralTest` (PostgreSQL) — Maya's cart with Sony ×2, paid with her 2,480 points, placed through
  the saga: stored `price_cents` 69,800 for quantity 2, the page and the return dialog write «2 × $349», the dialog
  offers $698.00 with 2,010 points back, the captures are $677.89 and $158.31, and returning the two pairs refunds
  $677.90 to the card; an order inserted raw as v0.3.0 wrote it reads the same totals, unit price, refunds, points
  shares, shipment shares and «2 × $349»; a saga payload stored under `priceCents`/`listCents` decodes into the
  totals. `ReturnRefundsTest.a line of several gives back what it cost and any set of lines what the set cost` —
  every subset of the three lines, with points; `ShipmentSharesTest.a line of several is shared by its total`.
- **Mutations**, each restored, `git status` clean: the refund multiplying by the quantity again → the two
  database tests and the refunds test (the QA's $770.97 comes back); the shares multiplying → the two database
  tests and the shares test; the page drawing the total → the two database tests; the payload without its serial
  name → the payload test.
- **Goldens**: none moved — every drawn order fixture has quantity 1, so `OrderFixturesTest` stays green against the
  checked-in client bodies; no client code changed; `viddikVerify` green.
- **Where it ran**: the Linux build machine (WSL) on `d26f686`: `:composeApp:wasmJsBrowserDistribution` alone, then
  `check :server:installDist` — `:server:test` 422 tests, `:composeApp:desktopTest` 268 (from the build cache, its
  inputs unchanged), `viddikVerify`, ktlint — then `scripts/e2e.sh` against an image built from the branch (the
  whole path, the shoppers' config and both synthetic-shopper walks). The Mac: `make check`, `make docs-against
  BASE=origin/main`. No migration.
