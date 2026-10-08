---
id: B-23
title: "server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings"
status: open
priority: P2
size: M
stage: stage-8-loyalty
blocked_by: [B-15, B-17, B-19]
---

# B-23 — server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings

Haul Plus and points are the loyalty layer the canvas sells; redemption backs the account tile's promise (research D6).

Feature: `feature-membership` — its scenarios are this item's acceptance where it names them.

- Not covered: real billing.

- AC: feature-membership and «points redeemed» pass; parity for `Home_PlusTrialDialog`, `Account_NotMember`, `Checkout_PointsApplied`.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/membership/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/`.
