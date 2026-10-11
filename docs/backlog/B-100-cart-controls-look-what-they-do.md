---
id: B-100
title: "client + server: the cart's controls look what they do"
status: wip
priority: P2
size: M
stage: stage-11-qa
---

# B-100 — client + server: the cart's controls look what they do

Found in the QA pass of 2026-10-10 (v0.3.0):
- with nothing selected, «Checkout» / «Sign in to check out» is a live-looking blue button that does nothing
  (`checkoutEnabled=false`; `HaulButton` has no disabled look — `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt:131-162`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt:529-539`);
- «Delete selected» is link-coloured and dead with nothing ticked; the stepper's −/+ at 1 or at the limit are dead but
  not greyed (`CartViews.kt:133-151, 398-411`);
- «Save for later» removes the line with no message and no saved-for-later section;
- «Discount −$0.00» is drawn in sale red when nothing is discounted (cart and checkout; the order page leaves the row
  out); the promo row is inside «Discount», so the rows do not add up to the total;
- a cart line's link drops the SKU (the travel-case line opens «Headphones only»);
- «Buy now» with the product already in the cart adds one more unit and takes the whole cart to checkout.

- **Decided as product owner:** «Buy now» checks out that product alone at quantity 1 (or the quantity already in the
  cart, without adding) — record the choice.
- AC: per line above; tests; goldens where the look changes.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`.
