---
id: B-14
title: "server: checkout tree, slots with capacity, pickup points, quote, the address form"
status: wip
priority: P1
size: M
stage: stage-5-order
blocked_by: [B-12]
---

# B-14 — server: checkout tree, slots with capacity, pickup points, quote, the address form

Checkout gathers delivery method, address or point, slot and payment into one quote the placement trusts.

Feature: `feature-checkout` — its scenarios are this item's acceptance where it names them.

- Not covered: placing the order (B-16).

- AC: quote scenarios pass; a full slot is refused.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/`.
