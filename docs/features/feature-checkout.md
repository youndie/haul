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
tags: []
---

# Checkout

## 1. Overview

One page: how to receive, the address or the point, the delivery window, the payment method, the summary, «Place order».

## 2. Business rules

* courier needs an address and a slot; pickup point and locker need a point, no slot;
* slots: the next 5 days from tomorrow, 4 windows a day; a slot at capacity is shown and not selectable; choosing a full slot at placement → refused;
* pay on delivery is not offered for parcel lockers;
* Haul Pay is offered for totals between $50 and $2,000: 4 equal payments two weeks apart, the first when the first shipment ships;
* «Use N points» redeems the whole balance, capped at the items total after discounts; 100 points = $1; redeemed points come back if the order is cancelled, and come back pro rata as points on a return;
* placing is idempotent by an `Idempotency-Key` the client generates once per checkout page;
* placement answers `202` with the order in `placed` and the client goes to the order page; the rest happens in the saga (feature-orders);
* «Your card is charged when the order ships» — authorisation at placement, capture per shipment at ship time.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Place by courier
* **Given:** Maya's cart, courier to 148 Wythe Avenue, Wed Oct 8 15:00–18:00, card ···· 4821
* **When:** she places the order
* **Then:** the server returns `202` with an `HL-` id, status `placed`, total $512.00, and two shipments (Sony Official Store, Brooklyn Home Co.).

### Scenario: Same key twice
* **When:** the client repeats the placement with the same `Idempotency-Key`
* **Then:** the server returns the same order and no second order exists.

### Scenario: Points redeemed
* **Given:** Maya with 2,480 points and the same cart
* **When:** she places it with «Use 2,480 points»
* **Then:** the total is $487.20 and her balance is 0 until the order earns.

### Scenario: Slot filled meanwhile
* **Given:** the chosen slot reached capacity after the page loaded
* **When:** she places the order
* **Then:** the server returns `409` with `slot_unavailable`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
