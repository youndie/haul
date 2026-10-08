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
* «Select all» / per-line selection; «Delete selected» removes the selected lines; checkout takes the selected lines only (*target*, B-14);
* «Save for later» moves the line's product to the Saved list and removes the line (*target*, B-20: the label is drawn, no command exists);
* totals, over the selected lines that count (ticked, in stock, unchanged): Items = Σ old price (or price when no old) × qty; Discount = Σ (old − price) × qty + promo — the promo is part of the discount, and also shown as a row of its own under it; Delivery per research D7; Total = Items − Discount + Delivery;
* delivery is free from $35 of the selected items at their price, before the promo, or for Plus; otherwise $5.99, one fee per cart rather than per seller group (research D5, «Decided in B-11»);
* one promo code per cart; an invalid, expired or inapplicable code is refused with its reason, and the refused code and its reason stay in the promo field until the next command; the code already applied, sent again, is not a refusal;
* a line whose `Sku` went out of stock or changed price since it was added is marked and excluded from selection until the shopper acknowledges it;
* «You'll earn N points»: whole dollars of the total, ×2 for Plus (Maya: $512 → 1,024); a guest is shown no points;
* a guest sees the cart; «Checkout» asks to sign in.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D5–D7, and are what
`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/Totals.kt` and
`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/CartCommands.kt` hold since B-11.
A customer's points are tested below the routes (`CustomerCartTest`), because nothing names a
customer over HTTP before sign-in (B-12); Plus's free delivery is in `Totals.kt` and no test holds it
yet.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` (the command bodies) and `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt` (the components) — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` (planned, B-13) |

## 5. Scenarios (BDD / test cases)

The three scenarios run against the server and PostgreSQL in
`server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartRoutesTest.kt`; the client half is B-13's.

### Scenario: Totals as drawn
* **Given:** Maya's cart (headphones $349 was $449, duvet cover $139 was $179, mugs $24)
* **When:** the client loads the cart
* **Then:** Items is $652.00, Discount −$140.00, Delivery Free, Total $512.00.
* **Automated:** `CartRoutesTest.totals as drawn` — over a guest cart holding Maya's three lines (Maya herself is a customer, B-12)

### Scenario: Promo applies once
* **Given:** `AUTUMN10` applied
* **When:** the client applies another code
* **Then:** the server returns `409` with `promo_already_applied`.
* **Automated:** `CartRoutesTest.a promo applies once` — with the summary at −$190.00 (the promo's $50 inside the discount) and $462.00

### Scenario: Expired promo
* **When:** the client applies `SUMMER5`
* **Then:** the server returns `422` with `promo_expired`.
* **Automated:** `CartRoutesTest.an expired promo is 422 promo_expired and the field says so`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
