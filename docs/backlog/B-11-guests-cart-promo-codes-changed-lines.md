---
id: B-11
title: "server: guests, cart, promo codes, changed lines, the Cart tree"
status: wip
priority: P1
size: M
stage: stage-4-cart
blocked_by: [B-05]
---

# B-11 — server: guests, cart, promo codes, changed lines, the Cart tree

The cart is where guests and customers meet, and where totals, promo codes and changed lines are computed once for checkout to reuse.

Feature: `feature-cart` — its scenarios are this item's acceptance where it names them.

- Not covered: save for later (B-20).

- AC: feature-cart scenarios pass.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/cart/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/`.
