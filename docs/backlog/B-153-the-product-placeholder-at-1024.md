---
id: B-153
title: "fix: the product page's placeholder at 1024 is the page's two columns"
status: done
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

## Findings (2026-10-13)

- **The cause, measured** (`ProductPageLayoutTest` at 1024, the medium band through the fixture's `medium`): the
  placeholder's photo 262 px wide where the page's is 444 — the item's numbers. Top and left already matched
  (334 and the gutter): B-143's rows hold at 1024 as well.
- **The fix**: the page's column measures are named beside the view that draws them — `photoWeight(medium)` (1.3 at
  1440's three columns, 1 below 1280), `COLUMNS_GAP` (40) and `BUY_BOX_WIDTH` (384) in `ProductDetailsView.kt` —
  and `ProductLoading` (`shell/Shell.kt`) reads them under `LocalHaulMedium`: at 1024 the photo and the details
  share the row and the buy box's placeholder stands under the title's and the rating's, over the choices', where
  the page puts the buy box. 1440 and the phone draw what they drew.
- **Tests**: `ProductPageLayoutTest` «the loading photo stands where the photo arrives at 1024» (red on the old
  code: width 262 vs 444). **Mutation**, each on the committed change, restored, `git status` clean: the
  placeholder's photo back to 1440's weight at 1024 (red, 502 vs 444); the buy box back to a third column at 1024
  (red, 232 vs 444).
- **Goldens**: none — there is no loading golden at 1024 (B-82 added `Product_Description_1024`, not a loading
  one), and the 1440 and 390 placeholders are unchanged (`viddikVerify` accepts every golden as it is). The 1024
  placeholder and the page were drawn side by side and looked at: the same two columns, the buy box's card under
  the rating in both. No artboard draws 1024, so nothing to retire.
