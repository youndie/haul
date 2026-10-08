---
id: B-21
title: "server + client: returns and refunds"
status: wip
priority: P2
size: M
stage: stage-6-account
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
