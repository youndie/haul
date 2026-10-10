---
id: B-05
title: "server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees"
status: done
priority: P1
size: L
stage: stage-2-browse
epic: feature-browse
blocked_by: [B-03, B-04]
---

# B-05 — server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees

The browse half of the storefront — home, category with facets, product page, delivery estimate — is what everything after it links into.

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: search (B-09), reviews and questions (B-22).

- AC: feature-browse and feature-product scenarios pass against PostgreSQL.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/`.

## Done (2026-10-08)

- Routes `/ui/home`, `/ui/c/{categoryPath}`, `/ui/p/{productId}` answer kompot trees through
  `respondKompotComponent` (`feature/catalog/CatalogRouting.kt`); refusals are `ErrorBody` with
  `ErrorCode` from `shared` (`HaulModule.kt`); Koin assembles the feature.
- Contract: the Home, Category and Product pieces (`shared/.../ui/BrowseComponents.kt`,
  `ProductComponents.kt`), `haulWireJson`, `ErrorCode`; `ProductCard` gained its navigation action.
- Data: `V2__product_facets.sql` (features, kind, dispatch days); the canvas's headphones and a
  Marshall pair in the seed (`seed/SampleHeadphones.kt`).
- Scenarios, automated in `server/src/test/.../feature/catalog/`: «Facets narrow the list»,
  «A filter set with no products», «An unknown category» (`CatalogRoutesTest`); «Variant changes the
  price», «Unknown product» and the page half of «Out of stock» (`ProductRoutesTest`); the guest home
  page (`HomeRoutesTest`). Mutation: drawing the product with the cheapest SKU instead of `sku` fails
  «a variant changes the price» and «an out of stock SKU is drawn out of stock».
- Found on the way: a facet counted «minus itself» lost every value when another filter matched
  nothing — the empty page then had no way back; every value is now listed, with 0 (research).
- Not done here, on purpose: `409 out_of_stock` on adding to the cart (B-11); the `reviews` and
  `questions` tabs answer `400 validation_failed` until B-22; «12K bought this month» needs orders;
  the member's Plus block and «Picked for you» (B-23, B-25).
- Findings for the drafted documents: endpoint-catalog lists `tab` values the route refuses until
  B-22; feature-product's out-of-stock scenario is split between this route and the cart's.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`,
  `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/`.
