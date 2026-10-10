---
id: B-106
title: "client: the display face draws every character it is given"
status: open
priority: P2
size: S
stage: stage-11-qa
---

# B-106 — client: the display face draws every character it is given

Found in the QA pass of 2026-10-10 (v0.3.0). The display face draws no hyphen or dash: «WH-1000XM6» reads «WH 1000XM6», «Thanks, Sam — order #HL-48303 is
placed» reads with no dash and «#HL 48303» (confirmed at phone full resolution); B-66 reworded its own titles around it.
At small sizes its «4» can read as «1» ($31.49 looked like $31.19; the 4.8 rating as 1.8).

- AC: the face (or its subset) carries hyphen, en/em dash and the other punctuation the store prints, or such text
  falls back to the body face; prices and ratings legible at every size the canvas uses; a golden with those
  characters.
- Anchors: `composeApp/src/commonMain/composeResources/font/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulFontResources.kt`.
