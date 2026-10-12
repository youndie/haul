---
id: B-118
title: "fix: a click outside a price field applies the bound typed into it"
status: done
priority: P3
size: S
stage: stage-12-qa-run
---

# B-118 — fix: a click outside a price field applies the bound typed into it

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), at 1440 on a category:
after typing `200` into the price «to» field, a click on empty page space left the caret in the field and applied
nothing; Tab, Enter or a click into another field applied it. A shopper expects a click elsewhere to finish the
field. Tab out of the field also sent the same parts request twice (`…price_min=100&price_max=400…` once
aborted, then answered 200).

- **A click on the page outside a field takes the focus off it**, so the bound is applied the way Tab applies
  it, and one leave sends one request. Rejected: applying on every keystroke — each digit would be a request
  and a grid redrawn under the typing.

- AC: type a bound, click blank page space — the grid narrows once, the address carries the bound; Tab sends
  one parts request, not two.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog`.

## Findings (2026-10-12)

- **Why a click elsewhere applied nothing.** A Compose field keeps the focus through a press nothing on the page
  takes: the web viewport clears focus on a mouse press only behind `ComposeViewportConfiguration
  .isClearFocusOnMouseDownEnabled`, off by default, mouse only, and for every field at once — the search field,
  the dialogs' boxes and the checkout's address form included, which decides itself which presses leave it. So
  the fix is the price fields' own: a focused price field holds the page's `BlankPress` (`ui/BlankPress.kt`)
  with its whole box, and `releasingOnBlankPress` on the storefront's page (and on the phone's filter sheet, the
  page its fields are on there) takes the focus off it on a press no control takes — read on the last pointer
  pass, never consumed, not the start of a drag — outside that box, once the keys typed before the press have
  landed (B-76). Leaving the field is then the same leave Tab is: one confirm, one load. A press on another
  control is that control's, as before; a press on the box around the digits keeps the field.
- **Why Tab sent the request twice.** Not Tab itself: walked in headless Chromium against a local stack of the
  v0.4.0 image, Tab out of «to» sent one request when «from» had not been focused, and two — the first
  `ERR_ABORTED` — 3 of 3 times when the shopper came from «from» (the run's step 1) and the press into «to», the
  digits and Tab followed each other within two frames. Each field's leave launched a confirm that waits two
  frames for typed keys (B-76) and then reads **both** fields; «from»'s, launched by the press into «to», was
  still waiting when Tab left «to», so both read `100`/`400` and both sent. One confirm now waits at a time: the
  later cancels the earlier, and reads the fields after it would have.
- **Verified in the browser** with the fixed image (built from this branch, the local stack, a scratch CDP probe
  on the Linux box): Tab after «from» — 3 of 3 one request, 200; a click on blank page space at (1400, 300) —
  3 of 3 one request, the address `?price_min=100&price_max=400&sort=price-asc`, the hidden input gone (the
  field let go); before the fix the same click sent nothing and left the input focused.
- **Tests** (`PriceRangeTest`): «a click on blank page space leaves a price field and applies it» (a mouse click
  at 1440), «a press on the box around a price field keeps it», «tab out of a price field sends one load» (the
  frames stepped one at a time so the two loads would start a frame apart, as in the browser — with the frames
  merely held both confirms resumed in one frame and the second cancelled the first load before it reached the
  transport, so the test passed either way), «a tap on the filter sheet's blank space applies a typed bound».
  Mutations, each red then restored: no `waiting?.cancel()` → the Tab test red with the stand's two identical
  requests; no `releasingOnBlankPress` on the page and the sheet → the click and the tap tests red; no box bounds
  in `BlankPress.outside` → the box test red.
- **Goldens.** None changed: nothing drawn moves.
- **Docs.** Research §«the price range is the one address the client completes» says how a field is left and that
  one confirm waits at a time; QA TC-CAT-05 (one request on Tab, step 4 left with a click on blank space) and
  TC-CAT-10 (a tap on the sheet's blank space) with the new coverage.
