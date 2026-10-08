---
id: B-18
title: "server + client: Order tree and renderers, reorder"
status: open
priority: P1
size: M
stage: stage-5-order
blocked_by: [B-01, B-15, B-17]
---

# B-18 — server + client: Order tree and renderers, reorder

The order page every placement lands on, and the source of reorder.

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: the return dialog (B-21).

- AC: parity for `Order_*` except ReturnDialog and Returned.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/`.
