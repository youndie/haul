---
id: B-134
title: "fix: a price bound typed while the other bound's load is on its way is kept"
status: done
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

## Findings

- **What an unconfirmed edit is.** `PriceRange` keeps the fields in a plain `remember` now, beside `Edits`: one flag
  per field, set by a key that changes its text, cleared when a confirm reads both fields (after B-76's frames) or a
  thumb is taken. A new range redraws only the fields whose flag is down. The item's «focused, its text differing from
  what it showed» is the same set in practice — a field holding a flag is focused or has a confirm waiting to read it
  — but a flag keeps two cases right that a text comparison would not: a field left whose confirm has not read it
  yet keeps its bound until it does, and a bound sent inside the other field's load is no edit any more.
- **The walk at once has two orders, and both end in one load with both bounds.** Keys landing in «to» after
  «from»'s confirm read the fields (the stand's walk; «a bound typed while the other bound loads is kept and sent
  with it», «bounds typed backwards while one loads end in one load swapped»): «to» keeps them, «from» takes the
  range's bound, the click sends both, swapped. Keys landing before it («a bound typed before the other's load
  reads it goes with that load once»): «from»'s load carries both, swapped; the answer redraws both fields, and
  leaving «to» sends nothing more — without the flags cleared on that read, «to»'s `20` stayed an edit and the
  same range went twice.
- **Where the other field's bound comes from.** «the other takes the range's bound», as decided: when «from»'s
  load reads an old «to» below it, the server swaps them, «from» shows the swapped low, and the bound typed into
  «to» replaces the high. The shopper sees «from» change before confirming «to».
- **The `100500` on the stand** is not reproduced on the desktop: no code path joins two bounds, so it is most
  likely the browser's input and the field's text parting when the field was reset under the keys. With the field
  no longer reset while typed into, that reset is gone; not walked in a browser to confirm.
- **The sheet at 390** is the same `PriceRange`: «the filter sheet keeps a bound typed while the other bound loads»
  walks the held answer there, left with a tap on the sheet's blank space.
- **Mutations**, each red then restored: `main`'s `FacetPanelView.kt` (the three walk tests red: «to» empty when the
  answer lands, as on the stand); the flags not cleared on a confirm's read (the keys-before-read test: «from» kept
  `$50`). The flags cleared on a thumb's grab are not held by a test: the drag writes both fields itself, so only a
  cancelled drag with a range landing during it could tell.
- **Verified** on the Linux box: `PriceRangeTest` (19), `:composeApp:wasmJsBrowserDistribution`, `check
  :server:installDist` (server and client tests, `viddikVerify`, ktlint) and `scripts/e2e.sh` against the image
  built from this branch, all green. No golden moved. Not walked in a browser.
