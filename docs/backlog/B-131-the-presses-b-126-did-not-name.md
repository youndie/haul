---
id: B-131
title: "fix: the presses and fields B-126 did not name"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
---

# B-131 — fix: the presses and fields B-126 did not name

Found outside the catalogue by its v0.5.0 run ([run](../qa/runs/2026-10-12-v0.5.0.md)), in the browser's accessible mirror (`cmp_a11y_root`) and
Chromium's tree:

- the phone header's cart button (390, 375): no name with an empty cart, named only by its count («1») with items
  (1440 reads «Cart» / «Cart 12»);
- the empty Saved list's picture (`saved-illustration`, a press to /deals), 1440 and 390 —
  `scripts/a11y-check.py --login sam` fails on it;
- the review dialog: its close «×» (`dialog-close`), the five stars (`review-star:1…5`), its title and body
  fields;
- the price «from» and «to» fields on a category.

`NamedPressesTest` did not reach them: its bodies have no empty Saved list and no dialog, and it checks presses,
not text fields. `a11y-check.py` walks popups and pages, not dialogs.

- **Every press and every field has a name**, the cart button «Cart» with its count as on the desktop, a star its
  value («4 stars»), a field its label. The test and the script grow to what they missed (an empty Saved list, the
  review and return dialogs, text fields).

- AC: `a11y-check.py` at 1440 and 390, signed in and as a guest, with the review dialog opened, reports no unnamed
  button or field; `NamedPressesTest` covers text fields and the dialogs and fails with any of the above unnamed.
- Anchors: `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/NamedPressesTest.kt`, `scripts/a11y-check.py`.
