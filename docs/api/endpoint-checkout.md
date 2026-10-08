---
id: endpoint-checkout
title: Checkout and placement
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared CheckoutRoutes
parent_feature: feature-checkout
---

# API: Checkout and placement

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/checkout` | haul-server | customer (shildik bearer) | yes | request: the current selection; answers tree: Checkout (methods, points, slots with capacity, payment methods, quote) |
| `POST` `/api/v1/orders` | haul-server | customer (shildik bearer) | yes | request: `PlaceOrderRequest`, header `Idempotency-Key`; answers `202`, action: navigate to `/ui/orders/{id}` |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/checkout` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/` |
| `POST` `/api/v1/orders` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` — ``CheckoutRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/checkout` | `401`, `409` cart_empty |
| `POST` `/api/v1/orders` | `400` validation_failed / idempotency_key_missing, `401`, `409` slot_unavailable / out_of_stock / cart_changed / points_balance_changed / idempotency_key_reused, `422` payment_method_not_allowed |
