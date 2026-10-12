---
id: B-128
title: "fix: the storefront loads nothing from another origin"
status: done
priority: P3
size: XS
stage: stage-12-qa-run
---

# B-128 — fix: the storefront loads nothing from another origin

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)): showing search results or the suggest panel loads
`https://fonts.gstatic.com/s/notosansmath/…woff2` — Compose's fallback font for a glyph the bundled faces lack —
the only request the storefront makes off its own origin. A shopper's browser telling a third party it opened a
search is not something the store chose.

- **Find the glyph and bundle what draws it** (or drop the glyph). Rejected: a CSP that blocks the request — the
  glyph would draw as a box.

- AC: a walk through home, search, a product, the cart and checkout makes no request off the store's origin.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulIcons.kt`, `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/theme/BundledGlyphsTest.kt`, `scripts/origin-check.py`.

## Findings (2026-10-12)

- **The glyph**: «↵» (U+21B5) after «All N results» in the suggest panel (`SearchSuggestPanelView`), set in the
  labels' JetBrains Mono. None of the four bundled faces has it (checked against each file's `cmap`), so the
  browser's paragraph left it unresolved and Compose fetched Noto Sans Math for it. Every other character the
  code writes is in a face; the arrows and «Σ», «∪» in the server's sources are in comments and logs.
- **Why the page asks Google at all**: Compose 1.12 on the web installs a fallback-font downloader in every
  `ComposeViewport` (`installFallbackFontDownloader`): each code point a paragraph leaves unresolved is mapped to
  a Noto face and loaded from `https://fonts.gstatic.com/s/` (a constant). It cannot be turned off — the
  downloader is `internal` (`defaultFallbackFontDownloader`, `@TestOnly` setter) and nothing in
  `ComposeViewport`'s configuration names it. A CSP would block it and draw a box (rejected in the item).
  Written into research §3.
- **The decision: drawn, not bundled.** The arrow is `HaulIcons.returnKey`, a path traced from the canvas's
  glyph (`Search_Autocomplete`: 7 × 5 px, a 1 px stroke) in a box as wide as the mono advance. A face for one
  glyph would be a fifth font request, a licence, and a face that loads after the first frame — until it lands
  the glyph would be unresolved, which is what calls the downloader. The canvas's character stays: what is drawn is
  its «↵», not a substitute that one of the faces has.
- **The guard**: `BundledGlyphsTest` lays every string literal of `composeApp` commonMain, `shared` and
  `server` main (comments skipped, escapes decoded) out in a Skia paragraph over the four bundled files and no
  system fallback — the browser's own resolution — and lists what stays unresolved, by file and line. Its
  control: «↵» is reported, the zero-width space, word joiner and no-break space the code uses are not (they
  resolve without a glyph). The server's sources are an input of `desktopTest` now, so a changed server string
  reruns it. Mutation: the old `Text("↵", …)` restored — red, naming `SearchViews.kt:422: U+21B5`; restored,
  green, tree clean.
- **The walk** (`scripts/origin-check.py`, standard library, B-28's DevTools client; also TC-ERR-10): on the
  Linux box, the server's `installDist` beside a seeded PostgreSQL and shildik in Docker, headless Chromium
  (Playwright's 1228), the cache off. Home, `/c/electronics`, the search field with `head` typed and the panel
  open, Enter, `/p/p-sony-wh-1000xm6`, the cart, `/checkout`:

  | bundle | who | requests off the origin |
  |---|---|---|
  | `origin/main` (`5633e17e`) | guest | 1 — `fonts.gstatic.com/s/notosansmath/…woff2`, in the suggest step |
  | this branch | guest | 0 |
  | this branch | Maya (her checkout drawn, tokens from the local shildik) | 0 |
  | this branch, `--control` (U+21B5 typed into the search field) | guest | 1 — the same Noto Sans Math file |

- **Goldens**: `Search_Autocomplete` and `Search_Autocomplete_Phone` moved — they drew «↵» from the Linux box's
  system face, a pixel off the canvas; the path sits where the canvas has it, and the label the row centres
  moves back with it (1440: label 782 vs the canvas's 783, was 780; phone: 127, as the canvas). No other golden
  moved under `viddikRecord --force`. Parity: `Search_Autocomplete` 3.92 % → 3.91 %, `_Phone` 10.84 % → 10.79 %
  (its DIFF is older than this item).
- **Not held**: text a shopper types or a seller names that the faces lack — Archivo and Bodoni Moda are Latin
  only, JetBrains Mono adds Greek and Cyrillic, none has CJK or emoji — still fetches a face. That is outside
  this item's walk; a fix would be a bundled fallback face (megabytes for CJK) or Compose growing a switch.
