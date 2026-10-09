---
id: B-60
title: "server + shared: dialogs write the listing name, one groupedCount"
status: open
priority: P3
size: S
stage: stage-7-reviews
blocked_by: [B-45]
---

# B-60 — server + shared: dialogs write the listing name, one groupedCount

Since B-45 cards, cart and orders write a product's listing name, but the review and question dialogs still write
«brand + title» — «Brooklyn Home Co. Linen Duvet …» where the canvas writes the short form. And `shared` has two
public `groupedCount` functions, one in `feature/returns/ReturnCommands.kt` (B-50) and one in
`feature/reviews/ReviewCommands.kt` (B-22). Found by B-45 and the PR #2 docs sync.

Decided: the dialogs' product line writes the listing name (the variant detail beside it as today); one
`groupedCount` lives where several features can import it (research D3a) and both callers use it.

- AC: the review, question and return dialogs' product name is the listing name (fixture bodies regenerated from
  the server, the dialog goldens re-recorded only where the name changes, parity not worse); one `groupedCount`.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.
