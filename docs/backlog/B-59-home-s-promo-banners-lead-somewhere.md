---
id: B-59
title: "server: home's promo banners lead somewhere"
status: wip
priority: P3
size: S
stage: stage-2-browse
blocked_by: [B-58]
---

# B-59 — server: home's promo banners lead somewhere

Home's promo banners are drawn with no action, and no item gave them one (they were not on B-49's list). A
shopper presses a sale banner and nothing happens. Found by the PR #2 docs sync after B-53.

Decided as product owner: a banner for a campaign navigates to the deals page filtered to that campaign (or the
deals page when the campaign has no page of its own), a banner for Haul Plus opens the Plus offer like the
header's pill (B-49), and a banner whose campaign has ended is not drawn (B-58). The same holds for home's hero (it announces the
sale) and the empty cart's sale line («Today's deals end at midnight — up to −70 % …»): drawn only while their
campaign or a deal is live — found by B-58.

- AC: each seeded banner carries its action and the client follows it (route + wiring test per banner, as
  `DrawnActionsTest` does); an ended campaign's banner, hero and sale line are absent; home and cart goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`.
