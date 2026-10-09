---
id: B-63
title: "server + client: a filter answers with the parts that changed"
status: wip
priority: P1
size: M
stage: stage-9-ship
blocked_by: [B-62]
---

# B-63 — server + client: a filter answers with the parts that changed

Since B-62 a press within a screen keeps the page, but the server still sends the whole page for it.
A facet tick on the catalog changes the title, the facet counts and the results; the header, the
breadcrumbs, the promo and the footer are built and sent again. «Add to cart» answers `refresh`, so the
whole page comes back to change one number in the header.

kompot 0.40.0.213 (kompot B-82) adds two actions:
- `update { updates, deeplink?, history? }` replaces nodes by `id`, and hands the new address to the app;
- `load { url }` is a `GET` (endpoint kind `load`) answered with an action.

On the client, `withUpdates(overrides, onAddress)` and `withLoad(scope, state, load)` sit in the handler
chain; on the server, `kompotUpdate { … }` / `respondKompotUpdate`.

- **Decided as product owner:** presses that only filter, sort or page what a screen already shows carry
  `load`. That covers facets, the sort, «Show N more» and the phone filter sheet's ticks on every screen
  that has them. They are answered with `update` for the nodes that change, plus `deeplink` with `push`,
  so back, reload and a shared link show the same state. The address keeps opening the whole page as
  today. «Add to cart» and the other actions that change one number elsewhere on the page answer with
  `update` for that node (and their message), not `refresh`. The progress line (B-62) shows while a
  `load` is in flight.
- An answer that cannot be partial (the address no longer exists, the category moved) answers `navigate`.
- Ids of the replaced nodes stay stable between the whole page and the parts. A test holds that the parts
  equal the same nodes of the whole page for the same address.

- AC: on the catalog, ticking a facet sends one `GET` to a `load` endpoint whose answer is an `update`
  (not the page). The title, counts and results change, the address changes and back restores the
  previous one. The whole page at the new address equals the page drawn after the update (server test
  over every node replaced). «Add to cart» updates the header's count without a page request. Client
  wiring tests, server route tests, the e2e green, the endpoint documented with its kind.
- Anchors: `gradle/libs.versions.toml`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogScreen.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
