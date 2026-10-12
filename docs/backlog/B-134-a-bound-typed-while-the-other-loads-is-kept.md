---
id: B-134
title: "fix: a price bound typed while the other bound's load is on its way is kept"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
---

# B-134 — fix: a price bound typed while the other bound's load is on its way is kept

Found by the catalogue's run on v0.5.1, TC-CAT-05 step 4 ([run](../qa/runs/2026-10-12-v0.5.1.md)), on the stand. Leaving «from» for «to» is leaving
«from», so its load goes at once; whatever is typed into «to» before that answer lands is wiped when the answer
redraws the range. On the stand, unthrottled: «from» `500`, the word «to», Backspace×8 and `200` at once — the
request carried `price_min=500&price_max=100500`, «to» went back to `$100500`, the `200` was never sent. With the
`/ui/parts/` answers held 8 s (a slow link): «from» `220`, then `330` typed in «to» and shown with its caret — the
answer to `price_min=220` lands and «to» is empty again, `330` never sent. The cause is older than B-133:
`PriceRange` keeps `low` and `high` in `remember(range)`, so any new range — the answer to the other field's
load — resets both fields, the one being typed into included.

- **An answer never takes an edit the shopper has not confirmed.** A field being edited (focused, its text differing
  from the bound it showed) keeps its text when a new range arrives; the other field takes the range's bound. The
  edit is confirmed as before (Enter, Tab, a press elsewhere) and sends both bounds. Rejected: holding the first load
  back until both fields are left — a bound typed alone would wait for nothing.

- AC: the two walks above end with one load carrying both bounds (swapped where typed backwards); a client test with
  the parts answer held back fails on today's code; B-118's and B-133's one-load tests still pass.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`.
