---
id: endpoint-catalog
title: Home, category, product
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared CatalogRoutes
parent_feature: feature-browse
---

# API: Home, category, product

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: —; answers tree: Home (with recommendations and the Plus block for a customer) |
| `GET` `/ui/c/{categoryPath}` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: filters, sort, page; answers tree: Catalog |
| `GET` `/ui/p/{productId}` | haul-server | public (none, or `X-Haul-Guest`) | yes | request: `sku`, `tab` (description / specifications / reviews / questions), page; answers tree: Product; records a view for a customer |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/ui/home` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| `GET` `/ui/c/{categoryPath}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| `GET` `/ui/p/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/` — ``CatalogRoutes`` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` | `503` unavailable |
| `GET` `/ui/c/{categoryPath}` | `400` validation_failed, `404` category_not_found |
| `GET` `/ui/p/{productId}` | `404` product_not_found |
