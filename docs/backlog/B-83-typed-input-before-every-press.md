---
id: B-83
title: "client: every press that reads a field sees what was typed"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-83 — client: every press that reads a field sees what was typed

B-76 found that on the web a press can be handled before the typed text reaches the focused field, and added
`awaitTypedInput` (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/TypedInput.kt`) for the promo code. The same race is open on the header's search button,
the review and question dialogs' «Post»/«Send», and checkout's address form (B-76 findings). B-69 also lost
keystrokes when focus moved to another field before a throttled frame applied them.

- AC: each of those presses sends what was typed just before it (a client test per press, red before);
  moving focus right after typing keeps the typed text, or the finding is recorded with its cause.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/TypedInput.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt`.

## Findings (2026-10-11)

- **Every press that reads a field now waits for pending input.** The header's search button
  (`ui/HaulHeaderView.kt`, `SearchField`) and the review and question dialogs' «Post» / «Send»
  (`feature/product/ReviewDialogs.kt`, `ReviewDialog`, `QuestionDialog`) run `awaitTypedInput` before they read
  the field, as the cart's «Apply» does since B-76; the dialogs check the server's rules and `sending` after the
  wait, so a second press inside the first one's round trip still sends nothing. Checkout's address form is B-97's
  and was not touched. The client's other text fields are the price range's (B-69, already waiting) and the cart's
  promo code (B-76): no press reads a field without the wait any more. Enter is not wrapped: in the browser its
  `keydown` is applied in the same frame as the keys typed before it, in their order
  (`NativeInputEventsProcessor.runCheckpoint` sorts the frame's events by time). Recorded in research §3, «Found in
  B-83».
- **Tests** (red before, each with the press first and the typing after it, before the next frame — the browser's
  order): `StorefrontTest` «the search button searches for a query typed just before the press» (the search
  button got a tag, `SEARCH_BUTTON_TAG`); `ReviewWiringTest` «post sends a review typed just before the press»
  (stars and title typed, the body typed after the press) and «send sends a question typed just before the press».
  Before the fix: the search opened nothing, the review and the question were never sent (the draft failed the
  rules). Mutation on the committed change: each press reading at once (the old code) — the three red; only the
  `awaitTypedInput` calls taken out, the `launch` kept — the three red too, so here the tests pin the wait itself,
  not only the deferral (unlike B-69's). Restored, green.
- **Moving the focus right after typing keeps the typed text** (the AC's second half). The keys of a frame are
  applied through the edit session of the field they were typed in — the pending frame holds that field's
  `onEditCommand`, and the field applies them although it has lost the focus. Walked in the browser pane against
  this branch's image (`haul/server:b83` beside a PostgreSQL on the WSL box, through an ssh tunnel), with
  `requestAnimationFrame` held back four seconds — longer than a hidden pane's throttling (about one frame a
  second, measured): `250` typed into the price «from» and «to» pressed 16 ms later — «from» kept `$250` and its
  leaving applied `price_min=250`; the header's field pressed, `kettle` typed and the price «from» pressed within
  49 ms — the field kept `kettle`.
- **What lost the text in B-69 and in the review walk is most likely a press that did not focus the field at all.**
  On a page just loaded in the pane, the first press on a text field often focuses nothing — no caret, no backing
  `<input>` created — 6 of 9 fresh loads, the price «from» pressed 4–10 s after the load; the press is delivered
  (a chip pressed first navigates), and a second press on the field focuses it. Keys typed after such a press go
  nowhere, and the next field's press works — the shape both reports describe («from» left as it was, the title
  empty). Not the load's timing (10 s waits fail too), not the canvas's DOM focus (focused by script before the
  press, still 2 of 3 fail). The cause is inside Compose's web input (Compose UI 1.12.1) and was not found; no
  workaround was added — a fix there needs the cause first.
- **Seen on the way**: the search suggest panel stays open while the focus is in another field (typed `kettle`,
  then the price field pressed: the panel stays over the page). That is B-77's rule — the panel closes on the
  scrim, an emptied field or a new page — not a defect of this item.
- **Not walked in a browser**: the search button and the dialogs with the fix. Once the pane was hidden there was
  no screenshot to click by, and the dialogs need a signed-in customer (shildik); the desktop tests hold the
  browser's order of press and input, as B-76's and B-97's do.
