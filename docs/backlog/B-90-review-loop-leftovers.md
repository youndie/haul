---
id: B-90
title: "client + server: the small leftovers of the review loop"
status: done
priority: P3
size: S
stage: stage-10-review
---

# B-90 — client + server: the small leftovers of the review loop

Recorded in the findings of B-69, B-72, B-77 and B-80, each too small for an item of its own:
- before the first tree, the shell's header has no search scopes, so the picker is drawn but opens nothing (B-72);
- a search scope with no matches shows its chip at 0 over an empty grid (B-72);
- «Show N more» has no cap on how many pages one grid accumulates (B-77; at most 192 cards on the seed);
- a slider thumb at the end of the track can be grabbed only by its inner half (B-69);
- a cold `/checkout` shows the storefront header's shape first, then checkout's own (B-80);
- the B-28 measurement's `wired` arm no longer builds — its patch expects `{ App() }` in `Main.kt` (B-80).

- AC: each line fixed or answered in the findings with why not; a test per fix.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`,
  `composeApp/src/wasmJsMain/resources/index.html`.

## Findings

- **Done (2026-10-11).** Five lines fixed, one answered:
  - **the picker before the first tree — answered.** Since B-105 a page that stays without a tree draws the header
    alone the shell asks for, scopes included, so the picker works there; `KnownHeaderTest` «a page with no tree
    offers the server's search scopes» holds it. It is still drawn and opens nothing while the first page loads
    (the tree brings the scopes), on `/sign-in` landed on cold (B-66 keeps that page free of server requests) and
    when `/ui/header` does not arrive — windows too narrow for a fix of their own
    (`docs/research/research-architecture.md`);
  - **a scope at 0 — fixed** (`SearchScreen`). With the query's results all outside the scope, the page is the title
    and the chips (the scope's chosen at 0), then an `EmptyState`: «Nothing for “q” in X», «N results in other
    categories.», «Search all categories». It has no grid, pages or sort, so its chips `navigate` rather than `load`.
    `SearchRoutesTest` «a scope the query finds nothing in …»; no golden, there is no artboard for the state;
  - **«Show N more» — capped** at eight pages per grid (`Browse.MAX_HELD_PAGES`, 192 cards), which holds «everyday»,
    the seed's longest search, whole — 185 cards, not the 192 B-77 wrote. A grid at eight has no «Show N more»; its
    page numbers go on. A `from` more than seven pages before `page` is `400 validation_failed` on category, search,
    deals and parts alike (`firstPage`). `PaginationTest`, `DrawnActionsTest` «a grid holds at most eight pages»;
  - **the end thumb — fixed** (`FacetPanelView`): the slider takes presses half a thumb past each end of its track;
    layout and drawing unchanged, no golden moved. `PriceRangeTest` «a thumb at the end of the track is grabbed by
    its outer half»;
  - **a cold `/checkout` — fixed** (`index.html`): a one-line inline script before the frame marks
    `<html class="checkout">` on `/checkout`, and the static frame draws checkout's own header shape (89 px at 1440,
    118 below 1024). Looked at in a browser at 1440 and 390 against the `Checkout_Loading` goldens; `/c/mugs` keeps
    the storefront's. A CSP forbidding inline scripts would fall back to the storefront's shape everywhere.
    `StaticFrameTest` «a cold checkout draws the checkout's header shape»;
  - **B-28's `wired` arm — fixed** (`scripts/measure-first-load.sh`): the patch swaps the one `App(...)` call
    (`WIRED_ROOT`) and `WiredHome` uses `HaulThemeAtWidth`. `WiredArmTest` reads the regex out of the script and
    holds `Main.kt` to it. The arm built and ran on the Linux box (`ARMS=wired PROFILES=none STOP_AFTER=3`, two
    rounds): Home drawn, every control passed.
- Each fix was reverted with its test red and the rest green, then restored.
- No golden moved.
