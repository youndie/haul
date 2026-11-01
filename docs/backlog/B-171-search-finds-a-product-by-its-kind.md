---
id: B-171
title: "fix: search finds a product by the words of its kind, not only of its name"
status: done
priority: P2
size: S
stage: stage-15-showcase
---

# B-171 — fix: search finds a product by the words of its kind, not only of its name

The shopper (2026-11-01) searched «wireless headphones» and did not get the wireless headphones the catalogue has
(WH-CH720N, «Sonora SH-C388N Wireless Noise Cancelling» since B-166): search matches the name's words, and the name does not say what the thing is.

- Search also matches the product's category and its key attributes (kind, connectivity), ranked below a name match.
  Reproduce first against the seed; the suggest's latency (B-09) is measured again after.

- AC: «wireless headphones» finds the catalogue's wireless headphones first; the existing search tests hold; the
  suggest's latency on the seed stays within B-09's number.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/`.

## Findings (2026-11-01)

- Reproduced on the seed (the server from `installDist` against PostgreSQL 18 in Docker on the build machine):
  `/ui/search?q=wireless%20headphones` answered «3 results» — Mallard Mode 146, Sonora Pulse 208T, Sonora SH-718N,
  the main pair third. The full-text document was the listing name (the title when none), the brand and the kind;
  `to_tsquery` required every word (`wireless:* & headphones:*`, stemmed to `headphon`). The SH-C388N's name has
  no «headphones» and its kind is «Over-ear»; five more pairs in Headphones with the feature «Wireless» have one of
  the two words in the name, or neither. The order inside the three was `ts_rank`'s proximity (the two words
  adjacent in the Mallard's name, three apart in the SH-718N's), not reviews.
- The rule (research, open question 1, «Decided in B-171»): a second document — the kind, the features, the
  leaf category's name and its parent's — weighted `B` under the name and brand's `A`; every word still required
  in either; a product whose name holds the query first, then `ts_rank`, then reviews. After: «wireless
  headphones» 12 results, the three named so first, the SH-C388N among the rest; «headphones» 8 → 15; «audio»
  0 → 30; «xqzt» still nothing. The suggest panel shares the matching (its products, categories, «All N
  results»); its query suggestions did not change.
- `ts_rank`'s weights alone do not keep the name first: it adds a term for every pair of the words near each
  other, whatever the weight, so a product whose features repeated «wireless headphones» four times ranked above
  every name match. Hence the explicit first key, held by `SearchByKindTest` «a product saying its kind over and
  over still comes after a name match» — the only test that fails without it (mutation: dropping the key fails
  it alone; reverting the repository fails the three seed tests, the «nothing» control passes both ways).
- Suggest latency, `scripts/suggest-latency.sh`'s method, interleaved before/after on the same machine: p50
  15.4–19.0 → 14.8–17.4 ms, p99 22.5–39.5 → 21.0–27.0 ms (B-09: p50 17.6–18.0, p99 24.4–25.4); the matching
  statement in PostgreSQL 5.4–7.2 → 7.6–12.8 ms. The script's own `EXPLAIN` statement was the B-09 one over the
  title, stale since B-45; it is the current statement now.
- Left as they are: the full-text indexes of V3 and V25 serve no statement since (the documents join the
  categories); the planner used neither at 1,920 products before either. A stored weighted `tsvector` column is
  the step at a marketplace's size, not this item's. The description, the specifications and the top-level
  category are not searched (research says why).
