---
id: B-49
title: "server + client: the last drawn controls without actions"
status: done
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-37]
---

# B-49 — server + client: the last drawn controls without actions

B-37 gave most drawn controls their action and listed the rest as «owned by nobody»: home's «All N
categories», the brand facet's «Show N more», the filter sheet's «×», a recent search's own row, the header
strip's «Help» and «HAUL PLUS» pill, and the footer's links. A shopper presses each and nothing happens.

Decided as product owner: «All N categories» → the catalog root; «Show N more» expands the facet in place
(a `navigate` with the facet expanded, keeping the filters); the sheet's «×» closes it; a recent search's
row runs that search (the tree carries the `/search?q=` action, the client builds nothing); «HAUL PLUS»
opens the Plus offer (B-23's trial when it exists, else `/account`); «Help», «Sell on HAUL», the language
and the footer's links stay inert and are drawn as text, not as links, until a page exists for them.

- AC: each listed control either carries an action followed by the client (route + wiring test per
  control, as `DrawnActionsTest` does) or is drawn as plain text; goldens unchanged unless a control's look
  changes, and then re-recorded and explained.
- Anchors (planned): `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt`.

## Findings (2026-10-09)

- **Built, control by control** (the contract in `shared/.../ui/`; the trees in `shell/Frame.kt` and
  `feature/*/screen/`; the client's views follow through `LocalHaulActions`):
  - **«All N categories»** — `SectionHeader.action`, `/c`. **The catalog's root did not exist**: there
    was no page listing every category (home draws eight, the header's row ten), and `StorefrontPage`
    refused `/c`. Built here, as B-37 built `/deals`: `StorefrontPage.Categories` (`/c`, drawn as
    `PageKind.Other`), `GET /ui/c` (the category route with no path, `CatalogScreen.root`) — «Home /
    Catalog», «Catalog · 32 categories», every top-level category as home's tiles in the header's order,
    footer. No artboard draws it, so no parity reference and no golden.
  - **The brand facet's «Show N more»** — `Facet.moreAction`, the same page with `expand=brand`, the
    filters, the sort and the page kept. The expansion is kept on every address the page builds (a brand
    ticked, the sort, a page, «Clear all»), so the list does not fold under the shopper's finger; an
    expanded facet has neither label nor action; any other `expand` is `400 validation_failed`.
  - **The filter sheet's «×»** — the sheet's `onClose`, **client-local**: the sheet is the client's own
    (`FilteredResultsRenderer`'s `remember`), the facets already in the tree drawn over the page, so
    there is no address to change and nothing for the server to decide. **«Filters» did not open the
    sheet either**: `onOpenFilters` was passed down to `CompactApplied` and never pressed, so the sheet —
    and its «×» — could not be reached at all; wired here because «×» cannot be verified without it.
  - **A recent search's row** — `SearchSuggestPanel.recent` is now `List<Link>` (it was `List<String>`),
    each the query and its `/search?q=` (encoded); the client follows it and builds nothing.
  - **«HAUL PLUS»** — `HaulHeader.plus`, on every page with the header: B-23's `present` of
    `PlusOffer.dialog` for a customer who is not a member (the block's own `PlusOffer.trial`), sign-in
    for a guest, `/account` for a member (`Customer.plus`, the flag a trial sets; the account draws the
    membership there).
  - **«Sell on HAUL», «Help», the language, the footer's links** — already drawn as plain text (no
    `clickable`, no link annotation); kept so, with a client test that says it and a KDoc saying why.
- **Wire changes.** `HaulHeader.plus`, `Facet.moreAction` are additions with defaults; `recent` changed
  type, so `search_suggest.json` gained the `Link` shape. The 20 account, cart, order and Saved bodies (B-24's `order_haul_pay.json` among them, after the rebase over it)
  the fixture tests hold equal to the server's trees gained the header's `plus`, taken from what the
  tests wrote — additions only, nothing drawn changed. Those tests built Maya's header from a `Viewer`
  without its `customer`, which no request has (`Viewers` always sets it), so she read as a non-member
  and was offered the trial; their viewers now carry the customer, and the bodies say `/account` for
  her, the dialog for Sam and Jordan, sign-in for the guest.
- **Decided here** and written into research D2 («Decided in B-49»): `/c` is a screen; the filter sheet
  is the one control wired in the client alone; a control with no page behind it is plain text.
- **Not done here, noticed on the way.** In the phone's sheet a facet press is a `navigate`, and the
  shell keys the page on its address, so the sheet closes after every tick (and after «Show N more»);
  the sheet's «Show N items» button carries nothing, though «×» now closes the sheet the same way it
  would. Neither is in this item's list.

## Verification (2026-10-09)

- **Tests**, per control a route test (the action in the tree, followed) and a client wiring test
  (pressed, followed): server `feature/catalog/DrawnActionsTest.kt` (+4: «All N categories» to `/c` and
  its tiles, followed to a category; «Show N more» with the filters, sort and page kept, every brand
  listed, kept expanded after a tick; `expand=colour` refused; a guest's pill is sign-in on four pages),
  `feature/search/RecentSearchesRoutesTest.kt` (+1, against shildik: the row is
  `/search?q=stoneware%20mug`, followed to its results), `feature/membership/MembershipRoutesTest.kt`
  (+1: Sam's pill presents the block's own dialog on three pages, Maya's opens `/account`, Sam's after
  his trial too, and that account draws the membership), `StorefrontPageTest`, `WebBundleTest` (`/c` is a
  page, `/c/` is not); the four fixture tests' viewers now carry their customer, so the bodies say what
  each viewer's pill is. Client `DrawnActionsTest.kt` (+7: «All 32 categories», «Show 3 more», the
  sheet opened by «Filters» and closed by «×» with no request, a recent row, «HAUL PLUS» presenting the
  dialog and opening a member's account, the strip and the footer with no click action while the pill
  has one), `AddressTest` (`/c`).
- **Mutations**, each seen failing the tests written for it (three batches, then restored, the tree
  clean): no action on «All N categories», no `moreAction`, `Link`s without an action, no `plus` in the
  header — the five new server tests; the expansion not kept on the page's addresses — «Show N more»'s;
  the facet's label, «Filters», the recent row and the pill not followed — the six client tests they
  guard; then «×» not pressable alone (the sheet's test) and «Help» made pressable alone (the
  plain-text test). The other client tests passed under each.
- **Goldens**: none re-recorded and none changed; nothing drawn changed (no indication on any press).
- **The gate** on the Linux build machine, after the rebase over B-25, B-52/B-53's filing and B-24:
  `:composeApp:wasmJsBrowserDistribution`, then `check :server:installDist` green — server 311 tests,
  client 161, `viddikVerify` 136 cases with 0 failing (B-24's `Order_HaulPay` and `_Phone` among them,
  unmoved by the header's `plus`); `scripts/e2e.sh` (the whole path against the image) green; `make
  check` on the Mac.
