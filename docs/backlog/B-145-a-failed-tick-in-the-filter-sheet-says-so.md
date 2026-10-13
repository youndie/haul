---
id: B-145
title: "fix: a facet ticked in the filter sheet that fails says so over the sheet"
status: done
priority: P2
size: S
stage: stage-12-qa-run
---

# B-145 — fix: a facet ticked in the filter sheet that fails says so over the sheet

Found by the v0.6.0 run, TC-ERR-03 at 390 ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B on the stand and D locally, each independently). On a phone a
facet is only reached through «Filters», whose sheet covers the screen. Ticked offline, `GET /ui/parts/…?brand=Sony`
fails, the tick falls back («0 applied», «Show 15 items») and «The page didn’t update» with «Retry» is drawn beneath
the sheet — four frames over 2.4 s show only the sheet. Its «Retry» sits where the sheet's «Show 15 items» is, so the
press meant for it closes the sheet; only then is the notice seen. 1440 behaves as the case expects.

- **A notice the shopper must act on is drawn above whatever is open** (the sheet, the phone menu, a dialog — B-126's
  `OverlayHost` layers), or the sheet itself says the tick did not go through with its own Retry; pick the one the
  shell's message frame (B-75, B-92, B-136) already supports and write down why.

- AC: at 390, offline, a tick in the sheet shows «The page didn’t update» with «Retry» while the sheet is open; Retry
  online applies the tick in place; 1440 unchanged; a client test fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Messages.kt`.

## Findings

- **Cause.** The notice a failed `load` leaves (`NotUpdatedNotice`, B-62) was drawn in the screen's own box, and
  kompot's messages (`MessageHost`, B-75) after the screen — both inside `OverlayHost`'s content, under every
  overlay `OverPage` draws. On a phone the filter sheet is such an overlay and covers the window, so the notice was
  under it; and the sheet reads every press over it, so the press at Retry's place went to the sheet's «Show N
  items» and closed it.
- **Decided: the notice is drawn over whatever is open, not by the sheet.** `OverlayHost` has a layer above every
  overlay (`NoticeOverPage`, `ui/Layers.kt`); the not-updated notice (both its places in `Storefront`) and
  `MessageHost` are drawn in it. Why this one: it is what the shell's message frame already is — one notice held by
  the shell, at the bottom of the window, over what is there (B-62, B-75, B-92, B-136) — it only had to be over the
  overlays too, and it covers the phone's menu and the dialogs with the same change. A notice of the sheet's own
  would have been a second frame for one failure, a second Retry in the tree beside the page's, and the page's own
  still waiting under the sheet. The cost: while the notice is up it covers the sheet's «Show N items» (at 390 both
  sit at the bottom); «×» still closes the sheet, and a Retry that goes through takes the notice away. Written in the
  research next to B-126 and B-144.
- **The phone's menu and the dialogs.** The menu's entries close it before they are followed, so nothing raises a
  notice while it is open; one that did would now be seen over it. The dialogs a `present` draws (the Plus trial's)
  were already under `MessageHost` — «Couldn't reach the store» stays over them; the notice of a failed `load` was
  under them, now over. The product's review dialogs show their errors in the card (`FormProblems`), not as a notice.
- **The keys (B-144).** A sheet takes the key focus back whenever nothing inside it holds it; with the notice over
  it, that would have kept Tab from ever reaching Retry. The notice layer reports whether it holds the focus
  (`Overlays.noticeFocused`), and a sheet leaves the focus there; once the notice is gone, the sheet takes it back.
- **Seen, not changed.** The client's own sign-in page returns from the storefront's content before `MessageHost`
  is drawn, so a message raised there (the shell header's «HAUL PLUS» dialog over it) is never shown. Not this
  item's case; noted for whoever touches that page.
- **Tests.** `InPlaceAnswersTest` «a tick in the filter sheet that does not arrive says so over the sheet and Retry
  applies it» (390): the parts' request fails as a fetch does offline (the fake transport throws), the notice shows
  with the sheet open and the address unchanged; Retry is pressed with the mouse where it is drawn, the second
  request goes, the sheet shows «1 applied» still open and the notice is gone. On today's code it is red at the
  Retry press: the press lands on the sheet and the request is never sent again. `SheetOverPageTest` «a notice over
  a sheet keeps the focus Tab gave it»: Tab from the sheet reaches the notice's control, Enter presses it, and once
  the notice is gone Escape closes the sheet. 1440: the existing «a tick whose answer does not arrive keeps the page
  under a notice and Retry presses it again» stays green unchanged. Mutations, after the commit, each red and then
  restored (`git status` clean): the not-updated notice drawn in place again → the 390 test red; the sheet's focus
  rule without the notice's exception → the focus test red.
- **Verified in the browser.** Headless Chromium on the Linux box against a local stack (this branch's image
  `haul/server:b145`, a throwaway PostgreSQL, the seed), DevTools' network offline, presses through the mouse at the
  places the accessible mirror gives: at 390 on headphones, «Filters», Sony ticked offline → «The page didn’t update»
  with Retry over the open sheet, address unchanged; online, Retry → `?brand=Sony`, the sheet still open with «1
  applied» and «Show 7 items», the notice gone. At 1440 the same as before: the notice over the page, Retry → «7
  items», Sony chipped. Control: the same walk against a bundle built from `main`'s `Layers.kt`, `Messages.kt` and
  `Storefront.kt` — at 390 the offline tick showed only the sheet («Show 15 items»), and the press at Retry's place
  closed it, applied nothing and left the notice on the page, as the run found; 1440 passed.
- **Goldens.** None changed: no screenshot draws a notice over an overlay, and the notice is drawn where it was.
