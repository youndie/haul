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

## 2. Business rules

* the saga (petich): reserve stock → authorise payment (card / Haul Pay; none for pay on delivery) → confirm; a failed step compensates the previous ones (release stock, void authorisation) and the order ends `cancelled` with a reason;
* the simulated card processor declines the test card ending `0002` and approves the others (§5a);
* the fulfilment simulator advances each shipment `placed → packed → in_transit → delivered` (courier) or `… → ready_for_pickup → picked_up` (point, locker) on a configurable clock; `in_transit` triggers the capture for that shipment;
* a pickup shipment keeps a 4-digit pickup code and is held 5 days from `ready_for_pickup`;
* the order's status is derived from its shipments (the least advanced one wins);
* a return can be requested for delivered lines within 30 days of delivery; the simulator picks it up and refunds; points earned on those lines are reversed;
* «Reorder» puts the order's still-available `Sku`s into the cart and reports the ones that are not.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/order/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/server/order/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/order/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/account/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Declined card cancels and releases
* **Given:** a `Sku` with stock 3 and an order of 1 paid with the card ending 0002
* **When:** the saga runs
* **Then:** the order is `cancelled` with `payment_declined` and the stock is 3 again.

### Scenario: Charged when shipped
* **Given:** an order with two shipments
* **When:** the first reaches `in_transit`
* **Then:** exactly that shipment's amount is captured and the second is still only authorised.

### Scenario: Late return
* **Given:** a shipment delivered 31 days ago
* **When:** the customer requests a return
* **Then:** the server returns `422` with `return_window_closed`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
