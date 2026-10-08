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

> Placement's saga and the payment simulator (B-16), the fulfilment simulator, capture per shipment
> and pickup codes (B-17) are described as built. The order page and reorder (B-18), the history
> (B-19) and returns (B-21) are still *target*, which keeps this document a draft.

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
* the fulfilment simulator (`server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/FulfilmentSimulator.kt`) moves each shipment of a `placed` order through every step that has come due — `placed → packed → in_transit → delivered` for a courier, `placed → packed → in_transit → ready_for_pickup → picked_up` for a point or a locker — and stamps each step at the moment it came due (`shipment_events`), so a pass after a pause writes the history an unbroken run would; every move is conditional on the status it leaves, so two passes at once move a shipment once;
* the store's pace (`FulfilmentPace.STORE` in `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/Fulfilment.kt`, research D4): packed 4 h after the seller sees the order, on the road 20 h later plus a day per dispatch day (the most of the shipment's products'), delivered or ready for pickup a day later, collected two days after that; `HAUL_FULFILMENT_SPEED` runs it that many times quicker (haul-server, section 7); the simulator's clock is the saga's `PetichClock`;
* **capture per shipment** — «your card is charged when the order ships»: the shipment's share is captured from the order's authorisation **before** it moves to `in_transit`, keyed by the shipment id, so a pass that dies between the capture and the move charges once; the share is the total in proportion to each seller's lines at the price paid, the last share taking the rounding, so the parts add up to the cent (`ShipmentShares`); captures never add up to more than the authorisation, nothing is captured without one, and an authorisation part of which was captured is no longer voided (`PaymentProcessor.capture` in `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`); a refused capture holds that shipment `packed` while the others move; pay on delivery captures nothing; Haul Pay is captured like a card (its four payments are B-24's);
* a pickup shipment gets a 4-digit pickup code when it becomes `ready_for_pickup` — an HMAC-SHA256 of the shipment id keyed by the order's saga id, so it cannot be derived from what the page shows — and is held 5 days from then; the code and the day it is held until are shown only while it waits, and only to the order's customer;
* the order's progress (`OrderProgress` in `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/OrderTracking.kt`) is the saga's while `placing` or `cancelled`, else the least advanced shipment's status; `OrderTracking.track` answers only the order's own customer (anyone else: no order, the route's `404`) — B-18 draws the page from it;
* *target* (B-21): a return can be requested for delivered lines within 30 days of delivery; the simulator picks it up and refunds; points earned on those lines are reversed;
* *target* (B-18): «Reorder» puts the order's still-available `Sku`s into the cart and reports the ones that are not.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; the saga's are checked against
the code, the rest are verified before this document goes `active`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | planned: `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` — the Order tree's contract; placement's request is in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` — placement, the saga, the order's storage; `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` — the payment simulator and capture; `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/` — the fulfilment simulator, its runner, pickup codes, `OrderTracking`; `server/src/main/resources/db/migration/V11__fulfilment.sql` |
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
* **Given:** an order with two shipments
* **When:** the first reaches `in_transit`
* **Then:** exactly that shipment's amount is captured and the second is still only authorised.
* **And:** nothing is captured at placement or packing; Sony's $349.00 is captured when it ships, then Brooklyn Home Co.'s $163.00, the two summing to the $512.00 authorised.
* **Automated:** `ChargedWhenShippedTest.charged when shipped` (`server/src/test/kotlin/io/github/youndie/haul/feature/fulfilment/ChargedWhenShippedTest.kt`); a death between capture and move, a refused capture, two passes at once and pay on delivery in the same file

### Scenario: An order reaches delivered on the fast clock
* **Given:** Maya's order by courier and the simulator at a day a minute (speed 1440)
* **When:** the clock moves on
* **Then:** the order goes placed → packed → in transit → delivered, the store's three days in at most four minutes.
* **Automated:** `ShipmentLifecycleTest.an order reaches delivered on the fast clock` (`server/src/test/kotlin/io/github/youndie/haul/feature/fulfilment/ShipmentLifecycleTest.kt`); the application doing it by itself, `FulfilmentRunnerTest.the application delivers an order by itself when fulfilment runs`

### Scenario: A pickup code only its customer sees
* **Given:** Maya's order to a pickup point
* **When:** its shipment is ready for pickup
* **Then:** her order shows that shipment's 4-digit code and the day it is held until (5 days on), while the order's progress is still its other shipment's «in transit»; Sam is answered no order; once collected the code is no longer shown.
* **Automated:** `ShipmentLifecycleTest.a pickup shipment waits with a code only its customer sees`; a locker, `ShipmentLifecycleTest.a locker shipment is collected not delivered`

### Scenario: Late return
*Target* (B-21); `ErrorCode` has no `return_window_closed` yet.
* **Given:** a shipment delivered 31 days ago
* **When:** the customer requests a return
* **Then:** the server returns `422` with `return_window_closed`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.

## 7. Quirks

* The simulator's pace does not aim at the courier window the shopper chose or the day checkout
  promised: at speed 1 an order can arrive before or after its window. The order page reads the window
  from the order, not from the simulator (B-17's findings).
* A shipment whose capture was refused and later taken is stamped `in_transit` at its due time, not
  when it actually shipped; only the log says it was held.
* The chart passes no `HAUL_FULFILMENT_SPEED`, so a release runs at the store's pace (B-27).
* Research §6's sample orders (#HL-48211 in transit, #HL-47960 ready with code 4821, the delivered
  ones) are not seeded: they are B-18's fixtures.
* Two shipments of one order captured at the same instant by two processes are serialised by the
  authorisation's row lock, which no test exercises.
