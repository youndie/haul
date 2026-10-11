---
id: B-100
title: "client + server: the cart's controls look what they do"
status: done
priority: P2
size: M
stage: stage-11-qa
---

# B-100 — client + server: the cart's controls look what they do

Found in the QA pass of 2026-10-10 (v0.3.0):
- with nothing selected, «Checkout» / «Sign in to check out» is a live-looking blue button that does nothing
  (`checkoutEnabled=false`; `HaulButton` has no disabled look — `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt:131-162`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt:529-539`);
- «Delete selected» is link-coloured and dead with nothing ticked; the stepper's −/+ at 1 or at the limit are dead but
  not greyed (`CartViews.kt:133-151, 398-411`);
- «Save for later» removes the line with no message and no saved-for-later section;
- «Discount −$0.00» is drawn in sale red when nothing is discounted (cart and checkout; the order page leaves the row
  out); the promo row is inside «Discount», so the rows do not add up to the total;
- a cart line's link drops the SKU (the travel-case line opens «Headphones only»);
- «Buy now» with the product already in the cart adds one more unit and takes the whole cart to checkout.

- **Decided as product owner:** «Buy now» checks out that product alone at quantity 1 (or the quantity already in the
  cart, without adding) — record the choice.
- AC: per line above; tests; goldens where the look changes.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`.

## Done (2026-10-11)

- **Checkout with nothing ticked looks idle.** `HaulButton` has an `enabled` (`ui/Basics.kt`): off, it is drawn as the
  buy box's buttons out of stock are (B-75) — Paper's grey under a muted label, an outline filled to its edge so the
  button keeps its size — presses nothing and is disabled for assistive technology (`Modifier.inert`, `ui/Links.kt`).
  The cart's checkout passes `OrderSummary.checkoutEnabled` to it (`CartViews.kt`, `SummaryCard`).
- **«Delete selected» with nothing ticked** is muted (`outlineMuted`) instead of Cobalt, and inert; **the stepper's
  «−» at one and «+» at the line's limit** are muted and inert, the live step beside them unchanged.
- **«Save for later» says where the line went**: `POST …/save-for-later` answers `sequence[refresh, show_message]`,
  «Moved to your Saved list» with «View saved» → `/account/saved` (`feature/saved/SavedRouting.kt`, `MOVED`).
  **Decided: no saved-for-later section in the cart.** The Saved list is the one place a saved product is drawn
  (B-20 — hearts fill the same list), no artboard draws such a section, and a second drawing of the same list on the
  cart would be a second place to keep right; the message's button is the way to it.
- **The summary's rows add up** (`summaryRows`, `CartScreen.kt`, used by the cart and checkout): «Discount» is the
  sale alone, the promo code's row beside it, and a row that takes nothing off is not drawn — no «Discount −$0.00» in
  the sale red. The order page already left it out and is unchanged.
- **A cart line opens its own SKU**: `productLink(productId, skuId)` → `/p/<product>?sku=<sku>`; the travel-case line
  opens the travel case.
- **«Buy now» checks out that product alone** — the owner's decision; **chosen: the quantity already in the cart,
  without adding, or one when the cart has none.** The buy box says «2 in your cart» right under the button, so a
  checkout of two is what the page said; buying one of two would undo a quantity the shopper chose, and the cart-based
  checkout has no way to place one while the line holds two without changing the line. On the wire it is a field of
  `LineChange`, `alone` (`shared/.../cart/CartCommands.kt`): the line is selected, every other line unticked and left
  in the cart, and the line's seen price and stock become the current ones — the product page showed that price, and
  a line changed since it was added would otherwise leave checkout empty. `alone` with `selected = false` is
  `400 validation_failed`. Recorded in research, «Decided in B-48» and a new «Decided in B-100».
- **Tests.** Server: `CartRoutesTest` («a line bought alone is the only one selected», «a line opens the product at
  its own SKU», the line link in «totals as drawn», the promo's rows and «no Discount −$0.00» in the existing ones),
  `ChangedLinesTest` («a changed line bought alone is accepted at the price the page showed»), `ProductButtonsTest`
  (a guest's «Buy now» twice keeps one; a customer's checks out the mug alone at the two the cart held, the headphones
  left unticked in the cart; the limit), `SavedRoutesTest` (the answer and its message), `CartFixturesTest` (a new
  body, `cart_nothing_selected.json`). Client: `CartWiringTest` («minus at one is idle», «plus at the line's limit is
  idle», «with nothing ticked checkout and delete selected are idle», their live control, «in the storefront save for
  later says where the line went», the title's link with its SKU); `BuyBoxWiringTest` and the product bodies carry the
  new `buy` change.
- **Mutation**, on the committed change, each red, restored, `git status` clean (mutations whose tests are disjoint
  ran together in one run): the old rows (promo inside «Discount», the zero row drawn) — 2 tests; the line link without the SKU — 2; `alone` not taking the seen price — 1;
  «Buy now» back to adding a unit — 3; «Save for later» a bare `refresh` — 1; `alone` not unticking the others — 2;
  `HaulButton` pressable while not enabled and the stepper's ends not inert — 3; «Delete selected» not inert — 1; the
  disabled button's look reverted — `Cart_NothingSelected` and its phone twin red in `viddikVerify`.
- **Goldens**: two new, no reference (no artboard draws nothing ticked): `Cart_NothingSelected` and
  `Cart_NothingSelected_Phone` — checkout grey, «Delete selected» muted, no «Discount» row. Re-recorded with `--force`
  for the Cart group, because a plain record kept them as within tolerance: `Cart_Content`, `_Guest`, `_ItemChanged`,
  `_PromoError` and their phone twins (the stepper's «−» at one muted), `Cart_PromoApplied` and its phone twin (also
  «Discount −$140.00»). `Cart_Empty`, `_Loading`, `_Error` came out byte-identical and are unchanged. Recorded on
  Linux in a copy outside the mutagen replica; each looked at.

## Findings (2026-10-11)

- **A guest's «Buy now» is alone only in the guest's cart.** It goes to checkout through sign-in, whose merge keeps the
  customer's own stored lines ticked as they were and sums a SKU both carts hold (B-12's rule). A guest whose account
  already has a ticked cart reaches checkout with those lines too, and a product both carts hold at the summed
  quantity. Closing it needs the merge to know the press was «Buy now» (the intent in `next`, or a merge flag) — not
  done here.
- **The goldens' tolerance does not see the stepper's colour.** Reverting the muted «−» leaves every Cart golden green
  (a few dozen pixels, under 0.05 %); the look is held by the semantics tests (`assertIsNotEnabled`), which share its
  condition, and by the re-recorded goldens as pictures. The disabled checkout is large enough to be caught.
- **«You'll earn 0 points on this order»** with nothing ticked (`cart_nothing_selected.json`): the points line is
  drawn whatever the total. Untouched.
- **A checkout line's link drops the SKU too** (`CheckoutScreen.kt`, `productLink(it.item.product.id)`), as the order
  page's does; this item fixed the cart's only.
- **A «Buy now» on a SKU out of stock that the cart already holds** is accepted by the server (as a selection change
  always was) but never offered by the tree; the line stays not counted.

## Findings: the server suite ran out of PostgreSQL connections (2026-10-11)

- **haul#121's `gradle` failed twice in `UniqueIdsTest`'s customer walks with «sorry, too many clients already»**
  (run 38134352751), after the rebase on B-99; `main` with B-99 was green. Not B-100's code and not the B-99 seed's
  time: test databases left open. Every `PostgresHarness.freshDatabase` comes with a Hikari pool that keeps its four
  connections open until it is closed, the suite runs in one JVM against one container, and 22 test databases were
  never closed — `RecentSearchesTest`, `CartMergeTest` and `CartFixturesTest` held one per test instance, and
  `ChangedLinesTest`, `SchemaTest`, `SeedTest`, `SeedReviewsTest`, `SeedLoyaltyTest` and `ProbesTest` opened them
  inside a test without `use`. With the three the suite keeps on purpose (the shared seeded database,
  `PhotoRoutesTest`'s and `DeliverySlotsTest`'s), that was 25 pools, 100 connections: PostgreSQL's limit. `main`
  sat at 96 — B-99's two new `SeedReviewsTest` databases had taken it there — and the database B-100's new
  `ChangedLinesTest` case opens was the one over; the first test to ask for a database after the seed tests,
  `UniqueIdsTest`, failed. Reproduced on the WSL box with a count of PostgreSQL's client backends at every new
  database: it climbed by four at each of those sites and reached 97 before `UniqueIdsTest`, which failed as on CI.
  With the fix the same count peaks at 13, and no more than the three kept databases are open when a test asks for
  one (`:server:test`, 203 databases).
- **Fixed in the tests, not in `max_connections`**: raising the limit would only move the wall, and the next leak
  would fail some other test far from where it is. Each of those databases is closed by the test that opens it
  (`use`, or `@AfterTest` for a test class's own). `PostgresHarness` now keeps the databases it handed out and
  refuses to open more than eight at once, naming where each open one was opened — a leak fails near its address,
  not in whichever test is unlucky (checked: with three of the fixes reverted, the ninth database was refused with
  the eight left open listed by file and line).
- `LiveOrderTest`'s timing failure in the first attempt is the suite's length (B-111), not this.

## Where the checks ran (2026-10-11)

On the WSL box (Ubuntu 24.04), after the rebase on `origin/main` (B-98 in): `:composeApp:wasmJsBrowserDistribution`
green; `check :server:installDist` green (server 441 tests, client 331, `viddikVerify` 152, ktlint);
`scripts/e2e.sh` against `haul/server:b100` green (WholePathTest, the shoppers); `viddikDesignParity --component
"Cart - "` 16/16 within 5 % (1.54–4.59 %), the two new fixtures without a reference. On the Mac: `make check` and
`make docs-against BASE=origin/main` clean, every anchor found. Not walked in a browser: the client's tests drive the
same renderers and the storefront.
