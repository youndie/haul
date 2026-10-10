---
id: B-78
title: "server + client: the stand shows pictures, not placeholder labels"
status: open
priority: P2
size: M
stage: stage-10-review
---

# B-78 — server + client: the stand shows pictures, not placeholder labels

Every product, category tile and campaign is a tinted box with a caption: «STUDIO», «HEADPHONES», «ELECTRONICS»,
«CAMPAIGN IMAGE» on the hero (`HomeScreen.kt:99`), «PRODUCT PHOTO · 1 / 8» on the product page
(`ProductScreen.kt:171-175`). On a public stand this reads as unfinished.

- **Question for the product owner:** where the pictures come from (licensed stock, generated, the seed's own
  illustrations) and how they are served (`ProductPhotos` exists on the server). Until decided, the captions that
  only describe a missing picture are not drawn.
- AC: per the decision; the first-load numbers (B-28) re-measured with images.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`.
