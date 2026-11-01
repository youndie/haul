---
id: B-172
title: "fix: the store's promises agree with its rules and with each other"
status: wip
priority: P2
size: S
stage: stage-15-showcase
---

# B-172 — fix: the store's promises agree with its rules and with each other

The shopper (2026-11-01) found the store contradicting itself: «Free delivery over $35» in one place, a banner
«Free delivery on everything» in another, and Haul Plus sold for $4.99 a month for free delivery; a count of
«1.2M» items on sale next to a catalogue of 832; a banner «Laptops from $399» that opens the generic deals. A demo
that is checked by its visitors' eyes cannot be caught out by its own copy.

- Every promise the seed and the trees make is read against the rules (D7 delivery from $35, Plus's benefits) and
  either made true or reworded; counts shown are the catalogue's; every banner leads to what it names.

- AC: a test over the home tree finds no delivery promise that the cart's rules contradict; the banners' targets
  show what their titles say; changed goldens re-recorded and looked at.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`.
