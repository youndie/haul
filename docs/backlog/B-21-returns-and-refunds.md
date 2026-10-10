---
id: B-21
title: "server + client: returns and refunds"
status: done
priority: P2
size: M
stage: stage-6-account
epic: feature-orders
blocked_by: [B-18]
---

# B-21 — server + client: returns and refunds

A delivered order can be returned within 30 days; points earned on returned lines are reversed.

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: a returns list in the account (hidden in v1).

- AC: «late return» passes; parity for `Order_ReturnDialog`, `Order_Returned`.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/`.

## Done (2026-10-08)

- **Decisions** (research D4, «Decided in B-21»): one return per order, of whole lines as the dialog ticks them;
  the 30 days are store days from each line's own arrival; the refund goes back when the parcel is back — the
  canvas's Returned artboard draws «Requested · Picked up · Refunded» and «$87.50 refunded Sep 19» — not when it
  is asked for. Refusals: `400 validation_failed` (every field named), `404 order_not_found` (not yours, or
  none), `409 already_returned`, `422 not_delivered`, `422 return_window_closed` («Late return»; `422` as for an
  expired promo code, and as endpoint-orders' draft wrote it).
- **Wire** (`shared`): `ReturnEntry` and `returnProblems` (`feature/returns/ReturnCommands.kt`, the rules both
  sides hold the form to), `exactDollars` (the dialog adds the refund up itself); `ErrorCode.ReturnWindowClosed`,
  `NotDelivered`, `AlreadyReturned`; `OrderTotals.returnAction` (kompot's `present`), `OrderShipment.returned`,
  and the `ReturnForm` component (`haul_return_form`) with `ReturnLine` and `ReturnReason`.
- **Server** (`server/.../feature/returns/`): `RequestReturn` (`POST /api/v1/me/orders/{id}/returns`, customer
  tier, `201` with `close` then `refresh`), `ReturnSimulator` (collected a day after the request, refunded a day
  later, run with the fulfilment pass by `FulfilmentRunner`; the refund before the move, keyed `refund:<order>`;
  pay on delivery outside the processor), `ReturnRefunds` (each line's share of the items as paid, the points in
  proportion), `ReturnWindow`. `PaymentProcessor.refund` / `refunded`: a key refunded once answers what it gave;
  refunds never add up to more than the order's captures (`Exceeds`), nothing without a capture (`NotCaptured`).
  The order page (`OrderScreen.kt`) presents the form from «Return items» while some line is inside its window,
  says «Returns closed on …» after, and draws a return: «Return requested / picked up / refunded», the lines kept
  in their shipments and the returned ones in a «Returned items» card, «Refunded −$x» and «Paid», the card's
  refund and «−174 points». `OrderProgress` gains `returning` and `returned` (from `Order.returnStatus`).
- **Storage** (`V17__returns.sql`): `returns` (one per order, keyed by it), `return_lines`, `payment_refunds`.
- **Client** (`composeApp/.../feature/order/ReturnDialog.kt`): `ReturnFormView` (the card of the review dialogs,
  `DialogFrame` now shared, the lines with checkboxes, the reason, the refund the ticked lines add up to) and
  `ReturnDialog` (the rules checked before sending, the command through the dialogs' seam —
  `ReviewCommand.Return` — a refusal drawn over the buttons); `ReturnFormRenderer`; «Return items» follows its
  `present`; a return's chip is Blush.
- **Points**: no points ledger exists (B-23). The number reversed is stored with the return and drawn on the page
  once refunded; B-23 turns it into a ledger entry.
- **Parity** (`viddikDesignParity --component "Order*"`, references rendered on Linux with grayscale text from the
  extracted artboards, tolerance untouched), first round: `Order_ReturnDialog` 2.51 %, `_Phone` 3.23 %,
  `Order_Returned` 1.84 %, `_Phone` 3.18 %. What is left is glyph edges, the refund box a pixel taller (Chrome's
  22.5 px line) and with it the card's shadow crescent, and a pixel per wrapped title line on the phones. The other
  sixteen `Order_*` did not move (`Order_Placed_Phone` still 5.11 %, B-18's). Goldens recorded for the four; the
  other Order goldens came out byte-identical. After the rebase onto B-19 the twenty `Order_*` and twelve
  `Account_*` read the same numbers and `viddikVerify` is green: no golden moved.
- **Tests written**: `ReturnRoutesTest` (**late return**; each line to the last day of its own window; a returned
  line refunded when the seller has it back — the page at each step, `returned` progress; the delivered page
  presenting the dialog; not delivered, not yours, no token, already returned; every field at fault),
  `ReturnLifecycleTest` (each step at its time and not a millisecond before; a pass after a pause; a pass that died
  between the refund and the move refunds once; a refused refund holds the return; two passes at once; pay on
  delivery), `RefundRulesTest` (the limit, a refund out of several captures, the replay, nothing without a
  capture), `ReturnRefundsTest` (no discount, a code shared, a sale price, the points, the window's last day, the
  dialog's money format equal to the page's), `OrderFixturesTest` (#HL-44019 returned, #HL-46102's dialog),
  `SchemaTest` (V17), `KoinGraphTest`; client `ReturnWiringTest` (the refund added up as lines are ticked; sent
  where the form says and closed; the rules before sending; a late return refused in the dialog; Cancel) and
  `OrderWiringTest` («Return items» presents the dialog); after B-19 merged, `AccountRoutesTest` (a returned
  order is «Returning», then «Returned», in the history and under its filter, with «Details») and
  `AccountFixturesTest` (#HL-44019 derived returned, no longer forced by the fixture).
- **Mutations**, each seen failing the test written for it and restored: no window check («late return», the
  last-day test), the refund at pickup (the refunded-when-back route test, two lifecycle tests), the refund key
  never found (the replay test, the died-between test), no refund limit (the limit test), a second return
  accepted (the already-returned assertion), an undelivered line accepted (the not-delivered assertion), the
  ownership check removed from `RequestReturn` (Sam's `404`), the discount counted against list prices (the sale
  test, the route tests' $349.00), the points not reversed (the 698), returned lines left in their shipment (the
  page test), the client skipping the rules, «Return items» not followed, the refund summing every line; after
  the rebase, the history mapping returns to «Delivered» (both account tests) and the orders' query leaving the
  return status out (the account route test). A
  failing route test closes the next one's pool (B-18's finding), so a mutation also fails the test after it with
  `HikariPool-n has been closed`; the test it was written for failed on its own assertion each time.
- **Where it ran**: the Linux build machine (WSL), on the branch rebased onto `a7c56c1` (B-19, B-46):
  `:composeApp:wasmJsBrowserDistribution` alone, then `./gradlew check :server:installDist` in a 5 GB scope,
  green (`:server:test` 236 tests, `:composeApp:desktopTest` 135, `viddikVerify`; PostgreSQL and shildik in
  containers); `scripts/image-check.sh` with its own tag and port, green (V16 and V17 migrated, 883 of 883 classes
  from the cache, page 200). `make check` and `make docs-against BASE=origin/main` on the Mac. The chart is unchanged.
- **Scenarios**: feature-orders «Late return» (`ReturnRoutesTest.late return`) — refused `422
  return_window_closed`, as the draft says.

## Findings (2026-10-08)

- **The migration number**: V17, after B-19's V16, which merged first — in order.
- **The dialogs' command seam is named for reviews.** The return goes through `ReviewCommands`
  (`ReviewCommand.Return`) — the seam is the dialogs' in all but name; renaming it touches the shell that B-44
  just changed, so it is left for a person to decide.
- **A partly returned order is drawn without an artboard**: the kept lines in their shipments, the returned ones
  in their own card, «Return requested / picked up» before the refund; the canvas draws only a fully refunded
  order. Its copy («A courier picks it up for free. $349.00 goes back to your card ···· 4821 once the seller has
  it.») is this item's, as is «Returns closed on …» on a delivered order past its window, the reason list (Doesn’t
  fit, Not as described, Arrived damaged, Changed my mind) and the pay-on-delivery and Haul Pay refund lines.
- **Points**: no ledger (B-23). The reversed number is the order's points in proportion to the refund, stored
  with the return; B-23 should turn it into a `reversed` ledger entry (research §5's `PointsEntry`).
- **B-19's history**, merged first, now derives its returns: `ExposedOrders` reads each order's return status in
  the same batch as its lines and shipments (four queries for any number of orders), `OrderProgress.of` answers
  `returned` / `returning`, and `OrderState` gains `Returning` beside `Returned` — a return in flight has a label
  of its own («Returning», Blush, «Details», counted under the «Returned» filter), rather than staying with the
  delivered orders, whose «Reorder» its page no longer offers. #HL-44019 is one sample order now
  (`testing/SampleOrders.kt`, `returned`): the account's returned row and the Returned page, its lines the
  Order_Returned artboard's (the account draws only their tiles, which are the same).
- **The closed-pool cascade** (B-18's test-isolation finding) shows up under every failing route test here too.
- PR #2's drafts that this changes: **feature-orders** (the *target* rule on returns becomes what is built: one
  return per order of whole lines, 30 store days per line, refund when the parcel is back, points in proportion;
  «Late return»: `ErrorCode` has `return_window_closed` now, `**Automated:** ReturnRoutesTest.late return`),
  **endpoint-orders** (the returns route is built in `feature/returns/ReturnsRouting.kt`, not `feature/order/`;
  answers `201` with `close` then `refresh`; errors `400 validation_failed`, `401`, `404 order_not_found`,
  `409 already_returned`, `422 not_delivered`, `422 return_window_closed`), **screen-order** (ReturnDialog and
  Returned built; a partly returned order and a return in flight are drawn without artboards).
