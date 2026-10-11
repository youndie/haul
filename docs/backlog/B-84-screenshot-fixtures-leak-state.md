---
id: B-84
title: "test: screenshot fixtures do not depend on their order"
status: done
priority: P3
size: S
stage: stage-10-review
---

# B-84 — test: screenshot fixtures do not depend on their order

B-75 and B-72 saw `viddikDesignParity` read the Search_* artboards at 5–35 % on one run and 2–3 % on another
with no search change between them; the suspicion is state leaking between screenshot fixtures depending on
their order. `viddikVerify` is the gate and stays green, but a fixture whose pixels depend on what ran before it
can hide a regression or invent one.

- AC: the cause found and fixed (or the measurement explained), with a run in two different fixture orders
  giving the same numbers.
- Anchors: `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/ComponentFixtures.kt`.

## Findings (2026-10-11)

- **No order dependence; the jump was B-77.** The Search_* numbers moved with the merge of B-77 (#97), which added
  the search's sort control to the chips' row (the grid one row lower on a phone) and drew the no-results tips as
  text — a decided departure from the canvas that B-85 owns. B-75's «2–3 %» was measured on its branch before it
  was rebased over #97; «main» there already had the sort. Measured on the Linux box from `git archive` copies,
  `viddikDesignParity`: `4bd4c21e^1` (before #97) Results 2.77, Results_Phone 3.17, Autocomplete_Phone 2.85,
  NoResults 1.95, NoResults_Phone 3.25 %; `4bd4c21e` (#97) 5.38, 29.41, 10.76, 9.62, 35.23 %. Loading and Error,
  which #97 did not touch, read the same on both. No search file changed in B-75 or B-72, but `main` under them
  did.
- **Two orders, the same pixels**, on this branch's base (`aeaa9014`), every image compared pixel by pixel with a
  throwaway `ImageIO` tool rather than by its percentage:
  - the whole module in the registry's order, one JVM, the scene reopened at each size change (the parity run)
    against the forced recording (`viddikRecord --force`: by size, two forks, one shared scene per size) — 161 of
    161 identical;
  - the parity run with every fixture in a scene of its own (`viddik { sceneReuse = false }`) against the same
    recording — 161 of 161 identical;
  - the ten Search artboards alone (`--component "Search - "`) against the same ten in the whole run — identical,
    and the summary reads the same numbers (Results 5.42, Results_Phone 29.49, Autocomplete_Phone 10.84,
    NoResults 9.67, NoResults_Phone 35.35 %).
  So neither the shared scene, nor the static caches (fonts, `remember`ed registries), nor the header's field, nor
  the clock carries anything from one fixture to the next; viddik needs no change.
- **Found on the way, not fixed here: 24 goldens on `main` draw what the code no longer draws.** Eighteen still read
  the old placeholder «Search 2.4 million products» (Home_Loading, Account_Content, the Catalog, Product and Saved
  pages at 1440 and 1024, and the three phone dialogs over a header: 1125 px, ≈0.05 % of a page), six the display
  face's quotation marks before B-106's cut (Search_Error, Checkout_Error and Search_NoResults with their phone
  twins). `viddikVerify` accepts
  both within its tolerance, and a recording without `--force` keeps a golden the verification accepts — which is
  how B-107's re-record left them. A forced recording on this base changes exactly those 24 and nothing else. Left
  for an item that re-records goldens (B-108 is re-recording beside this one).
