---
id: B-45
title: "server: a product's listing name, as cards, cart and orders write it"
status: done
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
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`,
  `server/src/main/resources/db/migration/V25__listing_name.sql`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Catalog.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/search/data/PostgresSearchRepository.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/ListingNameTest.kt`.

## Findings

Decided while building it:

- **The column** is `products.listing_name` (V25), nullable: `NULL` is the title, so every product the canvas
  writes as its title — all but one — carries none, and a blank name is refused (`products_listing_name_check`).
  The domain `Product.listingName` is already resolved (`listing_name ?: title`); cards, cart lines, the checkout's
  summary items (not drawn) and the search panel's product rows write it; the product page, its crumb and its
  accent keep the title.
- **The canvas's listing names.** Only the headphones differ from their title: «Sony WH-1000XM6 Wireless Noise
  Cancelling Headphones» on 28 artboards (Cart ×10, Order ×6, Home ×6, Catalog ×2, Saved ×4), «WH-1000XM6 …»
  under «Sony» on the 14 Product ones. One inconsistency: the duvet is «Linen Duvet Cover Set, Queen» on the
  16 Cart and Order artboards and «Linen Duvet Cover Set, Queen, Oat» on 8 cards (Home ×4, Cart_Empty ×2,
  Saved_PriceDrops ×2) — the colour, which a cart line writes on its own line, appended where a card has no
  such line. The majority (and the title) is kept; the hand-written bodies keep the canvas's «…, Oat». Every
  other card on the canvas writes its product's title (the Catalog headphones are titled with the brand already).
- **An order line keeps the listing name it was bought under**: placement copies it into `order_lines.title`
  (V10's «what was bought», the saga payload's `title`), which keeps its name — renaming the column would change
  the payload of sagas in flight for no reader's benefit. No backfill: a line placed before V25 was bought under
  its title, which was its listing name then. V25 backfills only the products (the headphones on a catalogue
  seeded before it; `ListingNameMigrationTest` holds the SQL to `SampleCatalog`).
- **Search reads the listing name**: the full text is over `coalesce(listing_name, title)`, the brand and the
  kind, and the substring match over the title and the listing name (V25 rebuilds `products_search` and adds
  `products_listing_name_trgm`). Over the listing name rather than beside the title, so a product with one does
  not count its words twice and outrank the rest; a title word missing from the listing name is still found by
  the substring. With the seed nothing new matches («Sony» was the brand already); the tests give the headphones
  a listing name of their own words.
- **The dialogs' product line** (reviews and questions) stays «brand + title», which happens to equal the
  headphones' listing name; for the duvet it writes «Brooklyn Home Co. Linen Duvet …» (B-22's finding). Whether
  it should write the listing name — or the canvas's shorter «Sony WH-1000XM6» — is for a person; not this
  item's AC.
- **V24** is not on `main` yet (B-53's); this is V25. Should B-45 merge first, a database migrated to V25 would
  refuse V24 as out of order (`validateOnMigrate`) — only a running stand, and none runs yet.
