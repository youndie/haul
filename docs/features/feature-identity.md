---
id: feature-identity
title: Sign-in, guests and the guest cart
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries: []
api:
  - endpoint-identity
tags: []
---

# Sign-in, guests and the guest cart

## 1. Overview

A shopper browses and fills a cart without an account; checkout, saving, reviews and the account need a sign-in through shildik (OIDC). Signing in keeps what the guest put in the cart.

No screen of its own.

## 2. Business rules

* the client gets a guest id from the server on first start and sends it with every request until sign-in;
* a request with both a valid bearer and a guest id acts as the customer; the guest id is used only by the merge;
* on sign-in the guest cart is merged into the customer's: same `Sku` → quantities summed and capped at 10 and at stock; the guest cart is then deleted;
* the first authenticated request of an unknown `sub` creates the `Customer` from the token's name claim.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code (planned) |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/identity/` — the contract |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/server/identity/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/identity/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Guest cart survives sign-in
* **Given:** a guest with 1 × Stoneware Mug in the cart and a customer whose cart holds 1 × the same mug
* **When:** the guest signs in
* **Then:** the customer's cart holds 2 × the mug and the guest cart no longer exists.

### Scenario: Account needs a sign-in
* **Given:** no bearer token
* **When:** the client opens `/ui/account`
* **Then:** the server returns `401` with `unauthenticated`.

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
