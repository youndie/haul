---
id: B-96
title: "server: home never draws one product twice"
status: done
priority: P1
size: S
stage: stage-11-qa
---

# B-96 — server: home never draws one product twice

Found in the QA pass of 2026-10-10 (v0.3.0), code audit. With no live deal, a customer's home draws «Popular on Haul» (B-70, most reviewed in stock) and
«Picked for you · Popular right now», built from the same most-reviewed products
(`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt:90-94, 107-116`, `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/data/ExposedPickSources.kt:41-53`).
The same `card-<id>` can appear twice: a duplicate row, a broken one-id-per-page rule (B-65), and a «+» update replacing
both twins. `UniqueIdsTest` runs only with deals live.

- AC: no product appears in two rows of one home; `UniqueIdsTest` also runs at a moment with no live deal, for a guest
  and a customer.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/recommendations/screen/PickedSection.kt`.

## Findings (2026-10-11)

- **The later row gives way, whichever the earlier one is**: `HomeScreen` hands «Picked for you» the products of
  every `ProductGrid` already in the page's sections (`productsIn`), and `PickedForYou` leaves them out as it leaves
  out a viewed or bought product, still filling the row to six from the popular order under the two-per-category
  cap. Nothing names the deals or the popular row, so B-87 replacing the fallback row (or any row added above) keeps
  the rule. «Picked for you» is the one that yields because it is the page's extra (B-25) and sits below; the
  fallback row stays exactly B-70's six most reviewed.
- **Reproduced before the fix**: at `CatalogSeed.DEALS_END` Sam (seeded, no views, no orders) got all six of
  «Popular on Haul» again in «Popular right now» — `card-p-039-04` and five more ×2 on `/ui/home`, and again on the
  page after a card's «+» update. Maya's views-based picks did not overlap the popular row on the seed, and at the
  canvas's «now» no customer's picks overlapped the deals, so the canvas's home and its goldens are unchanged.
- **Tests**: `UniqueIdsTest` — the guest walk and the customer walk now also run at `DEALS_END` (the sale live, no
  deal): the guest's every page, and Sam's then Maya's, with a control that the moment draws no deals and that Sam's
  home draws both «Popular on Haul» and «Picked for you». `PickedForYouTest` — a product the page already draws is
  never picked, from the popular basis and from the views, and the row still has six.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: `HomeScreen` passing no
  products (the no-deal customer walk red, six ids ×2); `PickedForYou` ignoring `onPage` (that walk and the new
  `PickedForYouTest` case red).
- **Goldens**: none changed — no client code or fixture changed.
