---
id: feature-search
title: Search and autocomplete
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-search
api:
  - endpoint-search
tags: []
---

# Search and autocomplete

## 1. Overview

One field in the header searches everything; while typing, a panel suggests queries, categories and top products, and shows recent searches.

## 2. Business rules

* suggestions start at 2 characters; up to 5 query suggestions, 3 categories with counts, 3 top products;
* a search is recorded in the customer's recent searches (last 10, de-duplicated, newest first); a guest has none;
* «Clear» empties recent searches (with sign-in, B-12: until then nothing records or clears them over HTTP);
* results group by category with counts (the chips above the grid) and use the catalog's card, sort and pages;
* no results → the page says so and offers the suggestions for the query, or three tips when there are none, above eight popular categories.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7; they are verified against the
code, not observed, until this document goes `active`.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SearchComponents.kt` — the contract, with `QueryTooShort` in `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/search/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/` |

## 5. Scenarios (BDD / test cases)

All scenarios are *target*: written against the intended behaviour and verified against the real
status codes and error strings before this document goes `active`.

### Scenario: Typing suggests
* **Given:** the seed catalog
* **When:** the client asks for suggestions for «running sh»
* **Then:** the first query suggestion is «running shoes» and «Sports › Running shoes» is among the categories.
* **Automated:** `SearchRoutesTest.typing suggests the query and the category`

### Scenario: Recent searches are personal
* **Given:** a customer who searched «wireless earbuds»
* **When:** another customer opens the suggestions
* **Then:** «wireless earbuds» is not in their recent list.
* Not automated: it needs two signed-in customers, and sign-in is B-12. The storage half is `RecentSearchesTest.one customer's searches are not another's`.

### Scenario: Too short
* **When:** the client asks for suggestions for «r»
* **Then:** the server returns `400` with `query_too_short`.
* **Automated:** `SearchRoutesTest.a one character query is 400 query_too_short` — on both routes, a padded «r» and a missing `q` included

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
