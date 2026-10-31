---
id: B-156
title: "chore: walk the selection and its toolbar on a phone"
status: open
priority: P3
size: S
stage: stage-13-web-habits
blocked_by: [B-155]
---

# B-156 — chore: walk the selection and its toolbar on a phone

B-155 made every page's words selectable; on a phone a long press selects, and Compose's own touch toolbar
(`WebTextToolbarArea`) offers what it offers. Neither could be pressed from the browser pane, which drives the page
with a mouse in its phone size too (B-155, findings).

- **Walk** on a phone (or a touch emulation that sends touches): a long press selects a word; the toolbar's entries
  do what they say (Copy, Select all); a field's toolbar (Cut, Copy, Paste); the page still scrolls under a finger,
  and a press on a control is a press.
- **Decide** what is kept: Compose's toolbar as it is, or the store's menu on a long press as on the right button.
- AC: the walk written into the findings with the device and the browser; what was decided in `research` D9 or here.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ContextMenus.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulTextField.kt`.
