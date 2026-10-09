---
id: B-56
title: "server: placement moves the order page live"
status: wip
priority: P3
size: S
stage: stage-9-ship
blocked_by: [B-29]
---

# B-56 — server: placement moves the order page live

B-29 pushes the order page's body when the fulfilment, return and Haul Pay passes move an order, but not when
the placement saga does: a customer who lands on the page while the order is still placing sees «placing»
until something else moves it, and a declined card's cancellation (the saga's compensation, or the sweeper's)
is not pushed either. Found by B-29.

Decided: the saga's last steps — placed, and cancelled by compensation or the sweeper — tell `OrderMoves`
like the passes do, after their transaction commits.

- AC: a server test that a page subscribed while placing hears «placed», and one subscribed to an order whose
  card is declined hears it cancelled; the existing live tests green.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/OrderMoves.kt`.
