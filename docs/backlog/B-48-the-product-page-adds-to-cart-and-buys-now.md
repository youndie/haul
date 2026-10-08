---
id: B-48
title: "server + client: the product page adds to cart and buys now"
status: open
priority: P1
size: S
stage: stage-4-cart
blocked_by: [B-37]
---

# B-48 — server + client: the product page adds to cart and buys now

The product page draws «Add to cart» and «Buy now» and neither carries a command (B-37 left them «owned by
nobody»). The storefront's main button does nothing: B-26's end-to-end run had to add through a category
card's «+» instead. Found by B-37, met again by B-26.

Decided as product owner: «Add to cart» puts the chosen SKU (the variant the page shows) in the cart — one
more of it, up to the line's limit, through the cart's existing command — and the page redraws with the
header's count; «Buy now» does the same and navigates to `/checkout` (a guest goes through sign-in with
`next=/checkout`, B-41/B-44). Out of stock, neither is offered (the page's own out-of-stock state).

- AC: both buttons carry commands fixed in the tree for the SKU shown; route tests for each (count, the
  line's SKU, the navigate, the limit, out of stock), client wiring tests; B-26's run adds from the product
  page instead of the card; product goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`.
