---
id: B-143
title: "fix: the product photo stands where its placeholder stood"
status: wip
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
