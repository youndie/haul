---
id: B-88
title: "server: a returned unit goes back to stock"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-88 — server: a returned unit goes back to stock

B-31 found that a refund does not put the returned unit back: every synthetic shopper's walk (one per 10 minutes
on the stand) takes a unit for good, and at that rate a category's first page runs dry in months.

- AC: a completed return adds its units back to stock once (idempotent under a replayed saga step); a test
  through the saga; the shoppers' walk leaves stock unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/`.
