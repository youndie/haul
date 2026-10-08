---
id: feature-recommendations
title: Picked for you
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-home
api:
  - endpoint-recommendations
tags: []
---

# Picked for you

## 1. Overview

Six products the customer is likely to want, from what they viewed recently.

## 2. Business rules

* from the customer's last 20 product views take their categories by frequency; return the top-rated in-stock products of those categories that were neither viewed nor bought, at most 2 per category, 6 in total;
* fewer than 3 views → the block shows popular products and its subtitle says «Popular right now»;
* a guest gets no block.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/recommendations/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/server/recommendations/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/home/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: From views
* **Given:** Maya viewed three headphones
* **When:** she opens the home page
* **Then:** «Picked for you» holds at most two headphones and none of the three she viewed.

### Scenario: Guest
* **Given:** no bearer
* **When:** the client opens `/ui/home`
* **Then:** the tree holds no «Picked for you» block and no request for recommendations was made.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
