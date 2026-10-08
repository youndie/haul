---
id: feature-cart
title: Cart
type: feature
status: active
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
* «Select all» / per-line selection; «Delete selected» removes the selected lines; checkout takes the selected lines only (B-14: `CheckoutRoutesTest.checkout quotes only the lines selected in the cart`); with nothing selected «Checkout» is disabled;
* «Save for later» moves the line's product to the customer's Saved list and removes the line (`POST /api/v1/cart/lines/{skuId}/save-for-later`, B-20); the save comes first, so pressing again finishes an interrupted move; a guest's leads to `/sign-in` ([feature-account](feature-account.md));
* totals, over the selected lines that count (ticked, in stock, unchanged): Items = Σ old price (or price when no old) × qty, labelled «Items (N)» with N the counted units; Discount = Σ (old − price) × qty + promo — the promo is part of the discount, and also shown as a row of its own under it («Promo · AUTUMN10»); Delivery per research D7; Total = Items − Discount + Delivery, written as a price tag («$512»); the same rows at checkout;
* delivery is free from $35 of the selected items at their price, before the promo, or for Plus; otherwise $5.99, one fee per cart rather than per seller group (research D5, «Decided in B-11»);
* one promo code per cart; an invalid, expired or inapplicable code is refused with its reason, and the refused code and its reason stay in the promo field until the next command; the code already applied, sent again, is not a refusal;
* a line whose `Sku` went out of stock or changed price since it was added is marked and excluded from selection until the shopper acknowledges it;
* «You'll earn N points on this order»: whole dollars of the total, ×2 for Plus (Maya: $512 → 1,024); a guest is shown no points;
* a guest sees the cart; «Sign in to check out» (`/sign-in?next=%2Fcheckout`) runs sign-in (feature-identity) and lands on `/checkout`; a sign-in that does not go through draws the cart again (B-41); a customer's «Checkout» goes to `/checkout`;
* each line groups under its seller with «Courier · <the group's latest delivery day>»; an empty cart offers today's deals («Picked for you» / «From today’s deals»), each with its «+» and its heart;
* the same line command is sent from outside the cart: a product card's «+», the product page's «Add to cart» and «Buy now» ([feature-product](feature-product.md), B-48), and an order's «Reorder» ([feature-orders](feature-orders.md), B-18) — every rule above holds for them.
* every command answers `refresh` and the client fetches the cart again in place, a refusal included (the server keeps a refused code and its reason for the next tree); no answer leaves the page as it was.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D5–D7, and are what
`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/Totals.kt` and
`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/CartCommands.kt` hold since B-11.
A customer's points are tested below the routes (`CustomerCartTest`; sign-in over HTTP is
`IdentityRoutesTest`); Plus's free delivery by `MembershipRoutesTest.Sam starts the trial and his next
cart has free delivery` (`server/src/test/kotlin/io/github/youndie/haul/feature/membership/MembershipRoutesTest.kt`).

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` (the command bodies) and `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt` (the components) — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` — `CartViews.kt` (the body at both widths), `CartCommands.kt` (each press a `CartCommand` sent through `Identity.send`) |

## 5. Scenarios (BDD / test cases)

The three scenarios run against the server and PostgreSQL in
`server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartRoutesTest.kt`. The client half
(B-13): `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/CartWiringTest.kt`
(one press per action → the command and the redraw; presses the tree does not allow send nothing; in
the storefront a command's answer fetches `/ui/cart` again),
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/CartCommandsTest.kt`, and
`server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartFixturesTest.kt` (the client's six
cart bodies are the server's trees).

### Scenario: Totals as drawn
* **Given:** Maya's cart (headphones $349 was $449, duvet cover $139 was $179, mugs $24)
* **When:** the client loads the cart
* **Then:** Items is $652.00, Discount −$140.00, Delivery Free, Total $512.00.
* **Automated:** `CartRoutesTest.totals as drawn` — over a guest cart holding Maya's three lines; Maya's own seeded cart in `CustomerCartTest.Maya's seeded cart is the one the canvas draws`

### Scenario: Promo applies once
* **Given:** `AUTUMN10` applied
* **When:** the client applies another code
* **Then:** the server returns `409` with `promo_already_applied`.
* **Automated:** `CartRoutesTest.a promo applies once` — with the summary at −$190.00 (the promo's $50 inside the discount) and $462.00

### Scenario: Save for later
* **Given:** a line in Maya's cart
* **When:** she presses «Save for later»
* **Then:** the line leaves the cart and the product is in her Saved list; a guest's press goes to sign-in.
* **Automated:** `SavedRoutesTest.save for later moves a line from the cart to the list` (`server/src/test/kotlin/io/github/youndie/haul/feature/saved/SavedRoutesTest.kt`); the client, `SavedWiringTest` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/saved/SavedWiringTest.kt`)

### Scenario: Expired promo
* **When:** the client applies `SUMMER5`
* **Then:** the server returns `422` with `promo_expired`.
* **Automated:** `CartRoutesTest.an expired promo is 422 promo_expired and the field says so`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.

## 7. Quirks

* **The discount against the canvas, unresolved.** Cart_PromoApplied draws «Discount −$140.00» and
  the promo as its own row beside it; the rule above (B-11, tested) folds the promo into the
  discount, so the server sends «−$190.00». B-13 kept the server's rule; the owner decides.
* The canvas names the headphones «Sony WH-1000XM6 …»; the seed's title has no brand, and the server
  sends the title (B-13's findings).
* The empty cart's picks are the day's deals, so the server says «From today’s deals» where the
  canvas says «Based on your recent views».
* «Select all» on a cart with a changed line: the server's `allSelected` speaks for the selectable
  lines, the client draws the partial box whenever a line is unticked.
* A disabled checkout and a stepper at its limit are drawn as the canvas draws them and send nothing.
