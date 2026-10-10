---
id: B-73
title: "client: on a phone every part of the store is reachable"
status: done
priority: P1
size: M
stage: stage-10-review
---

# B-73 — client: on a phone every part of the store is reachable

At 375 px the header has the heart, «Sign in» and the cart — no «Catalog» menu and no Orders. The category row is
clipped (`clipToBounds`) and does not scroll (`ui/HaulHeaderView.kt:371-383`), so categories past the edge
(Fashion is cut, Beauty and the rest are out of reach). HAUL PLUS is not drawn.

- AC: on a phone the shopper can reach every top-level category, the catalog menu, Orders, Saved, the account and
  Haul Plus; the category row scrolls; phone goldens updated and reviewed.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`.

## Findings (2026-10-10)

- **The canvas has no answer, so the conventional phone pattern is taken** (the decision, its cost and what was
  rejected are in research §2, «Decided in B-73»). Every phone artboard draws the same header — logo, heart,
  account, cart, search, a category row cut at the edge — and none draws a menu. The category row now scrolls
  sideways (`horizontalScroll` instead of `clipToBounds`), pixel for pixel the canvas's row at rest. A menu button
  left of the logo opens a full-screen menu (`ui/HeaderMenu.kt`): the account («Sign in» for a guest), «Orders»,
  «Saved», «Deals», «HAUL PLUS», then under «Catalog» all 32 top-level categories — the row names ten. Each entry
  follows what `HaulHeader` already carries; the server and the contract are unchanged. The shell holds the menu
  (`HeaderMenuState`) as it holds the filter sheet, and closes it on any page visited; an entry closes it before it
  is followed, so the Plus trial's dialog is not drawn under it. The menu does not open on the placeholder header
  drawn before a tree arrives.
- **Tests** (`DrawnActionsTest`, at 390 and 375 px): the menu opens a category the row does not show; the account,
  «Orders», «Saved» and «Deals» each open where the header says, the menu closed; «HAUL PLUS» presents the trial's
  dialog with the menu closed; back with the menu open closes it; at 375 px the row, swiped to its end, opens its
  last category («Pets») under the finger.
- **Mutation**: the row clipped again — the row test red (the press lands on another word); the shell not closing
  the menu on a visit — the back test red; the menu listing only the first category — the category test red;
  «Orders» following nothing — the account/orders test red; an entry not closing the menu — the Plus test red (a
  navigation is closed by the visit anyway). Restored, green, `git status` clean each time.
- **Goldens**: the 55 phone goldens that draw the shared header are re-recorded (every phone page but checkout,
  which has its own header; the filter sheet and the card grid draw none); in each only rows 49–76, the logo row,
  differ — compared byte for byte, then the eleven distinct header strips looked at (customer, guest, the
  placeholder, the search query, the dialogs over the page). New: `HaulHeader_Guest_Phone375` (the widest account
  slot at the narrowest width — the top row still fits, about 6 px between the logo and the heart's 44 px
  target), `HaulHeader_Menu_Phone`,
  `HaulHeader_Menu_Guest_Phone`. Recorded on Linux, in a copy outside the mutagen replica.
- **Parity** (`viddikDesignParity`, Linux, before → after on the same tree): +0.12 to +0.77 % on each phone
  artboard with the header, checkout and the filter sheet unchanged, every 1440 artboard unchanged. 125/125 within
  5 % before, 122/125 after: Catalog_Empty_Phone 4.54 → 5.06, Order_Placed_Phone 4.83 → 5.06,
  Product_NotFound_Phone 4.53 → 5.21 — short pages that already sat near the line, where the fixed header weighs
  most. The tolerance is unchanged; the way back is a phone header with the menu on the canvas.
- **In the browser**: this branch's `wasmJsBrowserDistribution` served from the Mac with `/ui` and `/api` passed
  through to the stand (v0.2.0), 375 × 812 as a phone, a guest. The menu opened; scrolled to «Bags» (the 32nd
  category) it opened `/c/bags` and closed; the row, scrolled sideways with the wheel, reached «Pets» and opened
  `/c/pets`; back with the menu open closed it; «Orders» started the sign-in as the top row's «Sign in» does. In the
  hidden pane the browser throttles frames, so a screenshot taken at once after a press shows the page before it —
  the press had worked (B-76's «Mechanism»).
- **Not looked at**: from 768 px up the 1440 header is drawn (`COMPACT_BELOW`); whether it fits at tablet widths
  is not a phone question and was not checked.
