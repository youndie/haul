---
id: B-168
title: "fix: the arrival is quick above the fold, does not replay on Back, and hover is for a mouse"
status: open
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
