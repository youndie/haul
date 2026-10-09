---
id: B-63
title: "server + client: a filter answers with the parts that changed"
status: done
priority: P1
size: M
stage: stage-9-ship
blocked_by: [B-62]
---

# B-63 — server + client: a filter answers with the parts that changed

Since B-62 a press within a screen keeps the page, but the server still sends the whole page for it.
A facet tick on the catalog changes the title, the facet counts and the results; the header, the
breadcrumbs, the promo and the footer are built and sent again. «Add to cart» answers `refresh`, so the
whole page comes back to change one number in the header.

kompot 0.40.0.213 (kompot B-82) adds two actions:
- `update { updates, deeplink?, history? }` replaces nodes by `id`, and hands the new address to the app;
- `load { url }` is a `GET` (endpoint kind `load`) answered with an action.

On the client, `withUpdates(overrides, onAddress)` and `withLoad(scope, state, load)` sit in the handler
chain; on the server, `kompotUpdate { … }` / `respondKompotUpdate`.

- **Decided as product owner:** presses that only filter, sort or page what a screen already shows carry
  `load`. That covers facets, the sort, «Show N more» and the phone filter sheet's ticks on every screen
  that has them. They are answered with `update` for the nodes that change, plus `deeplink` with `push`,
  so back, reload and a shared link show the same state. The address keeps opening the whole page as
  today. «Add to cart» and the other actions that change one number elsewhere on the page answer with
  `update` for that node (and their message), not `refresh`. The progress line (B-62) shows while a
  `load` is in flight.
- An answer that cannot be partial (the address no longer exists, the category moved) answers `navigate`.
- Ids of the replaced nodes stay stable between the whole page and the parts. A test holds that the parts
  equal the same nodes of the whole page for the same address.

- AC: on the catalog, ticking a facet sends one `GET` to a `load` endpoint whose answer is an `update`
  (not the page). The title, counts and results change, the address changes and back restores the
  previous one. The whole page at the new address equals the page drawn after the update (server test
  over every node replaced). «Add to cart» updates the header's count without a page request. Client
  wiring tests, server route tests, the e2e green, the endpoint documented with its kind.
- Anchors: `gradle/libs.versions.toml`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/CatalogScreen.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.

## Findings (2026-10-09)

- **The presses that load.** Every press that only filters, sorts or pages a screen is kompot's `load` of the
  screen's address under `/ui/parts` (endpoint kind `load`, `server/.../shell/Parts.kt`), answered `update` of
  the nodes it changes, `deeplink` the address, `push`: the category page (a kind, a facet, «Show N more», an
  applied chip, «Clear all» — the chips' and the empty state's —, a sort, «Show 24 more» and the page numbers:
  `title`, `kinds`, `results`), the search (a category chip, a page: `categories`, `grid`, `pagination`), the
  deals (a page: `grid`, `pagination`), the orders' history (a chip) and the Saved list (a filter, a page): the
  account's body, `account`. The endpoints are listed with their tier in research D2, «Decided in B-63»; Haul
  has no OpenAPI and no endpoint-kind check to keep green.
- **The parts are cut from the page the address opens**, built by the same code as `GET /ui<address>`, so they
  cannot drift from it; what is spared is the bytes and the frame's redraw, not the building (the header is
  one cheap node once the viewer is read). The search's parts do not record the search again. **Cannot be
  partial → `navigate`**: a category or a search category no longer there, and a page of the deals that draws
  «Deals of the day» — an `update` replaces nodes, it cannot add or remove a section — so the deals' pages load
  in place only between pages past the first, and a press to or from the first is still `navigate`.
- **Bytes, one facet tick** (uncompressed JSON, the seed at the canvas's «now», measured in the server's test
  application): `/c/headphones?brand=Sony` — the page 11,336 B, the `update` 8,216 B (−28 %); a search
  category chip (`/search?q=everyday&category=…`) — 19,278 B against 16,425 B (−15 %). The results — the
  facets and 24 cards — are most of a page, so what a filter saves is the frame: the header, the breadcrumbs,
  the footer, and their redraw.
- **`onAddress` and the Navigator.** `Navigator` tells a *visit* (`open`, `arrived`: a page to load, counted by
  `visits`) from a *recorded* address (`record`: an `update` already drew it — `push`es or `replace`s the
  history, moves `address`, loads nothing; `BrowserHistory.replace` is new, `replaceState` in the browser). The
  screen loader is keyed on the visit, not the address, so a recorded address is no load; the per-address state
  that was about loads (`loads`, `signedInFor`) is per visit now. Back and forward to a recorded address are
  visits and load its page behind the drawn one (B-62). The line under the header shows while the loader's or
  the screen's `KompotLoadState` is loading; a `load` that does not arrive draws B-62's notice, whose Retry presses
  the same `load` again; a `401` takes the page down as a refused refresh does.
- **A kompot defect, worked around in Haul.** An equal tree arriving drops no override (`NodeOverrides.kt:23-24`:
  «an equal tree is not news»). Back to the address before a tick loads exactly the tree the screen started
  from, so the tick's `update` stayed drawn at the old address — the page differs from the address, which
  §16.4.5 exists to prevent. Haul gives the override store a new instance on every page that arrives
  (`arrivals` in `Storefront.kt`). Minimal reproduction, in kompot's desktop tests:

  ```kotlin
  val overrides = KompotNodeOverrides()
  var tree by mutableStateOf<KompotComponent>(ColumnComponent("page", listOf(TextComponent("t", "before"))))
  setContent {
      CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) { KompotScreen(tree, registry, forms, handler) }
  }
  overrides.override("t", TextComponent("t", "after"))                      // what an `update` does
  tree = ColumnComponent("page", listOf(TextComponent("t", "before")))      // back: that address's page, equal
  // draws «after»; the address's page says «before»
  ```

  Mutation C2 below is the same defect in Haul: without the new store, back draws the tick's results.
- **«+» and «Add to cart» moved from `refresh` to `update`** (`feature/catalog/screen/LineAnswers.kt`): a card's
  «+» on the category, the search, the deals, home's deals and «Picked for you» — the header and the card — and the
  product page's «Add to cart» — the header and the buy box. The tree writes which node into the command's address
  (`?answer=card`, `?answer=details`, the search's `&q=` so the header keeps it); a card is the same from its
  product and SKU wherever it is drawn. The client drew a grid's cards straight from the grid, past the
  registry, so an `update` of one card's id reached nothing: grids draw their cards as nodes now
  (`LocalCardNodes`, `HaulRenderers.kt`), the same pixels — `viddikVerify` green, no golden changed. **Still
  `refresh`**: «Buy now» (its `next` is followed), the cart's own lines, promo and removals (the whole cart moves),
  the Saved list's «+» (its «Price dropped» mark is that page's own), the heart (on the Saved list it removes the
  card), «Helpful» (its node needs the page's SKU and tab, which the command does not carry), checkout's choices,
  the dialogs' `close` + `refresh` and the Plus trial (a member's prices change everywhere). None of them has a
  message to send in a `sequence`.
- **B-54's sheet, simplified, still held by the shell.** Its presses are `load`s answered by `update`s, so it stays
  open by itself and is drawn from the updated results (the renderer hands them over as before); `pressedTo`, `at`,
  `following` and `settled()` are gone. What stays in the shell: a *visit* closes it — only the shell tells a visit
  from a recorded address, and a sheet in the renderer's `remember` would stay open on back, which keeps the page —
  and its facets follow nothing while the screen's `load` is on its way, read from the same `KompotLoadState`
  rather than bookkept: a second tick from the facets before the first answer would load without the first and,
  the last press winning, drop it.
- **Ids.** Every replaced node's id is the page's: the server tests hold the page after each `update` — the page
  before with every named node replaced, wherever it is drawn — equal to the page at the update's address, over
  every `load` of `/c/headphones`, of a filtered, sorted, expanded second page of electronics, of a search, of the
  deals' second page, of the orders' history and of the Saved list, and over «+» on four pages and «Add to cart».
  Found on the way, not changed: the deals page's root column and its «Deals of the day» grid are both `deals`
  (the parts check looks for `deals-title`).
- **Not handled**: a tab opened before the deploy runs the old bundle against trees that carry `load`, which it
  does not know (§16.4.8: a dead press) until it is reloaded; one image serves both halves, so it lasts until the
  next reload.
- **Tests.** Server: `PartsRoutesTest` (5), `LineAnswersTest` (5), a `load` test in `SavedRoutesTest` and in
  `AccountRoutesTest`'s history, `DrawnActionsTest`, `ProductButtonsTest` and `DealPriceTest` reading `load` and
  `update` where they read `navigate` and `refresh`; the account and Saved wire bodies the goldens draw from
  regenerated (`account_orders.json`, `saved_content.json`, `saved_price_drops.json`). Client: `InPlaceAnswersTest`
  (7: a tick is one `GET` with the line on and no page; back draws the page before and forward the tick's page; a
  tick that fails keeps the page under the notice and Retry presses it again; the last tick wins; `navigate` as an
  answer opens the address; «+» changes the header's count with no page and the second press sends the next
  quantity; the phone's sheet waits for a tick and follows the page again after one that failed), B-54's four
  sheet tests on `load`s, and KeptPageTest's two sheet tests moved there. The e2e ticks a brand on the stand's
  category (its `update`'s results equal the page at its address, no header in it) and reads «Add to cart»'s
  `update`.
- **Mutations** (each on the committed change, restored, `git status` clean): server — a facet `navigate` again —
  `PartsRoutesTest` 2 red; `kinds` not among the parts — 1; the header left out of a line's answer —
  `LineAnswersTest` 5; a card's answer without the search — 1; the deals' first page answered in parts — 1.
  Client — an `update`'s address *visited* instead of recorded — 8 red (`InPlaceAnswersTest` 4, `DrawnActionsTest` 4);
  one override store for the screen (the kompot defect) — 2; a grid's cards drawn past the registry — 1; no line for a
  `load` — 1; the sheet not waiting for a `load` — 2; no notice for a `load` that failed — 2.
- **Where it ran** (the Linux box, `MemoryMax=8G`, two runs): `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` — `:server:test` 385, `:composeApp:desktopTest` 187, `viddikVerify`, ktlint, all green;
  `scripts/e2e.sh` against the branch's image — `WholePathTest` green; `scripts/image-check.sh` — ready, 1059 of
  1059 classes from the AOT cache. The Mac: `make check`, `make docs-against BASE=origin/main`.

