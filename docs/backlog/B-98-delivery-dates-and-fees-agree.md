---
id: B-98
title: "server: delivery dates, fees and the address agree across pages"
status: done
priority: P2
size: M
stage: stage-11-qa
---

# B-98 — server: delivery dates, fees and the address agree across pages

Found in the QA pass of 2026-10-10 (v0.3.0), code audit and signed-in walk:
- the product page, cards and cart say «Courier · <today + 1 + dispatchDays (+1 after the cut-off)>», while checkout
  always offers windows from tomorrow and labels the method «Tomorrow, <date>»; pickup is courier+1 on the product page
  and tomorrow+1 at checkout (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/DeliveryCalendar.kt:25-28`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt:199-205`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt:110`);
- the product page shows «$5.99» delivery under $35 to a Plus member, whose delivery is free; the constants are copied
  from `Totals` (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt:173, 208, 371-372`);
- «Delivering to Brooklyn, NY 11211» and «To Brooklyn, NY 11211» are shown to every customer whatever their address
  (`server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt:148`, `ProductScreen.kt:207`);
- an order with the slot Thu Oct 15 was «Delivered Oct 10» (the fast fulfilment clock against real dates).

- AC: one delivery calendar for every page; fees from `Totals` and the viewer's membership; the customer's own address
  where they have one; the order page explains a simulated delivery or keeps the slot's date; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/DeliveryCalendar.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`.

## Findings (2026-10-11)

- **One calendar** (research D7, «Decided in B-98»): `DeliveryCalendar` is the only place a delivery day is worked
  out. Cards, the product page and the cart already read it; checkout now offers its five days of windows from
  `DeliveryCalendar.courier(lines)`, and the order page's per-shipment day calls `DeliveryCalendar.courierDay` on the
  moment the order was placed instead of repeating the rule. The courier's method says the day the calendar's way
  («Tomorrow, Oct 8», or «Thu, Oct 9»), a pickup the day after (`DeliveryCalendar.pickupDay`). After the cut-off
  checkout moves a day with the product page; before, it still offered tomorrow.
- **Choice: windows from the earliest line's day, not the latest.** The canvas itself draws both: Maya's cart says
  Sony «Courier · Tomorrow» and Brooklyn Home Co. «Courier · Thu, Oct 9», and her checkout offers Wed 8 — so the
  window is the first courier's, and a slower seller's shipment comes on its own day, which the cart (per seller)
  and the order page (per shipment) already name. A cart of only the duvet is now offered windows from Thursday
  and refused Wednesday's (`slot_not_found`). Starting from the latest line would make the summary's single
  delivery day true for every shipment, but would move the canvas's checkout and every order page placed from it;
  it stays the alternative if the owner prefers it — a one-line change in `DeliveryCalendar.courier(items)`.
- **Fees from `Totals`**: `Totals.feeCents` / `Totals.deliveryCents` are the rule, `Totals.of` uses them, and the
  product page prices both its courier and its pickup line with them for the SKU alone and the viewer's
  membership: «Free» to a Plus member under $35 (was $5.99), and $5.99 for a pickup under $35 to a non-member (was
  «Free», though checkout charges the same fee for every method). The copied constants are gone.
- **The customer's own place**: `Viewer.place` is the city and ZIP of the address a courier delivers to
  (`StoredCheckout.deliveredTo`, the rule checkout's quote and address form already used, now shared), read by
  `Viewers` for a customer; the header's «Delivering to» and the product page's «To …» use `Frame.place`, which
  falls back to «Brooklyn, NY 11211» for a guest and for a customer with no address. Maya's address is Brooklyn,
  so no fixture moved. It costs a customer's page two reads (the stored checkout and the addresses).
- **Choice: the order page explains a simulated delivery, keeps the real date.** A delivered or picked-up order
  that arrived before the day it was promised (the window's, else the calendar's) reads, under «Delivered Oct 9»,
  «Deliveries here are simulated: this one came ahead of Sat, Oct 11, the window it was booked for.» (or «…, the
  day it was due.»), then the returns line. Keeping the window's date instead would draw a delivery in the future
  and start the return window on a day not yet come; making the simulator wait for the window would change B-17's
  pace and the stand's fast clock, outside this item.
- **Left as they were, worth a look**: the header's «Free delivery over $35» is also drawn to a Plus member (true,
  but not the member's whole rule — every signed-in golden draws it, so changing it is a golden-wide change);
  checkout's summary names one delivery day, the window's, while a slower seller's shipment comes later (the order
  page says so per shipment); the account's order cards do not carry the simulated-delivery line.
- **Tests**: `DeliveryAgreesTest` (server) — a cart of the duvet is offered windows from Thu Oct 9 as its product
  page and cart say, «Thu, Oct 9» on the courier, «Fri, Oct 10» on the pickup, and Wednesday's window refused; a cart
  of two sellers keeps the canvas's «Tomorrow, Oct 8»; after the cut-off checkout moves with the product page; the
  product page's fee is $5.99 for the mugs and «Free» to a member (both lines), the headphones' «Free» the control;
  a customer's header and «To …» name their own city and ZIP, the default before they have an address and to a
  guest. `EarlyDeliveryTest` — an order delivered before its window, and before its due day without one, says so
  under the real date; one delivered on its window's day reads as before. `CheckoutQuoteTest` and
  `CheckoutFixturesTest` take the calendar.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: checkout's windows back from
  tomorrow (three of `DeliveryAgreesTest` red); the product page's fee ignoring membership; `Viewers` dropping the
  place; the order page without the simulated line (both early cases red, the on-time control green); the
  calendar taking the latest line instead of the earliest (the two-seller case red); the pickup line back to a
  fixed «Free».
- **Goldens**: none changed — every fixture body is the same tree (Maya's cart starts on Wed 8, her address is
  Brooklyn, no fixture's product is under $35 to a member, no fixture order arrived early).
- **Where the checks ran**: on the Linux build machine — `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` (438 server and 321 client tests, 0 failed; `viddikVerify` and ktlint in it);
  `scripts/e2e.sh` (the whole path and the shoppers, green). `make check` on the Mac.
