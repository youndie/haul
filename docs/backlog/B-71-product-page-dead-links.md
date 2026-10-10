---
id: B-71
title: "client + server: what looks pressable on the product page does something"
status: open
priority: P2
size: M
stage: stage-10-review
---

# B-71 — client + server: what looks pressable on the product page does something

On the product page these look like controls and do nothing (`feature/product/ProductDetailsView.kt`):
- gallery thumbnails and the «+3» tile (:126-153); the phone gallery dots (:249-267); the extra thumbnails are
  invented tone tiles (`ProductScreen.kt:174, :329`), the counter says «1 / 8» with one photo;
- the share button (:229-245) — no wire field;
- the brand name in link colour (:271-273); stars and «N reviews» in link colour (:289-313) — should open the
  Reviews tab; «All specifications» (:417-420) — should open the Specifications tab;
- the seller card with a chevron (:664-688; `SellerSummary` has no action);
- out of stock, «Save» for an already saved item is drawn enabled and does nothing (:535-546);
- in the tabs (`ProductTabsView.kt`): review photo thumbnails (:375-379), the rating histogram bars (:314-334), the
  «Helpful» pill on the viewer's own review (:388-397); reviews and questions stop at 10 with no «more» or paging
  (`ReviewTabs.kt:56, :85, :213`).

- **Decided as product owner:** each item is wired or drawn as plain text; nothing keeps the look of a link without
  being one. The gallery shows the photos the product has (and no invented ones).
- AC: a client test per wired control; the rest no longer look pressable; goldens re-recorded where the look
  changes, each reviewed.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.
