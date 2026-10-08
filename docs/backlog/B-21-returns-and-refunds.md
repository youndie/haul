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
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/`.
