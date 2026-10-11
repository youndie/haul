---
id: B-114
title: "docs: a QA test-case catalogue the stand is walked against"
status: done
priority: P1
size: M
stage: stage-11-qa
---

# B-114 — docs: a QA test-case catalogue the stand is walked against

Two QA passes (2026-10-10 and the review before it) walked the stand by eye and by code audit; each found dozens of
problems, but there is nothing to walk the next release against, so coverage depends on who walks and what they
remember. The feature documents that would hold scenarios never landed (the draft layers PR is stale).

- **Decision:** a catalogue in `docs/qa/` — one file per area (header and navigation, home, catalog and filters,
  search, product and reviews, cart and promo, sign-in, checkout, order and returns, account and Saved, errors and
  loading, widths, stand-only: shoppers and observability). Each case has an id (`TC-<area>-NN`), who (guest or
  customer), width (1440, 1024, 390), preconditions, steps, the expected result, and **how it is covered**: the
  automated test that holds it (class and test name) or `manual`. Expected results come from what the product
  decided (research «Decided in B-…», item findings), not from what the code happens to do.
- A run of the catalogue is recorded as `docs/qa/runs/<date>-<release>.md`: case, result (pass / fail / blocked / not
  run), evidence; a failure becomes an item.
- AC: the catalogue covers every screen and every decision recorded in research §2 and the B-62…B-113 findings; a
  check in `make check` (or a small script it runs) refuses a case without an id, steps, an expected result or a
  coverage line, and a duplicate id; the manual cases are the ones a test cannot hold.
- Anchors: `docs/research/research-architecture.md`, `docs/backlog/`.

## Done (2026-10-11)

- **The catalogue**: `docs/qa/README.md` (what it is, the case format, how a run is recorded in
  `docs/qa/runs/<date>-<release>.md` and how a failure becomes an item) and thirteen area files, `01-…` to `13-…`
  in the order a shopper meets them. **127 cases**, each with an id, who, width, preconditions, numbered steps, the
  expected result, «Decided in» (the items whose decision it holds) and its coverage:

  | Area | File | Cases | Tests only | Tests + manual | Manual only |
  |---|---|---:|---:|---:|---:|
  | `TC-HDR` | header and navigation | 10 | 9 | 1 | 0 |
  | `TC-HOME` | home | 7 | 6 | 1 | 0 |
  | `TC-CAT` | catalog and filters (and `/c`, `/deals`, a card's «+») | 14 | 14 | 0 | 0 |
  | `TC-SRCH` | search | 10 | 9 | 1 | 0 |
  | `TC-PDP` | product and reviews | 17 | 15 | 2 | 0 |
  | `TC-CART` | cart and promo | 8 | 7 | 1 | 0 |
  | `TC-SIGN` | sign-in | 8 | 3 | 5 | 0 |
  | `TC-CHK` | checkout | 12 | 11 | 1 | 0 |
  | `TC-ORD` | order and returns | 12 | 11 | 1 | 0 |
  | `TC-ACC` | account and Saved | 9 | 9 | 0 | 0 |
  | `TC-ERR` | errors and loading | 9 | 7 | 2 | 0 |
  | `TC-WID` | widths | 5 | 3 | 1 | 1 |
  | `TC-STAND` | stand-only | 6 | 0 | 6 | 0 |
  | | | **127** | **104** | **22** | **1** |

  «Tests only» counts automated tests, goldens and scripts; the 481 test references name 97 test classes, and 66
  goldens are named besides. The 23 cases with a manual part say why no test holds it: a real browser's popup
  blocker, clipboard or throttled frames, the provider's page, a live resize across a band, and on the stand its
  host, its clock and pace, its tracy and its log. The one manual-only case is a live resize across 1024 and 1280
  (`TC-WID-04`): `WidthsTest` draws each width fresh.
- **Reachability**: every «Decided in B-…» of research §2 is named by a case but B-02 (Exposed and CIO) and B-111
  (how the tests seed) — neither has anything a shopper sees; every item B-62…B-113 is named but the open or
  deferred ones (B-78 pictures, B-85 canvas, B-86 review photos and histogram, B-87 the fallback rows, B-112, a
  question), which have no decision to hold yet, and the test-only ones (B-84, B-111, B-113). Every screen the
  storefront serves is walked: home, `/c`, a category, `/deals`, a search, a product, the cart, checkout, an order,
  the account, the history, the Saved list, `/sign-in`, the sign-in prompt, the placeholders and the error and
  not-found pages.
- **The check**: `scripts/qa-cases.py` (standard library), a recipe line of `gate` in the Makefile — the place the
  Makefile keeps the repository's own checks — so `make check`, and CI's `youndie/docs-bootstrap` step that runs
  it, refuse: a case heading without a `TC-<AREA>-NN` id, a duplicate id, an id of another area than its file's; a
  case without who, width, preconditions, numbered steps, an expected result or coverage, a field given twice or
  misspelt; a width the catalogue does not walk; a coverage entry naming a test class no test source set has (or
  that two modules have, unless `server:`/`composeApp:` says which), a test the class does not declare, a golden
  with no PNG, a script that is not there, or a bare `manual`; a «Decided in» naming no item; in a run, a case the
  catalogue lacks, a result other than pass, fail, blocked or not run, a fail naming no item, a file not named
  `<yyyy-mm-dd>-<release>.md`. `--summary` prints the table above.
- **Mutation** (each on the committed catalogue, restored with `git checkout`, `git status` clean): the twenty
  breakages above, one at a time — the id taken off a heading, a duplicate id, another area's id, the steps
  removed, the steps unnumbered, the expected result and the coverage turned into stray lines, «Expect» for
  «Expected», `CartWiringsTest`, a test name one word off, `DrawnActionsTest` without its module, the wrong
  module, a golden renamed, a bare `manual`, `B-999`, width 1920, and four runs (an unknown case, a fail without
  an item, «ok», a misnamed file) — each red with its file and line; the control, a run with a fail naming an
  item, green. `make check` exits 2 on a heading without an id.

### Decisions

- **The format is Markdown a person reads and a script parses**: a `###` heading per case and bold-labelled bullet
  fields, not a table (steps and expected results are lists) and not YAML (the walker reads it in a browser).
  Expected results are written step by step, quoting the store's words where an item fixed them.
- **Coverage is a test by its class and its backticked name**, so a renamed or deleted test fails the gate in the
  pull request that renamed it. `DrawnActionsTest` exists in `server` and `composeApp`, hence the optional module
  prefix, required only where a class name is ambiguous. Goldens and scripts count as coverage; `manual` must say why.
- **Widths**: the item's 1440, 1024 and 390, plus 375, 800, 1280 (B-82's band edges and the narrowest phone) and
  1366 (the QA pass's laptop), `any` and `n/a` for the stand's cases with no page.
- **A run's fail names its item**, checked against `docs/backlog/`; a case found wrong is corrected in the same pull
  request, not failed.
- **Cases are fewer and sharper**: one case per decision or per screen state, steps that move through a decision's
  branches (a guest, a member, a non-member) rather than a case per permutation. A card's «+» is walked once on
  home for every page that draws cards; paging once on the catalog for search and deals too.
- **Known departures stay visible inside the expected results** rather than as failures: a guest's «Buy now» merged
  with an account's ticked cart (B-100), checkout's tile opening the product's default SKU (B-100), the steps
  reading «Review» while another day holds the button (B-97), letters' hairlines in the display face (B-106).

### Findings

- **Walking the Plus trial on the stand is irreversible**: v1 has no way to end a membership (B-23), and Sam — the
  seeded non-member — is the synthetic shoppers' person (B-31). `TC-ACC-08` says to walk it on a local stack or with
  a person the owner can spare; the stand has no spare non-member to walk the offer with.
- **Some cases need what the stand cannot give**: an error of the store's own (`TC-ERR-05`), a filled window or a
  changed quote at placement (`TC-CHK-09`), a late return (`TC-ORD-07`), reorder shortfalls (`TC-ORD-08`) — a local
  stack with its database or clock under the walker's hand. The cases say so in their preconditions.
- **`composeApp:DrawnActionsTest` «haul plus opens a member's account»** is named after B-49's member pill, which
  B-108 took off the wire (`Frame.plus` is `null` for a member); the test still holds the client following an
  action when one comes — the client's own customer header before any tree (B-105) — but its name and KDoc describe
  a press no server tree sends now. Not renamed here (scope); no case cites it.
- **Not walked**: the catalogue has not been run against the stand in this item — that is the first run's, recorded
  under `docs/qa/runs/`. The stand-only cases were written from B-27, B-31, B-89, B-109 and B-110 without access to
  the stand, its tracy or its log.
- **The change touches `scripts/` and the Makefile**, so `scripts/code-changed.sh` counts it as code and the pull
  request runs the Gradle, image and chart jobs too; nothing they build changed.
