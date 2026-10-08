---
id: B-10
title: "design refs + client: Search screen and `SearchSuggestPanel`"
status: done
priority: P1
size: M
stage: stage-3-search
blocked_by: [B-01, B-07, B-09]
---

# B-10 — design refs + client: Search screen and `SearchSuggestPanel`

The search screen and the suggest panel over it.

Feature: `feature-search` — its scenarios are this item's acceptance where it names them.

- Not covered: search analytics.

- AC: parity for every `Search_*` artboard.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/`, `composeApp/src/desktopTest/snapshots/design`.

## Done (2026-10-08)

`viddikDesignParity` on the first round, all 10 artboards within tolerance (5 % of pixels, ±16 per
channel, untouched):

| Artboard | Mismatch | | Artboard | Mismatch |
|---|---|---|---|---|
| Search_Loading | 0.66 % | | Search_Loading_Phone | 0.52 % |
| Search_Results | 3.42 % | | Search_Results_Phone | 4.71 % |
| Search_Autocomplete | 3.38 % | | Search_Autocomplete_Phone | 4.74 % |
| Search_NoResults | 2.50 % | | Search_NoResults_Phone | 4.04 % |
| Search_Error | 1.84 % | | Search_Error_Phone | 3.76 % |

What is left is the text residual: glyph edges everywhere, the phone card titles up to 0.8 px right
of the canvas's (the card's own residual, as on Catalog), and the header's Orders / Saved / account
shortcuts, which sit about 7 px lower than the canvas's on every 1440 artboard — Home, Catalog and
Product goldens included, so it predates this item and is left for a change that re-records them all.

- References: the ten `Search_*` artboards rendered into `composeApp/src/desktopTest/snapshots/design/`
  from the canvas sources (their sizes added to `.canvas/canvas.json`'s `artboards`, which listed them
  only under `pages`); `manifest.json` lists all 49.
- Contract: `QuerySuggestion.prefix` — what a suggestion adds before the typed part («**trail**
  running shoes»), which the wire could not say; `SearchNoResults.count` and `accent` («0 results»,
  the quoted query in the italic); `PageTitle.quoted` — a search's title, the query in quotes with the
  count above it. The server builds them, its no-results tips and «Popular *categories*» now read as
  the canvas does, and `SearchRoutesTest` asserts the new fields, with
  `a query typed inside a suggestion keeps what comes before it apart` for the prefix.
- Client: `feature/search/SearchViews.kt` — the no-results page (tips, or the queries to try when
  there are any), the suggest panel at both widths and `SearchSuggestOverlay`, which opens it under the
  header's field over a scrim; `SearchFieldState` in the header tells the overlay where the field is,
  and the field draws its caret only while focused (the HaulHeader_Search fixture says so explicitly,
  its golden unchanged). `PageTitleView` draws a quoted title; `FilterChipsView` the chips' counts.
  `SearchLoading` and `SearchError` in `shell/` keep the query in the header; the error's title is
  balanced as CSS's `text-wrap: balance` balances it (`BalancedText`). A pagination standing on the
  page (under a search's grid) takes the gutter and its gap.
- Fixtures: `composeApp/src/desktopTest/.../SearchFixtures.kt`, one per artboard; Results, NoResults
  and the panel are wire bodies with the canvas's copy (`resources/bodies/search_*.json`). The Results
  body carries no pagination, as the artboards draw none. Goldens recorded on Linux.
- Not drawn from a reference: the queries-to-try form of NoResults (the canvas has only the tips
  form) — the same pills with the search glyph instead of the number; the panel's shadow colour,
  black at 45 %, has no role in `canvas.json` (`HaulColors.shadow`), a question for the designer.
- Not done here: the screen's loader (fetching `/ui/search` and `/ui/search/suggest` as the shopper
  types) — the app has no navigation yet; «Clear» does nothing until sign-in brings
  `DELETE /api/v1/me/recent-searches` (B-12).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/`,
  `composeApp/src/desktopTest/snapshots/design`.
