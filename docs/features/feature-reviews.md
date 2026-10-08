---
id: feature-reviews
title: Reviews and questions
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
  - endpoint-reviews
tags: []
---

# Reviews and questions

## 1. Overview

Reviews with a rating histogram and helpful votes; questions to the seller. Answers exist only in the seed data — there is no seller side to write new ones.

## 2. Business rules

* one review per customer per product; rating 1…5, title 1…120 characters, body 20…5,000;
* «Verified purchase» when the author has a delivered shipment containing the product;
* one helpful vote per customer per review; the author cannot vote on their own;
* the histogram and the average are recomputed when a review is written;
* reviews sort: most helpful (default), newest; 10 per page;
* a question is 10…1,000 characters; a new question shows «Not answered yet».

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: A verified review
* **Given:** Maya with delivered order #HL-46102 containing the product
* **When:** she posts a 5-star review
* **Then:** it is marked verified and the product's review count grows by one.

### Scenario: A second review
* **Given:** Maya already reviewed the product
* **When:** she posts another
* **Then:** the server returns `409` with `review_exists`.

### Scenario: Too short
* **When:** a review body is 5 characters
* **Then:** the server returns `400` with `validation_failed` naming `body`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
