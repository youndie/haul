---
id: B-58
title: "server: campaigns and deals end"
status: open
priority: P2
size: S
stage: stage-4-cart
blocked_by: [B-57]
---

# B-58 — server: campaigns and deals end

Nothing reads `deals.ends_at` or `campaigns.ends_at`: the seeded October deals are still drawn on the stand, and
a campaign's prices never end. Every seeded window is dated around the canvas's day, so ending them as they
stand would remove every markdown from the stand at once. Found by B-53.

Decided as product owner: a deal's card and price end at its `ends_at`, and a campaign's prices end at its
`ends_at` (the SKU returns to its regular price). The seed dates its campaign and deal windows relative to the
day it seeds, so a stand keeps a live sale; tests keep the canvas's fixed clock and the canvas's dates.

- AC: route tests on the store clock before and after a deal's and a campaign's end; a stand seeded today shows
  the canvas's markdowns; the goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/CampaignPricing.kt`.
