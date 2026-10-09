---
id: B-61
title: "server: the sale's promo code moves with the sale"
status: open
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-58]
---

# B-61 — server: the sale's promo code moves with the sale

Since B-58 the seed dates the Autumn sale from the day it seeds and re-dates an ended sample sale at start, but the
sale's promo code `AUTUMN10` keeps the canvas's 2025 window: on a stand's wall clock it answers `promo_expired`,
while the sale it belongs to is live. Found by B-58.

Decided: the seed's promo codes that belong to the sample sale are dated with it — moved by the same whole store
days in `CatalogSeed.generate(day)` and by `Seeder.redateSale` — and nothing else's codes move.

- AC: a stand seeded on another day accepts `AUTUMN10` during its sale and refuses it after (`promo_expired`); the
  re-date moves the code with the sale; tests on the canvas clock unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/seed/Seeder.kt`.
