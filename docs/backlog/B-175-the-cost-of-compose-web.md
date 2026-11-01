---
id: B-175
title: "docs: audit the pixel workarounds since B-85 and write down what Compose for Web cost"
status: open
priority: P3
size: M
stage: stage-15-showcase
---

# B-175 — docs: audit the pixel workarounds since B-85 and write down what Compose for Web cost

A good part of the client is workaround for matching the canvas to the pixel — cropped line boxes, clipped layers,
hand-measured glyphs — and since B-85 the canvas is no longer the reference, so some of them may have lost their
reason. The architect's review (2026-11-01) also noted that the list of what Compose Multiplatform 1.12 in the
browser made hard (text selection, cursors, focus, title, first load) is worth more to other developers than any one
fix.

- Each workaround is listed with its reason and whether it still holds; those that do not are removed in this item
  if the goldens stay within tolerance, or turned into items. The list of costs becomes a section of the research
  that the README links.

- AC: the research has the audit and the cost list; removed workarounds leave the goldens within `viddikVerify`.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/`, `docs/research/research-architecture.md`.
