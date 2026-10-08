---
id: B-22
title: "server + client: reviews and questions, the two dialog routes and forms"
status: open
priority: P2
size: L
stage: stage-7-reviews
blocked_by: [B-08, B-17]
---

# B-22 — server + client: reviews and questions, the two dialog routes and forms

Reviews and questions complete the product page; answers are seed data only, as there is no seller side.

Feature: `feature-reviews` — its scenarios are this item's acceptance where it names them.

- Not covered: moderation.

- AC: feature-reviews scenarios pass; parity for the remaining `Product_*`.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/server/reviews/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/product/`.
