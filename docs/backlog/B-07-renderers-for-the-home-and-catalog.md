---
id: B-07
title: "client: renderers for the Home and Catalog components; Loading / Error shells"
status: open
priority: P1
size: L
stage: stage-2-browse
blocked_by: [B-05, B-06]
---

# B-07 — client: renderers for the Home and Catalog components; Loading / Error shells

Home and Category are the first screens drawn from server trees; Loading and Error are the client's own and are shared by every screen.

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: the Plus trial dialog (B-23).

- AC: `viddikDesignParity` within tolerance for every `Home_*` and `Catalog_*` artboard.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/home/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/catalog/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/shell/`.
