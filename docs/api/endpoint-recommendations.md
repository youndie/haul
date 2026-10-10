---
id: endpoint-recommendations
title: Picked for you
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared ProductGrid
  - haul:shared SectionHeader
parent_feature: feature-recommendations
---

# API: Picked for you

> Built by B-25 and described as the code has it. Not a route of its own: the block is part of the
> `/ui/home` tree for a customer ([endpoint-catalog](endpoint-catalog.md)), drawn with components that
> already existed (`SectionHeader`, `ProductGrid` in
> `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`), and the views it reads
> are recorded by the product tree, `GET /ui/p/{productId}`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/home` (the «Picked for you» part) | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | request: —; answers tree fragment — `SectionHeader` «Picked for you» with «Based on your recent views» or «Popular right now» (`picked-title`) and a six-card `ProductGrid` (`picked`), after the Plus block; absent for a guest |
| `GET` `/ui/p/{productId}` (the view it records) | haul-server | public (optional bearer; `X-Haul-Guest`) | yes | records a customer's view beside the page's reads; the answer is the product's tree as before |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/home` (the «Picked for you» part) | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt` → `PickedSection` (`server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/screen/PickedSection.kt`) → `PickedForYou` (`server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/domain/Recommendations.kt`) |
| `GET` `/ui/p/{productId}` (the view) | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogRouting.kt` → `RecordView` → `ExposedProductViews` (`server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/data/ExposedProductViews.kt`, `server/src/main/resources/db/migration/V20__product_views.sql`) |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt` |

## Request and response bodies

The components are in `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/BrowseComponents.kt`;
not copied here. The cards are the server's ordinary product cards, with their «+» and heart.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/home` (the «Picked for you» part) | none of its own: a failure inside the block drops the block, logged (`PickedSectionTest.a block that fails is left out`); the page as a whole answers `503 unavailable` when the database cannot be reached ([endpoint-catalog](endpoint-catalog.md), B-32) |
| `GET` `/ui/p/{productId}` (the view) | none of its own: a failed write is logged and never fails the page (`PickedSectionTest.a view is recorded for a customer only and a failed write is swallowed`) |
