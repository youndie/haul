---
id: endpoint-recommendations
title: Picked for you
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared CatalogRoutes
parent_feature: feature-recommendations
---

# API: Picked for you

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

Not a route of its own: the
block is part of the `/ui/home` tree for a customer; its scenarios run against the use case.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` (the `PlusBlock` and «Picked for you» part) | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers tree fragment, absent for a guest |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/home` (the `PlusBlock` and «Picked for you» part) | `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/recommendations/` — ``CatalogRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/recommendations/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` (the `PlusBlock` and «Picked for you» part) | `503` unavailable |
