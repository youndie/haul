---
id: B-144
title: "fix: the review photo viewer keeps the keyboard after a press on its arrows"
status: wip
priority: P2
size: XS
stage: stage-12-qa-run
---

# B-144 — fix: the review photo viewer keeps the keyboard after a press on its arrows

Found by the v0.6.0 run, TC-PDP-19 ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B and C independently, 1440 and 390). On a viewer just opened the arrow
keys step and Escape closes; after a mouse press on «›» or «‹» the arrow keys do nothing and Escape does not close it
(it stays on «Photo 2 of 2»), and a press on the photo does not bring the keys back — only «×» or a press outside close
it. With two photos every press lands on an end where the pressed arrow turns disabled, so the key focus goes with it
(`document.activeElement` stays the canvas; the keydowns reach the page). The case's own order (press «›», «‹», then
the keys) hits it; `ReviewControlsTest` keys without a press first.

- **The viewer holds the key focus while it is open**, whichever of its controls was pressed or disabled since.

- AC: open, press «›», then ArrowLeft steps back and Escape closes, at 1440 and 390; a client test with that order
  fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewPhotos.kt`.
