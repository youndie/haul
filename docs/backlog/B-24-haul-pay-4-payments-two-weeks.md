---
id: B-24
title: "server + client: Haul Pay, 4 payments two weeks apart"
status: wip
priority: P2
size: M
stage: stage-8-loyalty
blocked_by: [B-15, B-16]
---

# B-24 — server + client: Haul Pay, 4 payments two weeks apart

Haul Pay as decided: 4 interest-free payments two weeks apart, simulated.

Feature: `feature-membership` — its scenarios are this item's acceptance where it names them.

- Not covered: collections for an overdue plan.

- AC: an instalment order shows its schedule; captures follow it on the simulator clock.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/membership/`, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`.
