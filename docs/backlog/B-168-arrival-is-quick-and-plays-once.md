---
id: B-168
title: "fix: the arrival is quick above the fold, does not replay on Back, and hover is for a mouse"
status: done
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
  to the sixth, 100 ms down the window). The designer's 1.4 s is the old cascade's worst case, 560 + 260 + 620 ms; the new one's is 175 + 100 + 280 =
  555 ms, plus the frames before a block is first placed.
- **There is no page cache to key on.** Every visit, back and forward included, loads its tree again
  (`shell/Storefront.kt`); a screen is kept only while its path is. So «seen» is the storefront's own memory of the
  addresses whose trees it drew (`SeenPages`), read once per visit and per address an update records, before the tree
  drawn marks it — a filter's new address is a page not seen, and its new cards rise in as before.
- **Photos replayed what the blocks no longer did.** Walked in Chromium, the home back from a category had its
  blocks at rest from the first frame but its photos fading in again for half a second: Coil decodes them anew on
  every visit (the bytes come from the browser's cache), and B-158's fade draws a photo that was not already decoded.
  A photo on a page that arrives at rest is drawn as it arrives (`ui/ProductPhoto.kt`). `MotionTest` does not see
  this: its photo loader draws synchronously.
- **The phone's lift was not reproduced.** Compose 1.12.1's web input (`ComposeWindowInternal.web.kt`) sends a
  finger's pointer events as touches and drops their enter and exit, and in the scene a touch's enter arrives as a
  move; `hoverable` reacts to enter and exit only. So a finger alone never started a hover: a touch tap on a card's
  «+» in headless Chromium with touch emulation left the card at rest on v0.7.2 as on this branch. What the old
  modifier did keep is a hover the mouse left over what a finger then taps (a touch-screen laptop): `MotionTest`
  «a touch press leaves no hover lift» fails with Foundation's `hoverable` (35,021 pixels off) and passes with the
  mouse-only node. If the designer's phone shows a lifted card again, the next step is a real device with remote
  debugging, logging the pointer events the canvas gets.
- **Walked** in headless Chromium on the WSL box over CDP (`Cdp` of `scripts/measure-first-load.py`), v0.7.2's
  image against this branch's (`scripts/image-check.sh`), each on a PostgreSQL of its own: back from a category to the
  home, the first window (below the header) at 150, 300, 450, 600 ms and 2.5 s after `history.back()` — v0.7.2 four
  frames still arriving, this branch all five equal to the page at rest; a touch tap on a card's «+» at 390 — the card
  at rest on both; the mouse over the «From $399» banner — lifted on both, at rest again once it leaves.
