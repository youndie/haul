---
id: B-96
title: "server: home never draws one product twice"
status: open
priority: P1
size: S
stage: stage-11-qa
---

# B-96 — server: home never draws one product twice

Found in the QA pass of 2026-10-10 (v0.3.0), code audit. With no live deal, a customer's home draws «Popular on Haul» (B-70, most reviewed in stock) and
«Picked for you · Popular right now», built from the same most-reviewed products
(`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt:90-94, 107-116`, `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/data/ExposedPickSources.kt:41-53`).
The same `card-<id>` can appear twice: a duplicate row, a broken one-id-per-page rule (B-65), and a «+» update replacing
both twins. `UniqueIdsTest` runs only with deals live.

- AC: no product appears in two rows of one home; `UniqueIdsTest` also runs at a moment with no live deal, for a guest
  and a customer.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/screen/PickedSection.kt`.
