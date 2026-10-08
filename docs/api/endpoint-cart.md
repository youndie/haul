---
id: endpoint-cart
title: Cart
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared CartRoutes
parent_feature: feature-cart
---

# API: Cart

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

Public tier means «a guest id or
a customer token»; neither is `401`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/cart` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers tree: Cart (Content, Empty, ItemChanged, Guest, promo states) |
| `PUT` `/api/v1/cart/lines/{skuId}` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `quantity`, `selected`; answers action: refresh (and the header count) |
| `DELETE` `/api/v1/cart/lines` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `skuIds`; answers action: refresh |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers action: refresh |
| `PUT` `/api/v1/cart/promo` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `code`; answers action: refresh |
| `DELETE` `/api/v1/cart/promo` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers action: refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/cart` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| `PUT` `/api/v1/cart/lines/{skuId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| `DELETE` `/api/v1/cart/lines` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| `PUT` `/api/v1/cart/promo` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| `DELETE` `/api/v1/cart/promo` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` — ``CartRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/cart` | `401` |
| `PUT` `/api/v1/cart/lines/{skuId}` | `400` validation_failed, `401`, `404` sku_not_found, `409` out_of_stock |
| `DELETE` `/api/v1/cart/lines` | `401` |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | `401`, `404` |
| `PUT` `/api/v1/cart/promo` | `401`, `404` promo_not_found, `409` promo_already_applied, `422` promo_expired / promo_not_applicable |
| `DELETE` `/api/v1/cart/promo` | `401` |
