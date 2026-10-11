---
id: B-102
title: "server: the catalog's filters show exactly what is applied"
status: wip
priority: P2
size: S
stage: stage-11-qa
---

# B-102 — server: the catalog's filters show exactly what is applied

Found in the QA pass of 2026-10-10 (v0.3.0):
- the applied chips leave out «Delivery: Tomorrow» and the rating, but `filterCount` counts them: the phone shows
  «Filters 2» / «2 applied» over one chip (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt:327-349`);
- «Clear all» is link-coloured with nothing applied; pressing it reloads the same address or silently drops the kind
  and the page (`CatalogScreen.kt:102-105, 445`);
- a top-level category draws its first subcategory's kinds: Electronics shows «Gaming, On-ear, Over-ear, Studio, True
  wireless» (headphone kinds) over speakers, TVs and projectors;
- pressing the selected kind chip, sort entry, history chip or «Any rating» sends a pointless `load`.

- AC: every applied filter has a chip and is counted once; «Clear all» only when something is applied; a top-level
  category's chips are its subcategories (or none); selected entries are inert; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Browse.kt`.
