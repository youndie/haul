---
id: B-55
title: "server: the order page names what the card gets back"
status: wip
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
