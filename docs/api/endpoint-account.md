---
id: endpoint-account
title: Account overview
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared AccountRoutes
parent_feature: feature-account
---

# API: Account overview

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: Account (Content, NotMember, NoOrders) |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/account` | `server/src/main/kotlin/io/github/youndie/haul/feature/account/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` — ``AccountRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account` | `401` unauthenticated |
