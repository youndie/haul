---
id: B-05
title: "server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees"
status: open
priority: P1
size: L
stage: stage-2-browse
blocked_by: [B-03, B-04]
---

# B-05 — server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees

The browse half of the storefront — home, category with facets, product page, delivery estimate — is what everything after it links into.

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: search (B-09), reviews and questions (B-22).

- AC: feature-browse and feature-product scenarios pass against PostgreSQL.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/`.
