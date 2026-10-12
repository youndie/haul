---
id: B-131
title: "fix: the presses and fields B-126 did not name"
status: done
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
- Anchors: `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/NamedPressesTest.kt`, `scripts/a11y-check.py`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt`.

## Findings (2026-10-12)

- **Names.** Each is a `Modifier.named(…)` (B-126's content description): the phone header's cart button «Cart»,
  «Cart 3» with three in it — the wide button's words, which it does not draw; the empty Saved list's picture by the
  empty state's own way on, «Browse deals»; a dialog's «×» «Close» — the review's, the question's and the return's
  (`DialogFrame`), and the Plus trial's, which had none either; the stars «1 star» … «5 stars»; the review's fields by
  their labels («Title», «Review») and the question's by its own; the price bounds by the facet and the word, «Price
  from» and «Price to», at 1440 and in the phone's filter sheet. The return dialog had nothing unnamed: its lines,
  reason and buttons are named by their words.
- **The chosen star is not heard in the browser.** It is `selected` in semantics, but the 1.12 mirror writes a node's
  `role`, `aria-label` and `aria-live` and no state at all (read from `ui-wasm-js-1.12.1.klib` and seen: no
  `aria-selected` or `aria-checked` after a press on «4 stars»), so a chosen star reads as the others — as B-126's
  ticked box and chosen swatch do. Putting the state into the name («4 stars, chosen») was the other way; not taken,
  as a name that changes under a press is what a state is for. Recorded in the research (B-126's paragraph).
- **Tests.** `NamedPressesTest` reads fields too — a node with a text to set, named by its content description alone,
  since what is typed is its value and a placeholder goes once something is (the catalog's price fields passed B-126's
  check by their values «80» and «400») — mounts an empty Saved list, opens the review, question, return and Plus trial dialogs
  from the storefront by the press that presents them (their semantics `OnClick`: a click below the window's fold
  misses), and reads the header's cart button for the word «Cart». A second control: a field with something typed
  and no name is reported. Mutation, every new name removed at once: red, each listed on its own — the phone cart
  («3», or nothing) on every page at 390, the Saved picture at both widths, `price-from` and `price-to`, the dialogs'
  `dialog-close` and `plus-trial-close`, the five stars and both review fields at both widths; the question's field
  alone unnamed — red on `question-text` at both widths. Restored, green, tree clean.
- **The browser** (`scripts/a11y-check.py`, now reading `role="textbox"` nodes without an `aria-label`, the cart
  button's name, a popup's nodes while it is open, and the review dialog — the reviews tab scrolled to «Write a
  review», pressed — at 1440 and 390 when signed in): on the Linux box, the server's `installDist` with a seeded
  PostgreSQL and shildik in Docker, Playwright's headless Chromium, as a guest, as Sam (nothing saved: the empty
  list) and as Maya. Every popup, the review dialog at both widths and every page: no unnamed button or field in the
  mirror or in Chromium's tree. The control, the bundle built from `main`'s client: 11 failing as a guest (the phone
  cart, empty, unnamed on every page; the price fields at 1440 and in the filter sheet), 18 as Sam (the cart named
  «1», the Saved picture, the dialog's «×», stars and fields at both widths), 17 as Maya (the cart «3», the same
  dialog). Not walked in the browser: the return dialog (no order of a shopper's is delivered on a fresh stack) and
  the Plus trial's; both are in `NamedPressesTest`.
- **Goldens**: none moved — a content description draws nothing.
