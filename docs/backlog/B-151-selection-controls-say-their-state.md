---
id: B-151
title: "fix: selection controls say their state, dialogs hide the page beneath, and the trial dialog closes on Escape"
status: done
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

## Findings (2026-10-13)

- **States in the name.** The cart's line box, «Select all» and checkout's points switch are `Modifier.toggles`
  (`ui/Links.kt`): named for what they do and whether they are on — «Select Sony WH-1000XM6 Wireless Noise Cancelling
  Headphones, ticked» / «…, not ticked», «Select all, ticked» / «partly ticked» / «not ticked», «Use 2,480 points
  (−$24.80), off. 100 points = $1 · all or nothing» (the detail stays in the name: a press's label hides the words
  under it) — with `ToggleableState` in semantics. The line's photo keeps the product's name alone, so the box and
  the tile no longer share one.
- **The role stays a button's, by measurement.** Compose 1.12's mirror writes `Role.Checkbox` as `role="checkbox"`
  with no `aria-checked`, and Chromium reads a checkbox without it as not checked: a page with `role="checkbox"
  aria-label="Select A, ticked"` gives `checked: false` in `Accessibility.getFullAXTree` (headless Chromium on the
  Linux box), the same for `role="switch"`. A ticked box would have said «ticked» and «not checked» at once. So the
  role in semantics is a button's (`MIRRORED_ROLE`) until the stack is on 1.13, whose mirror writes `aria-checked`
  (in 1.13.0-alpha02's `ui-wasm-js` library, with `aria-selected` and `aria-modal`); then it is one line. Written in
  the research next to B-126 and B-131.
- **A card's «+»** is named by its card's product, «Add <product> to cart» (`addToCart`), and has a test tag of its
  own (`card-add`), which the tests that pressed «Add to cart» by name now find it by.
- **Only the overlay in the mirror.** What a `present` draws (the review, question, return and Plus trial dialogs)
  is drawn as a `SheetOverPage` now, and every sheet — those, the phone's menu, the filter sheet, the review's photos
  — is modal: `OverlayHost` clears the semantics of the page and of every overlay opened before the newest sheet
  (`clearAndSetSemantics`; 1.12's mirror walks `replacedChildren`, which a clearing node empties, and reads no
  `hideFromAccessibility`). A menu is not modal — the page under it stays — and the notices of B-145 stay over all
  of it. Nothing drawn changes: the dialog fills the storefront's box as it filled the page's.
- **The dialog's body as a button** was the scrim: a `clickable` with the card inside it, so the mirror had one
  button holding the whole dialog. The scrim's press is now the pointer's alone (`pointerPress`); a reader closes the
  dialog by its «×» or by Escape.
- **Escape.** The dialogs read no key before; as sheets they hold the focus (B-144) and Escape is their dismissal —
  read now after what has the focus inside the sheet declined it (`onKeyEvent`, no longer `onPreviewKeyEvent`), so
  the return dialog's open reason list closes first and the dialog stays (B-129's test holds it). Text fields map no
  key to Escape (read from foundation 1.12.1's key mappings), so Escape with the caret in a field closes the
  dialog. This takes in every presented dialog, not the Plus trial's alone: one mechanism, and «the others» close on
  Escape in the same run's terms. The photo viewer's arrows stay in the preview, as before.
- **Tests.** `NamedPressesTest`: «boxes and switches say what they do and whether they are on» — the cart's body with
  its second line unticked and checkout's, both widths: every node with a toggleable state, its role, state and
  label, against the list; «each card's plus names its product» — home and a category, both widths: every «+» named
  «Add <title> to cart» for a card title of the body, and not one name for all; «an open dialog or sheet leaves only
  itself in the tree» — the five dialogs at both widths and the phone's menu and filter sheet: no press, field or
  words outside `over-page`, and no press holding the dialog's «×». `PlusTrialWiringTest` «escape closes the dialog
  and sends nothing»; `ReviewWiringTest` «escape takes the dialog away and sends nothing» (typed in the title field
  first). `DrawnActionsTest`'s filter sheet test reads the results under the sheet in the unmerged tree: they are
  drawn, and hidden from assistive technology. Mutations, after the commit, each red and then restored (`git status`
  clean): the clearing made a no-op — the modal test red on every dialog and sheet («under it: DELIVERING TO …»); the
  scrim a `pressable` again — red, «a press holding the dialog» on all four dialogs at both widths; the state word
  left out of the name — the states test red; «Add to cart» on every «+» — the plus test red; the dialogs drawn
  without `SheetOverPage` — both Escape tests red; Escape read in the sheet's preview again — `ReturnWiringTest`
  «escape closes the reasons' list and keeps the dialog» red.
- **The browser** (`scripts/a11y-check.py`, now: a guest's cart given a line; the cart's boxes and «Select all»
  named with their state and a box's name turning when pressed; checkout's switch the same; every card's «+» on home
  and a category; under the phone's menu, the filter sheet, the review dialog and the Plus trial's dialog nothing of
  the page outside `#over-page` and no button holding the «×»; Escape closing the trial's dialog): on the Linux box,
  the server's `installDist` with a seeded PostgreSQL and shildik in Docker, headless Chromium, as a guest, as Sam
  (not a member: the trial's dialog) and as Maya (points: the switch). All green. The control, the bundle built from
  the branch point's client: 8 failing as a guest (both sheets with 106 and 143 nodes of the page under them, the
  cart's box named by the product alone and «Select all» with no state, every «+» «Add to cart»), 12 as Sam (also
  the review dialog over 169 nodes of the page with one button holding it, the trial's dialog not closed by Escape),
  12 as Maya (the switch named «Use 2,480 points (−$24.80) 100 points = $1 · all or nothing», no state).
- **Goldens:** none moved — names draw nothing, and the dialogs and sheets are closed in every artboard.
- **Not done, the same defect elsewhere:** the return dialog's line boxes (`ReturnDialog.kt`, a `pressable` row with
  a drawn box) and the facet options of the filter panel say no state either; a chosen swatch and star (B-126, B-131)
  neither. Not in this item's list; each is a `toggles` call when it is taken.
