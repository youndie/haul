---
id: B-153
title: "fix: the product page's placeholder at 1024 is the page's two columns"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-153 — fix: the product page's placeholder at 1024 is the page's two columns

Found by B-143 (findings). At 1024 the product page's loading placeholder still draws the 1440 three-column layout
(gallery, details, buy box), while the page itself draws two columns there (B-82's 1024 band: the buy box under its
title beside the photo, the facts under the copy) — so the photo arrives 444 px wide where its placeholder was 262,
and everything beside it jumps. B-143 made the placeholder's rows the page's own heights at 1440, 1280 and 390, and its
layout test compares the photo's top, left and width at those widths only.

- **The placeholder takes the page's band**: at 1024 (and anywhere B-82's medium band applies) it draws the page's
  two-column arrangement from the same measures the page uses, as B-143 did for heights.
- AC: at 1024 the photo's box has the same top, left and width in the placeholder and the page; `ProductPageLayoutTest`
  covers 1024 and fails on today's code; the loading goldens at 1024 (if any) recorded with --force and looked at.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/ProductPageLayoutTest.kt`.
