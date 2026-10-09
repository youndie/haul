---
id: B-53
title: "server: Plus members see campaign prices early"
status: wip
priority: P3
size: S
stage: stage-8-loyalty
blocked_by: [B-23]
---

# B-53 — server: Plus members see campaign prices early

The Plus trial dialog sells «Early access to sales», and the seed stores `campaigns.plus_early_access_at`,
but no price reads it: a member sees a campaign's prices when everyone does. Found by the PR #2 docs sync
after B-25.

Decided as product owner: from `plus_early_access_at` until the campaign's start, a member (trial included)
gets the campaign's prices everywhere a price is drawn and charged — cards, product page, cart, checkout — and
a non-member the regular ones; from the start, everyone. The quote's fingerprint already follows the price,
so a membership that ends mid-checkout redraws as `cart_changed`.

- AC: route tests for a member and a non-member inside the early window and after the start, through the
  cart to the checkout's total; the canvas's goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/CatalogTables.kt`.
