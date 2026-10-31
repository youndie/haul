---
id: B-158
title: "feat: the storefront moves — reveals, hovers, presses, pops, a shimmer and rolling figures"
status: done
priority: P2
size: L
stage: stage-14-motion
---

# B-158 — feat: the storefront moves — reveals, hovers, presses, pops, a shimmer and rolling figures

The canvas is still: every artboard is a page at rest, and so was the store built from it. A press answered only once
the server did, a loading page was grey blocks, a card did not acknowledge the pointer over it, a dialog appeared
whole in one frame. The owner asked for the site to come alive with many modern animations (2026-10-31).

- **The decision and its reason: motion is a layer over the page at rest, never a different page.** Every golden is
  drawn still, and they are the store's reference; a page that moved and then rested a pixel off them would pass
  every golden and be wrong on the stand. So everything moves only under `Motion.Lively` (`ui/Motion.kt`), ends
  exactly where the still page is drawn, and under `Motion.Still` adds nothing at all — no layer, no pointer reader.
  The screenshots, and every view drawn outside the storefront, are still; the storefront is lively unless the
  browser asks for reduced motion (`prefers-reduced-motion`, read in `Main.kt`).
- **What moves.**
  - *Arrival and scroll.* Product cards, category tiles, the campaign and its banners, section headings, Haul Plus
    and the product page's photo, details and buy box rise into place as they first come into the window: on a page
    that arrives, cascading down it and along a row; further down, as the page scrolls to them (`reveal`). A
    filtered page brings its new cards in and keeps those it shares (cards are keyed by id).
  - *Hover.* A card rises, its photo leans nearer and casts a shadow, and its «+» turns a quarter; a category tile
    rises and its picture comes nearer; a banner rises and its picture tilts; the campaign's picture drifts nearer and
    its figure grows; a button lightens and its arrow leans on; «View all» and the footer's links lean on.
  - *Press.* Every control sinks a few pixels on the primary button or a touch and springs back on release —
    the same few pixels whatever its size, so a card sinks less than a «+».
  - *Change.* A heart saved pops with a ring and sparks; a line put in the cart shakes the bag, pops the badge and
    rolls its figure; the deals countdown rolls its figures as an odometer; a tab's underline grows from its middle.
  - *Opening.* Dialogs fade their scrim in and spring their card up; menus and the suggest panel drop open from
    their control; a message springs up from the bottom edge.
  - *Loading.* A light sweeps across every placeholder at once, as one band across the page; a photo that arrives
    fades in over its tile and settles from a little nearer.
- **Rejected: a CSS-like page transition over the whole tree.** The header is part of each page's tree; fading the
  tree in faded the header on every visit. Revealing the blocks under it leaves the header where it was.
- **Rejected: rolling figures as a row of one-character texts.** At rest they drew the countdown a pixel off the
  still text, which a tick every second would have shown as the whole pill twitching. The roll draws the text recorded
  before the change and the text now, each clipped to the places of the characters that changed; at rest it is the
  same `Text`.
- Not covered: exits — a dialog, a menu or a message still goes in one frame; the phone's sheets slide no differently
  from a dialog fading in; nothing reads the device's motion.
- AC: on a lively storefront every movement listed above plays (walked in Chromium on a local stand at 1440); at rest
  the lively home is pixel-identical to the still home; a ticking countdown is between its two seconds half-way
  through a tick and the still text after it; every golden recorded with the change is pixel-identical to one
  recorded without it; a browser that prefers reduced motion gets a
  still page.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Motion.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/App.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/MotionTest.kt`.

## Findings

- **A page's scroll moves its layer, not its nodes.** The first reveal waited for `onGloballyPositioned` to say a
  block had come into the window; scrolled to, the blocks below the fold were never told and stayed clear. A reveal
  now takes where its block was first placed and the page's scroll then, and follows the scroll (`LocalPageScroll`).
- **A draw that skips a state read is not redrawn when it changes.** The roll's first frame drew «pending» without
  reading its progress, so the draw was never subscribed to the animation and the countdown jumped to the next
  second. Found by the frozen-clock test, not by eye: the test's half-way frame equalled the end.
- **A hidden browser pane hides motion.** Walked in the app's browser pane while it was hidden, hover looked stuck:
  a hidden page runs a frame now and then. The walk that counts was in headless Chromium on the WSL box, driven
  over CDP (the CDP client of `scripts/measure-first-load.py`).
- **Two goldens drift on main, not here.** Every golden re-recorded on the WSL box matches a recording of `main` to the
  pixel (168 of 168). Both recordings differ from the committed `Search_Results` (901 px) and `Search_Autocomplete`
  (188 px) in the kind chips' row, rows 380–411 — within the tolerance `viddikVerify` holds them to, and the same
  without this change; left as they are.
