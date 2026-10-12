---
id: B-126
title: "fix: the accessible tree follows the page after a popup, and every button has a name"
status: done
priority: P2
size: M
stage: stage-12-qa-run
---

# B-126 — fix: the accessible tree follows the page after a popup, and every button has a name

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), in headless Chromium on a local v0.4.0 stack:

- After any popup opens and closes (the account menu, the sort menu, «Catalog», the phone menu) the browser's
  accessible mirror of the page (`cmp_a11y_root` in the shadow root) keeps only the popup's nodes («Account | Sign
  out»); navigating does not refresh it, only a reload does. A screen reader reads a menu that is gone.
- Buttons with empty names: the phone header's heart and account, the account's «Price drops» tile, checkout's
  summary tiles, the category row's words.

- **First find whose bug the mirror is** — Compose Multiplatform's wasm accessibility or the way the storefront
  hosts its popups — with a minimal page; a framework defect goes upstream as a report, the storefront's own part is
  fixed here. Names are the storefront's: each press gets a `contentDescription` or semantics.

- AC: open and close each popup, then navigate — the mirror holds the page's nodes; every button in the mirror has a
  name; where the defect is upstream, a minimal reproduction exists and the report is linked here (asked of the
  owner before it is filed).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Layers.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/LinkMenu.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`, `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/NamedPressesTest.kt`, `scripts/a11y-check.py`.

## Findings (2026-10-12)

- **Whose defect the mirror is: Compose Multiplatform's, already known and fixed upstream.** In 1.12
  (`ComposeWebSemanticsListener`) the browser's mirror follows one `SemanticsOwner`: each scene layer a `Popup` or a
  `Dialog` opens appends its own, which replaces the page's; the layer's removal sets it to `null`, and from then on
  every sync returns at once — the mirror keeps the closed layer's nodes until a reload. The page's own owner is
  appended once, when the scene is made. The minimal page — one `Popup` opened and closed, `BasicText` only, Kotlin
  2.4.20 — shows it on 1.12.1 (mirror and `getFullAXTree` after closing: «Close menu» alone) and not with the same
  menu drawn inline (the control: «Open menu», «Page title», «Presses: 1»), and not on 1.13.0-alpha02. Upstream it is
  [CMP-9368](https://youtrack.jetbrains.com/issue/CMP-9368) «Web A11y. Incorrect semantics after closing dialogs/popups», fixed by compose-multiplatform-core #3298
  (2026-08-17, «Support multiple SemanticOwners in CMP Web») and released from 1.13.0-alpha01; not in 1.12.1. So
  there is nothing to report. The reproduction is kept outside the repository, on the owner's Mac in
  `/tmp/b126-repro` (`run.sh` builds it on 1.12.1 and 1.13.0-alpha02 and prints a verdict with its control).
- **The storefront's part: popups are drawn in the page's own layer** (`ui/Layers.kt`). The stack holds Compose at
  1.12.1 until Kotlin 2.5.20, so waiting for 1.13 was the other choice, at the price of a mirror that lies after any
  menu until then. `OverlayHost` wraps the storefront and draws whatever `OverPage` opened over it, with the
  composition locals of where it was opened; the menus (`Menu`: «Catalog», the sort, the search field's picker, the
  account) are placed under their control from its window position, a press off them closes them and reaches
  nothing under them, Escape closes them as before (B-75); the phone's menu and the filter sheet are
  `SheetOverPage`s: no press reaches the page under them, Escape closes them, and a press on a sheet's blank space
  is still read by the sheet (B-118's blank press: the cover consumes nothing). Outside the storefront — a
  screenshot — nothing opens, as before. Once the stack is on 1.13, `Popup` and `Dialog` work again; the host can
  stay.
- **Unnamed buttons**, all of the run's and more: the phone header's heart («Saved») and account (the customer's
  name), the search field's button («Search») and the field itself (its placeholder), the category row's words and
  the crumbs, the account's tile fills at 1440 (the tile's label — «Price drops» among them), checkout's summary
  tiles, the cart's check boxes and photos and promo field, the order's line photos, checkout's empty fields (their
  label), the colour swatches of the facet panel and of the product page (the colour's name). Each is a
  `Modifier.named(…)` (a content description) except the words and crumbs: those were `LinkAnnotation`s, which
  Compose 1.12 draws as presses of their own with no name (upstream since #3331, also 1.13). They are now
  `linkedText` runs: the text is drawn as before and each run is a named press laid over its words, so the pixels
  do not move.
- **Tests.** `NamedPressesTest` mounts the storefront itself on sixteen wire bodies at 1440 and 390 — home, catalog,
  search, the product's three tabs, both carts, checkout, account, orders, Saved and its drops, two orders — and
  lists every node with a click action whose content description, text and descendants' text are all empty; the
  fixtures' facet options get the action the server sends, or the swatches are no presses at all. Its control: a
  bare icon press is reported. `DrawnActionsTest`: the sort's menu, the phone's menu and the filter sheet open with
  one root (a `Popup` or `Dialog` is a second), a press off the menu closes it and «Show 24 more» under it opens
  nothing, Escape closes the phone's menu. Mutations: `named` made a no-op — red (the category words, the cart's boxes and
  photos, the account's tile); the search button's name removed — red on every page; the swatches' name removed — red on the
  catalog; the overlays put back in a `Popup` — both layer tests red (two roots); the press-catching layer under a
  menu removed — red («Show 24 more» opened page 2). Restored, green, tree clean.
- **The browser** (`scripts/a11y-check.py`, standard library, B-28's DevTools client): on the Linux box, the
  server's `installDist` with a seeded PostgreSQL and shildik in Docker, Playwright's headless Chromium 1228, Maya
  signed in through the provider's popup. Each popup — «Catalog», the sort, the picker, the account menu at 1440;
  the menu and the filter sheet at 390 — opened from the mirror by its name, closed (Escape, a press off it, «×»),
  then a link of the page followed: the mirror holds the new page (107–164 nodes, the header's, none of the
  popup's). Then home, a category, a search, a product, the cart, account, orders, Saved and checkout at both
  widths: no button without a name in the mirror or in Chromium's tree. The control, the bundle built from the
  branch point's client: every popup fails (the mirror kept the menu, 35 nodes, or held nothing to follow) and every
  page has unnamed buttons — the heart and account, the search button, the category words, the account tile, the
  cart's boxes and photos, checkout's tiles: the run's list. Not walked in the browser: an order's page (the seed's
  orders are not Maya's on a fresh stack); its presses are in `NamedPressesTest`.
- **Goldens**: none moved — the menus are closed in every artboard, and the linked text draws the same glyphs in
  the same place.
- **Not done**: a press's role stays «button» — the cart's check box is not a `checkbox`, a swatch does not say it
  is chosen. The page under an open sheet stays in the mirror, as it does under a `Dialog` on 1.13.
