---
id: B-97
title: "client + server: what checkout shows is what it will place"
status: done
priority: P1
size: M
stage: stage-11-qa
---

# B-97 — client + server: what checkout shows is what it will place

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk at 1366:
- the address form is sent only when focus leaves the form or on Enter (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt:391-397`);
  typing the address and clicking the greyed «Place order» (or blank space) sends nothing, and the button keeps
  saying «Fill in the delivery address»;
- picking a delivery day is drawn only on the client: after Sun 11 12:00–15:00, clicking «Thu 15» sent no request,
  highlighted Thu with no slot, kept «Place order» enabled, while the server held Sun 11 and the summary said
  «Delivery · Sun, Oct 11».

- AC: leaving the address by any press sends it (B-83's `awaitTypedInput` where a press reads it); choosing a day either
  asks the server or disables «Place order» until a slot of that day is picked; the summary always shows the server's
  choice; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt`.

## Done (2026-10-11)

- **The address is sent by any press outside the form** (`CheckoutBodyView`, `onPressOutside`): released anywhere on
  the checkout's body but in the address section — the held «Place order», blank space, another section's control —
  the press waits for pending input (`awaitTypedInput`, B-76) and then clears focus, which sends the form as Tab and
  Enter already did; a drag past the touch slop is a scroll and does nothing. A press inside the section (a field, a
  label) is not leaving it. The form's state moved into `AddressDraft` (typed values per server form, focus kept
  across a redraw), so the press and «Place order» can read it.
- **«Place order» with an address not yet quoted sends the address, not the order.** The button waits for pending
  input too; if the form differs from the server's, the press sends the address (above) and places nothing — the
  order would have gone to an address the summary does not quote. The next tree quotes the new address and the next
  press places it. A form that differs and has no focus — an address that got no answer (B-92 gives a `POST` no
  «Retry») — is sent again by the next press outside it.
- **Decided: a day's tile holds the button, it does not ask the server.** While a day other than the chosen window's
  is shown, «Place order» is drawn held with the server's words `DeliverySlots.otherDayHint` («Pick a delivery
  window», the same words as its hint for no window) until one of that day's windows is chosen — a `PUT` like any
  choice — or the chosen day is shown again. Asking the server per tile would need a stored day without a window
  (a column, a migration) and a round trip per tile for a state the window's choice settles anyway. With no window
  chosen (Checkout_PlaceError) the server's hold stands as it was. Recorded in research D5, «Decided in B-14».
- **The summary is the server's throughout**: the client never redraws it but for holding the button; its delivery
  day follows the chosen window, held server-side by `CheckoutRoutesTest` (Wed 8 → Thu 9 after a choice).
- **Tests** (`composeApp/src/desktopTest/.../checkout/CheckoutWiringTest.kt`): «a press on the held button sends the
  address typed just before it» (Checkout_Validation, the clock held, the press then the typing — the browser's
  order); «a press on blank space sends the address»; «a press inside the form sends nothing»; «place order with an
  address not yet sent sends the address and places nothing» (typed after the press, before the next frame); «an
  address that got no answer is sent again by the next press»; «another day holds place order until one of its
  windows is chosen» (hint shown, summary still «Delivery · Wed, Oct 8», button inert; back on Wed it places).
  Server: the four courier bodies carry `otherDayHint`, held by `CheckoutFixturesTest`.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: the press outside never acting
  (4 tests red), the press-outside without `awaitTypedInput` (1), «Place order» ignoring an unsent address (2), «Place
  order» without `awaitTypedInput` (1), no hold for another day (1), the form's bounds never containing a press (3,
  the existing «sent when the shopper is done» among them), no resend of an unanswered address (1), the server
  without `otherDayHint` (`CheckoutFixturesTest`).
- **Goldens**: none changed — no artboard draws another day shown, and the redraw is the same pixels.

## Findings (2026-10-11)

- **The header's steps still say «Review» while another day holds the button**: the indicator is the server's
  (B-79) and the hold is the client's. Small and honest enough — the order the server holds is placeable — but the
  two disagree for as long as another day is shown.
- **A press on the checkout header** (the logo, `CheckoutHeaderView`) is outside the body and leaves the page; the
  typed address is not sent then, as before. Leaving the page drops what was not sent, as any page does.
- **Two sends of one address are possible** when a second press comes inside the first one's round trip (the form
  still differs from the server's until the redraw). Harmless: a form equal to an address the customer has chooses
  it and stores nothing (B-40).
- **On the desktop the press-outside's wait is held by one test only** (the placement one): there input reaches the
  field at once, so the press on the held button sees the typing with or without it; the browser is where it counts.
- **Not walked in a browser**: no local stack with a signed-in customer was stood up for this item; the desktop tests
  hold the browser's order of press and input as B-76's do. B-83's checkout part (the address form) is covered here;
  its search button and dialogs remain.

## Where the checks ran (2026-10-11)

On the WSL box (Ubuntu 24.04), after the rebase on `origin/main` (B-88, B-95 in): `:composeApp:wasmJsBrowserDistribution`
green; `check :server:installDist` green (server and client suites, `viddikVerify`, ktlint); `scripts/e2e.sh`
against `haul/server:b97` green (WholePathTest, the shoppers). On the Mac: `make check` and `make docs-against
BASE=origin/main` clean, anchors all found.
