---
id: B-17
title: "server: fulfilment simulator, capture per shipment, pickup codes"
status: wip
priority: P1
size: M
stage: stage-5-order
blocked_by: [B-16]
---

# B-17 — server: fulfilment simulator, capture per shipment, pickup codes

The fulfilment simulator is the outside world that moves an order; capture per shipment is «your card is charged when the order ships».

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: returns (B-21).

- AC: «charged when shipped» passes; an order reaches delivered on the fast clock.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/`.
