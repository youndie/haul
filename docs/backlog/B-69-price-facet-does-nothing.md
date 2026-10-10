---
id: B-69
title: "client + server: the price filter can be used"
status: done
priority: P1
size: M
stage: stage-10-review
---

# B-69 — client + server: the price filter can be used

The price facet draws «from» and «to» fields and a slider with two thumbs. None of them works: the fields are not
editable, the thumbs cannot be dragged (`feature/catalog/FacetPanelView.kt:121-180`), and the server sends no action
for the range facet (`CatalogScreen.kt:234-245`); `price_min`/`price_max` can only be set through the address.

- AC: typing a bound and confirming (Enter or leaving the field), or releasing a thumb, applies the range in place
  with a `load` (B-63); the applied range shows as a removable chip; the phone filter sheet has the same; client and
  server tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`.

## Findings (2026-10-10)

- **Decision: a URL template, not a `load` per bound.** kompot's `load` is an address and nothing else, and a
  bound is any whole number typed, so no list of `load`s could hold the ranges; one per step of the slider would
  have capped the fields to the steps. `Facet.range` (`FacetRange`) carries the applied bounds, the dollars at the
  track's right end and `template`, the parts address of the page as it is — the category's one address (B-68),
  every other filter, the sort, from the first page — with `{min}` and `{max}` where the bounds go
  (`CatalogUrl.priced`). The client only puts the two numbers in (`FacetRange.applying`, in `shared`, so the
  server test completes the same way): an empty bound drops its parameter, bounds the wrong way round are
  swapped. Recorded in research D2, «Decided in B-69».
- **Client** (`feature/catalog/FacetPanelView.kt`): the fields are `BasicTextField`s of digits drawn after «$»,
  muted «$0» and «$<top>» while empty; Enter, «Done» and leaving the field apply after `awaitTypedInput` (B-76);
  a press on the slider takes the nearer thumb, which follows the pointer without passing the other, and its
  release applies — a thumb at its end of the track is no bound. The bounds already applied load nothing, and
  neither does anything while the sheet follows nothing (B-54). The phone's sheet draws the same block.
- **Server**: the chip names a single bound «From $80» / «Up to $400» instead of «$80 – $∞» / «$0 – $400»;
  «$80 – $400» as before.
- **Tests**: `server/.../catalog/DrawnActionsTest` «the price range applies with a load and its chip removes it»
  (the template from page 2 of a sorted, filtered page; both bounds, one bound, the chip). `composeApp/.../feature/
  catalog/PriceRangeTest`: Enter, a bound typed after Enter is down and before the next frame (B-76's order),
  leaving the field, a field left as it was, a thumb dragged and released, the phone sheet with «Done» staying
  open, the chip removing the range, and the template's completion. The canvas bodies carry a `range`; the
  Catalog goldens did not move (`viddikVerify` green, nothing re-recorded).
- **Mutation**: reading the fields at once instead of after `awaitTypedInput` — «typed just before enter» red
  (taking only `awaitTypedInput` out, the `launch` alone still defers the read past the input on the desktop, so
  the test pins the order, not the two frames); Enter and «Done» not confirming — four tests red; no confirm on
  leaving the field, no drag handling, no «already applied» check — one test red each; an empty lower bound kept
  as `price_min=` — the completion test red; the old chip label — the server test red; the server writing the
  bounds instead of the placeholders — the server test red. Restored, green.
- **In the browser** (this branch's image, `haul/server:b69`, on the WSL box beside a PostgreSQL, through an ssh
  tunnel to the app's browser pane, the pane hidden — frames throttled): typing `100` and Enter at once loaded
  `/ui/parts/…?brand=Sony&price_min=100`, chip «From $100», 7 items → 5; leaving «to» with Tab applied it;
  dragging the upper thumb loaded `price_max=395`, chip «$300 – $395»; the chip's × loaded the page without the
  range. On a phone (375×812) the sheet: «to» `200` and Enter → `price_max=200`, «2 applied», «Show 4 items», the
  sheet open; the lower thumb dragged → `price_min=86`.
- **Seen once, not fixed** (Compose on the web, outside this item): with the pane hidden, keystrokes typed into
  «from» and a click on «to» straight after — the click moved the focus before the throttled frame applied the
  keys, so «from» left with what it held before and the keys went nowhere. B-76's wait covers a read, not a
  focus change ahead of the input; in a visible tab the window is one frame.
- **Seen on the way**: a thumb at the end of the track is grabbable by its inner half only — the gesture area is
  the track's box, and the outer half of an end thumb is drawn past it.
