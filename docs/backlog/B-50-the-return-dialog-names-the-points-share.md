---
id: B-50
title: "server + client: the return dialog names the points share of a refund"
status: open
priority: P3
size: S
stage: stage-6-account
blocked_by: [B-21, B-23]
---

# B-50 — server + client: the return dialog names the points share of a refund

B-21's return dialog shows the refund as the ticked lines' full value. Since B-23 an order paid partly with
points refunds the card that value minus the points' share, and gives the share back as points — the dialog
does not say so, so the card is refunded less than the dialog promised. Found by B-23.

Decided as product owner: for an order paid partly with points the dialog's refund box reads the card amount
and, beneath it, «+ N points back»; the order page's «Refunded» row and the account's numbers already follow
the ledger. An order paid without points reads as today.

- AC: the dialog's total for a points-paid order equals what the card is refunded, with the points line; a
  route test over an order paid with points; the `Order_ReturnDialog` goldens unchanged (Maya's sample order
  is paid without points).
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`.
