---
id: B-82
title: "client: the store reads well between a phone and 1440"
status: open
priority: P2
size: M
stage: stage-10-review
---

# B-82 — client: the store reads well between a phone and 1440

Found by B-73 and B-75: between 768 and 1439 px the wide header is drawn as at 1440 and squeezed — at 1024 px the
search field is a sliver and «HAUL PLUS» is set vertically. B-76 saw the cart line's title drawn one letter per
line at 800 px. No artboard covers these widths.

- AC: at 800, 1024 and 1280 px every page's header, cart and product page read without clipped or stacked
  words; goldens at 1024 for home, catalog, product and cart, each looked at.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt`.
