---
id: endpoint-membership
title: Haul Plus
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared MembershipRoutes
parent_feature: feature-membership
---

# API: Haul Plus

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/me/plus/trial` | haul-server | customer (shildik bearer) | yes | request: —; answers action: close the route, refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `POST` `/api/v1/me/plus/trial` | `server/src/main/kotlin/io/github/youndie/haul/server/membership/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/membership/` — ``MembershipRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/membership/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/me/plus/trial` | `401`, `409` already_member / trial_used |
