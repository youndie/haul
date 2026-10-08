---
id: B-06
title: "design refs: `Home_*`, `Catalog_*`, `Product_*` PNGs"
status: done
priority: P1
size: S
stage: stage-2-browse
blocked_by: [B-01]
---

# B-06 — design refs: `Home_*`, `Catalog_*`, `Product_*` PNGs

The client items of this stage are accepted against these references; exporting them once keeps the PNGs and the canvas in one commit.

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: references of later stages.

- AC: one PNG per artboard, at the artboard's size.
- Anchors (planned): `composeApp/src/desktopTest/snapshots/design`.

## Done (2026-10-08)

- 39 references in `composeApp/src/desktopTest/snapshots/design/`: every `Home_*`, `Catalog_*` and
  `Product_*` artboard at its `canvas.json` size, desktop and phone, rendered by
  `design-to-compose`'s `canvas-references.mjs --dir` with headless Chrome; `manifest.json` beside
  them records each artboard, size and warning (none).
- The sources were read from the Claude Design project «E-commerce витрина» (`get_file`, 40 files)
  into `design/.canvas/`, which git ignores. The project's `canvas.json` keeps sizes under
  `pages[].artboards[]`; a top-level `artboards` list was added to the local copy from those same
  numbers, the shape the script reads.
- Every PNG looked at: the three fonts load (no fallback face), every state is distinct, no broken
  image or empty frame.
- Anchors: `composeApp/src/desktopTest/snapshots/design/`.
