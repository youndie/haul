---
id: B-09
title: "server: search, suggest, recent searches; suggest latency measured on the seed"
status: done
priority: P1
size: M
stage: stage-3-search
epic: feature-search
blocked_by: [B-05]
---

# B-09 — server: search, suggest, recent searches; suggest latency measured on the seed

Search is a header field on every screen; its suggest latency on PostgreSQL full text is research open question 1.

Feature: `feature-search` — its scenarios are this item's acceptance where it names them.

- Not covered: ranking beyond full-text relevance and popularity.

- AC: feature-search scenarios pass; the latency number is in the research document.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/search/`.

## Done (2026-10-08)

- Routes, public tier (`server/.../feature/search/SearchRouting.kt`): `GET /ui/search` answers the
  results page — the query as the title with «N results», the category chips with counts (`All`
  first, then the leaf categories, the most results first; `category=` narrows the grid), the
  catalog's card, sort and pages — or, when nothing matches, `SearchNoResults` with the queries to try,
  three tips and the eight popular categories. `GET /ui/search/suggest` answers `SearchSuggestPanel`:
  up to five queries (the typed part apart from the completion), three categories with counts as
  «Sports › Running shoes», three top products, «All N results». Both refuse fewer than two
  characters, trimmed, with `400 query_too_short`.
- Matching (`data/PostgresSearchRepository.kt`, `V3__search.sql`): full text over title, brand and kind,
  every typed word a prefix, or the query inside the lower-cased title under a `pg_trgm` index;
  suggestions from category, brand and kind names, a misspelling caught by `similarity()`.
- Recent searches (`data/ExposedRecentSearches.kt`, table `recent_searches`): keyed by a customer id,
  the last ten, one per query, newest first; recorded when a signed-in viewer searches, listed in the
  panel for one. `Viewer.customerId` exists and nothing sets it yet.
- Contract: `SearchSuggestPanel`, `SearchNoResults` and their parts in `shared/.../ui/SearchComponents.kt`;
  `Chip.count`; `ErrorCode.QueryTooShort`. Shaped after the canvas's notes for the Search artboards
  (`canvas.json`); the artboards' own sources are not in this repository yet, so B-10 checks the shape
  against them when it draws the screen.
- Seed: Sports has «Running gear» (Running shoes, Running jackets) and «Fitness» (Yoga mats,
  Dumbbells), which the scenario names; the generated catalog is otherwise the same row for row.
- Scenarios, automated in `server/src/test/.../feature/search/SearchRoutesTest.kt` against PostgreSQL:
  «Typing suggests», «Too short» (on both routes, a padded «r» and a missing `q` included); also the
  panel's limits, grouping with counts that add up, the category filter, no results, and a
  misspelling. Mutation: dropping the prefix from every full-text word broke *none* of those — the
  substring half alone satisfies them — so `typed words match in any order across title and brand`
  was added, and it is the one the mutation fails.
- Not automated: «Recent searches are personal». It needs two signed-in customers, and sign-in is
  B-12; its storage half is `RecentSearchesTest` (one customer's searches are not another's, the last
  ten, a repeat moving to the top, clear). B-12 also brings `DELETE /api/v1/me/recent-searches`
  (customer tier) and the «Recent» part of the panel for a signed-in viewer, with its «Clear» action.
- Latency: measured and written into research, open question 1 — p50 17.6–18.0 ms, p99 24.4–25.4 ms
  for a suggestion on the seed (`scripts/suggest-latency.sh`, method there); the planner does not use
  the search indexes at 1,920 products.
- The image trains on the search screens too (`server/build.gradle.kts`); `scripts/image-check.sh`
  passed with V3 in it (391 of 391 classes from the cache).
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/`,
  `server/src/main/resources/db/migration/V3__search.sql`, `scripts/suggest-latency.sh`.
