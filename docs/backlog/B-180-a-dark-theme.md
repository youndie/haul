---
id: B-180
title: "feat: a dark theme for the whole store"
status: done
priority: P2
size: L
stage: stage-15-showcase
---

# B-180 — feat: a dark theme for the whole store

The store is light only (owner, 2026-11-01: a dark theme, for everything). The client's colours are one light set
(`HaulColors`), and a server tree that names a colour names a light one.

- A dark set of the theme's roles, chosen so every page reads as the light one does (contrast at least what the light
  one has), and every page drawn through the roles — the shell, every renderer, the dialogs, the skeletons, the
  desktop window. It follows the system's setting (the browser's `prefers-color-scheme`, the desktop's), with a
  switch to override it that is kept per browser; the page's first paint (`index.html`'s frame and loader) follows
  too, so there is no white flash on a dark system. A colour a tree carries from the server is a role, not a value,
  or has its dark counterpart decided in the research.
- Goldens: a dark variant of the screens that show what changed (not every golden twice), and the research says
  which and why.

- AC: every page of the store is usable in dark — checked by walking the stand's pages in a browser with
  `prefers-color-scheme: dark` and in the desktop app; no hard-coded light colour left outside the theme (a test or a
  check says so); the switch is kept across reloads; light pages unchanged (their goldens do not move).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulColors.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulTheme.kt`.

## Findings (2026-11-01)

- **Every colour is a role of the scheme being drawn.** `HaulColors` keeps its call sites (`HaulColors.primary`) and
  reads `LocalHaulColorScheme`; `HaulColorScheme.Light` is the canvas's and the default wherever nothing chose, so the
  light goldens did not move — re-recorded with `--force` on Linux, all 183 came out byte-identical. The dark roles,
  their contrasts against the light ones (every pair at least the light pair's, or 7:1 where that is higher), the
  tile tones' counterparts, the switch's place and the dark goldens' choice are in the research, D9, «Decided in
  B-180». One light value meant two things in three places, so three role groups were added rather than a value per
  call site: the **band** (the strip, the footer, the Plus block and dialog head, the messages — dark in both schemes,
  for their Acid words), the **inverse fill** (Material's, flipping to Paper in the dark so a chosen chip stays
  chosen; its accent `inverseAccent`) and **fixed Cobalt** (`primaryFixed`, the campaign and the membership tile).
  Words drawn on Acid took the page's ink in about twenty places (badges, counts, the pickup code, the points
  banner, selected pills and slots); they name `onSecondaryContainer` now, the same value in light.
- **No colour outside the theme**: `HardCodedColourTest` scans the client's sources (comments aside) for every way of
  writing a colour and keeps four, each with its reason; mutation-checked (below). The server is unchanged: its
  trees name no colour token outside the roles; the tile tones it sends are drawn as the scheme's counterparts
  (`HaulColorScheme.tone`, `onTone`), and `HaulColorSchemeTest` fails on a tone in the server's sources with neither a
  counterpart nor a place among the kept ones (Acid, Cobalt).
- **The switch** is the footer's «Theme» column (System / Light / Dark), kept per browser in `localStorage`
  (`haul.theme`) and on the desktop in its preferences; the desktop app has View → Theme too. `index.html` paints its
  frame from the schemes' roles under `prefers-color-scheme` and the kept choice, so a dark system sees no white
  page before the bundle (11.6 KB, inside the first round trip).
- **The desktop window** follows the system's setting through Compose's `isSystemInDarkTheme()` (skiko reads it on
  macOS and Windows, live; Linux reports none, so the app starts light there and the switch is the way to dark). Its
  ground is the scheme's paper; on a Mac its title bar is asked for by `apple.awt.application.appearance` and
  `apple.awt.windowAppearance` — **not looked at on a Mac in this item** (the box is Linux, the app was not run on
  the Mac). Windows' title bar stays the system's: a dark one is a DWM call the app does not make.
- **Mutation check**: with `HaulFooterView`'s footer background written back as `Color(0xFF0F0F0F)`,
  `HardCodedColourTest` fails naming the line; restored, green. (Run detail in the pull request.)
- **Walked in dark** in headless Chromium on the build box against a local stack (the built bundle, the server with
  its seed, PostgreSQL; no provider, so a guest's pages) with `prefers-color-scheme: dark` emulated, 1440 and 390:
  the first paint on a throttled link (the frame dark, the body `rgb(20, 19, 17)`), home, the catalog's root, a
  category, a search, a product, deals, the empty cart, about, /numbers, /live, sign-in, a product not found,
  checkout's and the account's sign-in prompts, and the footer's switch; then «Light» kept over a dark system across a
  reload (`data-theme="light"` marked before the frame), «Dark» over a light one, and «System» following a change of
  the emulated setting with no reload. All readable. **Not walked in a browser:** the signed-in pages (account,
  orders, checkout, the review dialog) — no provider in the local stack; their dark goldens stand for them
  (`Account_Content_Dark`, `Order_*_Dark`, `Checkout_PointsApplied_Dark`, `Product_ReviewDialog_Phone_Dark`). The
  desktop app was not run (its switch and the window's ground are held by `DesktopStoreTest`).
- **For B-181 (pictures with dark versions):** the signal is `LocalHaulColorScheme.current.isDark` (or
  `HaulColors.scheme.isDark`) in a composition — `PhotoLoader` implementations (`coilPhotoLoader`, the fixtures'
  `ArtLoader`) can read it and ask for the dark picture (a `?scheme=dark` or a `-dark` path the server serves), and
  must key their cache on it. The tile under a picture is already the scheme's (`toneColor`, `HaulColorScheme.tone`),
  so a picture whose own ground is transparent sits on the right tile in both. HTML outside Compose reads the root's
  `data-theme` or the media query. A dark golden of a picture is `Dark { <fixture> }` (`FixtureHarness.kt`).
