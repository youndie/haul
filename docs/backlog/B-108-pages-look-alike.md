---
id: B-108
title: "client + server: the same thing looks the same on every page"
status: wip
priority: P3
size: M
stage: stage-11-qa
---

# B-108 — client + server: the same thing looks the same on every page

Found in the QA pass of 2026-10-10 (v0.3.0):
- the footer is drawn only on home, `/c` and `/deals`;
- `/deals` and `/c` load with only the header (no skeleton), unlike every other page (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt:73-76`);
- Saved has page numbers only (no «Show N more»); a one-page result draws a lone «1»; paging Saved on a phone keeps the
  scroll at the bottom of the next page;
- account: active-order cards open only from the small «Details ›», history rows from anywhere (B-74);
- cart lines, checkout items, order items and the suggest panel's «Top products» draw a blank tinted square where the
  card and the product page draw the stored photo (the wire has no image field there; partly B-78);
- the order page (cancelled) draws «Back to cart» twice; its summary drops the «Promo · AUTUMN10» row the cart and
  checkout show; the header's HAUL PLUS pill does nothing for a member;
- deal cards wrap the struck-out price mid-number («$432.9 / 9»); the «Added to your cart» message is hard to see over the
  dark Plus block.

- AC: per line, or the difference recorded as intended; goldens where the look changes.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/saved/screen/SavedScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductCardView.kt`.
