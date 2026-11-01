---
id: B-179
title: "fix: a press on blank page space keeps the scroll where it is"
status: open
priority: P1
size: S
stage: stage-15-showcase
---

# B-179 — fix: a press on blank page space keeps the scroll where it is

A press on blank space low on a page — under the Plus block's «Every order.», the gap above the footer — scrolls the
page to the top (owner, 2026-11-01). The page's text selection (B-154) is the cause: Compose 1.12.1's
`SelectionManager.updateSelection` starts a selection at any press inside the `SelectionContainer` and calls
`bringDraggedPositionIntoView()`; on blank space the dragged position resolves above the viewport, and the page
scrolls after it — 352 requests for a 52 px rect above the viewport, from 2424 px to 0, in a probe on the real home
tree at 800×600. Without the `SelectionContainer` the same press keeps the scroll.

- Keep the scroll on a press that lands on no text, without losing what B-154 gave: a press on words still starts a
  selection, a drag to the window's edge still scrolls with it, a press on blank space still clears a selection.

- AC: a desktop test on the storefront's home tree, scrolled to the bottom, pressed on blank space, keeps its scroll
  (fails before the fix); `ContextMenusTest` and the selection's tests stay green; the stand's home page, scrolled to
  the bottom, keeps its place on a click under «Every order.».
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ContextMenus.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
