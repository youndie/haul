---
id: B-169
title: "fix: on a phone the product's variant is chosen before its buy buttons"
status: wip
priority: P2
size: S
stage: stage-15-showcase
---

# B-169 — fix: on a phone the product's variant is chosen before its buy buttons

On a phone's product page the colour and size choice sits below «Add to cart» and «Buy now» (designer's review,
2026-11-01): the shopper presses Add before they see there was a choice, and gets the default.

- The phone layout puts the variants between the price and the buttons, as the desktop column does.

- AC: on the phone fixture the variants are above the buttons; the desktop is unchanged; the phone product goldens
  re-recorded and looked at.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`.
