---
id: B-37
title: "server + client: actions the trees draw but do not carry"
status: wip
priority: P2
size: M
stage: stage-4-cart
blocked_by: [B-35]
---

# B-37 — server + client: actions the trees draw but do not carry

B-35 wired every action the contract gives; walking the screens showed controls that are drawn but
carry nothing to follow, so they do nothing when pressed: pagination and «Show 24 more», the sort,
«Clear all» on applied filters, the header's category row, «Catalog», «Deals» and «Cart», a card's
«+» (add to cart — the cart route exists since B-11), and «Clear» on recent searches (planned for
B-12, not built: `DELETE /api/v1/me/recent-searches`). «View all deals» points at `/deals`, which has
no screen.

For each: the server puts the action in the tree (a navigate to an address the shell maps, or a
command URL like the cart's), the client follows it. A control whose screen belongs to a later item
(the heart → Saved, B-20; «Orders» → B-18) gets no action here and is listed in that item instead.

- AC: each control above changes what the shopper sees, by a route test (the action is in the tree)
  and a client wiring test (pressing it follows the action); `/deals` either draws a screen or is
  no longer linked.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/`.

## Findings (2026-10-08)

- **Built, control by control** (the contract in `shared/.../ui/`; the server's trees in
  `shell/Frame.kt` and `feature/*/screen/`; the client's views follow through `LocalHaulActions`):
  - **Pagination and «Show 24 more»** — `HaulPagination.moreAction` (the next page) and `links` (every
    page number but the current one and `…`), built once in `Cards.kt`'s `pagination()` for the
    category, search and deals pages; the address keeps the filters, the sort and the query, and page
    1 has no `page=`. «Show 24 more» opens the next page — the same address as its number — rather than
    appending in place, which would need the shell to keep the scroll across an address (research D2,
    «Decided in B-37»).
  - **The sort** — `AppliedFilters.sorts`, one `Link` per order (`Sort`), the filters kept and the page
    reset; the control opens them as a menu (`ui/LinkMenu.kt`).
  - **«Clear all»** — `AppliedFilters.clearAction`, the category without filters, the sort kept; drawn
    in three places (wide, phone, the filter sheet), all three follow it.
  - **The header** — `HaulHeader.catalog` (every top-level category with its page: «Catalog» opens it
    as a menu, and each word of the category row follows the entry of its name), `deals` (`/deals`),
    `cart` (`/cart`). The row's first ten are cut in `Frame` now; `navigation()` returns `Link`s.
  - **A card's «+»** — `ProductCard.add`, a `LineCommand` (`shared/.../feature/cart/CartCommands.kt`):
    `PUT /api/v1/cart/lines/{sku}` with the line's *next* quantity, for the SKU whose price the card
    shows (the deal's SKU on a deal card). The server knows the viewer's cart (`Viewer.inCart`, read in
    `Viewers` from the cart's lines; `CartRepository.units` went with it), so «+» is absent at ten, at
    the stock and out of stock, and a press sent twice adds one. The client sends it and draws the
    screen again; the header's count moves.
  - **«Clear» on recent searches** — `SearchSuggestPanel.clearUrl` (it replaces `clearAction`, never
    set), present only when the customer has recent searches; `DELETE /api/v1/me/recent-searches` in
    the customer tier (`customerSearchRouting`), `refresh`; the shell sends it and asks for the panel
    again.
  - **«View all deals»** now carries `/deals` (it carried nothing; «Shop the sale» and the empty cart
    already pointed there).
- **`/deals` is a screen** — the owner's call taken here (research D2): `DealsScreen`, `GET /ui/deals`
  (`?page=`, `400 validation_failed` below 1): «Deals · N items on sale», today's deals with the
  countdown on the first page, then every product whose shown price is under its old one, the deepest
  discount first, 24 a page, footer. Built from the components other screens draw; **no artboard draws
  it**, so it has no parity reference and no golden. It reads the whole catalog per request (some
  3,000 products on the seed) the way a top-level category page reads its descendants; research D3's
  in-memory note covers both.
- **Where B-13 folds in the «+»:** `composeApp/.../feature/cart/AddToCart.kt` — `AddToCart` /
  `LocalAddToCart` and `HaulCommands.changeLine(LineCommand)`; the storefront provides it in
  `Shown` (`shell/Storefront.kt`). With B-13's `CartCommands` it is `CartCommand.ChangeLine(command.url,
  command.change)` sent through `LocalCartCommands`, and this file goes. Both run over the same
  `Identity.send`; B-37's transport for commands is `HaulCommands`/`ktorCommands` in
  `shell/Transport.kt` (`App` and `Storefront` take it after the transport).
- **`/cart` needed no mapping**: every address the shell has no kind for is asked of the server at
  the same path under `/ui` (`Address.screen`), so the cart button loads `/ui/cart`; `AddressTest`
  says so. B-13's `PageKind` for the cart, if it adds one, is a loading state, not the route.
- **Menus have no artboard.** «Catalog» and the sort open a menu drawn from the theme's tokens
  (surface, outline, Archivo 15); the canvas draws both controls closed only, and closed is all a
  screenshot draws (no handler, no menu).
- **Still drawn and inert, owned elsewhere or by nobody** (not filed, per the assignment): the
  heart and «Saved» (B-20) and «Orders» (B-18) — noted in those items; the product page's «Add to
  cart» and «Buy now»; home's «All N categories»; the brand facet's «Show N more»; the filter sheet's
  «×»; a recent search's own row (the client would have to build `/search?q=` from a string the tree
  gives without an action); the strip's «Sell on HAUL», «Help», the language, the «HAUL PLUS» pill;
  the footer's links; the Plus block's offer.

## Iteration 1 (2026-10-08)

- **Done:** the contract, the server's trees and routes, the client's wiring, and the tests for every
  control: `server/src/test/.../feature/catalog/DrawnActionsTest.kt` (10: the header on three screens
  and a row word followed; pages and «Show 24 more» with the filters kept and none on the last page;
  the sort's five orders, followed, prices ascending; «Clear all» keeping the sort; search's pages;
  «+» at 1, 2, gone at 10; none out of stock; home's deal links and the deal SKU; the deals page;
  `page=0` refused), `feature/search/RecentSearchesRoutesTest.kt` (2, against shildik: clear empties
  and the panel stops offering it; `401` without a token, a guest offered nothing), `KoinGraphTest`
  (`DealsScreen`, `RecentSearches`); client `DrawnActionsTest.kt` (9: «Show 24 more» and a page
  number, the sort menu, «Clear all», a row category, the «Catalog» menu, «Deals» and the cart, «View
  all deals», «+» sending `PUT … {"quantity":2}` and redrawing without navigating, «Clear» sending
  `DELETE` and asking for the panel again), `AddressTest` (`/cart`). Those suites passed on the Linux
  build machine (server 13 of 13, client 26 of 26 with `StorefrontTest` and `AddressTest`);
  `make check` green on the Mac.
- **Stopped by:** the build machine stopped answering (SSH times out in the banner exchange, `wsl`
  on the host reports the guest running but the service does not respond) while the full gate —
  `check :server:installDist :composeApp:wasmJsBrowserDistribution`, with `viddikVerify` — was
  running, before any result. Not restarted from here: the guest is shared with the other items'
  agents.
- **Left:** the full gate (the whole server suite, `viddikVerify`, the parity run), the mutation
  checks, and then `done`.
