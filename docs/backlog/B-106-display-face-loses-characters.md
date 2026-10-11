---
id: B-106
title: "client: the display face draws every character it is given"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-106 — client: the display face draws every character it is given

Found in the QA pass of 2026-10-10 (v0.3.0). The display face draws no hyphen or dash: «WH-1000XM6» reads «WH 1000XM6», «Thanks, Sam — order #HL-48303 is
placed» reads with no dash and «#HL 48303» (confirmed at phone full resolution); B-66 reworded its own titles around it.
At small sizes its «4» can read as «1» ($31.49 looked like $31.19; the 4.8 rating as 1.8).

- AC: the face (or its subset) carries hyphen, en/em dash and the other punctuation the store prints, or such text
  falls back to the body face; prices and ratings legible at every size the canvas uses; a golden with those
  characters.
- Anchors: `composeApp/src/commonMain/composeResources/font/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulFontResources.kt`.

## Findings

- **The premise was wrong: no glyph is missing.** `bodoni_moda.ttf` and `bodoni_moda_italic.ttf` are the full
  Bodoni Moda 2.005 files of `google/fonts` (563 glyphs, `opsz` 6–96, `wght` 400–900); their `cmap` maps the hyphen,
  U+2010–2014, the minus, «#», «$», «%», «+», the quotes and every digit, read from the files' own tables. A fuller
  file would change nothing.
- **What vanishes is the hairline.** The face's horizontal strokes are hairlines, and their thickness falls with the
  optical size: the hyphen (all hairline) is 72/2000 em at `opsz` 6, 38 at 11, 20 at 22, 13 at 30, 8 at 44, 4 at 96
  — about 0.2 em ÷ `opsz` at every weight and in the italic (outlines interpolated through `avar` and `gvar`). The
  canvas draws `font-optical-sizing: auto`, `opsz` = the pixel size, so every hairline is about 0.2 px at every size:
  the hyphen and the dashes disappear, a 4 keeps only its stem («$31.19», «1.8»), a comma loses its tail («2.480» for
  2,480), and the design references themselves draw it so. The phone's density does not save it: 0.6 device px at 3×.
- **Decision: figures and punctuation take a legible cut; letters keep the canvas's.** `HaulType.legibleFigures`
  draws every character of a display style that is not a letter or a space at `legibleOpticalSize(size)` — the
  largest loaded optical size whose hairline is at least 1 px, `size / 5` rounded down (6 below 30 px, where the
  hairline is still 0.8 px at 22; 11 at 56, 18 at 96). The app's `Text`, `BalancedText` and a fact tile's fitted
  title (`FactTitle`) measure and draw through it. The cut is the same face, so a price still reads as Bodoni; and a
  figure's or a mark's advance does not change along the axis (a «#» or «/» by at most half a pixel), so no line
  breaks anywhere it did not. The body-face fallback the item offered would fix the dashes but not the 4.
- **Rejected: a smaller optical size for the whole face.** Tried first (`opsz` = `size / 5` for everything): the
  letters' advances grow by up to 2 % towards the small cuts, 139 of 156 goldens moved, and titles re-broke —
  `Account_Error_Phone`'s «Your account didn't load» went from two lines to three. Letters therefore keep the
  canvas's cut, with what it costs, next point.
- **Left as it is: an H's bar, an A's, an e's.** They are letters' hairlines, and still vanish: the order code's
  italic «#HL-48303» reads «#IIL-48303» at 56 px and up, a product title's «WH» as «WII». Fixing them is the rejected
  variant above or a per-letter exception; a decision for the canvas's owner, not taken here. `DisplayFace_Characters`
  shows it.
- **B-66's wording**: the sign-in page's title was reworded to avoid a hyphen («The browser blocked the window»); the
  wording stays, its comment in `SignInPage.kt` says why it was chosen. Going back to «sign-in window» is a copy change.
- **Tests**: `LegibleFiguresTest` — a display style spans exactly its non-letters, a span's own family is kept, the
  body face is left alone, and a line of prices, dashes and times measures the same width (±1 px) in both cuts at
  every size the canvas uses from 20 to 128 px. Mutation (`legibleFigures` returning its text): the first two red,
  86 goldens red; restored, tree clean.
- **Goldens**: new `DisplayFace_Characters` (1440 × 900, no artboard): the hyphen, en and em dash, minus, «#», «$»,
  the comma and a 4 at 20–56 px, the figures at 72, 96 and 112 px, and an accented order title. 85 goldens moved, each
  looked at as before/after crops of the changed rows: only display-face figures and punctuation changed — prices,
  totals, ratings, the pickup code, delivery windows, the product title's hyphen, the order title's dash and code,
  the hero's «−70%», quotes — at most 0.53 % of a golden's pixels (`Order_Placed_Phone`), no text moved or re-broke.
  The 71 others did not move. Recorded on the Linux build machine in a copy outside the mutagen replica; with the
  change reverted the same record left every committed golden as it was.
- **Hypothesis of B-04 («wasmJs honours `opsz`»)**: not checked in a browser here. The QA pass's missing dash on a
  phone is indirect evidence that it does — at the default `opsz` 11 the 56 px title's dash would be 1 px wide.

