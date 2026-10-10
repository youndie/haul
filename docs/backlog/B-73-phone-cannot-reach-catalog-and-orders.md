---
id: B-73
title: "client: on a phone every part of the store is reachable"
status: open
priority: P1
size: M
stage: stage-10-review
---

# B-73 — client: on a phone every part of the store is reachable

At 375 px the header has the heart, «Sign in» and the cart — no «Catalog» menu and no Orders. The category row is
clipped (`clipToBounds`) and does not scroll (`ui/HaulHeaderView.kt:371-383`), so categories past the edge
(Fashion is cut, Beauty and the rest are out of reach). HAUL PLUS is not drawn.

- AC: on a phone the shopper can reach every top-level category, the catalog menu, Orders, Saved, the account and
  Haul Plus; the category row scrolls; phone goldens updated and reviewed.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`.
