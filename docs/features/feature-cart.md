---
id: feature-cart
title: Cart
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-cart
api:
  - endpoint-cart
  - endpoint-saved
tags: []
---

# Cart

## 1. Overview

The cart groups lines by seller, each group with its delivery day; it shows what the discounts and the promo code saved, the total, and the points the order will earn.

## 2. Business rules

* quantity 1…10 per line and never above stock;
* «Select all» / per-line selection; «Delete selected» removes the selected lines; checkout takes the selected lines only;
* «Save for later» moves the line's product to the Saved list and removes the line;
* totals: Items = Σ old price (or price when no old) × qty; Discount = Σ (old − price) × qty + promo; Delivery per §8; Total = Items − Discount + Delivery;
* one promo code per cart; an invalid, expired or inapplicable code is refused with its reason;
* a line whose `Sku` went out of stock or changed price since it was added is marked and excluded from selection until the shopper acknowledges it;
* «You'll earn N points»: whole dollars of the total, ×2 for Plus (Maya: $512 → 1,024);
* a guest sees the cart; «Checkout» asks to sign in.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/cart/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/server/cart/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/cart/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Totals as drawn
* **Given:** Maya's cart (headphones $349 was $449, duvet cover $139 was $179, mugs $24)
* **When:** the client loads the cart
* **Then:** Items is $652.00, Discount −$140.00, Delivery Free, Total $512.00.

### Scenario: Promo applies once
* **Given:** `AUTUMN10` applied
* **When:** the client applies another code
* **Then:** the server returns `409` with `promo_already_applied`.

### Scenario: Expired promo
* **When:** the client applies `SUMMER5`
* **Then:** the server returns `422` with `promo_expired`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
