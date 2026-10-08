---
id: feature-orders
title: Order lifecycle, tracking and returns
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-order
  - screen-account
api:
  - endpoint-orders
tags: []
---

# Order lifecycle, tracking and returns

## 1. Overview

After placement a saga reserves stock and authorises payment; a fulfilment simulator then moves each seller's shipment along. The order page shows it; a delivered order can be returned and reordered.

> Placement's saga and the payment simulator exist since B-16 and are described as built. The
> fulfilment simulator and capture (B-17), the order page and reorder (B-18), the history (B-19) and
> returns (B-21) are still *target*, which keeps this document a draft.

## 2. Business rules

* the saga (petich, `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt`) runs inside the placement request, its members in this order:
  1. `reserve-stock` — every line's units off its `Sku`, all or nothing, held by the order; a shortage refuses with `out_of_stock`;
  2. `reserve-slot` — a place in the courier's window (`DeliverySlots.reserve`); a full window refuses with `slot_unavailable`; a pickup takes nothing;
  3. `open-order` — the order `placing`, its lines, one shipment per seller;
  4. `authorise-payment` — the total, by card or Haul Pay; nothing for pay on delivery; a decline cancels the order with `payment_declined` and refuses;
  5. `confirm` — `placed`;
  6. the announcement `clear-cart` — the bought lines leave the cart (a failure there does not take the order back);
* a refusal or a failure compensates in reverse — void the authorisation, cancel the order and its shipments, release the window, release the stock — each by the order's name and safe when its step never ran; an order undone by a failure ends `cancelled` with `failed`;
* every member is idempotent: a saga left `PROCESSING` or `COMPENSATING` by a process that died is carried on by the sweeper the application starts (stuck after 60 s, `STUCK_AFTER` in `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/SagaEngine.kt`), and nothing is taken twice; the saga's clock is a `PetichClock` apart from the store's «now»;
* an order's own status is the saga's: `placing`, `placed`, `cancelled` with `cancel_reason`;
* the simulated card processor (`server/src/main/kotlin/io/github/youndie/haul/feature/payment/`) declines the test card ending `0002` and approves the others (§5a);
* *target* (B-17): the fulfilment simulator advances each shipment `placed → packed → in_transit → delivered` (courier) or `… → ready_for_pickup → picked_up` (point, locker) on a configurable clock; `in_transit` triggers the capture for that shipment;
* *target* (B-17): a pickup shipment keeps a 4-digit pickup code and is held 5 days from `ready_for_pickup`;
* *target* (B-17): from `placed` on, the status the shopper sees is derived from the shipments (the least advanced one wins);
* *target* (B-21): a return can be requested for delivered lines within 30 days of delivery; the simulator picks it up and refunds; points earned on those lines are reversed;
* *target* (B-18): «Reorder» puts the order's still-available `Sku`s into the cart and reports the ones that are not.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; the saga's are checked against
the code, the rest are verified before this document goes `active`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | planned: `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` — the Order tree's contract; placement's request is in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` — placement, the saga, the order's storage; `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` — the payment simulator |
| haul-web | planned: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` |
| haul-web | planned: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` |

## 5. Scenarios (BDD / test cases)

### Scenario: Declined card cancels and releases
* **Given:** a `Sku` with stock 3 and an order of 1 paid with the card ending 0002
* **When:** the saga runs
* **Then:** the order is `cancelled` with `payment_declined` and the stock is 3 again.
* **And:** its shipments are cancelled, the window's place is given back, the authorisation is recorded declined, the cart is as it was, and placement still answers `202` to the order.
* **Automated:** `PlacementRoutesTest.a declined card cancels the order and gives everything back` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`)

### Scenario: A saga killed mid-way finishes after a restart
* **Given:** a placement whose process dies inside the card processor's call, leaving the order `placing` and the saga `PROCESSING`
* **When:** a new application starts on the same database and its sweeper finds the saga stuck
* **Then:** the order is placed, nothing is taken twice, and the cart is cleared.
* **Automated:** `PlacementRestartTest.a placement killed inside the payment is finished by the next process`

### Scenario: A failure after the payment is undone in reverse
* **Given:** a member after `authorise-payment` fails
* **Then:** the authorisation is voided, the order cancelled, the window and the stock released.
* **Automated:** `OrderSagaTest.a failure after the payment undoes every member in reverse` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/OrderSagaTest.kt`)

### Scenario: Charged when shipped
*Target* (B-17).
* **Given:** an order with two shipments
* **When:** the first reaches `in_transit`
* **Then:** exactly that shipment's amount is captured and the second is still only authorised.

### Scenario: Late return
*Target* (B-21); `ErrorCode` has no `return_window_closed` yet.
* **Given:** a shipment delivered 31 days ago
* **When:** the customer requests a return
* **Then:** the server returns `422` with `return_window_closed`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
