---
id: B-38
title: "design refs: re-render the Search references with grayscale text"
status: done
priority: P2
size: S
stage: stage-3-search
blocked_by: []
---

# B-38 — design refs: re-render the Search references with grayscale text

B-13 found that Chrome on the Linux build machine draws text with coloured (LCD) antialiasing by
default, while the fixtures draw grayscale: about 90 % of the text pixels in a Linux-rendered reference
carry colour fringes and inflate `viddikDesignParity` (Catalog_Content_Phone read 4.66 % against an LCD
render and 1.37 % against a grayscale one). The Cart references were re-rendered with
`--disable-lcd-text`; B-10's ten `Search_*` references were rendered on Linux the old way and still
carry the fringes, so their parity numbers (up to 4.74 %) overstate the gap.

Re-render the ten `Search_*` references through `canvas-references.mjs` with the same Chrome and
`--disable-lcd-text` (B-13's findings name the binary and the flag), look at every PNG, re-measure, and
record the numbers. No hand edits, tolerance untouched.

- AC: the ten `Search_*` references have no colour fringes on text; parity re-measured and within 5 %;
  B-10's item gains a line pointing here.
- Anchors: `composeApp/src/desktopTest/snapshots/design/`.

## Done (2026-10-08)

`viddikDesignParity --component "Search*"` (default tolerance, 5 % of pixels at ±16 per channel,
untouched; references not edited), on the Linux build machine against the same code, before and after
the re-render:

| Artboard | LCD text (before) | Grayscale text (after) |
|---|---|---|
| Search_Loading | 0.66 % | 0.45 % |
| Search_Loading_Phone | 0.52 % | 0.26 % |
| Search_Results | 3.42 % | 2.77 % |
| Search_Results_Phone | 4.71 % | 2.97 % |
| Search_Autocomplete | 3.38 % | 2.52 % |
| Search_Autocomplete_Phone | 4.74 % | 2.65 % |
| Search_NoResults | 2.50 % | 1.95 % |
| Search_NoResults_Phone | 4.04 % | 2.83 % |
| Search_Error | 1.84 % | 1.41 % |
| Search_Error_Phone | 3.76 % | 2.44 % |

The before column reproduces B-10's numbers exactly. What is left is what B-10 named: glyph edges,
the card titles' sub-pixel offset and the header's Orders / Saved / account shortcuts sitting lower
than the canvas's.

- References: the ten `Search_*` artboards rendered again from the canvas sources (`.canvas/`, copied
  from the checkout that holds them; their sizes were already in `canvas.json`'s top-level `artboards`)
  with B-13's recipe: the same `canvas-references.mjs` with `--disable-lcd-text` added to its Chrome
  flags in a copy, Chrome 152.0.7977.75 (`~/.cache/shashki/chrome/152.0.7977.75`), node 24.16.0
  (`~/.gradle/nodejs/`), `--only "Search_*"`. Sizes unchanged (each 1440 or 390 wide, as before); every
  PNG looked at — the display serif, the grotesk and the mono all loaded, nothing broken.
- `manifest.json` is unchanged: the run's ten entries are identical to the committed ones (same
  artboard, size, no warnings), and the other 55 were left as they were.
- Goldens unchanged: `git status` shows only the ten references and the backlog; `viddikVerify` green.

## Findings (2026-10-08)

- **Fringes, counted.** A pixel on the edge of black text is one next to a neutral near-black pixel
  (every channel < 90, spread ≤ 8) with a neutral near-white pixel (every channel > 245, spread ≤ 4)
  in its 3×3; it is fringed when its own channel spread exceeds 16 — a grayscale blend of black and
  white stays neutral. Before: 6,142 of 12,203 such pixels fringed across the ten references (50 %;
  25–62 % per artboard). After: 0 of 34,492 (0.00 % on every artboard). The edge count grows because
  the LCD fringes had taken the near-white neighbours the definition needs.
- A looser count — any pixel next to a dark core, background whatever it is — still finds 5–20 %
  "fringed" after the re-render. That is not LCD text: it is the lime-on-black antialiasing of the top
  bar («BROOKLYN, NY 11211») and of the coloured badges, a blend of two colours, the same in both
  renders. Counting fringes has to hold the background neutral.
- Old against new reference, pixels differing by more than 16 in a channel: 0.47–3.73 % per artboard,
  all on glyphs; no block moved.
