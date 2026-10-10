---
id: B-79
title: "client + server: checkout's step indicator and summary tell the truth"
status: done
priority: P3
size: S
stage: stage-10-review
---

# B-79 — client + server: checkout's step indicator and summary tell the truth

Checkout shows «1 Delivery – 2 Payment – 3 Review» with step 1 always current (`CheckoutScreen.kt:68`) on a page
that holds all three; the summary's item tiles do not open the product (`CheckoutViews.kt:~760`); there is no way
back to the cart but the browser's back.

- AC: the indicator follows what is filled in (or is removed); items open their product; a «Back to cart» link;
  tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt`.

## Findings (2026-10-10)

- **Decided: the indicator follows the stored checkout, not removed** (research D5, «Decided in B-14», the
  bullet for B-79). `CheckoutScreen.step` marks «Delivery» until the order can be placed
  (`CheckoutState.placeable`) and «Review» after it; the steps before the current one are drawn done — an acid
  circle with the tick the option cards use — so a complete checkout reads «✓ Delivery ✓ Payment 3 Review».
  «Payment» is never the step waiting: a way to pay is always chosen (the card ···· 4821 by default), and every
  reason the order is held is the delivery's. Removing the indicator was the other choice; it would have thrown
  away the canvas's header for no gain once the server knows the answer. The contract keeps `current: Int` and
  says what it means: the steps before it are done, and one outside the steps marks none.
- **Loading and Error mark no step** (`CHECKOUT_SHELL_HEADER.current = -1`): before the tree the client does not
  know where the shopper is, and marking «Delivery» there was the same untruth, followed by a jump to «Review».
- **The summary's tiles open their products** (`SummaryItem.action`, the product page through the catalog's
  `productLink`), and **«Back to cart»** (`CheckoutSummary.back`, `/cart`) sits in the summary's title row
  beside «Your order». In that row the page keeps every artboard's layout; a crumb above «Checkout» would have
  moved the whole page down. While the order is being placed both do nothing, as the sections already did.
- **The address form is not touched**, so B-76's `awaitTypedInput` does not apply here.
- **The goldens' tolerance does not see the link**: `viddikRecord` left `Checkout_PlaceError` and
  `Checkout_Validation` (wide) as they were, with «Back to cart» missing, because a 15 px word on a
  1440 × 1600 artboard is within `viddikVerify`'s tolerance. They were recorded anyway, with the other sixteen
  checkout goldens; the step marking of the client's Loading is held by a test for the same reason.
