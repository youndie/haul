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

Everything needed to decide: photos (a stored photo when there is one, placeholder tiles otherwise — B-30), variants, price, delivery options with dates, returns, the seller, description, specifications, reviews and questions — and the buttons that buy it («Add to cart», «Buy now», B-48) or keep it (the heart and «Save», B-20).

> Still not built, which keeps this document a draft: recording a `ProductView` when a customer opens
> the page (B-25, recommendations), and «12K bought this month», which the server does not send
> (`ProductDetails.bought` is never set; no backlog item builds it yet).

## 2. Business rules

* choosing a colour and a bundle selects one `Sku`; price, old price, discount and stock follow it;
* delivery estimate per method for the customer's default address: courier «tomorrow» when ordered before the seller's cut-off (23:30 local) and in stock, otherwise the day after; pickup point and locker one day later than courier; free over $35 or for Plus (§8);
* «Order within 3 h 42 min» counts down to the cut-off;
* a `Sku` with stock 0 cannot be added to the cart; the page says «Out of stock», draws «Add to cart» and «Buy now» greyed with no command, and keeps «Save»;
* **«Add to cart»** (`ProductDetails.add`) is the card's «+» for the SKU the page shows: one more of it, through the cart's `PUT /api/v1/cart/lines/{skuId}`, the line's selection left alone; the page redraws with the header's count; it is absent at the line's limit (ten, or the whole stock);
* **«Buy now»** (`ProductDetails.buy`) is the same line with `selected = true` — checkout takes the selected lines only — then a navigate fixed in the tree (`LineCommand.next`): `/checkout` for a customer, **`/sign-in?next=%2Fcheckout` for a guest** (the cart's «Sign in to check out» address, so both ways into checkout run one sign-in and one cart merge; decided as product owner, B-48); at the line's limit it only selects and goes on; the client follows `next` only when the change was accepted — a refused «Buy now» redraws the page and goes nowhere;
* the heart and «Save» keep the product in the customer's Saved list (`ProductDetails.heartCommand`: `PUT` to keep, `DELETE` to let go) and are drawn filled when it is there; a guest's lead to `/sign-in` ([feature-account](feature-account.md));
* *not built*: «12K bought this month» is the count of delivered and in-transit units in the last 30 days, rounded down to thousands above 1,000;
* *planned* (B-25): opening the page as a customer records a `ProductView`;
* under the price, for prices between $50 and $2,000: «or 4 payments of $87.25 with Haul Pay» (the price ÷ 4, rounded to cents).

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`; the buttons' one press `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/LinePress.kt` |

## 5. Scenarios (BDD / test cases)

### Scenario: Variant changes the price
* **Given:** Sony WH-1000XM6
* **When:** the shopper picks «+ Travel case»
* **Then:** the price shown is that `Sku`'s price and «Add to cart» adds that `Sku`.
* **Automated:** `ProductRoutesTest.a variant changes the price`

### Scenario: Out of stock
* The page half — the SKU drawn «Out of stock», no cut-off offered, «Silver is out of stock.» (`stockAdvice`) where the delivery lines were — is `ProductRoutesTest.an out of stock SKU is drawn out of stock`; the `409` is the cart route's ([endpoint-cart](../api/endpoint-cart.md)).
* **Given:** a `Sku` with stock 0
* **When:** the client adds it to the cart
* **Then:** the server returns `409` with `out_of_stock`.
* **Automated:** `CartRoutesTest.quantities stay within one to ten and the stock` (`server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartRoutesTest.kt`)

### Scenario: Add to cart from the page
* **Given:** the Sony WH-1000XM6 with «+ Travel case» chosen
* **When:** the shopper presses «Add to cart»
* **Then:** the travel-case bundle — the SKU shown, not the cheapest — is one more in the cart, the page is drawn again with the header's count, and «Add to cart» now offers one more; at the line's limit it is gone.
* **Automated:** `ProductButtonsTest.add to cart puts one more of the SKU shown into the cart`, `ProductButtonsTest.at the line's limit add to cart is gone and buy now only selects the line` (`server/src/test/kotlin/io/github/youndie/haul/feature/catalog/ProductButtonsTest.kt`); the client, `BuyBoxWiringTest.add to cart sends its line change and draws the page again` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/BuyBoxWiringTest.kt`); over HTTP, `WholePathTest.a shopper browses buys receives and returns an order over HTTP`

### Scenario: Buy now
* **Given:** a guest, then a customer, on a product page
* **When:** each presses «Buy now»
* **Then:** the SKU is in the cart and selected (an unticked line is ticked again); the guest is sent to `/sign-in?next=%2Fcheckout`, the customer to `/checkout`, which opens on the line.
* **Automated:** `ProductButtonsTest.a guest's buy now adds the SKU shown and goes to checkout through sign-in`, `ProductButtonsTest.a customer's buy now adds the SKU shown and opens checkout`; the client, `BuyBoxWiringTest.a guest's buy now signs in and then opens checkout`, `BuyBoxWiringTest.a refused buy now stays on the page and draws it again`

### Scenario: Out of stock offers nothing to buy
* **Given:** the colour «Silver», out of stock
* **Then:** neither «Add to cart» nor «Buy now» carries a command, and pressing them sends nothing.
* **Automated:** `ProductButtonsTest.out of stock neither add to cart nor buy now is offered`, `BuyBoxWiringTest.out of stock neither button sends anything`

### Scenario: Unknown product
* **When:** the client asks for a product id that does not exist
* **Then:** the server returns `404` with `product_not_found`.
* **Automated:** `ProductRoutesTest.an unknown product is 404 product_not_found`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
