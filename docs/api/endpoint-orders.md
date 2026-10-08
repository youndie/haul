---
id: endpoint-orders
title: Orders and returns
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared OrderRoutes
parent_feature: feature-orders
---

# API: Orders and returns

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/orders/{id}` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: Order (per status) |
| `GET` `/ui/account/orders` | haul-server | customer (shildik bearer) | yes | request: status filter, page; answers tree: Account with Orders selected |
| `POST` `/api/v1/me/orders/{id}/reorder` | haul-server | customer (shildik bearer) | yes | request: —; answers action: navigate to the cart, with the unavailable lines named |
| `POST` `/api/v1/me/orders/{id}/returns` | haul-server | customer (shildik bearer) | yes | request: the return form; answers action: close the route, refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/orders/{id}` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `GET` `/ui/account/orders` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `POST` `/api/v1/me/orders/{id}/reorder` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `POST` `/api/v1/me/orders/{id}/returns` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` — ``OrderRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/orders/{id}` | `401`, `404` order_not_found |
| `GET` `/ui/account/orders` | `401` |
| `POST` `/api/v1/me/orders/{id}/reorder` | `401`, `404` |
| `POST` `/api/v1/me/orders/{id}/returns` | `400` validation_failed, `401`, `404`, `422` return_window_closed / not_delivered |
