---
id: B-70
title: "server: a running stand keeps its deals of the day"
status: open
priority: P1
size: M
stage: stage-10-review
---

# B-70 — server: a running stand keeps its deals of the day

On 2026-10-10 the stand (started 2026-10-09) had no «Deals of the day» on home or `/deals`: the seeded deals end at
the store's midnight and are re-dated only at start-up (B-58 recorded it: «a stand running for days without a
restart loses its deals at midnight»). With them gone a guest's home page shows no product at all — hero, banners,
categories, Plus, footer. The sale itself ends on the eighth day the same way.

- AC: on a stand that has run past midnight (and past the sale's end) home and `/deals` draw deals of the day, with
  a countdown to the next midnight; the sample sale keeps moving the way the start-up re-date moves it; a test with
  a moving store clock across two midnights; a guest's home always has at least one row of products.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/Seeder.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt`.
