---
id: B-07
title: "client: renderers for the Home and Catalog components; Loading / Error shells"
status: wip
priority: P1
size: L
stage: stage-2-browse
blocked_by: [B-05, B-06]
---

# B-07 — client: renderers for the Home and Catalog components; Loading / Error shells

Home and Category are the first screens drawn from server trees; Loading and Error are the client's own and are shared by every screen.

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: the Plus trial dialog (B-23).

- AC: `viddikDesignParity` within tolerance for every `Home_*` and `Catalog_*` artboard.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.

## Iteration 1 (2026-10-08)

Built: a renderer per Home and Catalog component, the client's Loading and Error shells, the phone's
filter sheet, and one parity fixture per artboard (17). `viddikDesignParity` after six rounds
(tolerance untouched: 5 % of pixels, ±16 per channel):

| Artboard | Mismatch | | Artboard | Mismatch |
|---|---|---|---|---|
| Home_Loading | 0.39 % | | Catalog_Loading | 0.48 % |
| Home_Loading_Phone | 0.66 % | | Catalog_Loading_Phone | 0.56 % |
| Home_Content | 2.32 % | | Catalog_Content | 3.51 % |
| Home_Content_Phone | **5.18 %** | | Catalog_Content_Phone | 4.80 % |
| Home_Guest | 2.72 % | | Catalog_Empty | 3.02 % |
| Home_Guest_Phone | **6.03 %** | | Catalog_Empty_Phone | **6.49 %** |
| Home_Error | 2.31 % | | Catalog_Error | 2.32 % |
| Home_Error_Phone | 4.98 % | | Catalog_Error_Phone | **5.18 %** |
| | | | Catalog_FiltersSheet_Phone | 4.66 % |

13 of 17 within tolerance; the item stays `wip`. What stopped it, by artboard:

- All four are phone artboards, where text covers a larger share of the frame, so the text residual
  (glyph edges, Archivo's and Bodoni's advances rounding differently from Chrome's) is close to the
  limit on its own; the diffs are spread evenly down the page rather than concentrated.
- Bodoni Moda's italic cut is not bundled (`composeResources/font` holds the roman only), so every
  accent — «*load*», «*filters*», «*Up to*», «*category*», «*day*», «*Every*», «*$186*» — is a
  synthesised oblique of the roman, a different drawing from the canvas's italic. It is the largest
  single block of red on Catalog_Error_Phone and Catalog_Empty_Phone. Next: bundle
  `BodoniModa-Italic[opsz,wght].ttf` from the same `google/fonts` commit as the roman.
- Catalog_Empty_Phone also: «Filters» and the sort split the row in halves, where the browser grows
  the sort to its unbreakable content (`min-width: auto`) and gives «Filters» the rest (156 / 191 px);
  `FlexHalves` measures the sort's minimum intrinsic width, which for a no-wrap row is its narrowest
  word. Next: measure the maximum intrinsic width there. Not re-measured after the analysis, by the
  five-round rule.
- Home_*_Phone also: the page runs 2–6 px short above the footer (deals row), and the footer
  wordmark is cut at a slightly different height.

## Done so far (2026-10-08)

- Contract (`shared/.../ui/BrowseComponents.kt`), because one tree has to lay out at both widths:
  `CampaignRow` (the campaign with its banners: side by side at 1440, stacked over a pair at 390),
  `CategoryGrid` (eight across, or rows of four), `FilteredResults` (the facets beside the results at
  1440, behind «Filters» in a sheet on a phone, with the sheet's «Show 48 items»); `ProductGrid.scroll`
  (the deals row scrolls sideways on a phone, «Picked for you» wraps); `accent` on the titled
  components (the words drawn in the italic); `SectionHeader.compactLinkLabel` («All 32»);
  `Facet.rangeStart` / `rangeEnd` (the slider's selection as fractions of its track). The server's
  home and category trees build them (`feature/catalog/screen/`), and the route tests walk into them.
- Client: renderers in `registry/HaulRenderers.kt`, kompot's standard renderers added to the registry
  (the page is a `column`) and a `KompotDesignSystem` over the canvas's roles
  (`theme/HaulDesignSystem.kt`); views in `feature/home/`, `feature/catalog/` and `ui/`; the shells in
  `shell/Shell.kt` (`HomeLoading`, `CatalogLoading`, `ErrorShell` with `ShellFailure`); the header's
  guest and pending forms. The deals countdown reads «now» from `LocalHaulNow`, never the clock;
  `CountdownTest` holds it at the canvas's 04:12:37 (mutation: parsing the server's instant without
  restoring its seconds fails it). The app's root provides a ticking «now» once it draws screens.
- Fixtures: `composeApp/src/desktopTest/.../ScreenFixtures.kt`, one per artboard at its size; content
  states decode wire bodies written with the canvas's copy (`resources/bodies/home_*.json`,
  `catalog_*.json`), the shells and the sheet are drawn directly. Goldens recorded on Linux.
- Found, and fixed for every screen (research, «Found in B-07»): Compose grows a paragraph whose line
  height is below the font's own, where CSS keeps the line box and lets glyphs overflow — `Text` now
  reports the line boxes; whole-pixel glyph positions widened every line of text — the fixtures place
  glyphs at fractional positions; an inline label in a block sits on the block font's strut —
  `InlineLine`. The six B-04 goldens were re-recorded for the first two.
- Token mapping: every colour is a `HaulColors` role; the only literals are the canvas's alpha tints
  of roles (white 9 % / 65 % over Cobalt, Ink 8 % / 55 % on a banner tile), kept as named constants
  in `HomeViews.kt`; sizes and weights come from the artboards through `HaulType`.
- Not done here: the Plus trial dialog (B-23); wiring the screen loader, navigation and a ticking
  «now» into `App` (the shells and renderers are ready for it).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.
