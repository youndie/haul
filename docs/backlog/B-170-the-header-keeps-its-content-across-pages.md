---
id: B-170
title: "fix: the header keeps its content while the next page loads"
status: done
priority: P2
size: S
stage: stage-15-showcase
---

# B-170 — fix: the header keeps its content while the next page loads

The designer and the shopper (2026-11-01) both saw the header go back to its placeholders on navigation — the
search, the account and the cart blink to grey bars and back. The header is the same on every page; drawing it
again from nothing on each is a flash with no reason.

- First reproduce it on the stand with a visible browser (the reviews ran in a hidden pane, which slows frames); if
  it is real, the shell keeps the last header drawn until the next tree brings its own. If it does not reproduce,
  the item is dropped with what was seen.

- AC: navigating between home, a category and a product never shows the header's placeholders after the first load
  (a client test over two trees); or the item is dropped with the walk that did not reproduce it.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.

## Findings (2026-11-01)

- **It is real, and it is in the code, not in the hidden pane.** A visit to another path draws that page's
  placeholder (`Loading` in `shell/Storefront.kt`), and every placeholder draws `PendingHeader`, which drew the header
  kept since B-67 with `pending = true`: the account slot a grey bar, «Cart» with no count, no demo shopper's pill —
  on every page, whatever header the last tree had drawn. The hidden pane only made the moment long enough to see.
- **The walk, before the fix** (`efce2495`, a seeded local stack on the WSL box — PostgreSQL 18 in Docker, the
  server from `:server:installDist` with `HAUL_SEED=true` — and headless Chromium over CDP with the repository's own
  DevTools client, `scripts/measure-first-load.py`/`a11y-check.py`): a guest with one line in the cart, the home page
  loaded cold, then 600 ms of latency on every request (`Network.emulateNetworkConditions`, the bundle already in),
  then «Electronics» pressed in the header's row (`pushState` at 390, where the row is in the menu) → the product
  `/p/p-sonora-sh-718n` (`pushState`) → `history.back()` → the logo pressed. After each, the accessible mirror read
  every 35–130 ms for 3 s and a screenshot of the header's band every other read. At 1440: of the reads with the
  page's placeholder drawn — category 21 (0.17–1.84 s), product 10 (0.15–0.80 s), back 23 (0.15–1.71 s), home 23
  (0.14–1.71 s) — every one had no «Sign in» and «Cart» with no count, and the screenshots show the grey bar in the
  account slot. At 390 the same: 24, 11, 26, 23 reads, all without «Sign in» or the count. Without the added
  latency (loopback) no read caught the placeholder at all: a tree answers within a frame there, which is why it
  only shows on a real network or a slowed pane.
- **The search field was never a placeholder** in either walk: it is drawn the same pending or not (its text is the
  placeholder words on a page with no query). The reviewers' «grey bars for search» was the account slot's bar beside
  it, or the page's own placeholders under it; nothing about the field changed.
- **The fix:** `PendingHeader` draws the header as it is once the server has drawn one for who is looking
  (`LocalShellHeaderDrawn`, `ShellHeaders.serverDrew`); `pending` is the client's own header's alone — the first page
  on its way, a sign-in or sign-out before the server has drawn for the new shopper, a page with no tree whose header
  alone did not arrive (B-105). **The header kept is the one the renderer drew** (`LocalHeaderDrawn`, reported by
  `HaulHeaderRenderer`) instead of the arriving tree's: «Add to cart» answers the count with an `update`, and the
  tree's header would have drawn the old count over the next placeholder. An error page after a tree draws the kept
  header as it is too — the same rule; an error that followed a kept header would otherwise blink to the placeholder.
  Research «Decided in B-170»; TC-ERR-01 rewritten.
- **The walk, after the fix** (`002fe5a2`, the same stack and script): at 1440 the placeholder in 22, 10, 23, 25
  reads and at 390 in 23, 11, 27, 27 — «Sign in» and «Cart 1» in every read of all four steps at both widths; the
  screenshots show the header unchanged over the placeholders.
- **Held by** `KeptHeaderTest`: two trees with the second held back — the first page on its way draws the
  placeholder header, then category, product and back each keep «Sign in» and the count; and the count an `update`
  wrote is the one the next placeholder draws. Mutation-checked: `pending = true` back fails both; the header
  reported once as the tree arrived (`LaunchedEffect(Unit)` in the renderer) fails the second. Goldens unchanged.
