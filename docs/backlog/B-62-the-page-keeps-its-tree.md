---
id: B-62
title: "client: a filter or a sort redraws the results, not the page"
status: done
priority: P1
size: M
stage: stage-9-ship
---

# B-62 — client: a filter or a sort redraws the results, not the page

Every press that stays on the same page — a facet, a sort, «Show N more», a page of results — is a
`navigate`. The shell keys everything on the full address (`key(address)` in `Storefront.kt`), so each
press throws the page away: the loading placeholder takes its place, the scroll goes back to the top, and
whatever the page held in `remember` starts over. The server only changed the results and the counts.

kompot 0.40.0.212 (kompot B-81) lets the screen keep its tree: `KompotScreenLoader(screenKey = …)` loads
a new address behind the drawn tree when the application says it is the same screen. It also keeps one
override store per screen, and keys `column`/`row` children by `id`. Compose then redraws only what
changed.

- **Decided as product owner:** the same screen is the same **path** — `/catalog?brand=a` and
  `/catalog?brand=b` are one screen, `/catalog` and `/p/…` two. Navigating within a screen keeps the
  page, its scroll and its opened parts while the next tree loads. A thin progress line under the header
  shows that a load is in flight. Navigating to another path draws the loading placeholder and starts at
  the top, as today. Back and forward follow the same rule.
- A load that fails within a screen keeps the page and shows the error as a notice with a retry, not
  the full error screen.
- The filter sheet's hoisting (B-54) stays only if it is still needed once the page is no longer thrown
  away; otherwise it is simplified, with the reason recorded.
- Not here: answering with only the changed nodes (`update` / `load`, kompot B-82) — a follow-up item
  once kompot ships it.

- AC: on the catalog, ticking a facet keeps the header, the breadcrumbs and the scroll position, with no
  placeholder in between, and shows the new results; opening a product still shows the placeholder and
  starts at the top; back from the product returns to the catalog; client tests for each; goldens
  unchanged except where a state is meant to change; the e2e green.
- Anchors: `gradle/libs.versions.toml`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`.

## Findings (2026-10-09)

- **How the shell tells one screen from another.** The page is keyed on `(path, signedOut)` instead of the
  whole address (`Storefront.kt`), and `KompotScreenLoader` gets `key = address to loads`,
  `screenKey = (path, signedOut)` and a hoisted `KompotScreenLoaderState` (kompot 0.40.0.212). Inside one
  path the loader loads the new address behind the drawn tree; the scroll (`rememberScrollState`, one per
  path) and every node's `remember` stay. What a `present` put over the page, the sign-in bookkeeping
  (`loads`, `signedInFor`) are per address. Another path recreates the whole block: placeholder, scroll at
  the top. Back and forward go through the same `Navigator.address`, so they follow the same rule. The
  product page's tabs and SKUs (`/p/…?sku=…&tab=…`) are one screen by the same rule.
- **The progress line** (`shell/InPlace.kt`, `LoadingLine`) is drawn while `isLoading` and a tree is on
  screen, over the page at the header's bottom edge minus the scroll, so it sits under the header and
  stays at the top of the viewport once the header has scrolled away. The header's height comes from its
  renderer through `LocalHeaderMeasured` (`HaulHeader` and `CheckoutHeader`); an `onSizeChanged`, no pixel
  moves. No artboard draws it; it is the theme's `primary`, 3 dp.
- **A failed load within a screen** draws `NotUpdatedNotice` at the bottom of the viewport — «The page
  didn’t update», the error pages' wording for why (`ShellFailure`; a `404` says the link may be old) and
  Retry, which asks for the same address again. The page under it keeps working. With no tree yet the
  error pages are as before. **The exception is a `401`**: a customer's page is not kept for a shopper who
  is a guest now, so the shell counts it (`signedOut`), which changes the screen key, takes the page down
  and loads it again — refused, it asks for the sign-in (B-44). A refresh refused the same way does the
  same directly. Cost: a `401` inside a screen is asked twice.
- **B-54's `FiltersSheetState` stays hoisted**, for a reason other than the one it was hoisted for: the
  page is no longer thrown away by a tick, but back and forward within the path keep the page too, and
  only the shell sees whether an arrival came from the sheet; a sheet in the renderer's `remember` would
  stay open on back. Two changes. (1) The renderer hands the sheet new results only when the results
  change (`LaunchedEffect(sheet, component)`, the handler through `rememberUpdatedState`): the page's
  handler changes with the address *before* the tree of that address arrives, and keyed on it the sheet
  followed the old facets again mid-load. (2) `settled()`: a tick whose page does not arrive leaves the old
  page drawn, whose facets are true again, so the sheet follows them instead of staying inert. The notice
  is under the sheet's dialog until «×».
- **Realtime.** Haul neither read nor provided `LocalKompotRealtimeUpdates`; nothing to migrate. The
  `key(tree)` around `KompotRealtimeProvider` is gone: kompot 0.40 drops a frame's override when a newer
  tree of the screen arrives, which is what the key was for, and the key threw the order page's state away
  on every refresh. `LiveOrderPageTest` green.
- **Tests** — `KeptPageTest` (7, desktop): a tick keeps the header, the breadcrumbs and the scroll with no
  placeholder and the line on, then draws the new results at the same scroll; back between two addresses of
  the path keeps the page; a product draws its placeholder, starts at the top, back returns to the catalog;
  a failed tick keeps the page under the notice and Retry loads it; a lapsed sign-in inside a screen takes
  the page down and asks for one; the phone sheet follows nothing while a same-path tick loads, and follows
  the kept page after one that failed.
- **Mutations** (each on the committed change, restored, `git status` clean; `KeptPageTest`,
  `DrawnActionsTest`, `SignInPromptTest`): `screenKey` = the address — 4 red (tick, back, failure, sheet
  after failure); no line — 1; no notice (every failure a page) — 3; no `401` branch — 1; no `settled()` —
  1; `drawn` keyed on the handler again — 1. **Survived:** one block for every path (`key(Unit)`) — the
  product still starts at the top, because the short placeholder in between clamps the shared scroll to
  0; and a refused refresh counting `loads` instead of `signedOut` — the loader's `401` branch takes the
  page down anyway, one request later.
- **Goldens unchanged**: no state any fixture draws changed; `viddikVerify` green.
- **Where it ran** (the Linux box, `MemoryMax=5G`): `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` — `:server:test` 374, `:composeApp:desktopTest` 182, `viddikVerify`, ktlint, all
  green (run apart: the two together were OOM-killed at 5 G); `scripts/e2e.sh` against the branch's image
  — `WholePathTest` green. No server source changed; the server takes kompot 0.40.0.212 too, which the e2e
  exercised. `scripts/image-check.sh` not run here.

