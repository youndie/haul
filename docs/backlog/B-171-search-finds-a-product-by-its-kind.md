---
id: B-171
title: "fix: search finds a product by the words of its kind, not only of its name"
status: wip
priority: P2
size: S
stage: stage-15-showcase
---

# B-171 — fix: search finds a product by the words of its kind, not only of its name

The shopper (2026-11-01) searched «wireless headphones» and did not get the wireless headphones the catalogue has
(WH-CH720N): search matches the name's words, and the name does not say what the thing is.

- Search also matches the product's category and its key attributes (kind, connectivity), ranked below a name match.
  Reproduce first against the seed; the suggest's latency (B-09) is measured again after.

- AC: «wireless headphones» finds the catalogue's wireless headphones first; the existing search tests hold; the
  suggest's latency on the seed stays within B-09's number.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/`.
