---
id: B-129
title: "fix: the small lines the v0.4.0 customer walk noticed"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-129 — fix: the small lines the v0.4.0 customer walk noticed

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), on a local v0.4.0 stack, each small:

- A promo code outlives the order: `AUTUMN10` applied before placing HL-48304 stayed on the cart and was applied to
  the next two orders unasked. A code is for the order it was typed for; placing clears it.
- Seeded reviews from 2023 and 2025 read «Oct 21» on Oct 11 — a date with no year reads as the future. A date
  outside the current year carries its year.
- The overview draws «All orders» with an active order on screen though every other order is listed below.
- A cart with nothing ticked still says «You'll earn 0 points on this order».
- The return dialog's reason list stays open on Escape.
- A new question is listed last, not first.

- AC: each behaves as its line says; a test per line where it is the server's or the client's logic.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt`.

## Findings (2026-10-12)

Each line read against what the product had decided first; four changed, two stay as decided.

- **The promo code** — nothing decided it should outlive an order (research §5 has «one per order»). The saga's
  `clear-cart` announcement (`ClearBoughtLines`, `OrderSaga.kt`) now takes the code off with the bought lines, only
  while the cart still holds the code the order was placed with, so a code typed since stays and a re-run is
  harmless. A declined card never reaches the announcement: the cart and its code stay for the next try.
  `PlacementRoutesTest` «placing takes the order's promo code off the cart».
- **Dates without a year** — the canvas writes «Sep 28» within its year, so the year is added only outside the
  store's current year (`ReviewTabs.day`, which now takes the `StoreClock`): reviews, «Asked …», the seller's answer
  date and «You reviewed this on …». `ReviewRoutesTest` «a review or a question from an earlier year carries its
  year»; the reviewer test's «Jan 15» (a review of 2024 at the canvas's 2025) now reads «Jan 15, 2024». Other «MMM d»
  dates (orders, returns, the account's history) are not reviews' and were left: the walk saw none of another year
  (no seeded order exists), and a store's orders are this year's until one runs past a new year.
- **«All orders» on the overview — decided otherwise, left.** `AccountScreen.recent` counts the active orders as
  left out of the history on purpose (its KDoc: «those above it among the active, or older ones»), and the canvas's
  `Account_Content` draws «All orders» over exactly the walk's case: two active cards and four history rows, six
  orders, nothing else (`AccountFixturesTest` holds the body). The link is also the way to the history's chips.
- **«You'll earn 0 points»** — B-100 noted it and left it untouched; no decision behind it. The summary draws no
  points line when the order earns none (`CartScreen.summary`): `CustomerCartTest` «a cart with nothing ticked
  promises no points»; `cart_nothing_selected.json` and the goldens `Cart_NothingSelected`(`_Phone`) lose the line.
- **The return dialog's reasons on Escape** — the list is drawn inline under the field, held by no focus, so the key
  reached nothing. It now takes the focus when it opens and closes on Escape before its entries see the key, as
  `Menu` does since B-75; the dialog stays. `ReturnWiringTest` «escape closes the reasons' list and keeps the dialog».
- **A new question listed last — decided otherwise, left.** Questions list answered first (B-22, which named this very
  cost: «on a product with more than ten answered a new question would not be on the tab»; B-99; TC-PDP-09 expects it),
  the canvas's `Product_Questions` draws its open question after the two answered, and `ReviewRoutesTest` «a question
  asked shows as not answered yet and is counted» holds the asker's own question after the 85 answered. Leading the
  asker's list with their own question, as B-99 does for their review, would be a new decision for the owner.
- **Goldens:** a forced re-record on the Linux box, in a copy outside the mutagen replica, wrote 293 and changed two
  byte-wise — `Cart_NothingSelected` and `Cart_NothingSelected_Phone`, the points line gone; both looked at.
- **Mutation:** each implementation reverted with its test kept — the four new tests red (and `CartFixturesTest` red
  on the points line), green again once restored.
