---
id: B-62
title: "client: a filter or a sort redraws the results, not the page"
status: wip
priority: P1
size: M
stage: stage-9-ship
---

# B-62 — client: a filter or a sort redraws the results, not the page

Every press that stays on the same page — a facet, a sort, «Show N more», a page of results — is a
`navigate`. The shell keys everything on the full address (`key(address)` in `Storefront.kt`), so each
press throws the page away: the loading placeholder takes its place, the scroll goes back to the top, and
whatever the page held in `remember` starts over. The server only changed the results and the counts.

kompot 0.40.0.212 (kompot B-81) lets the screen keep its tree: `KompotScreenLoader(screenKey = …)` loads
a new address behind the drawn tree when the application says it is the same screen. It also keeps one
override store per screen, and keys `column`/`row` children by `id`. Compose then redraws only what
changed.

- **Decided as product owner:** the same screen is the same **path** — `/catalog?brand=a` and
  `/catalog?brand=b` are one screen, `/catalog` and `/p/…` two. Navigating within a screen keeps the
  page, its scroll and its opened parts while the next tree loads. A thin progress line under the header
  shows that a load is in flight. Navigating to another path draws the loading placeholder and starts at
  the top, as today. Back and forward follow the same rule.
- A load that fails within a screen keeps the page and shows the error as a notice with a retry, not
  the full error screen.
- The filter sheet's hoisting (B-54) stays only if it is still needed once the page is no longer thrown
  away; otherwise it is simplified, with the reason recorded.
- Not here: answering with only the changed nodes (`update` / `load`, kompot B-82) — a follow-up item
  once kompot ships it.

- AC: on the catalog, ticking a facet keeps the header, the breadcrumbs and the scroll position, with no
  placeholder in between, and shows the new results; opening a product still shows the placeholder and
  starts at the top; back from the product returns to the catalog; client tests for each; goldens
  unchanged except where a state is meant to change; the e2e green.
- Anchors: `gradle/libs.versions.toml`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`.
