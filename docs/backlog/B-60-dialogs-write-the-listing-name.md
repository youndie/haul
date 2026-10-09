---
id: B-60
title: "server + shared: dialogs write the listing name, one groupedCount"
status: done
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
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`,
  `shared/src/commonMain/kotlin/io/github/youndie/haul/GroupedCount.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewRoutesTest.kt`.

## Findings

- **What each dialog writes now.** The review and question dialogs (`ReviewTabs.formProduct`) write
  `Product.listingName`, the detail beside it unchanged. The return dialog needed no change: its lines write
  the order line's title, which placement copies from the listing name (B-45).
- **The canvas against the listing name.** Product_ReviewDialog and Product_QuestionDialog write «Sony
  WH-1000XM6»; the headphones' listing name is «Sony WH-1000XM6 Wireless Noise Cancelling Headphones», which
  is also their brand and title, so the product dialogs draw what they drew before and their goldens did not
  change. The short form is the canvas's alone: no card, cart line or order line writes it. Order_ReturnDialog
  writes the order lines' titles, as before.
- **Bodies.** `order_delivered.json` and `order_delivered_with_points.json` carry a review dialog per
  delivered line; the sweater and the serum lost their brands («Northline …», «Clear Skin Lab …») and now read
  as their order lines do. Regenerated from the server (`OrderFixturesTest`). No drawn fixture shows those
  dialogs, so no golden moved.
- **`groupedCount`** lives once at the root of `shared` (`io.github.youndie.haul.groupedCount`, D3a: the
  reviews and returns contracts, both dialogs, the server's question hint and the filter panel import it). The
  filter panel's private third copy (`Int.formatted()` in `FacetPanelView.kt`) went too. The server keeps
  its own JVM formatter (`count` in `feature/catalog/domain/Formats.kt`), which `ReturnRefundsTest` holds
  equal to the shared one.
