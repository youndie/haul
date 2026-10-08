---
id: feature-account
title: Account overview and the Saved list
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-account
  - screen-saved
api:
  - endpoint-account
  - endpoint-saved
  - endpoint-orders
tags: []
---

# Account overview and the Saved list

## 1. Overview

One page answers «what is coming, what did I save, what do I have»: points, Plus savings, price drops, active orders, history. The Saved list holds hearted products and lines saved for later, and marks the ones that got cheaper.

## 2. Business rules

* the overview shows up to 3 active orders (not delivered, not cancelled) and the last 4 of the history;
* «Price drops N» counts saved products whose cheapest in-stock price is below the price at saving time;
* saving is idempotent; the heart on a card toggles it;
* the Saved list filters «All» / «Price dropped», 24 per page, newest first.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/account/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Price drop counted
* **Given:** Maya saved the Robot Vacuum S8 at $499 and it now costs $299
* **When:** she opens the account
* **Then:** «Price drops» counts it and the Saved list marks it.

### Scenario: Saved twice
* **When:** the client saves the same product twice
* **Then:** the second call returns `200` and the list holds it once.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
