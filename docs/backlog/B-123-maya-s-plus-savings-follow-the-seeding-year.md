---
id: B-123
title: "fix: Maya's carried Plus savings belong to the year the stand was seeded"
status: wip
priority: P2
size: XS
stage: stage-12-qa-run
---

# B-123 — fix: Maya's carried Plus savings belong to the year the stand was seeded

Found by the catalogue's first run, TC-HOME-07 ([run](../qa/runs/2026-10-11-v0.4.0.md)), on a local v0.4.0 stack seeded on 2026-10-11: Maya's home
Plus block draws no saving at all (the block omits the line when the figure is 0), and the account's Haul Plus tile
reads «$0 Saved on delivery this year». The seed carries her $186 under `carriedSavingsYear` of the canvas's
day (2025, `CatalogSeed.NOW`), and `Membership.carriedSavings(year)` answers 0 for any other year. B-58 already
moves the sale to the day a stand is seeded; the savings stayed on the canvas's year, so every stand seeded after
2025 shows a member who saved nothing.

- **The carried savings are the seeding day's year**, as the sale is the seeding day's week. Tests that seed on the
  canvas's day keep drawing $186 there. Rejected: dropping the year — «this year» is what the tile says.

- AC: a stack seeded on any day shows Maya «$186 Saved on delivery this year» plus her orders' waived fees, on the
  tile and the home block; `SaleCalendarTest`-style test seeds on another day and reads it.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCustomers.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/membership/domain/Membership.kt`.
