---
id: B-143
title: "fix: the product photo stands where its placeholder stood"
status: done
priority: P3
size: XS
stage: stage-12-qa-run
---

# B-143 — fix: the product photo stands where its placeholder stood

Found by the v0.6.0 run, TC-PDP-16 ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B on the stand and C locally, each independently). Under Fast 4G from a
category card, the placeholder's photo box and the real one share their left edge and width (B-71, B-104 hold), but
the box moves down 5 px when the page arrives: y 253–750 → 258–755 at 1440, 253 → 258 at 1280, 254 → 259 at 390. The
placeholder's breadcrumb bar is 12 px tall where the breadcrumb row it stands for is about 17 px.
`ProductPageLayoutTest` «the loading photo stands where the photo arrives» asserts only `left` and `width`.

- **The placeholder's rows are the page's heights**, the breadcrumb's first; the layout test compares the photo's
  top too.

- AC: the photo's box has the same top, left and width in the placeholder and the page at 1440, 1280 and 390; the
  layout test fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/ProductPageLayoutTest.kt`.

## Findings (2026-10-13)

- **The cause, measured** (`ProductPageLayoutTest`, desktop fixture, the page's own header): the placeholder's
  photo stood at y 253 at 1440 and 1280 and 254 at 390, the page's at 258 and 259 — the run's numbers to the
  pixel. The crumbs' line is 12 × 1.4 = 16.8 px, which `Text` draws 17 tall; the placeholder's bar was 12.
- **The fix**: a placeholder row standing for a line of text is that line's height — `lineOf(style)` in
  `ui/Basics.kt`, the height `Text` gives one line (its line height rounded as `Text` rounds it, else the face's
  own line) — with the artboard's bar centred in it (`LineSkeleton` in `shell/Shell.kt`). The measures are the
  page's, read from the views that draw them rather than copied: `crumbsStyle`/`crumbsTop` (`BreadcrumbsView`),
  `detailsTop` (`ProductDetailsView`), `titleStyle`/`titleTop`/`titleBottom` (`PageTitleView`, `BadgeTitle`),
  `kindStyle`/`kindsHeight`/`kindsBottom` (`KindChip`), `FILTERS_HEIGHT`/`FILTERS_BOTTOM`/`APPLIED_BOTTOM`
  (`CompactApplied`). No number of the page changed.
- **The other placeholders, the same way** (first content's top, placeholder → page, before the change):
  - *Category* — 519 → 521 at 1440 and 1280, 454 → 466 at 390 with no filter applied. Same cause, several rows:
    the crumbs (12 vs 17), the title (96 + 20 + 26 vs 101 + 18 + 24), the kinds' pills (44 and 40 vs the page's
    40 and 37), and on a phone the applied filters' row, which the page draws (24 px) even with none applied.
    Fixed; the first card now stands where the page's arrives at the three widths.
  - *Cart* — 357 → 362 at 1440 and 1280: the title's bar was 96 where the title's line is 101. Fixed. 390 already
    matched (50 = 50).
  - *Order* — 421 → 580 at 1440 (368 → 557 at 390): the crumbs and the meta line are 12-px bars on 16.8-px lines
    too (10 px), but the rest is the order's own content — the title takes two lines at 1440 and a lead follows —
    which no placeholder knows. Not changed; written in research §B-143.
  - *Category, content-dependent remainder*: applied filters (a row of chips on a phone, a second line of them at
    1024) and, on a phone, a name long enough to put the count under the title («Headphones», 28 px) bring the
    cards lower. The test uses «Audio» with no filter, the arrival from a category card.
  - *The catalog's root and the deals* (`TitleSkeleton`, a crumbs bar too) have no page fixture to compare with;
    not measured, not changed.
- **Outside the AC, not fixed — the product at 1024**: the placeholder draws 1440's three columns (photo 1.3fr),
  the page below 1280 draws two (B-82), so the photo arrives 444 px wide where its placeholder was 262
  (334 → 334 top since this change). A placeholder for the medium arrangement is an item of its own.
- **Tests**: `ProductPageLayoutTest` compares `top` too, at 1440, 1280 and 390 (red on the old code: 253 vs 258,
  253 vs 258, 254 vs 259). `LoadingPlaceTest`: a category's first card and the cart's lines at 1440, 1280 and 390.
  **Mutation**, each on the committed change, restored, `git status` clean: the product's crumbs row back to the
  bare 12-px bar (3 product tests red), the category's crumbs row (3 category tests red), the cart's title row
  (1440 and 1280 red; 390 green, the phone's bar was already the line), the kinds' pills back to 44/40 (3 category
  tests red), the phone's filters row back to 24 under it (390 red).
- **Goldens**: `viddikRecord --force` in a copy outside the mutagen replica on the Linux box; five changed byte-wise
  from this change and were brought back — `Product_Loading`, `Product_Loading_Phone`, `Catalog_Loading`,
  `Catalog_Loading_Phone`, `Cart_Loading` — each looked at: the content under the crumbs or the title 5 px lower,
  the kinds' pills 4 px (3 on a phone) shorter. `Search_Results` and `Search_Autocomplete` also differed byte-wise
  but not visibly and through no code this change touches; left as they were, `viddikVerify` accepts them.
- **Design parity**: `Product_Loading_Phone` 1.19 % → 5.36 % and `Cart_Loading` 1.06 % → 5.91 % (tolerance 5 %),
  the 5 px shift down their length; retired with B-143 named in `manifest.json` (research §1.6 counts 78/47).
  `Product_Loading` 1.28 → 3.52 %, `Catalog_Loading` 0.62 → 0.69 %, the phone's 0.78 → 0.78 %; the rest unchanged.
