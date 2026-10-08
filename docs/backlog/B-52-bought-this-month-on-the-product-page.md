---
id: B-52
title: "server: «bought this month» on the product page"
status: open
priority: P3
size: S
stage: stage-2-browse
blocked_by: [B-18]
---

# B-52 — server: «bought this month» on the product page

The product page artboards draw «12K bought this month» under the rating and the contract has
`ProductDetails.bought`, but the server never sends it — only the client's fixture bodies carry it. Found by
the PR #2 docs sync after B-25.

Decided as product owner: the count is the units of the product in orders placed in the last 30 store days
and not cancelled, shown rounded the way the canvas writes it («12K», «840»), and absent below 50 so a
quiet product does not advertise its quietness. The seed's sample products carry a base so the stand reads
like the canvas.

- AC: the product tree sends `bought` by that rule (route test with orders inside and outside the window,
  cancelled ones not counted, below the threshold absent); the product goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`.
