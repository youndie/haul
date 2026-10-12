---
id: B-118
title: "fix: a click outside a price field applies the bound typed into it"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-118 — fix: a click outside a price field applies the bound typed into it

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), at 1440 on a category:
after typing `200` into the price «to» field, a click on empty page space left the caret in the field and applied
nothing; Tab, Enter or a click into another field applied it. A shopper expects a click elsewhere to finish the
field. Tab out of the field also sent the same parts request twice (`…price_min=100&price_max=400…` once
aborted, then answered 200).

- **A click on the page outside a field takes the focus off it**, so the bound is applied the way Tab applies
  it, and one leave sends one request. Rejected: applying on every keystroke — each digit would be a request
  and a grid redrawn under the typing.

- AC: type a bound, click blank page space — the grid narrows once, the address carries the bound; Tab sends
  one parts request, not two.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog`.
