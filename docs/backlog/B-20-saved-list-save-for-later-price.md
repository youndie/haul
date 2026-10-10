---
id: B-20
title: "server + client: Saved list, save for later, price drops"
status: done
priority: P2
size: M
stage: stage-6-account
epic: feature-account
blocked_by: [B-01, B-13, B-19]
---

# B-20 — server + client: Saved list, save for later, price drops

The Saved list holds hearted products and lines saved for later, and marks price drops.

Feature: `feature-account` — its scenarios are this item's acceptance where it names them.

- Not covered: price-drop notifications outside the app.

- AC: parity for every `Saved_*`; the price-drop scenario passes.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/saved/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/`.

- **From B-37:** the heart on a product card and the header's «Saved» shortcut are drawn and carry no
  action; they get one here — the heart a command fixed in the tree like the card's «+»
  (`ProductCard.add`, a `LineCommand`), «Saved» a `navigate` in `HaulHeader`.

- **From B-19:** the account reads the Saved list's counts through `SavedLists`
  (`server/src/main/kotlin/io/github/youndie/haul/feature/account/domain/Account.kt`), bound in `AccountModule.kt`
  to `SampleSavedLists` (Maya's 48 saved and 6 price drops, research §6). Bind it to the Saved list here; the menu's
  «Saved» (`AccountMenuItem`, no action yet) gets a `navigate` to `/saved` once that is a `StorefrontPage`.

## Done (2026-10-08)

- **The decisions** are research D6, «Decided in B-20»: one list per customer of products, each at its price on the
  day of the first save (a second save changes nothing); «Save for later» moves the line out of the cart into the
  list; a product has dropped when its cheapest SKU in stock is below that price, the mark the difference; the page is
  `/account/saved` with `?filter=price-dropped` and `?page=2`, newest first, 24 to a page; the heart is a command
  fixed in the tree; Maya's 48 saved and 6 price drops are seeded rows.
- **The contract** (`shared`): `SaveCommand` (`feature/saved/SaveCommand.kt`: the URL and the state a press
  leaves); `ProductCard.heartCommand`, `.heartAction` (a guest's sign-in) and `.drop` («Price dropped −$200»);
  `ProductDetails.heartCommand` and `.heartAction`; `CartLine.saveUrl` and `.saveAction`; `HaulHeader.saved`;
  `AccountBody.count` (the pill beside the title) and `.saved`, a `SavedList` (`ui/SavedComponents.kt`: the filter's
  chips — the history's `HistoryFilter` — the cards, the page numbers, or the empty state and its three
  `SavedStep`s, or the sentence for a filter that matches nothing); `StorefrontPage.Saved`.
- **The server** (`feature/saved/`): `saved_items` (`V18__saved.sql`, the primary key the save is written against),
  `ExposedSavedItems`; `SavedCommands` (save at the cheapest price in stock, `404 product_not_found` for a product
  the catalog does not have; let go; save for later, `404 line_not_found` for a SKU the cart has no line of);
  `SavedListing`, which reads the list against the catalog now and is the account's `SavedLists`; `SavedScreen`, the
  list's part of the account's body, built by `AccountScreen` for `AccountPage.Saved`. Routes in the customer tier:
  `GET /ui/account/saved`, `PUT` and `DELETE /api/v1/me/saved/{productId}`, `POST
  /api/v1/cart/lines/{skuId}/save-for-later`, each command answered `refresh`. `Viewer.saved` (read by `Viewers`)
  draws every card's and the product page's heart filled with the command that lets it go, any other a customer's
  command that keeps it, a guest's the way to sign in; the cart's lines carry «Save for later» the same way; the
  header's «Saved» and the menu's go to the list (`Frame.SAVED`). Maya's list is seeded (`seed/SampleSaved.kt`,
  `SeedDigest` covers it); `SampleSavedLists` is gone.
- **The client**: `feature/saved/SavedViews.kt` (the chips, the grid of four — two on a phone — the page numbers;
  the empty state, the saved-heart tile ringed in Cobalt and the three cards), drawn inside `AccountBodyView` with the
  title's pill; the heart filled in Hot (`HaulIcons.heartFilled`) and pressable on cards and the product page
  (`rememberHeartPress`, through the cart's command seam: `CartCommand.Heart`, `CartCommand.SaveForLater`); the
  «Price dropped» mark on a card; `PageKind.Saved` with the shell's `SavedLoading` and «Saved didn’t *load*»
  (`SavedError`), sharing the account's placeholder frame (`AccountPageLoading`).
- **Parity** (`viddikDesignParity --component "Saved*"`, references rendered on the Linux box with grayscale text,
  tolerance untouched), all ten within 5 %, wide / phone: Loading 0.37 / 0.22, Content 1.75 / 3.28, PriceDrops
  1.64 / 3.22, Empty 2.09 / 3.53, Error 1.53 / 2.62. Two rounds: the steps' titles measured with the browser's
  sub-pixel width (Empty 2.23 → 2.09: «Tap the heart on any product» broke onto two lines in a card Compose gives a
  whole pixel less). What is left is glyph rasterisation and rows a pixel apart, the cart's and the account's floor
  (B-13: Content 1.69 / 3.17). Goldens recorded for `Saved*`, and `ProductCard_Grid_Phone` re-recorded: its Bose
  card is `saved` in `product_cards.json`, and the saved heart is now filled, as every `Saved_*` artboard draws it
  (`ProductCard_Grid` stays within its tolerance and was kept).
- **Tests written**: `SavedRoutesTest` — feature-account's «Saved twice» (`a product saved twice is in the list
  once`) and «Price drop counted» (`a product cheaper than on the day it was saved is counted and marked`: the
  scenario's Robot Vacuum S8 at $499 saved, then $299 — the account's tile from 6 to 7, «Price dropped −$200» under
  «All» and «Price dropped», no drop once out of stock), letting go twice, the address's paging and filter, a
  customer sees only their own list, save for later moves a line, an unknown product, the hearts and «Saved» for a
  customer and a guest, the sign-in tier; `SavedFixturesTest` (the three client bodies are the server's trees, the
  cards the canvas's in the server's shape); `SchemaTest` for `saved_items`; `StorefrontPageTest`, `WebBundleTest`
  (`/account/saved` reloads as the page), `KoinGraphTest`; in the client `SavedWiringTest` (a saved heart sends its
  `DELETE`, an unsaved one its `PUT`, a guest's goes to sign-in; chips and pages follow their addresses; «Browse
  deals»; «Save for later» sends the line's `POST`, a guest's goes to sign-in), `AccountWiringTest` (the menu's
  «Saved»), `AddressTest`, ten `Saved_*` goldens. The account's, cart's and order's client bodies follow the header's
  «Saved» and the lines' «Save for later»; the empty cart's picks carry their hearts.
- **Mutations**, each seen failing and restored: a drop counted with nothing in stock and save for later leaving the
  line in the cart (the price-drop and the save-for-later tests); a second save overwriting the first and the heart
  always saving (`Saved twice`, the hearts and the paging tests); the list read without its customer filter (`a
  customer sees only their own list`); the client heart sending the opposite state and a guest's «Save for later»
  following nothing (three `SavedWiringTest`s).
- **Where it ran**: the Linux build machine (WSL), on the branch rebased onto `5859c32` (B-21's returns, B-26's
  e2e), two workers in a 5 GB scope: `:composeApp:wasmJsBrowserDistribution` alone, then `./gradlew check
  :server:installDist` green (`:server:test` 247 tests, `:composeApp:desktopTest` 141, `viddikVerify` 132 goldens,
  0 failed; PostgreSQL and shildik in containers); `scripts/image-check.sh haul/server:b20` on port 18120 green (V17
  and V18 migrated and seeded, 912 of 912 classes from the AOT cache); `scripts/e2e.sh` against that image, its
  whole path passing with every card now carrying a heart. `make check` and `make docs-against BASE=origin/main` on
  the Mac. The chart is unchanged.

## Findings (2026-10-08)

- **The canvas's pager reads «1 2 2»** on `Saved_Content` (both widths): a typo. 48 saved are two pages, drawn
  «1 2»; the third box is about 0.15 % of each artboard.
- **`Saved_Empty` keeps «Saved 48» in the menu** (the tab on a phone) over an empty list; the server draws an empty
  list without a count, so the menu and the title agree with the list.
- **The canvas's Saved cards are not the seed's products** (robot vacuum, keyboard, coffee set, …): the bodies keep
  the canvas's cards, the way the empty cart's picks are kept (B-13), and `SavedFixturesTest` checks each is shaped
  like a card the server writes on that page. The page the stand shows Maya is her seeded list, not the canvas's.
- **The addresses in the assignment** said `/saved`; the screen document, the endpoint document (`/ui/account/saved`),
  the canvas's note on `Saved_Error` and B-19's own `StorefrontPageTest` (which listed `/account/saved` as «not yet a
  page») all name `/account/saved`, and the page is drawn as an account page; `/account/saved` was taken.
- **Saves in one instant tie**: the store's clock is the canvas's «now» in the tests, so two saves in one test have
  one `saved_at` and are ordered by product id; on the stand the clock moves. No sequence column was added for it.
- **The migration's number**: `V18__saved.sql` as assigned, after B-21's `V17__returns.sql`, which merged first — in
  order (there is no V13 or V16 file; Flyway runs without `outOfOrder`, so the order the numbers merge in matters, and
  B-23's V19 comes after this one).
- **Not covered**: price-drop notifications outside the app (the item's own exclusion); a guest's hearts are kept
  nowhere — after sign-in the page is drawn again and the heart has to be pressed again.

