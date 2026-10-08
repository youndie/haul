---
id: B-38
title: "design refs: re-render the Search references with grayscale text"
status: wip
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
- Anchors (planned): `composeApp/src/desktopTest/snapshots/design/`.
