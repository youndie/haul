---
id: B-120
title: "fix: a product with nothing in stock promises no delivery date"
status: done
priority: P2
size: S
stage: stage-12-qa-run
---

# B-120 — fix: a product with nothing in stock promises no delivery date

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)): `p-073-13`, every SKU
out of stock, shows «Courier · Tomorrow · Free» on its page and «TOMORROW» on its card. Nothing can be bought, so
nothing arrives tomorrow; the date comes from `DeliveryCalendar` regardless of stock.

- **No stock, no date.** A product (or the chosen SKU) with nothing to sell draws no delivery promise on its page
  or card; the buy box says it is out of stock, as it does. Rejected: a later date — there is no restock date
  to promise from.

- AC: an all-out-of-stock product's page and card show no delivery line; a product with one SKU in stock shows
  the date for that SKU and none when an out-of-stock SKU is chosen.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/DeliveryCalendar.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`.

## Findings (2026-10-12)

- **One rule, in the calendar**: `DeliveryCalendar.promised(item, sku)` is the day a card or the product page
  promises, `null` for a SKU with nothing in stock; `courier(item)` stays for the cart, checkout and the order page,
  whose lines are bought. A card asks for the SKU it shows (`Listed.shown`, out of stock only when every SKU is; a
  deal's card for the deal's SKU), so the card of a product with nothing to sell comes without `delivery` — the
  field is now optional on the wire (`ProductCard.delivery: String?`) and the card ends at its rating. The page asks
  for the SKU chosen: out of stock it sends no delivery lines (the stock advice already stood in their place on
  screen, so the drawn page changes only in what the tree carries), and the advice names the day of the colour it
  turns to, read for that colour's SKU. `DeliveryCalendar.pickup(item)` went with it: the page reads the pickup day
  off the promised courier day.
- **Also the «Delivery: Tomorrow» facet**: it counted and kept every product whose seller ships tomorrow, so with
  the card's day gone it would have listed a card with no day under «Tomorrow». `isTomorrow` now reads the card's
  promise; the facet keeps exactly the cards that say «Tomorrow».
- **Left as it was**: the cart names a seller's courier day per group even when the group's only line is out of
  stock (that line is neither counted nor bought, B-11's changed lines). Outside this item's page and card; worth a
  look if the cart should say nothing there either.
- **Tests**: `OutOfStockPromiseTest` (server, on the seed) — a generated product with every SKU out of stock has no
  delivery lines, no cut-off and no day in its advice on its page, and a card with no `delivery` beside an in-stock
  card that says «Tomorrow»; the «Delivery: Tomorrow» grid leaves it out and keeps exactly the cards that say
  «Tomorrow»; the headphones on Midnight Black say «Courier · Tomorrow» / «Pickup point · Thu, Oct 9», on Silver
  nothing, with «Midnight Black is in stock and arrives tomorrow.», and their card «Tomorrow».
  `OutOfStockCardTest` (client) — a card encoded the server's way without the field decodes, draws no day, and ends
  at its rating, beside one that draws «TOMORROW».
- **Mutation**, each on the committed change, each red, restored, `git status` clean: the card back to
  `calendar.courier(item)` (card and facet red); the page asking for `item.shown` instead of the chosen SKU (the
  headphones case red); `promised` ignoring stock (all four red); the facet back to `courier(item)` (facet red); the
  card drawing an empty line for an absent day (`OutOfStockCardTest` red).
- **Goldens**: none changed. No fixture body draws a card out of stock (every card in `bodies/` has its «+»);
  `product_out_of_stock.json` (Product_OutOfStock) still carries the canvas's delivery lines, which the buy box never
  drew beside the stock advice, so its pixels do not move.

