---
id: B-180
title: "feat: a dark theme for the whole store"
status: wip
priority: P2
size: L
stage: stage-15-showcase
---

# B-180 — feat: a dark theme for the whole store

The store is light only (owner, 2026-11-01: a dark theme, for everything). The client's colours are one light set
(`HaulColors`), and a server tree that names a colour names a light one.

- A dark set of the theme's roles, chosen so every page reads as the light one does (contrast at least what the light
  one has), and every page drawn through the roles — the shell, every renderer, the dialogs, the skeletons, the
  desktop window. It follows the system's setting (the browser's `prefers-color-scheme`, the desktop's), with a
  switch to override it that is kept per browser; the page's first paint (`index.html`'s frame and loader) follows
  too, so there is no white flash on a dark system. A colour a tree carries from the server is a role, not a value,
  or has its dark counterpart decided in the research.
- Goldens: a dark variant of the screens that show what changed (not every golden twice), and the research says
  which and why.

- AC: every page of the store is usable in dark — checked by walking the stand's pages in a browser with
  `prefers-color-scheme: dark` and in the desktop app; no hard-coded light colour left outside the theme (a test or a
  check says so); the switch is kept across reloads; light pages unchanged (their goldens do not move).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulColors.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/HaulTheme.kt`.
