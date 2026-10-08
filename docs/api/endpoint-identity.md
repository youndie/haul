---
id: endpoint-identity
title: Guests, cart merge, addresses
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared GuestRoutes, AddressRoutes
parent_feature: feature-identity
---

# API: Guests, cart merge, addresses

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/guests` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers `GuestDto` (id) |
| `POST` `/api/v1/me/cart/merge` | haul-server | customer (shildik bearer) | yes | request: header `X-Haul-Guest`; answers action: refresh |
| `POST` `/api/v1/me/addresses` | haul-server | customer (shildik bearer) | yes | request: the address form; answers action: refresh Checkout |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `POST` `/api/v1/guests` | `server/src/main/kotlin/io/github/youndie/haul/server/identity/` |
| `POST` `/api/v1/me/cart/merge` | `server/src/main/kotlin/io/github/youndie/haul/server/identity/` |
| `POST` `/api/v1/me/addresses` | `server/src/main/kotlin/io/github/youndie/haul/server/identity/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/identity/` — ``GuestRoutes, AddressRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/identity/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/guests` | `429` rate_limited |
| `POST` `/api/v1/me/cart/merge` | `401`, `404` guest_not_found |
| `POST` `/api/v1/me/addresses` | `400` validation_failed, `401` |
