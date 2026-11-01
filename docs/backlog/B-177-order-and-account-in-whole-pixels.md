---
id: B-177
title: "refactor: lay the order and account pages out in whole pixels"
status: done
priority: P2
size: M
stage: stage-15-showcase
---

# B-177 — refactor: lay the order and account pages out in whole pixels

B-175 measured every canvas-only workaround and found one whose removal buys code back: the fraction bookkeeping that
lays the order and account pages out the way the browser's CSS did, a fraction of a pixel at a time (`CssColumn`,
`CssBox`, `cssLines` in `CssLayout.kt`, and `Tiles`, `GridPair`, `PhoneRow` in the two pages). The canvas is no longer
the reference (B-85), so the pages may sit a few pixels from where they are now; the owner said yes (2026-11-01).

- The two pages are laid out with Compose's own rows, columns and spacing in whole pixels; the code that only kept
  fractions goes, and the 23 goldens it moves (0.29–5.99 %, B-175's table) are re-recorded with `viddikRecord --force`
  and each looked at.

- AC: `CssColumn`, `CssBox` and `cssLines` are gone or kept only where a reason other than the canvas is written next
  to them; the re-recorded goldens show the same pages a few pixels apart, nothing missing or overlapping; `check` is
  green.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/AccountViews.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderViews.kt`.

## Findings

- **Done: `ui/CssLayout.kt` is deleted** — `CssColumn`, `CssBox`, `cssLines`, `css()` and `lines()` with it — and
  both pages are `Column`s and `Row`s with `Arrangement.spacedBy` and `padding`: a CSS margin is a top padding or the
  column's gap, `GridPair` a `Row` (`weight(1f)`, a 40 px spacer, `Alignment.CenterVertically`), an order fact a
  hairline over an icon and two texts. 350 lines out and 113 in across the three files (−237 net).
- **Two layouts of their own stay, in whole pixels, for reasons other than the canvas** (said next to each): the
  wide `Tiles`, whose fill is a sibling of its words so the fill alone is pressed and named (B-126), stretched to the
  tallest tile; and `PhoneRow`, whose right column is as wide as the wider of the status and the way on, a column two
  grid rows share, which no `Row` of `Column`s says.
- **Goldens: the 23 B-175 measured, with the same percentages.** Recorded on the WSL box with `viddikRecord --force`
  in a copy outside the mutagen replica (every other golden came out byte for byte the committed one), then verified
  against the old goldens to read viddik's count: the order's 18 drawn states (all but its error, loading and
  not-found) and 5 of the account's, 0.29 % (`Order_Placed`) to 5.99 % (`Order_ReadyForPickup_Phone`). Each was looked
  at beside its diff: the same pages, a pixel or two lower under the title or between cards, nothing missing, clipped
  or overlapping.
- **Parity (report-only), the same box, base against branch:** 47/47 within 5 % on `main`, 45/47 here.
  `Account_Content_Phone` went 4.06 → 5.37 % and `Account_Orders_Phone` 4.46 → 5.75 %; both moved to
  `design/retired/` with a `retired` line naming B-177, as CLAUDE.md's «Design references» says. The order's
  artboards were all retired already (B-78); the account's wide ones moved within the line (`Account_Content` 1.74 →
  2.85 %, `Account_NotMember` 1.90 → 2.55 %).
- **The research's workaround list** (§3, «The client's workarounds for the canvas») marks the block flow removed;
  B-175's follow-up, B-18's and B-19's findings say B-177 took it out.
- **Verified on the WSL box**: `./gradlew check :server:installDist :composeApp:wasmJsBrowserDistribution` green
  (1,346 tests, none failed, `viddikVerify` on the new goldens); `make check` there too.
