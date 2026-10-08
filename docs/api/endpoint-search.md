---
id: endpoint-search
title: Search
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared SearchRoutes
parent_feature: feature-search
---

# API: Search

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/search` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `q`, category, sort, page; answers tree: Search results or NoResults |
| `GET` `/ui/search/suggest` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `q`; answers tree: `SearchSuggestPanel` |
| `DELETE` `/api/v1/me/recent-searches` | haul-server | customer (shildik bearer) | yes | request: —; answers action: refresh the panel |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/search` | `server/src/main/kotlin/io/github/youndie/haul/server/search/` |
| `GET` `/ui/search/suggest` | `server/src/main/kotlin/io/github/youndie/haul/server/search/` |
| `DELETE` `/api/v1/me/recent-searches` | `server/src/main/kotlin/io/github/youndie/haul/server/search/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/search/` — ``SearchRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/search/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/search` | `400` query_too_short |
| `GET` `/ui/search/suggest` | `400` query_too_short |
| `DELETE` `/api/v1/me/recent-searches` | `401` |
