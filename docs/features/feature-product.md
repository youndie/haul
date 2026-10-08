---
id: feature-product
title: Product page
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-product
api:
  - endpoint-catalog
  - endpoint-reviews
  - endpoint-cart
  - endpoint-saved
tags: []
---

# Product page

## 1. Overview

Everything needed to decide: photos (placeholders in v1), variants, price, delivery options with dates, returns, the seller, description, specifications, reviews and questions.

## 2. Business rules

* choosing a colour and a bundle selects one `Sku`; price, old price, discount and stock follow it;
* delivery estimate per method for the customer's default address: courier «tomorrow» when ordered before the seller's cut-off (23:30 local) and in stock, otherwise the day after; pickup point and locker one day later than courier; free over $35 or for Plus (§8);
* «Order within 3 h 42 min» counts down to the cut-off;
* a `Sku` with stock 0 cannot be added to the cart; the page says «Out of stock» and keeps «Save»;
* «12K bought this month» is the count of delivered and in-transit units in the last 30 days, rounded down to thousands above 1,000;
* opening the page as a customer records a `ProductView`;
* under the price, for prices between $50 and $2,000: «or 4 payments of $87.25 with Haul Pay» (the price ÷ 4, rounded to cents).

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Variant changes the price
* **Given:** Sony WH-1000XM6
* **When:** the shopper picks «+ Travel case»
* **Then:** the price shown is that `Sku`'s price and «Add to cart» adds that `Sku`.

### Scenario: Out of stock
* **Given:** a `Sku` with stock 0
* **When:** the client adds it to the cart
* **Then:** the server returns `409` with `out_of_stock`.

### Scenario: Unknown product
* **When:** the client asks for a product id that does not exist
* **Then:** the server returns `404` with `product_not_found`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
