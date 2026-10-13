---
id: B-142
title: "fix: a search's chips row reaches every scope on a desktop"
status: done
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-142 — fix: a search's chips row reaches every scope on a desktop

Found by the v0.6.0 run, TC-SRCH-11 ([run](../qa/runs/2026-10-13-v0.6.0.md), walker A on the stand). From 1280 up a search's category chips are one line
clipped at the sort: `classic` has 111 chips and shows «All 600» … «Books · Classics 1» with its count cut under the
sort at 1440. The row does not move for a horizontal wheel, a shift-wheel, a mouse drag or 25 Tabs from the field; at
1024 and 390 the same input scrolls it (B-82 decided «a search's kinds scrolling sideways» there). So on a desktop the
case's «Party · Classics» has nothing to press, and a chosen chip past the line
(`/search?q=classic&category=party-essentials-classics`) is not visible at all.

- **Every chip is reachable and the chosen one is in view.** The desktop row scrolls sideways like the narrower bands
  (wheel, shift-wheel, drag, keyboard focus), or wraps — whichever the code's own layout rules make cheaper; the chosen
  chip is scrolled into view when the page arrives. Rejected: hiding chips past the line — they are results.

- AC: at 1280 and 1440 `classic` reaches «Party · Classics» by wheel, drag and Tab, pressing it scopes the results, and
  arriving at a chip past the line shows it chosen; 1024 and 390 unchanged; a client test per band.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/CatalogViews.kt` (`FilterChipsView`,
  `KindChips`), `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/search/SearchChipsReachTest.kt`.

## Findings (2026-10-13)

- **Why the desktop row did not move.** The row is not in `SearchViews.kt` but in `FilterChipsView`
  (`CatalogViews.kt`), shared with a category's kinds. B-82 made the search row scroll only under `LocalHaulMedium`
  (below 1280); from 1280 up the pills were a plain `Row` with `weight(1f)`, so each pill got what width was left —
  «Books · Classics 1» cut at the sort, every later pill measured to nothing — and there was no scroll to wheel, drag
  or bring a focused pill into.
- **Choice: scroll, not wrap.** The row now scrolls sideways at every width, as B-82 already made it below 1280 and
  as the phone always had it: the change is the condition dropped. Wrapping `classic`'s 111 categories would take a
  dozen lines at 1440 and push the grid a screen down; it would also be a third arrangement of one row.
- **The chosen pill is scrolled into view when it arrives** (`KindChips`): measured in the scrolled content, it is
  centred in the row's view if any part of it is outside, and left alone if it is in view — so a press on a visible
  pill, or the shopper's own scrolling, moves nothing. It holds at every band, the phone and 1024 included, where a
  scope past the edge was equally out of sight. The page's vertical scroll is never moved (no `bringIntoView`, which
  would pull a page restored further down back to the row).
- **A mouse drag scrolls the row too** (`draggedByMouse`). Compose scrolls a row under a finger, a sideways wheel,
  a shift-wheel, a touchpad and the keyboard's focus, but not under a mouse held down and moved — at 1024 and 390 as
  much as at 1440 (the run's «drag» there was a finger's). The row now follows a mouse past the touch slop; the
  drag is consumed, so the chip it starts on is not pressed. A plain vertical wheel over the row scrolls the page,
  as everywhere in the store; a sideways wheel, shift-wheel or touchpad moves the row.
- A category's kinds without a sort control (the `else` branch) still lay out as one plain line at the desktop; no
  category has more kinds than fit at 1280, so it is left as it was.
- Goldens: none moved — `viddikVerify` green with no recording; the search fixture's six chips fit at 1440, and an
  unscrolled row draws the same pixels.
- **Verified** — `SearchChipsReachTest`, one test per band (1440, 1280, 1024, 390): a page arriving on
  `party-essentials-classics` draws its chip whole in the row's view; a sideways wheel, a finger's swipe and a mouse's
  drag move the row (the drag presses nothing); Tab from «All» focuses «Party · Classics 15» in view; pressing it loads
  `/ui/parts/search?q=classic&category=party-essentials-classics` and pushes the address. Red on `origin/main` at every
  band (no sideways scroll at 1440/1280; the chosen chip out of view at 1024/390); red again with only the into-view
  effect removed (all four bands) and with only the mouse drag removed (all four bands). In headless Chrome 152 on the
  build box against the branch's image (seeded, `classic` = 600 results, 111 chips), at 1440, 1280, 1024 and 390 each
  of sideways wheel, shift-wheel, mouse drag and Tab from the search field reached «Party · Classics 15» (24–46
  steps), a press on it opened `/search?q=classic&category=party-essentials-classics` with «15 results» and the chip
  chosen in view; opening that address cold drew the chip chosen, centred in the row.
