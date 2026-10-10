---
id: B-79
title: "client + server: checkout's step indicator and summary tell the truth"
status: wip
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
