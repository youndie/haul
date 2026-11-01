---
id: B-167
title: "fix: the control the keyboard is on is visibly marked"
status: done
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
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/FocusRing.kt`.

## Findings

- **Done as research «Decided in B-167»**: a 2 px Cobalt ring over a 4 px Paper halo just outside the control,
  around its own shape, while the focus is the keyboard's (`ui/FocusRing.kt`: `KeyboardFocus`, `ringsWhenFocused`;
  `pressable`, `follows`, `followsToTabs`, `toggles`, `Menu`/`LinkMenu` and the account slot take it). The page's
  last input decides, as Chromium's `:focus-visible` does; the root reads what reaches it (`watchesInput`, in `App`,
  else in `OverlayHost`), and the browser's document tells it every key and pointer press in the capture phase
  (`wasmJsMain/…/shell/BrowserKeyboardFocus.kt`). A field's frame rings whenever its field has the focus
  (`rememberFieldRing`, `ringsAround`, `ringedBy`; the search box, the price range, the promo code, the checkout
  address and the review dialogs' fields). Goldens unchanged: nothing is drawn without a focus, and no fixture holds
  one.
- **Compose 1.12 already did the rest**: a `clickable` and a `toggleable` are in the Tab order, Tab and Shift+Tab move
  in the order they are composed — reading order on every page walked — and Enter and Space press them
  (`FocusRingTest`). A mouse press focuses the control (B-144), which is why the ring follows the input and not the
  focus.
- **Not fixed, Compose's web — the page's selection is a Tab stop of its own.** `SelectionContainer` (B-154, B-155)
  is focusable: the first Tab from nothing, and one per round, focuses the whole page and shows nothing
  (`FocusRingTest`'s walk skips it; the browser walk's first step). It is what holds the focus after words are
  selected with the mouse, for ⌘C/Ctrl+C (`Selectable`), so it is left focusable.
- **Not fixed, Compose's web — after a field, one Tab is spent by the browser.** Leaving a field by Tab moves the
  focus in the page, but the browser's focus goes from the field's hidden `<input class="compose-backing-field">` to
  `<body>`, not back to the canvas; the next Tab is the browser's own and moves the canvas's focus nothing, and the
  ring stays where it was (`document.activeElement` read at each step: `INPUT.compose-backing-field`, then `BODY`,
  then `CANVAS`). Measured after the search field (before the search button) and the promo field (before
  «Sign in to check out»), every time. Giving the canvas its focus back when the field's input blurs would be the
  page reaching into Compose's own element; it is left as it is, the ring meaning what it says throughout.
- **Walked** in headless Chromium 1228 on the WSL box over CDP (`Cdp` of `scripts/measure-first-load.py`), this
  branch's image (`scripts/image-build.sh`) on a PostgreSQL of its own, a guest with one cart line, 1440 × 900: each
  Tab's new accent pixels against the step before. Home (34 Tabs), the product (44) and the cart (44): the strip's three
  links, «Under the hood» and its «×», the logo, «Catalog», the search box's frame, «All categories», the search
  button, Orders, Saved, Sign in, Cart, eleven categories, «HAUL PLUS», then the page — the hero, the banners, a card
  (210 × 355) and so on; the crumbs, the heart and share, the brand, the rating, the variants; «Select all», «Delete
  selected», a line's box, photo, title, «Save for later», «Remove», «+», the promo frame, «Apply», «Sign in to check
  out» — one ring at a time, the last gone as the next came. A mouse press on «Select all» turned it and added no
  accent; the Tab after it rang the line's box. The line's photo pressed opened its product; there the first Tab
  rang nothing (the selection's stop), the second the strip's first link. Enter at a card's ring opened its product
  (`/` → `/p/p-002-08`).
- **A race in B-170's kept header, found by this pull request's CI.** `LapsedHeaderTest` timed out waiting for the
  guest's header in 2 of 3 CI runs. The header renderer told the shell its header from a `SideEffect`, so on every
  recomposition, and the shell files what it is told under whoever is looking at that moment: Maya's header drawn
  again after her sign-in lapsed was filed as the guest's (logged: `name=Maya count=3 signedIn=false` in each failing
  run, never in a passing one), so the guest's header was never asked for. Main had it as well — 4 of 20 runs of the
  test in one JVM on the WSL box, this branch 7 of 20. The renderer now tells the shell once per header it draws
  (`DisposableEffect` keyed on the header, `registry/HaulRenderers.kt`); an `update` draws a new header and is told as
  before. 0 of 50 since, `KeptHeaderTest` green.
