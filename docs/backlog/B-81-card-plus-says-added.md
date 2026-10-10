---
id: B-81
title: "server + client: a card's «+» says the item was added"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-81 — server + client: a card's «+» says the item was added

B-75 gave «Add to cart» on the product page a message («Added to your cart», «View cart») and left a card's
«+» silent: the card stays under the finger and the header's count answers.

- **Decided as product owner (2026-10-10):** the «+» answers with the same message. Its answer becomes
  `sequence[update, show_message]`, the way B-75 made the buy box's.
- AC: on every page that draws cards (home, catalog, search, deals, «Picked for you»), «+» updates the header's
  count and the card, and shows «Added to your cart» with «View cart»; a «+» with nothing to add shows nothing;
  server and client tests; the e2e reads the message.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.

## Findings (2026-10-10)

- **The change is the one line B-75 left**: `LineAnswers.after` answers every press it answers in place —
  `?answer=card` as well as `?answer=details` — with `sequence[update, ADDED_MESSAGE]`. The client needed nothing:
  a card's «+» goes through the same `linePress` → `LocalHaulActions.handle` chain as the buy box, which already
  draws `show_message` (`shell/Messages.kt`). Research §2's «Decided in B-75» now says the card speaks too.
- **«Nothing to add» is B-75's**: a «+» the tree gives no command (out of stock, the line at its limit) is
  disabled and sends nothing, so there is no answer to say anything. The Saved list's cards (`inPlace = false`)
  answer `refresh` and stay silent — not in the AC's list of pages. A stale «+» whose quantity the line already
  holds (a double press before the first answer arrives) is accepted as no change and says «Added» again; the
  second message replaces the first, so the shopper sees one. Not chased: telling it apart needs the line's
  quantity before the change in the answer.
- **Seen, not changed — the cart page's «Picked for you»** (`CartScreen`, an empty cart's picks) draws its cards
  `inPlace`, so «+» there answers `update` of the header and the card only: the count turns 1 while the page still
  reads «Your cart is empty» until it is loaded again. That is B-63's answer on a page whose body depends on the
  cart; it predates this item. Since B-81 the message's «View cart» is offered over it.
- **Tests**: `LineAnswersTest` — every card press reads the message after the update (`pressedOn`); new: a card's
  «+» on every page that draws cards for a guest (home's deals, the deals page's two grids, a category, a search),
  and the press that takes the last one in stock says so and leaves a card with nothing to add.
  `RecommendationsRoutesTest` — a pick's «+» on a customer's home. `DealPriceTest` reads the update inside the
  sequence. Client: `InPlaceAnswersTest` — the card's «+» draws the count and the message with «View cart»; the
  disabled «+» draws no message. e2e: a step where a guest of its own presses a home card's «+» and reads the
  update and the message, so the shopper's own cart is untouched.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: the bare `update` for cards
  (7 of `LineAnswersTest` and the pick test red); the shell dropping `show_message` (the card test in
  `InPlaceAnswersTest`); `addToCart` ignoring the stock (the last-one test).
- **Goldens**: none changed — no tree a fixture draws changed.
