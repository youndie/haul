---
id: B-151
title: "fix: selection controls say their state, dialogs hide the page beneath, and the trial dialog closes on Escape"
status: wip
priority: P2
size: M
stage: stage-12-qa-run
---

# B-151 — fix: selection controls say their state, dialogs hide the page beneath, and the trial dialog closes on Escape

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers A, B, C, E):

- The cart's per-line tick box is a button named with the product's title (the same name as the line's tile), with no
  checkbox role and no checked state; «Select all» and checkout's points toggle carry no state either. A screen reader
  cannot tell which lines go to checkout. (B-131 noted that Compose 1.12's mirror carries no state; the name can.)
- Every card's «+» is named «Add to cart» — six identical names in a row.
- With a dialog or the phone menu open, the page underneath stays in the accessible mirror (77 buttons exposed under
  the trial dialog); the dialog's body is announced as a button.
- The Plus trial dialog does not close on Escape (menus, the photo viewer and the return reasons list do).

- AC: each line's box is named for what it does and says whether it is ticked («Select Sony WH-1000XM6, ticked»), the
  same for «Select all» and the points toggle; each «+» names its product; an open dialog or menu leaves only itself
  in the mirror; Escape closes the trial dialog; `NamedPressesTest` / `a11y-check.py` extended to hold them.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Layers.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/NamedPressesTest.kt`.
