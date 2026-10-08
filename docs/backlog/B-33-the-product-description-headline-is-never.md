---
id: B-33
title: "server: the product description's headline is never sent"
status: wip
priority: P2
size: S
stage: stage-3-search
blocked_by: []
---

# B-33 — server: the product description's headline is never sent

`ProductDescription.title` and `.accent` exist in the contract and the client draws them («Silence,
*tuned to you*» in `Product_Description`), but `ProductScreen.kt` sets neither: the parity fixture
carries the canvas's copy, so B-08 passed while the running app shows the description without its
headline. The catalogue has nowhere to keep one — it needs a per-product headline in the seed and the
schema, sent with its accent.

- AC: `GET` of a product's description tab carries the headline the seed gives it; a route test
  asserts it, and the description fixture body matches what the server sends for the sample headphones.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/seed/`.
