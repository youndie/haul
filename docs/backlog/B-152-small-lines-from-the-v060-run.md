---
id: B-152
title: "fix: the small lines the v0.6.0 run noticed"
status: done
priority: P3
size: S
stage: stage-12-qa-run
---

# B-152 — fix: the small lines the v0.6.0 run noticed

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md)), each small:

- `/favicon.ico` answers 404 on every first load, a console error each time (the page links no icon); it may be what
  voids `scripts/measure-first-load.py --url` runs («no static frame on the page» while screenshots show one).
- A home page left open across the store's midnight: its countdown reads 00:00:00 one second early and stays there
  over the ended deals' cards until a reload, while the server has the new day's deals.
- A cart line at quantity 10 with a struck-through price cuts «$31.49 each» to «$31.4» at 390.
- The card fact on an order flips its wording: «$462.00 charged» when delivered, «… charged as the shipments shipped»
  once a return is requested.
- Checkout with Haul Pay chosen still says «Your card is charged when the order ships.»
- Generated descriptions lowercase the leaf name: «Travel tvs by Northwind.» (`CatalogSeed.kt`).
- A press on the variant already chosen reloads the product (checkout's chosen method sends nothing for the same press).
- «Placing order…» leaves «Back to cart» in link blue though inert.
- A scoped search that finds nothing anywhere resets the picker to «All categories» while the address names the scope.
- The phone's category row at rest shows no cut word at 375, 390 and 800 (the next word starts just past the edge),
  so nothing says there is more — TC-HDR-05 expects the cut; its golden shows the uncut row. Decide the layout or the
  case.

- AC: each behaves as its line says, or is recorded as decided otherwise with its reason; a test per line where it is
  code's logic.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`, `server/src/main/kotlin/io/github/youndie/haul/seed/CatalogSeed.kt`.

## Findings (2026-10-13)

Each line, what it was and what it is now; one commit each.

- **The icon.** `index.html` named none, so the browser asked for `/favicon.ico` on every first load — a 404 the
  bundle has no file for and a console error. The page now names `favicon.svg`, beside it in the bundle
  (`composeApp/src/wasmJsMain/resources/`): the logo's «H» in Ink with its Cobalt dot on a white tile, drawn as
  shapes, so it fetches nothing — no font, no link — and comes from the store's origin like the rest of the
  bundle (`no-cache`, precompressed by the image as every SVG is). The browser does not wait for it before the
  first paint. Whether the 404 was what voided `measure-first-load.py --url` runs was not tried: no stand runs this
  branch.
- **The countdown over midnight.** Two causes. `countdown` cut the time left down to whole seconds while the clock
  ticks a few milliseconds after each one, so it read 00:00:00 a second early: a second begun is now counted. And
  nothing asked for the page when the deals it counted ended: the section's heading (home's and the deals page's)
  now refreshes the screen in place at the end (`LocalScreenRefresh`, as a command's answer does), and again every
  30 s while the page still draws the countdown that ended — a browser clock ahead of the server's asks before the
  server's day has turned. The tree that arrives ends it, whichever it is: the next day's countdown or, in the
  seconds before the deals move, the popular row (TC-HOME-04). A view drawn on its own (a golden) asks nothing.
- **«$31.49 each» at 390.** The phone's line drew the price, the old price and the unit price on one row that does
  not wrap; at ten with a deal's struck-out price the third had no room and was cut. The row wraps (`FlowRow`):
  what does not fit goes under the price whole. No golden moved — every fixture's prices fit on one line.
- **The card fact's wording.** `OrderPage.paid` read «$X charged» only while the order was `arrived` (delivered or
  picked up); a return requested moves the order's progress on, so the fact fell to «… charged as the shipments
  shipped» until the refund replaced it. It now reads by `received` — arrived, whatever has gone back since — which
  is what «Reorder» already reads (B-101).
- **Haul Pay's note.** Haul Pay is a `card` way to pay (the money moves at shipping), so it got the card's note.
  It now says «The first of 4 payments is charged when the order ships.», which is what B-24's plan does and what
  the order page says after placement; pay on delivery still has no note.
- **«Travel tvs by Northwind.»** The seed lowered the whole leaf name to sit inside the sentence. A word with a
  capital past its first letter (an initialism) now stays as written (`CatalogSeed.inSentence`); «TVs» is the only
  such leaf. The stand was seeded before, and a seed never rewrites a catalog, so `V30` rewrites the generated
  sentences of the `tvs` leaf — only the seed's exact shape, «<Adjective> tvs by <Brand>.» — and
  `LeafNameMigrationTest` holds the two together (a catalog seeded the old way and migrated equals a fresh seed).
- **The variant already chosen.** Its swatch and its pill followed the option's `navigate` like any other, so the
  page was opened again. The chosen option presses nothing now, in the client, as checkout's chosen way to pay
  and to deliver already did; the tree is unchanged.
- **«Back to cart» while placing.** It was already inert (no press) but drawn in link blue. It is muted
  (`outlineMuted`) and inert for assistive technology (`inert()`), as a control with nothing to do is (B-100).
  Goldens `Checkout_Placing` and `Checkout_Placing_Phone` re-recorded for it.
- **A scoped search that finds nothing anywhere.** `SearchScreen.noResults` built the header with no scope, so the
  picker read «All categories» over `&category=fashion`. The page now hands the address's scope to the header as
  the results and «nothing in scope» pages do (an unknown slug the header drops, as before). The tips and the
  queries to try are unchanged: they search every category.
- **The phone's category row at rest — decided otherwise: the case, not the layout.** TC-HDR-05 expected a word
  cut at the right edge as the sign that there is more; at 375, 390 and 800 the next word starts just past the
  edge and none shows. That is the canvas: its 390 phone header (every phone artboard) ends the row on «Fashion»
  whole, «Beauty» past the edge, and B-73 kept the row pixel for pixel. B-82's rule (no word cut at an edge)
  exempts this row because it scrolls, so it neither asks for the cut nor forbids it, and the golden
  (`HaulHeader_Guest_Phone375`) is the canvas's row. Whether a word shows cut depends on the width and the
  categories' names, not on any rule; the menu, not the row, is the phone's way to every category (B-73); a fade or
  a padding that pulls a word in would move every phone golden for a hint no artboard draws. TC-HDR-05, research
  («Decided in B-73») and the comment in `HaulHeaderView` now say so.

- **Tests**, each red by mutation after its commit and green restored (`git status` clean each time):
  `StaticFrameTest` «the page names its icon, a file of the bundle that fetches nothing» and the page's fetch list
  (link removed: both red); `CountdownTest` «the last second reads one until it is over» (whole-second cut: red);
  `StorefrontTest` «the page is asked for again when its countdown ends» (the ask removed: red; asked once, not
  again: red); `WidthsTest` «a phone's cart line keeps its unit price whole» at 375 and 390 (red before the fix:
  «$31.49 each» cut at its edge); `ReturnRoutesTest` «a returned line is refunded when the seller has it back»
  now holds the card fact across the request (`arrived` again: red); `CheckoutRoutesTest` «the note says what is
  charged when the order ships» (red); `LeafNameMigrationTest` both tests (the old seed: both red; V30 removed:
  the migration one red); `ProductPageLinksTest` «the chosen variant presses nothing and another opens its sku»
  (red); `CheckoutWiringTest` «the summary's links do nothing while the order is placed» now asserts «Back to cart»
  inert (red); `SearchRoutesTest` «a scoped query that finds nothing anywhere keeps its scope in the picker» (red).
- **Goldens**: `viddikRecord --force` on the Linux box in a copy outside the mutagen replica: four differ.
  `Checkout_Placing` and `_Phone` brought back, 556 px each, «Back to cart» grey — looked at. `Search_Results`
  (901 px) and `Search_Autocomplete` (188 px) not brought back: the chips' glyph edges only, the same two B-149
  left, which `viddikVerify` accepts. No live artboard moved (`Checkout_Placing`'s references were retired in
  B-78), so nothing is retired here and parity was not re-run.
- **Not changed**: a `/favicon.ico` request from something that ignores the page's link still answers 404 — no
  catch-all, as B-27 decided.
- **The page refresh's one side effect in the tests**: `PlusTrialWiringTest` drew the home fixture under the real
  clock, where its deals ended in 2025, so the page now asked for itself again and the tests' request count moved.
  It is pinned to the canvas's «now», as the other storefront tests that count requests are.
- **Where it ran**: the Linux box — `:composeApp:wasmJsBrowserDistribution` (the bundle carries `favicon.svg`);
  `check :server:installDist` green (server 542 tests, client 491, `viddikVerify`, ktlint); `scripts/e2e.sh` against
  this branch's image, 10 passed; `scripts/image-check.sh` green, `/favicon.svg` answering `200 image/svg+xml`. The
  Mac — `make check`, `make docs-against BASE=origin/main`, `ktlintFormat`.
