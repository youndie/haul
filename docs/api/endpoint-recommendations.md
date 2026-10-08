---
id: endpoint-recommendations
title: Picked for you
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared ProductGrid
  - haul:shared PlusBlock
  - haul:shared ErrorCode
parent_feature: feature-recommendations
---

# API: Picked for you

> Drafted from the product brief; nothing of it is built yet (B-25). `/ui/home` exists since B-05
> and answers every viewer the guest's page ([endpoint-catalog](endpoint-catalog.md)). There are no
> route classes in `shared`: the path is the server's string, and the block will be drawn with
> components that exist (`ProductGrid`, `SectionHeader`, `PlusBlock` in
> `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`).

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
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`;
not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` (the `PlusBlock` and «Picked for you» part) | none of its own (*target*) |

The `503` unavailable this draft first gave the personalised part is withdrawn: since B-32, `503`
means one thing — the database cannot be reached — and `/ui/home` as a whole answers it then
(`server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`; see
[endpoint-catalog](endpoint-catalog.md)). Whether a failure inside the block drops the block or
fails the page is B-25's decision.
