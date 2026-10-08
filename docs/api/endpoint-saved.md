---
id: endpoint-saved
title: Saved list
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared SavedRoutes
parent_feature: feature-account
---

# API: Saved list

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account/saved` | haul-server | customer (shildik bearer) | yes | request: filter, page; answers tree: Saved |
| `PUT` `/api/v1/me/saved/{productId}` | haul-server | customer (shildik bearer) | yes | request: —; answers action: refresh the card |
| `DELETE` `/api/v1/me/saved/{productId}` | haul-server | customer (shildik bearer) | yes | request: —; answers action: refresh the card |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | haul-server | customer (shildik bearer) | yes | request: —; answers action: refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/account/saved` | `server/src/main/kotlin/io/github/youndie/haul/server/saved/` |
| `PUT` `/api/v1/me/saved/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/server/saved/` |
| `DELETE` `/api/v1/me/saved/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/server/saved/` |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | `server/src/main/kotlin/io/github/youndie/haul/server/saved/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/saved/` — ``SavedRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/saved/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account/saved` | `401` |
| `PUT` `/api/v1/me/saved/{productId}` | `401`, `404` product_not_found |
| `DELETE` `/api/v1/me/saved/{productId}` | `401` |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | `401`, `404` |
