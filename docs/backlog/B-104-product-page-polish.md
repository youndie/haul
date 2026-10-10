---
id: B-104
title: "client + server: the product page keeps what the shopper chose"
status: open
priority: P2
size: S
stage: stage-11-qa
---

# B-104 — client + server: the product page keeps what the shopper chose

Found in the QA pass of 2026-10-10 (v0.3.0):
- picking a colour or bundle on the Reviews, Questions or Specs tab jumps back to Description: the variant link drops
  `tab` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt:304`);
- the loading skeleton still draws the gallery's thumbnail column B-71 removed — the page jumps when it arrives;
- the description tiles cut their titles («Noise ca», «Connect»);
- share gives no sign when the clipboard refuses (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt:210`) — no fallback.

- AC: per line; tests; goldens where the look changes.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt`.
