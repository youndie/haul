---
id: feature-checkout
title: Checkout
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-checkout
api:
  - endpoint-checkout
  - endpoint-identity
tags: []
---

# Checkout

## 1. Overview

One page: how to receive, the address or the point, the delivery window, the payment method, the summary, «Place order».

> The server half is built: the quote, windows with capacity, points and lockers, ways to pay and the
> address form (B-14), placement through the order saga (B-16). Still *target*, which keeps this
> document a draft: the client's renderers and its key per quote (B-15), points (B-23), Haul Pay's
> schedule (B-24), capture at ship time (B-17).

## 2. Business rules

* the quote is the cart's selected, counted lines priced by the cart's own rules (`CartCommands.priced`, `Totals.of`): checkout total and cart total are the same number; nothing selected → `409 cart_empty`;
* courier needs an address and a slot; pickup point and locker need a point, no slot — until then «Place order» is disabled (`CheckoutSummary.placeEnabled`);
* slots: the next 5 days from tomorrow, 4 windows a day (09, 12, 15, 18; three hours), 20 orders each; a slot at capacity is shown and not selectable (`409 slot_unavailable`); the default is the first with room;
* a window's place is taken at placement, not held by the quote (research D5, «Decided in B-14»): a chosen window that filled since is cleared, the page says so, and placing it is refused (`409 slot_unavailable`);
* pay on delivery is not offered for parcel lockers (`422 payment_method_not_allowed`); a method that no longer allows the chosen way to pay falls back to the card;
* Haul Pay is offered for totals between $50 and $2,000 (`HaulPay` in `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/HaulPay.kt`): 4 equal payments; *target* (B-24): two weeks apart, the first when the first shipment ships;
* every customer is offered the simulator's two cards, ···· 4821 (approved) and the test card ···· 0002 (declined); there is no card form;
* a promo code that expired after it was applied is not in the quote, and a notice says so;
* *target* (B-23): «Use N points» redeems the whole balance, capped at the items total after discounts; 100 points = $1; redeemed points come back if the order is cancelled, and come back pro rata as points on a return;
* placing is idempotent by an `Idempotency-Key` the client generates **once per quote it places** — the same key with another quote is `409 idempotency_key_reused` — and a placement refused before the saga (`cart_changed`, `slot_unavailable`) does not spend the key;
* placement places only the quote the shopper saw: a fingerprint that is no longer the checkout's is `409 cart_changed`;
* placement answers `202` with kompot's `navigate` to `/orders/{id}`, the order `placed` — or `cancelled` with `payment_declined` when the card was declined; the rest happens in the saga (feature-orders);
* «Your card is charged when the order ships» — authorisation at placement (built); capture per shipment at ship time is *target* (B-17).

Numbers in these rules are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D5–D7, and checked against the code
(`Slot`, `PaymentMethod`, `HaulPay`).

## 3. Flow

1. `GET /ui/checkout` (customer tier) → the tree, with `CheckoutSummary.quote` (the fingerprint).
2. Each choice: `PUT /api/v1/me/checkout` with `CheckoutChoice`, or `POST /api/v1/me/addresses` with
   `AddressEntry` → `refresh`, and the page is drawn again with a new quote.
3. «Place order»: `POST /api/v1/orders` with `PlaceOrderRequest(quote)` and an `Idempotency-Key`
   (customer tier, end-user credential) → the saga runs in the request → `202` and `navigate`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` — the command bodies and the key's header; `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt` — the eight components |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/` — the quote, windows, points, ways to pay, the address form, the tree; placement in `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt` |
| haul-web | *planned* (B-15): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` |

## 5. Scenarios (BDD / test cases)

### Scenario: Quote as drawn
* **Given:** Maya's cart, courier to 148 Wythe Avenue 4F, Wed 8 15:00–18:00, card ···· 4821
* **When:** she opens the checkout
* **Then:** the total is $512 («Place order · $512.00»), she earns 1,024 points, the windows run Wed 8 to Sun 12, and the quote can be placed.
* **Automated:** `CheckoutRoutesTest.Maya's quote is the cart's totals by courier to her address` (`server/src/test/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRoutesTest.kt`, against shildik and PostgreSQL)

### Scenario: Only the selected lines are quoted
* **Given:** Maya's cart with the duvet cover set unticked
* **When:** she opens the checkout
* **Then:** the quote holds the other two lines with the cart's own totals; with nothing selected the server returns `409` with `cart_empty`.
* **Automated:** `CheckoutRoutesTest.checkout quotes only the lines selected in the cart`

### Scenario: Checkout needs a sign-in
* **Given:** a guest id and no bearer token
* **When:** the client opens `/ui/checkout` or sends a choice
* **Then:** the server returns `401` with `unauthenticated`.
* **Automated:** `CheckoutRoutesTest.checkout needs a sign-in`

### Scenario: A full window is refused
* **Given:** Wed 8 15:00–18:00 at capacity
* **When:** Maya chooses it
* **Then:** it is drawn unavailable, the default skips it, and the server returns `409` with `slot_unavailable`; a window not offered is `404` with `slot_not_found`.
* **Automated:** `CheckoutRoutesTest.a full window is drawn unavailable and refused`

### Scenario: Pickup needs no address and no window
* **When:** Maya chooses «Pickup point»
* **Then:** the two nearby points are listed with distance and hours, the nearest chosen, and the quote can be placed without an address or a window.
* **Automated:** `CheckoutRoutesTest.a pickup point needs no address and no window`

### Scenario: A parcel locker is not paid on delivery
* **When:** Maya chooses a parcel locker after choosing pay on delivery
* **Then:** pay on delivery is not offered and she is back on the card; choosing it is `422` with `payment_method_not_allowed`.
* **Automated:** `CheckoutRoutesTest.a parcel locker is not paid on delivery`

### Scenario: Haul Pay outside its range
* **Given:** a $24 order
* **Then:** Haul Pay is not offered, and choosing it is `422` with `payment_method_not_allowed`.
* **Automated:** `CheckoutRoutesTest.Haul Pay is refused for a total under fifty dollars`

### Scenario: The address form is refused field by field
* **When:** a customer with no address saves the form with the street and the ZIP empty
* **Then:** the server returns `400` with `validation_failed` and a `FieldError` per field, and the page draws the form again with what was typed and an error under each field.
* **Automated:** `CheckoutRoutesTest.the address form is refused field by field and drawn again`

### Scenario: An expired code is not honoured
* **Given:** a promo code applied while valid that has expired since
* **Then:** the quote's total does not count it and a notice says so.
* **Automated:** `CheckoutQuoteTest.a promo code that expired after it was applied is not in the quote`

### Scenario: Place by courier
* **Given:** Maya's cart, courier to 148 Wythe Avenue, Wed Oct 8 15:00–18:00, card ···· 4821
* **When:** she places the order
* **Then:** the server returns `202` with a navigate to `/orders/HL-48302`, the order is `placed` at $512.00 with two shipments (Sony Official Store, Brooklyn Home Co.), one unit of each line is off its stock, the window has one place taken, the total is authorised and the bought lines left the cart.
* **Automated:** `PlacementRoutesTest.Maya places her cart by courier and the saga takes stock window and payment` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`)

### Scenario: Same key twice
* **When:** the client repeats the placement with the same `Idempotency-Key`
* **Then:** the server returns the same order and no second order exists; the same key with another quote is `409` with `idempotency_key_reused`, and no key is `400` with `idempotency_key_missing`.
* **Automated:** `PlacementRoutesTest.the same key places once and a different request under it is refused`

### Scenario: Points redeemed
* **Given:** Maya with 2,480 points and the same cart
* **When:** she places it with «Use 2,480 points»
* **Then:** the total is $487.20 and her balance is 0 until the order earns.
* Not automated: points are B-23's; no balance is stored.

### Scenario: Slot filled meanwhile
* **Given:** the chosen slot reached capacity after the page loaded
* **When:** she places the order
* **Then:** the server returns `409` with `slot_unavailable`, nothing is placed, and the checkout she returns to has the window cleared and says why.
* **Automated:** `PlacementRoutesTest.a window that filled since the page was drawn is refused and the checkout says so`; the window filling inside the saga is `OrderSagaTest.a window that fills inside the saga gives the stock back`, the quote's half `CheckoutRoutesTest.a window that filled after it was chosen is cleared and the shopper told`

### Scenario: A stale quote
* **Given:** the page was drawn, then a line's quantity changed
* **When:** she places the old quote
* **Then:** the server returns `409` with `cart_changed` and places nothing; the same key with the new quote places it.
* **Automated:** `PlacementRoutesTest.a quote that changed since the page was drawn is refused`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
* Editing or deleting a saved address, and a card form: no artboard draws them.

## 7. Quirks

* The windows start tomorrow whatever the items' dispatch days: the canvas offers Maya Wed 8 for a
  cart whose duvet and mugs arrive Thu 9 (B-14's findings; B-17 or the owner reconciles).
* The checkout's delivery fee is the cart's (`Totals.deliveryCents`), drawn as every method's detail
  («Free» for a Plus member or at the free-delivery threshold, `Totals.FREE_DELIVERY_FROM_CENTS`); no method has a fee of its own.
