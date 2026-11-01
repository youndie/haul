---
id: B-177
title: "refactor: lay the order and account pages out in whole pixels"
status: wip
priority: P2
size: M
stage: stage-15-showcase
---

# B-177 — refactor: lay the order and account pages out in whole pixels

B-175 measured every canvas-only workaround and found one whose removal buys code back: the fraction bookkeeping that
lays the order and account pages out the way the browser's CSS did, a fraction of a pixel at a time (`CssColumn`,
`CssBox`, `cssLines` in `CssLayout.kt`, and `Tiles`, `GridPair`, `PhoneRow` in the two pages). The canvas is no longer
the reference (B-85), so the pages may sit a few pixels from where they are now; the owner said yes (2026-11-01).

- The two pages are laid out with Compose's own rows, columns and spacing in whole pixels; the code that only kept
  fractions goes, and the 23 goldens it moves (0.29–5.99 %, B-175's table) are re-recorded with `viddikRecord --force`
  and each looked at.

- AC: `CssColumn`, `CssBox` and `cssLines` are gone or kept only where a reason other than the canvas is written next
  to them; the re-recorded goldens show the same pages a few pixels apart, nothing missing or overlapping; `check` is
  green.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/CssLayout.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/AccountViews.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderViews.kt`.
