---
id: B-89
title: "e2e: the synthetic shoppers also walk a declined payment"
status: wip
priority: P3
size: S
stage: stage-10-review
---

# B-89 — e2e: the synthetic shoppers also walk a declined payment

B-31 found that the declining test card is not offered at checkout, so the stand's traffic never runs the
order saga's compensation path; tracy and metrik show only the happy path.

- AC: a share of walks (configurable, default small) pays with a card that declines and sees the order refused
  and the stock released; the saga's compensation visible in the stand's tracy.
- Anchors: `e2e/src/main/kotlin/io/github/youndie/haul/e2e/shoppers/`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/`.
