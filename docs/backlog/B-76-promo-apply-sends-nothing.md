---
id: B-76
title: "client: «Apply» on the cart's promo code is applied"
status: open
priority: P1
size: S
stage: stage-10-review
---

# B-76 — client: «Apply» on the cart's promo code is applied

On the stand, as a guest with one line in the cart: typing `AUTUMN10` into «Promo code» and pressing «Apply» twice
sent no request (no `…/cart/promo` in the network log) and nothing changed. The code audit reads the button as wired
(`feature/cart/CartViews.kt`), so the first job is a reproduction: the typed text not reaching the command, the
guest cart, or the press itself.

- AC: a guest and a customer can apply `AUTUMN10` and see the discount line, and see why when a code is refused;
  the reproduction becomes a client test that fails before the fix.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt`.
