---
id: B-102
title: "server: the catalog's filters show exactly what is applied"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-102 — server: the catalog's filters show exactly what is applied

Found in the QA pass of 2026-10-10 (v0.3.0):
- the applied chips leave out «Delivery: Tomorrow» and the rating, but `filterCount` counts them: the phone shows
  «Filters 2» / «2 applied» over one chip (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt:327-349`);
- «Clear all» is link-coloured with nothing applied; pressing it reloads the same address or silently drops the kind
  and the page (`CatalogScreen.kt:102-105, 445`);
- a top-level category draws its first subcategory's kinds: Electronics shows «Gaming, On-ear, Over-ear, Studio, True
  wireless» (headphone kinds) over speakers, TVs and projectors;
- pressing the selected kind chip, sort entry, history chip or «Any rating» sends a pointless `load`.

- AC: every applied filter has a chip and is counted once; «Clear all» only when something is applied; a top-level
  category's chips are its subcategories (or none); selected entries are inert; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Browse.kt`.

## Findings (2026-10-11)

- **Counted from the chips.** The applied chips now include «Delivery: Tomorrow» and the rating («Rating: 4.5 and
  up», the facet's own words); `AppliedFilters.filterCount` is the number of chips (`Filters.count`, which counted
  two filters that had no chip, is gone), so the phone's «Filters N», the sheet's «N applied» and the chips agree.
  Each new chip removes its filter alone.
- **«Clear all» only when a chip is there.** The server sends `clearAction` only then (the empty state's «Clear
  all» likewise), and the client draws «Clear all» — beside the chips at 1440 and on a phone, and in the filter
  sheet — only while `filterCount > 0`. The client keys on the count rather than the action because the
  screenshot bodies carry no actions; the goldens all have filters applied, so none changed. **Decision: «Clear all»
  keeps the kind** (`CatalogUrl.cleared`): the kind is the pill row above the results, not an applied chip, so
  clearing the chips leaves it, as it leaves the sort. It still goes back to the first page, as any filter change does.
- **A category with subcategories offers them.** Its row is its subcategories in their order, each a `navigate` to
  its page, none selected and no «All» (the AC's «its subcategories (or none)»); a leaf keeps its kinds with «All».
  This covers the middle level too: `/c/electronics/audio` drew headphone kinds over speakers the same way, and now
  offers «Headphones» and «Speakers». **Decision: a `kind` the row does not offer is not applied** — any kind on a
  category with subcategories, or one a leaf has no product of (`?kind=Bogus` used to empty the page with nothing
  saying why) — so nothing filters a page without a chip or a selected pill; the page's own links drop it.
- **Selected entries are inert.** The selected kind (or «All»), the sort shown, the rating ticked («Any rating» with
  none) and the orders history's selected chip carry no action; the client already drew a `null` action unpressable
  (`follows`, `LinkMenu`), so no client change was needed for them. A ticked checkbox, pill, swatch or the delivery
  toggle keeps its action: pressing it unticks.
- **Out of scope, left as they are**: the search's chips and sort (`SearchScreen`, B-77/B-72) have the same pattern —
  «All» selected and the sort shown still load the page they are on — and the Saved list's selected filter chip
  loads its first page; B-103 is on the search panel now. `research-architecture.md` records the rule («No press
  loads the page it is on») and the `kinds` part's new content.
- **Tests** — `AppliedFiltersTest` (server, 5): every filter applied has a chip and the count is the chips', the two
  new chips' removals; «Clear all» absent with nothing applied (a kind alone, a sort and a page), present and keeping
  the kind with a chip, and the empty state's the same; Electronics and Audio offer their subcategories and ignore a
  kind; what is chosen carries nothing; and every `load` on five category pages answers a different page than the
  one it is pressed on. `ClearAllTest` (client, 2): «Clear all» drawn only with a filter applied, wide, phone and the
  sheet. Changed: `DrawnActionsTest` (clear all keeps the kind and leaves nothing to clear), `CatalogRoutesTest` (the
  rating ticked is the B-75 exception), `AccountRoutesTest` (the selected history chip carries nothing), and
  `account_orders.json`, which `AccountFixturesTest` holds to the server's tree (its «All» chip lost its action; drawn
  the same).
- **Mutation**, on the committed change, each red, restored, `git status` clean: the whole server and client change
  reverted (all five `AppliedFiltersTest`, the three changed server tests and both `ClearAllTest` red); the sort shown
  given its action back and the kind applied on any category (the no-reload, chosen and subcategory tests red, the
  others green).
- **Goldens**: none changed.
- **Where the checks ran**: on the Linux build machine — `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` (443 server and 329 client tests, 0 failed; `viddikVerify` and ktlint in it);
  `scripts/e2e.sh` (the whole path and the shoppers, green). After rebasing onto B-99 (#120), which changed the seed:
  `:composeApp:wasmJsBrowserDistribution` and `check :server:installDist` again (449 server and 333 client tests,
  0 failed). `make check` and `make docs-against BASE=origin/main` on the Mac.
