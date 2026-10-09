---
id: B-65
title: "server: every node id appears once on a page"
status: done
priority: P2
size: S
stage: stage-9-ship
---

# B-65 — server: every node id appears once on a page

kompot requires a node's `id` to be unique within one screen's tree (SPEC §4.2): the update channel, the
override store and now `update` (B-63) address nodes by it, and with a duplicate the target is undefined.
The deals page breaks that: its root column is `deals` (`Frame.page("deals", …)`) and so is the «Deals of
the day» grid on its first page. B-63 worked around it by looking for the section through its header
(`DealsScreen.TODAY`). Nothing checks the rule, so the next duplicate would also go unnoticed.

- **Fix the deals page** so each id appears once. Prefer renaming the page's root, which nothing addresses,
  over the grid that tests, fixtures and the e2e look up on home and deals alike; record what was renamed.
- **Guard the rule:** one server test draws every page the storefront serves — home, categories, a
  catalog page with filters, search, deals pages 1 and 2, a product, the cart empty and with lines, the
  account pages, an order — as a guest and as a signed-in customer where the page differs, plus every
  `update` answer B-63 sends, and fails on any id that appears twice, naming the page and the id.
- AC: the guard red on main (the deals page) and green after; no other duplicate, or each found one fixed
  and listed; goldens and wire fixtures unchanged unless an id they carry changed; the e2e green.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/testing/HaulTestApp.kt`.

## Findings (2026-10-09)

- **The guard**: `server/src/test/kotlin/io/github/youndie/haul/shell/UniqueIdsTest.kt`, two tests. A guest opens
  home, the catalog's root, `/c/electronics`, `/c/headphones`, `/c/mugs`, a filtered, sorted, expanded second page
  of electronics, a filter with no results, a search with results and one without, deals pages 1 and 2, a product
  on each of its four tabs, and the cart empty and with the lines the presses put in it. Maya (signed in, Plus, an
  order placed) opens all of those plus the account, the orders' history, the Saved list, checkout and the order's
  page. On each page every `load` is followed and the first «+» or «Add to cart» answered with an `update` is
  pressed; every `update` is checked twice, its nodes together and the page drawn after them. The walk is over the
  decoded tree by reflection, not over `all()`: `all()` does not descend into the Saved list (`SavedList`'s cards
  and pagination), which the walk reaches. A tree a `present` shows is checked as a tree
  of its own.
- **Red on the item's base** (112e091 + this test), both tests, the guest's: `/ui/c: «categories» ×2, /ui/deals:
  «deals» ×2, /ui/deals › «+» …?answer=card: the page after it: «deals» ×2, /ui/cart: «cart» ×2, /ui/cart: «cart»
  ×2`; the customer's adds `/ui/account`, `/ui/account/orders`, `/ui/account/saved` and every update of their parts
  (`«account» ×2`), and the order's page (`«order» ×2`).
- **Five duplicates, each the page's root named after a section it draws; the roots renamed**, since nothing
  addresses a root and the sections are what tests, fixtures, the e2e, the parts and the live channel look up:
  `deals` → `deals-page` (today's deals grid), `categories` → `categories-page` (the catalog's root and its grid of
  every category), `cart` → `cart-page` (the cart's body), `account` → `account-page` (the account's and the Saved
  list's body), `order` → `order-page` (the order's body). Two of them were live defects, not only a rule broken:
  the account's parts (`GET /ui/parts/account/…`, B-63) found the root first (`Parts.node` looks at the root
  before its children), so their `update` was the whole page, header included, where the research table says
  `account`; and the order's live frame (`UpdateComponentMessage("order", …)`, B-29) named two nodes.
- **Fixtures**: the 21 wire bodies whose root changed carry the new id and nothing else (`cart_*`, `account_*`,
  `order_*`, `saved_*` — line 3 of each); the goldens are unchanged (`viddikVerify` green, no PNG in the change).
- **B-63's workaround removed**: `DealsScreen.TODAY` is today's grid (`deals`) again, as on the home page, and the
  deals' parts check looks for it rather than for its header (`deals-title`); the header's id is unchanged.
- **Mutation**: the deals root back to `deals` — the guest's and the customer's tests red, `/ui/deals: «deals» ×2`
  and the page after its «+»; restored, green.
- `Frame`'s KDoc states the rule and the `-page` names; the research document says what holds it, under B-63's
  table of parts.
- **Where it ran** (the Linux box, `MemoryMax=8G`, two runs): `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` — `:server:test` 387, `:composeApp:desktopTest` 188, `viddikVerify`, ktlint, all green;
  `scripts/e2e.sh` against the branch's image — `WholePathTest` green. The Mac: `ktlintFormat`, `make check`,
  `make docs-against BASE=origin/main`.
