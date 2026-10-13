---
id: B-144
title: "fix: the review photo viewer keeps the keyboard after a press on its arrows"
status: done
priority: P2
size: XS
stage: stage-12-qa-run
---

# B-144 — fix: the review photo viewer keeps the keyboard after a press on its arrows

Found by the v0.6.0 run, TC-PDP-19 ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B and C independently, 1440 and 390). On a viewer just opened the arrow
keys step and Escape closes; after a mouse press on «›» or «‹» the arrow keys do nothing and Escape does not close it
(it stays on «Photo 2 of 2»), and a press on the photo does not bring the keys back — only «×» or a press outside close
it. With two photos every press lands on an end where the pressed arrow turns disabled, so the key focus goes with it
(`document.activeElement` stays the canvas; the keydowns reach the page). The case's own order (press «›», «‹», then
the keys) hits it; `ReviewControlsTest` keys without a press first.

- **The viewer holds the key focus while it is open**, whichever of its controls was pressed or disabled since.

- AC: open, press «›», then ArrowLeft steps back and Escape closes, at 1440 and 390; a client test with that order
  fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewPhotos.kt`.

## Findings

- **Cause.** `pressable` is `clickable`, which a mouse press focuses. The viewer's «›» pressed on photo 1 of 2 shows
  photo 2, where «›» is `inert` — no longer focusable — and Compose clears the focus to the window, past the
  `SheetOverPage` that reads Escape and hands it the arrows. Nothing inside the sheet asked for the focus again, so
  the keys reached nothing until «×» or a press outside closed it.
- **Fix, at the layer** (`ui/Layers.kt`): `SheetOverPage` watches whether it or anything inside it holds the focus
  and takes it back whenever nothing does, so long as it is the newest thing over the page (`LocalOnTop`, set by
  `OverlayHost`): a menu opened over a sheet keeps its own keys until it closes, then the sheet has them again.
  Chosen over a focus request after each of the viewer's presses — which misses a control whose press goes for
  another reason — and over controls that cannot be focused, which would leave nothing for Tab to reach.
- **The other overlays.** The phone's filter sheet had the same loss: a facet pressed with the mouse loses its press
  while the load it sent runs (`LocalHaulActions` is withheld), and Escape stopped closing the sheet — walked in the
  browser below, fixed by the same change. The phone's menu sheet has no control that goes away while it is open.
  The menus (`LinkMenu`/`Menu`) and the return dialog's reason list close on the press of an entry, so the focus they
  lose is theirs to lose; the dialogs (`DialogOverlay`) read no key of their own. Nothing else to fix.
- **Tests.** `ReviewControlsTest` «after a press on an arrow the arrow keys and escape still work» (1440) and «on a
  phone …» (390): open, a mouse press on «›», ArrowLeft back to «Photo 1 of 2», Escape closes — the keys sent to the
  window, not to a node of the viewer. `SheetOverPageTest`: «a sheet keeps the keys after the control pressed is
  disabled», «… is gone», «a menu opened over a sheet has the keys until it closes». Mutations, each red and then
  restored: the old `Layers.kt` → all five red (the two viewer tests stuck at «Photo 2 of 2», as in the run); the
  `onTop` guard dropped → the menu test red (the sheet takes the focus from the menu, and Escape no longer closes it).
- **Verified in the browser.** Headless Chromium on the Linux box, against a local stack (this branch's
  `installDist` and bundle, a throwaway PostgreSQL, the seed), mouse and keys through DevTools: at 1440 and at 390
  the photos open at «Photo 1 of 2», «›» shows «Photo 2 of 2», ArrowLeft «Photo 1 of 2», Escape closes; at 390 the
  filter sheet, a brand pressed, then Escape closes it. The same walk against a bundle built from `main`'s
  `Layers.kt`: ArrowLeft left «Photo 2 of 2» and Escape did not close, at both widths, and Escape left the filter
  sheet open.
- **Goldens.** None changed: nothing drawn moves.
