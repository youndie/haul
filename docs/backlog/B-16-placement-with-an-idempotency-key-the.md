---
id: B-16
title: "server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator"
status: wip
priority: P1
size: L
stage: stage-5-order
blocked_by: [B-14]
---

# B-16 — server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator

Placement is the seam this product exists to show: stock and payment across sellers, compensated when a step fails (research D4).

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: fulfilment after placement (B-17).

- AC: feature-checkout and «declined card» pass; a saga killed mid-way finishes after a restart.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`.
