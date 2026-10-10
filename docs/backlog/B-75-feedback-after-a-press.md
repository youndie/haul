---
id: B-75
title: "client: a press shows that it worked"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-75 — client: a press shows that it worked

Seen on the stand and in the code:
- «Add to cart» on the product page changes only the header's count — no message, the button stays «Add to cart»;
  the header's search field shrinks when the count badge appears (layout shift).
- A card's «+» is drawn the same when there is nothing to add (out of stock, at the limit) and the press then opens
  the product (`ui/ProductCardView.kt:82-93`).
- Brands with a count of 0 stay pressable after another filter is applied.
- The sort menu does not close on Escape.

- AC: «Add to cart» answers with a short message (`show_message` in its `sequence`) and the buy box shows the item
  is in the cart; the header does not move when the count appears; a «+» with nothing to add looks disabled and does
  nothing; zero-count facets are disabled; Escape closes the sort menu; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductCardView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.
