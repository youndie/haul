---
id: B-98
title: "server: delivery dates, fees and the address agree across pages"
status: wip
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
