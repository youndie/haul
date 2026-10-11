---
id: B-93
title: "client: a press after switching a product tab redraws the page"
status: done
priority: P1
size: S
stage: stage-11-qa
---

# B-93 — client: a press after switching a product tab redraws the page

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk at 1366. On a product page: Description → Reviews (or Questions), then «Helpful», posting a
review or asking a question. The command answered 200/201 and the follow-up `GET /ui/p/…&tab=reviews` returned the new
tree («1 person found this helpful», «2,342 reviews», questions 87), but the screen kept the old tree until a reload.
On a freshly loaded page the same presses redraw.

Likely cause (read, not verified): in `Shown` (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` ~510-525) `refresh` is `remember(address)` and
writes into the `tree` state made by `remember(loaded)`; an in-place navigation changes the address before the new
`loaded` arrives, so the refresh writes into a state no longer drawn.

- AC: a client test (switch tab in place, then a `refresh`-answered press → the new tree is drawn), red before; the
  same for every in-place navigation (filters, pages, scoped search).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.

## Findings (2026-10-11)

- **The cause was the one suspected.** A `navigate` within a screen (B-62) — a product tab, a facet or a page
  that still `navigate`s, a scoped search, back or forward — changes `Navigator.address` at once and the tree
  later. `Shown` recomposed with the old `loaded` and the new address, so `remember(address)` made a refresh
  bound to the old `remember(loaded)` tree state; when the new tree arrived its state was a new one, and the
  refresh, no longer re-made (the address had not changed again), kept writing into the old state. The fetch
  was right, the redraw went nowhere. A `load` answered in place (B-63) was not affected: it changes the
  address without a new `loaded`.
- **The fix** (`shell/Storefront.kt`, `Shown`): one `ScreenRefresh` per screen, which reads the address, the
  tree's setter and `onSignedOut` through `rememberUpdatedState` when it runs. Every `refresh`-answered press
  goes through it — kompot's `refresh` (`withRefresh`), a command's answer («Helpful», the dialogs, the cart),
  a sign-in that redraws, `LocalScreenRefresh` — so they all follow. The arrival count (B-64) is unchanged: a
  refresh still bumps it, so the overrides go with the tree it brings. Reading the address late opened a race
  the old code did not have — a refresh still on its way when the shopper opens another tab would land on the
  new tab's page — so a refresh whose address is no longer the one shown when its answer lands is dropped.
- **Tests** (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/RefreshInPlaceTest.kt`): the QA walk
  over the server's product bodies (Description → Reviews in place → «Helpful» → the new count drawn), and a
  refresh after a facet, a page of results, a scoped search and back within the screen, each opened in place
  — five red before the fix, timing out on the redraw after the refresh's `GET` had gone. Two guards green on
  both sides: a refresh after a `load` answered in place asks for the address the update made, and a refresh
  after an in-place visit drops what an update wrote (B-64). An eighth catches the race above: a refresh held
  on its way, another address opened, then the refresh lands — the page moved to stays. Mutations: the old
  `remember(address)` refresh fails the five; reading the address captured at the first composition fails
  six; not bumping the arrival fails the B-64 guard; drawing a refresh that landed late fails the eighth.
