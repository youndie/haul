---
id: B-57
title: "server: a deal's price is what the cart charges"
status: open
priority: P1
size: S
stage: stage-4-cart
blocked_by: [B-53]
---

# B-57 — server: a deal's price is what the cart charges

Deal cards (`dealCards`) draw `deals.price_cents`, but the card's «+» puts the SKU in the cart, which charges
`skus.price_cents`. The Sony deal happens to match ($349); the five generated deals are drawn at 70 % of the
price the cart then charges in full — the storefront advertises a price it does not honour. Found by B-53.

Decided as product owner: a deal is a price of its SKU for its window. While a deal is live its price is what
every surface draws and charges — the deal card, the product page, the cart, the quote, placement — through
the same pricing rule B-53 made (`CampaignPricing`); outside its window the SKU sells at its campaign or
regular price. A deal and a campaign on one SKU: the lower price wins.

- AC: route tests for a generated deal through card → cart → checkout → placement, the amount authorised
  equal to the card's price; after the deal's window the card is gone and the cart charges the regular price;
  the Sony deal unchanged; goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`.
