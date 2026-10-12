---
id: B-87
title: "server: what home and the empty cart show when no deal is live"
status: done
priority: P3
size: S
stage: stage-10-review
---

# B-87 — server: what home and the empty cart show when no deal is live

B-70 chose «Popular on Haul» (the six most-reviewed products in stock) for home while no deal of the day is live;
the item only asked for «at least one row of products». The empty cart's «Picked for you» still shows only
deals, so it is empty in the seconds after midnight and on a store without deals.

- **Decided (owner, 2026-10-12): «Popular on Haul» stays on home**, and the empty cart's «Picked for you» falls back to
  the same row while no deal is live.
- AC: with no live deal, home shows «Popular on Haul» and the empty cart shows the same six; with deals, both show the
  deals as today; tests for both states.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`.

## Findings

- **One rule, two pages** (`popular`, `popularTitle`, `feature/catalog/screen/HomeScreen.kt`): home's private
  `HomeScreen.popular` became a top-level function that both home and the empty cart call, so the cart's fallback is
  home's six in home's order rather than a second rule that could drift from it. It takes the prices to draw at — home
  passes the viewer's, the empty cart the cart owner's, as its deal picks already did (B-53) — and `inPlace`, which
  the cart passes `false`: a pick's «+» turns the page into a cart and is answered with the page (B-95).
- **The title follows home: «Popular on Haul»** (decided here, as the owner asked). The title names what the row
  holds: in the cart's fallback that is the most reviewed products in stock, not a pick from today's deals, and
  «Picked for you · From today's deals» over it would be the kind of line B-107 took out. The row also takes home's
  ids, `popular-title` and `popular`, and no subtitle. Its grid stays the cart's (six columns, no scroll), as the
  deal picks are drawn; the canvas has no artboard for this state, so no design decides otherwise.
- **With deals nothing changes**: the empty cart draws «Picked for you · From today's deals» and no popular row, home
  draws «Deals of the day»; the canvas's bodies (`cart_empty.json`, `home_*.json`) are at the canvas clock, where
  deals are live, so no fixture and no golden changed.
- **Unchanged on purpose**: «See today's deals» still leads to `/deals` with no deal live (it shows «On sale»); the
  item's AC does not cover the button.
- **Tests** — `DealsOfTheDayTest`: `the empty cart draws home's row with deals and without` (with deals: «Picked for
  you» over the six deals' products, no popular row, home's deals as the control; at the deals' end: no picks, «Popular
  on Haul» with no subtitle, the same six products in the same order as home's row and as the six most reviewed in
  stock, each «+» naming no part to redraw, «Your cart is empty» still there). The existing B-58 and B-70 tests in the
  same class stay green.
- **Mutations** (each on the committed change, restored after, `git status` clean), all red in the new test: no
  fallback in the cart — «the empty cart has no row of products»; the fallback titled «Picked for you» — «expected
  Popular on Haul»; the cart's popular cards answered in place — «redraws a part, not the page: …?answer=card»; the
  popular row drawn beside live deals — «the empty cart draws the popular row beside deals». The owner's prices are
  not mutation-checked: a guest's and the public prices are the same list.
