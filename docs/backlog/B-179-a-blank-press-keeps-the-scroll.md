---
id: B-179
title: "fix: a press on blank page space keeps the scroll where it is"
status: done
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

## Findings

- **What the real page has that a simple one lacks: a selectable text of no width.** `InlineLine` (B-07, kept by
  B-175) sits an inline run on its block's strut, a `Text(" ")` drawn `width(0.dp)`. Inside the page's
  `SelectionContainer` that strut is a selectable like any text — 0×30 px in the Plus block, 0×17 px in the
  campaigns' eyebrows; across the 50 screen bodies at 1440 and 390 px, every zero-width selectable on any page is one
  of these struts. A press under «Every order.» resolves the selection to the strut below it (read off the
  `SelectionManager` by reflection in a probe: `SingleSelectionLayout`, the current selectable a `" "` 0×30 px at
  (48, 237) for a press at y 185; a press at y 300 resolved to «Haul Plus» instead and kept the scroll).
- **The mechanism, Compose 1.12.1.** The container's own mouse observer (`DefaultMouseSelectionObserver` over the
  registrar, no selectable id) starts a selection at any press that no text took; `SelectionManager.updateSelection`
  then, with `ComposeFoundationFlags.isSelectionAutoScrollEnabled`, calls `bringDraggedPositionIntoView()`: a rect
  from the pressed point to the nearest point of the current selectable, in that selectable's coordinates, handed to
  its `BringIntoViewRequester`. `NodeCoordinator.localBoundingBoxOf` answers `Rect.Zero` for a child whose bounds are
  empty — a node of no width is empty from the start — so the rect reaches the page's scroll as if the strut sat at
  the scroll's top: offset −52, size 52 for a press 52 px above the strut, the same on every step while the page
  moved, 352 calls of the scroll's `BringIntoViewSpec` from 2426 px to 0. Every blank press at x 400, y 65…233 px
  (under «Every order.») did it; a press beside a text
  of any width brings a rect that is already on screen into view and nothing moves.
- **The fix**: the strut is kept out of the selection (`DisableSelection`, as the footer's name and the hero's figure
  already are). It holds no words to select or copy, and it draws the same: no
  golden moved (`viddikVerify` in `check`). Not chosen: turning the autoscroll flag off (it is what scrolls a drag
  past the window's edge, B-154's), intercepting the selection's bring-into-view requests (they cannot be told
  from a field's or the product tabs' own), keeping a blank press from the selection (it is what clears a
  selection). A text that measures zero wide anywhere else on a page would do the same; none does today.
- **Tests** (`shell/BlankPressTest`, the server's home tree and header at 800×600): every blank point under «Every
  order.», the page at its foot, keeps the scroll — 15 of them scrolled to 0 before the change; a drag over the title
  selects it and a blank press clears it; a drag from words past the window's foot scrolls the page, seen to fail
  with the autoscroll flag off. `ContextMenusTest` and `DrawnActionsTest` stay green.

