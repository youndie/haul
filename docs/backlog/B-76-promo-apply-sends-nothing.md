---
id: B-76
title: "client: «Apply» on the cart's promo code is applied"
status: done
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

## Findings (2026-10-10)

- **Reproduced on the stand (v0.2.0), as the same guest with the same one line**, in the app's browser pane while it
  was hidden: a click on the field, `AUTUMN10` typed and «Apply» clicked straight after sent no `PUT
  /api/v1/cart/promo`, with `AUTUMN10` drawn in the field; a second «Apply» a moment later sent it (`200`, the promo
  row and «10% off items, up to $50»). Typed, then a screenshot or two seconds' wait, then «Apply»: sent every time.
  `SUMMER5` came back `422` with «This code has expired» under the outlined field, an unknown code `404` with «There
  is no such code» — the refusal path was never broken.
- **Mechanism: in the browser a keystroke reaches the field a frame after a press does.** Compose UI 1.12 on the web
  (`NativeInputEventsProcessor`, `DomInputStrategy.scheduleCheckpoint`) collects the page's `keydown`/`beforeinput`
  events and applies them to the focused field in a `requestAnimationFrame` callback, while a pointer event goes to
  the scene at once (`ComposeWindowInternal.onPointerEvent`). «Apply» read `typed` before the code had reached it,
  found it empty and, by design, sent nothing. In a visible tab the window is one frame; in a hidden or background
  tab, where the browser throttles frames, it is seconds — the stand review's browser, and any script that fills
  then clicks. The wiring, the guest cart and the server were right; the press read a stale field.
- **The fix**: «Apply» waits for pending input before it reads the field (`ui/TypedInput.kt`, `awaitTypedInput`,
  two frames: the first may be one requested before the keystrokes, which runs ahead of the input). Recorded in
  research §3, «Found in B-76».
- **Tests**: `CartWiringTest` «apply sends a code typed just before the press» (Cart_Guest) holds the clock, presses
  «Apply», then types `AUTUMN10` before the next frame — the browser's order — and expects `ApplyPromo(AUTUMN10)`
  and the redraw. `CartRoutesTest` «a customer applies a code and sees why another is refused»: the customer half
  of the AC over HTTP with a shildik token (promo row `−$2.40` on the mug, then `SUMMER5` → `422 promo_expired` and
  the field with «This code has expired»); the guest half was already `a promo applies once` and `an expired promo
  is 422 …`.
- **Mutation**: the press reading the field at once (the old `CartViews.kt`) — the wiring test red, `expected
  [ApplyPromo(…AUTUMN10)] but was []`; the route skipping `applyPromo` for a customer — the route test red (the field
  not applied); restored, green.
- **Not checked on the stand**: a customer's «Apply» in the browser (no test account to sign in with there); the
  press is the same for both, only the identity header differs, and that path carries every other cart command.
- **Same exposure, not fixed here** (scope): every other press that reads a field — the header's search button
  (`ui/HaulHeaderView.kt`, `input.submit()`), the review and question dialogs' «Submit»
  (`feature/product/ReviewDialogs.kt`), checkout's address form (`feature/checkout/CheckoutViews.kt`) — reads it
  the moment it is pressed; `awaitTypedInput` is there for them.
- **Seen on the way**: at 800 px wide the cart line's title is drawn one letter per line in a sliver beside the
  picture (the stand's Cart, guest, one line) — a layout defect for the review stage, not this item's.
