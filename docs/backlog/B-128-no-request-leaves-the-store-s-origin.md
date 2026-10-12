---
id: B-128
title: "fix: the storefront loads nothing from another origin"
status: wip
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
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme`.
