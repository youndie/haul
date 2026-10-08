---
id: feature-membership
title: Haul Plus, points and Haul Pay
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-home
  - screen-cart
  - screen-checkout
  - screen-account
api:
  - endpoint-membership
tags: []
---

# Haul Plus, points and Haul Pay

## 1. Overview

The loyalty layer: a subscription that removes delivery fees and doubles points, points that turn into money off, and paying in instalments. All money here is simulated.

No screen of its own: screen-home (Plus block, trial dialog), screen-cart and screen-checkout (points, Haul Pay), screen-account (tiles).

## 2. Business rules

* the trial starts on request, lasts 30 days, then «renews» at $4.99/month with no real charge; the account shows the renewal date;
* Plus: free delivery on every order, courier next day regardless of the order total, points ×2, campaign prices from the early-access start;
* points: 1 point per whole dollar of an order's total, ×2 for Plus, credited when a shipment is delivered, proportional per shipment; 100 points = $1 (2,480 → $24.80);
* Delivery savings this year = Σ the fee a non-member would have paid on the member's orders since January 1;
* Haul Pay: 4 equal payments two weeks apart, simulated; a declined instalment is retried once and then marks the plan overdue (no collections in v1).

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/membership/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/membership/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Trial
* **Given:** Sam (no membership)
* **When:** he starts the trial on 2025-10-07
* **Then:** his membership is `trial` until 2025-11-06 and his next cart shows free delivery.

### Scenario: Already a member
* **Given:** Maya (active)
* **When:** she starts the trial
* **Then:** the server returns `409` with `already_member`.

### Scenario: Points on delivery
* **Given:** Sam's $103.00 order without Plus
* **When:** its only shipment is delivered
* **Then:** his balance grows by 103.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
