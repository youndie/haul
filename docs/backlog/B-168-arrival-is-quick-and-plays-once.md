---
id: B-168
title: "fix: the arrival is quick above the fold, does not replay on Back, and hover is for a mouse"
status: wip
priority: P2
size: S
stage: stage-14-motion
---

# B-168 — fix: the arrival is quick above the fold, does not replay on Back, and hover is for a mouse

The designer's review of B-158 (2026-11-01): the cascade over the first screen takes about 1.4 s before the last
block settles, which reads as slowness rather than life; going Back to a page plays it again; and a phone's tap
leaves a card lifted as if hovered.

- **Faster and once.** Blocks in the first window arrive in 250–350 ms with a 30–40 ms stagger; a page come back to
  (Back, or a page already seen in the session) arrives still; hover effects follow only a mouse pointer, not touch.
  `Motion.kt`'s constants and the reveal's memory change; the rest of B-158 stays.

- AC: the last block above the fold settles within 600 ms of the tree arriving (MotionTest with the clock); Back
  shows the page at rest at once; a touch press does not leave a hover lift; goldens unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Motion.kt`, `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/MotionTest.kt`.

## Findings

- **The cascade's numbers, on the held clock.** `MotionTest` steps the home (1440 × 1600, the fixture body) frame by
  frame from the frame its tree is first drawn to the first frame that is the still home: 1,264 ms with B-158's
  constants (620 ms reveals, 70 ms apart up to the ninth, 260 ms down the window), 544 ms now (280 ms, 35 ms apart up
  to the sixth, 100 ms down the window). The designer's 1.4 s was the same cascade in a taller window.
- **There is no page cache to key on.** Every visit, back and forward included, loads its tree again
  (`shell/Storefront.kt`); a screen is kept only while its path is. So «seen» is the storefront's own memory of the
  addresses whose trees it drew (`SeenPages`), read once per visit and per address an update records, before the tree
  drawn marks it — a filter's new address is a page not seen, and its new cards rise in as before.
