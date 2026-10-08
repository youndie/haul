---
id: B-01
title: "design: canvas per the design briefs — static artboards, every screen state, desktop and phone, the three disagreements resolved"
status: done
priority: P1
size: L
stage: stage-1-skeleton
---

# B-01 — design: canvas per the design briefs — static artboards, every screen state, desktop and phone, the three disagreements resolved

The canvas is what every client item is accepted against: one static artboard per screen state at both widths, so a reference PNG can be rendered from it without a person exporting anything.

- Not covered: the reference PNGs themselves — each client item exports the ones it needs.

- AC: every `<Screen>_<State>` and its `_Phone` twin exists as a static artboard with its height fixed.
- Anchors: `canvas/canvas.json` in the Claude Design project.

Done: 125 static artboards in the Claude Design project «E-commerce витрина», listed in `canvas/canvas.json` there; their sizes are in each screen document.
