---
id: B-167
title: "fix: the control the keyboard is on is visibly marked"
status: wip
priority: P2
size: S/M
stage: stage-15-showcase
---

# B-167 — fix: the control the keyboard is on is visibly marked

Tab moves through the store's controls, but none of them shows it has focus: every press is drawn with
`indication = null` (`ui/Links.kt`), so a keyboard user does not know where they are. The designer's review
(2026-11-01) found it on every page.

- **A focus ring drawn only for keyboard focus** (`:focus-visible` behaviour: a ring when focus came from the
  keyboard, none after a click), in the theme's accent, around the control's own shape — the same for `pressable`,
  `toggles`, fields and the header's slots. Goldens are drawn with no focus and do not move.

- AC: tabbing through the home, a product and the cart shows a ring on each control in turn; a click shows none;
  a test drives focus with the keyboard and finds the ring; goldens unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt`.
