---
id: B-157
title: "fix: controls keep the hand over their words, and crumbs under a title take their presses"
status: done
priority: P1
size: S
stage: stage-13-web-habits
blocked_by: [B-155]
---

# B-157 — fix: controls keep the hand over their words, and crumbs under a title take their presses

After B-155 the owner saw that much of what had been a button — the hand over it — showed the text cursor
(2026-10-31). Walking it found a second, worse regression of the same change on the stand (v0.7.0): a crumb on a
category page pressed with the mouse did nothing.

- **The hand over a control's words.** A selectable text asks for the text cursor over itself, and a hover icon gives
  way to its descendants' unless it overrides them. Every control drew its words inside itself — «Catalog», «Sign
  in», a chip, a card, the sort — so the text cursor showed wherever the words were. The store's presses
  (`pressable`, `toggles` in `ui/Links.kt`) now ask for the hand over their descendants too (`handOverWords`), and so
  does the header's account slot, which had no hand at all. A dialog's scrim keeps its hand without overriding: the
  dialog's words over it are words.
- **Crumbs under a title.** A category's title is set at `line-height: .9`; its face's own line reaches above the
  line box it is laid in, over the crumbs. `Text` (`ui/Basics.kt`) reports the line boxes as its size and lets the rest overflow, as CSS does, and once the title's words were selectable that reach took the pointer:
  the crumbs above it were neither pressed nor hovered. Only a clipping layer stops the pointer short of a node's
  bounds, and a clip cuts glyphs (a «$» in the top line lost its stroke, and every word under a clip drew its edges a
  shade differently: 161 of 168 goldens moved). So a cropped `Text` draws its glyphs into a graphics layer of its
  own, under a clip that keeps the pointer from the first line box down, and draws that layer outside the clip where
  it always drew (`keepsReachBelowLineTop` in `ui/Basics.kt`).
- AC: the hand over every control's words and the text cursor over words that are not controls (walked on the home,
  category and product pages); a crumb under a category's title opens its page when the mouse presses it; every
  golden is pixel-identical to a recording without the change.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Basics.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/DrawnActionsTest.kt`.

## Findings

- **Why the tests let it through.** Every press in the suites found its node by name and pressed its centre, as a
  pointer does — but no suite had a crumb under a title. The new test in `DrawnActionsTest` presses a crumb laid as
  the category page lays it; it failed before the change and passes after it.
- **The hand is not in a test.** Compose keeps the pointer icon behind an internal service; the hand was walked in
  the browser instead, against v0.7.0 on the stand as the control: «Catalog», «Sign in» and «Laptops from $399» read
  `text` there and `pointer` with the change; the crumbs, the brand, «2,341 reviews» and the heart read `pointer`;
  the title, a badge and the search field read `text`.
- **Pixels.** Every golden recorded twice in one checkout on the build box, with and without the change: 168 of 168
  identical pixel for pixel. Recorded with the clip alone (no layer of glyphs): 161 differed, up to 68 of 255 on a
  channel, the «$» of «FREE DELIVERY OVER $35» cut.
- **What is drawn into the layer** follows the text: a word double-pressed in the title draws its selection there.
- **Not changed:** a selection in a title draws its highlight over the face's whole line, above the line box too, as
  it did before.
