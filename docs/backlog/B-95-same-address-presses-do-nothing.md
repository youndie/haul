---
id: B-95
title: "client: a press to the page already shown refreshes it"
status: done
priority: P1
size: S
stage: stage-11-qa
---

# B-95 — client: a press to the page already shown refreshes it

Found in the QA pass of 2026-10-10 (v0.3.0). `Navigator.open` ignores the address already shown (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt:146`), so:
- on an empty cart, «+» on a «Picked for you» card answers an `update` of the header and the card only; the page still
  says «Your cart is empty» with the count at 1, and the message's «View cart» and the header's Cart do nothing
  (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt:230`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt:58-65`);
- submitting the query already shown (Enter, the button, a suggestion, «All N results») does nothing and the
  suggestion panel stays open (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:193-195, 221-226`).

- AC: a press to the page already shown reloads it in place (B-62 keeps it on screen) and closes what it opened; on
  the empty cart the line appears after «+»; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.

## Findings (2026-10-11)

- **One cause, two symptoms on the cart.** `Navigator.open` returned on the address shown, so every press whose
  `navigate` named the page already drawn was dropped: the header's Cart and a message's «View cart» on the cart,
  the logo on home, Enter, the button, a suggestion or «All N results» for the query shown — and with no visit the
  suggest panel, which closes on a visit, stayed open. The empty cart's «+» was a second defect: its picks are cards
  built in place (B-63), so the answer updated the header and the card and left «Your cart is empty» drawn under a
  count of one.
- **The fix, client** (`shell/Navigation.kt`): a press to the address shown is a visit without a history entry. It
  goes the way B-62 loads a new address of the screen — behind the drawn page, its scroll kept, the loading line on,
  a failure under the notice with Retry — and closes what any visit closes: the panel, the filter sheet, the phone
  menu, the shell header's dialog and, now also on the same address, a dialog a tree presented over the page
  (`presented` is remembered per address and visit). A visit was chosen over the screen's refresh (B-93): the
  refresh is silent and its failure invisible, while a press the shopper made should show that it went and say
  when it did not. `openOrReload`, the one place that had worked around the drop (B-67's sign-in over the Saved
  list's error page), is gone; a tree's sign-in returning to the page shown still redraws through the refresh, in
  place, since only who is looking changed.
- **The fix, server** (`feature/cart/screen/CartScreen.kt`): the empty cart's picks are not built in place
  (`card(..., inPlace = false)`), so their «+» answers `refresh` and the cart is drawn again with its line, as the
  cart's own lines are. No message says «Added to your cart» there: the line on the page is the answer, as on the
  Saved list.
- **Tests**: `KeptPageTest` — a press to the address shown loads it again behind the page (scroll kept, loading
  line, no placeholder, no entry), and closes a dialog presented over it; `StorefrontTest` — the query shown,
  submitted again by Enter and by a suggestion, loads the results again and closes the panel; `ShellHeaderTest` —
  the cart's button on the cart now loads it again (it asserted the old no-op); `CartRoutesTest` — a pick's «+» on
  the empty cart answers `refresh` and the cart then lists the line. Mutations: the old `open` fails the four new
  client tests and two header walks (the cart's, and the Saved list's sign-in that leaned on `openOrReload`);
  `presented` remembered per address alone fails the dialog test; the picks built in place fail the cart route test.
