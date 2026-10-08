---
id: B-45
title: "server: a product's listing name, as cards, cart and orders write it"
status: open
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-18]
---

# B-45 — server: a product's listing name, as cards, cart and orders write it

The canvas writes «Sony WH-1000XM6 Wireless Noise Cancelling Headphones» on cards, in the cart and on
the order, and «WH-1000XM6 …» under the brand on the product page. The server sends the product's title
everywhere, so every list of the headphones differs from its artboard by one line — the largest single
share of `Order_Placed_Phone`'s 5.11 % (B-18), and the same drift in the cart (B-13). «Brand + title» is
not the rule either: the duvet's brand is its seller's, and the canvas leaves it out. Found by B-13,
measured by B-18.

Decided as product owner: a product has a listing name — what a card, a cart line and an order line
write — which defaults to its title; the seed gives each product the canvas's listing name. The product
page keeps brand above title. An order line keeps the listing name it was bought under.

- AC: cards, cart lines and order lines carry the listing name; the headphones read «Sony WH-1000XM6 …»
  there and «WH-1000XM6 …» on the product page; the client bodies and goldens that change are re-recorded
  and the parity of the affected screens (Cart, Order, Home, Catalog, Search) does not get worse —
  `Order_Placed_Phone` within tolerance.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`.
