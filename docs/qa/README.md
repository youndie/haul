# QA — the cases a release is walked against

Two QA passes walked the stand by eye and by code audit (the review before 2026-10-10 and the pass of that
day, B-62…B-113); each found dozens of problems. This catalogue is what the next pass walks, so that what is
checked does not depend on who walks and what they remember (B-114).

Each case says what the product decided, not what the code happens to do: the expected results come from
research §2 («Decided in B-…») and the findings of the items that decided them, named on each case. An artboard
of the design canvas is never an expected result — the canvas is a historical reference the code has moved past
(research §1.6, «Decided in B-85»); where a case points at a picture, it is a golden.

## The areas

| File | Area | What it walks |
|---|---|---|
| [01-header-and-navigation.md](01-header-and-navigation.md) | `TC-HDR` | the header at 1440, 1024 and on a phone, its menus, the footer, back and forward |
| [02-home.md](02-home.md) | `TC-HOME` | the hero, banners, deals of the day and their countdown, the popular row, «Picked for you», the Plus block |
| [03-catalog-and-filters.md](03-catalog-and-filters.md) | `TC-CAT` | the catalog's root, a category's one address, kinds and subcategories, facets, the price range, chips, «Clear all», the sort, pages and «Show N more», the filter sheet, the deals page, a card's «+» |
| [04-search.md](04-search.md) | `TC-SRCH` | the suggest panel, the scope picker, relevance, finding a product by what it is, a query too short, no results, recent searches |
| [05-product-and-reviews.md](05-product-and-reviews.md) | `TC-PDP` | variants and tabs, the links, share, the buy box, prices, reviews and questions, the dialogs, «Helpful» |
| [06-cart-and-promo.md](06-cart-and-promo.md) | `TC-CART` | steppers and limits, the selection, «Save for later», promo codes, the summary, changed lines, the empty cart |
| [07-sign-in.md](07-sign-in.md) | `TC-SIGN` | the popup, a blocked popup, `/sign-in`, `next`, the prompt, sign-out, a lapsed sign-in |
| [08-checkout.md](08-checkout.md) | `TC-CHK` | methods, days and windows, the address form, points, Haul Pay, placing, refusals, the way back |
| [09-order-and-returns.md](09-order-and-returns.md) | `TC-ORD` | the live order page, the simulated delivery line, pickup, returns and refunds, the one-return rule, reorder |
| [10-account-and-saved.md](10-account-and-saved.md) | `TC-ACC` | the overview and its tiles, the history, the Saved list and hearts, Haul Plus and points |
| [11-errors-and-loading.md](11-errors-and-loading.md) | `TC-ERR` | placeholders, the first load, the kept page's notice, refused pages, unreachable against our side, pages not there, a command that fails, requests off the origin |
| [12-widths.md](12-widths.md) | `TC-WID` | 375, 800, 1024 and 1280, the widths no artboard draws |
| [13-stand-only.md](13-stand-only.md) | `TC-STAND` | the deploy, the synthetic shoppers, the saga in tracy — what only a running stand has |
| [14-desktop.md](14-desktop.md) | `TC-DESK` | the desktop app: the same pages and a guest's cart in a window, its back and forward, the guest kept between runs, sign-in not yet there |

## A case

```markdown
### TC-CART-04 — A promo code is applied, and a refused one says why at the field

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** a cart with the mug ticked, no code applied; the sale is live (`AUTUMN10` moves with it).
- **Steps:**
  1. Type `AUTUMN10` into «Promo code» and press «Apply» at once, without a pause.
  2. …
- **Expected:**
  - Step 1: …
- **Decided in:** B-11, B-76, B-92, B-100
- **Coverage:** `CartWiringTest` «apply sends a code typed just before the press»; golden `Cart_PromoError`;
  manual: the press reading the field before the keys reach it shows only in a throttled browser tab
```

- **Id** — `TC-<AREA>-NN`, the area of the file, never reused: a case that is no longer true is rewritten or
  deleted, and its number is not given to another.
- **Who** — a guest (a fresh browser profile, no sign-in), Maya (the seeded customer: Plus, 2,480 points, her
  cart, 48 saved), Sam (seeded, no membership — on the stand also the synthetic shoppers' person), or the
  operator (stand-only cases). Maya and Sam sign in with the password the owner keeps; it is printed nowhere.
- **Width** — the page's width in CSS pixels: 1440 and 390 are the canvas's, 1024 the desktop rearranged,
  800 and 1280 the bands' edges (research «Decided in B-82»), 375 the narrowest phone; `any`, or `n/a` for a
  case with no page.
- **Preconditions**, **Steps** (numbered), **Expected** — what the walker sets up, does, and must see. An
  expected result quotes the store's words where the product fixed them.
- **Decided in** — the items whose decision the case holds.
- **Coverage** — what holds the case between walks, separated by `;`:
  - `` `ClassName` «test name» `` — an automated test, by its class and its name (`` `server:ClassName` `` when
    two modules have a class of that name);
  - `` golden `Name` `` — a screenshot golden that `viddikVerify` holds;
  - `` script `scripts/…` `` — a script that runs on every pull request or against a stand;
  - `manual: <why>` — the part no test can hold, and why: a real browser's popup blocker or clipboard, the
    stand's own clock and pace, its tracy, the provider's page.

A case held only by tests is still walked: the tests hold the code, a walk holds the release on the stand.

## Recording a run

A run of the catalogue is one file, `runs/<yyyy-mm-dd>-<release>.md` (`runs/2026-10-12-v0.4.0.md`): where it
was walked (the stand or a local stack, its image tag), who walked, the browser, then one row per case walked.

```markdown
# Run 2026-10-12 — v0.4.0

- Stand: haul.kotlin.website, `ghcr.io/youndie/haul-server:v0.4.0`
- Walked by: …, Chrome 149, a fresh profile

| Case | Result | Evidence |
|---|---|---|
| TC-CART-04 | pass | |
| TC-SRCH-06 | fail | B-131: «Keep typing» with no query in the field at 390 — screenshot in the item |
| TC-STAND-04 | blocked | tracy unreachable from the walker's network |
| TC-WID-04 | not run | |
```

- **Result** is one of `pass`, `fail`, `blocked` (could not be walked: say what stood in the way) and `not run`.
- **A failure becomes a backlog item** before the run is committed: a `B-<next free>` file from the item
  template, its title the defect, the case's id and the run's file in its first paragraph, the evidence
  (screenshots, the request that went wrong) in it. The row names the item; the check refuses a `fail` without one.
- A case found wrong — the product decided otherwise since — is not a failure: the case is corrected in the same
  pull request, and the row says so.

## The check

`scripts/qa-cases.py`, run by `make check` (under `gate`, so CI runs it too), refuses a case without an id,
its fields, numbered steps, an expected result or a coverage line; a duplicate id; a coverage line naming a
test class or test that does not exist, a golden or a script that is not there, or a `manual` that does not
say why; a «Decided in» naming no item; and in a run, a case the catalogue lacks, a result outside the four,
or a failure that names no item. `scripts/qa-cases.py --summary` prints the cases per area and how each is
held.
