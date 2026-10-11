---
id: B-103
title: "server: the search panel keeps the query and the scope"
status: wip
priority: P2
size: S
stage: stage-11-qa
---

# B-103 — server: the search panel keeps the query and the scope

Found in the QA pass of 2026-10-10 (v0.3.0), code audit:
- «In categories · Sports › Running shoes 12» counts the query's matches but opens the whole category without the
  query (`server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt:94`) — it could open `/search?q=…&category=`;
- the panel ignores the picker's scope (B-72): «All N results» and the counts are unscoped and a suggestion drops the
  scope (`SearchScreen.kt:84, 107-110`);
- the sort defaults to «Popular» and drops the database's relevance order; there is no relevance sort
  (`SearchScreen.kt:145`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Browse.kt:128`).

- AC: category suggestions keep the query; the panel follows the scope; a «Relevance» sort is the default for a
  search; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt`.
