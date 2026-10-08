---
id: endpoint-reviews
title: Reviews and questions
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared ReviewRoutes
parent_feature: feature-reviews
---

# API: Reviews and questions

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

Reading them is the Product
screen's `tab` parameter.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/products/{id}/reviews` | haul-server | customer (shildik bearer) | yes | request: the review form; answers action: close the route, refresh |
| `PUT` `/api/v1/reviews/{id}/helpful` | haul-server | customer (shildik bearer) | yes | request: —; answers action: refresh |
| `POST` `/api/v1/products/{id}/questions` | haul-server | customer (shildik bearer) | yes | request: the question form; answers action: close the route, refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `server/src/main/kotlin/io/github/youndie/haul/server/reviews/` |
| `PUT` `/api/v1/reviews/{id}/helpful` | `server/src/main/kotlin/io/github/youndie/haul/server/reviews/` |
| `POST` `/api/v1/products/{id}/questions` | `server/src/main/kotlin/io/github/youndie/haul/server/reviews/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/reviews/` — ``ReviewRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/reviews/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `400` validation_failed, `401`, `404` product_not_found, `409` review_exists |
| `PUT` `/api/v1/reviews/{id}/helpful` | `401`, `404`, `409` own_review |
| `POST` `/api/v1/products/{id}/questions` | `400` validation_failed, `401`, `404` |
