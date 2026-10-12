---
id: B-135
title: "fix: the search field's loose ends — one letter, Escape, the empty page's notice"
status: done
priority: P3
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-135 — fix: the search field's loose ends — one letter, Escape, the empty page's notice

Found outside the catalogue by its v0.5.1 run ([run](../qa/runs/2026-10-12-v0.5.1.md)):

- Typing one character sends `GET /ui/search/suggest?q=a`, which answers `400 query_too_short` and logs a console
  error each time. The client knows the minimum: under two characters it asks nothing (or recall, B-124, for an empty
  field).
- Escape does not close the suggest panel, and the next press outside it is swallowed (it only closes the panel).
  Escape closes it, as it closes the menus (B-75); a press outside closes it and still reaches what it pressed — the
  panel is a suggestion over the page, not a menu whose scrim B-126 made absorb the press.
- `/search` with nothing typed draws «Search needs at least two characters.» above B-132's «Start typing» — two
  notices for one state; only B-132's stays.

- AC: each of the three behaves as its line says; a client test per line.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/search/SearchViews.kt`.

## Findings (2026-10-12)

- **One letter** — the shell asked for the panel as soon as anything was typed and paused on. The minimum is now the
  contract's (`MIN_SEARCH_QUERY` in `SearchComponents.kt`, which the server's `MIN_QUERY` is), and the field asks for
  suggestions only from it (`Storefront`); under it the panel is closed, and the empty field is still a customer's
  recall (B-124). Enter on one character still opens «Keep typing» (TC-SRCH-06), as before. The number went into
  the contract rather than a second `2` in the client, so the two cannot drift.
- **Escape and the press outside** — the panel is not hosted by B-126's `OverlayHost`: `SearchSuggestOverlay` draws
  it over the page itself, and its scrim was a `pressable` box over the page that only dismissed — that is what
  swallowed the press. The scrim is now drawn and reads nothing; the overlay watches presses in the initial pass,
  before the page, consumes none, and closes the panel for one outside the panel and the field (the field's row holds
  the picker and the search button, which keep their own presses). A press on a menu `OverPage` opened is the menu
  layer's — a sibling drawn over the storefront, which Compose's hit testing stops at, as B-126's layer relies on — so
  it does not reach the overlay (by construction, not tested apart). Escape, read in
  the overlay's preview key handler while the panel is open, closes it and leaves the focus in the field
  (decided: the combobox pattern — the shopper goes on typing; the press outside still takes the focus, as the scrim
  did). Recorded in the research as a decision next to B-124's.
- **The empty page's notice** — the two notices were one paragraph: B-132's words for `/search` with nothing typed
  were prefixed with the minimum («Search needs at least two characters.»). The page now says only «Start typing»
  and «Type what you are looking for in the field and press Enter.», the words B-132's findings decided
  (`SEARCH_NOTHING_TYPED`). «Keep typing» for a one-character query keeps the minimum: there something was typed.
- **Tests** — `SuggestPanelClosingTest` (one character asks nothing and the second asks once; Escape closes the panel
  with the field focused, nothing asked or opened; a press on the field keeps the panel, a press on a `refresh` button
  under the scrim closes it and sends the refresh) and `RefusedPagesTest` «a search with nothing typed asks for a
  query without saying the minimum». QA catalogue: TC-SRCH-12 added, TC-ERR-04 says the copy.
- **Mutations**, each red then restored, tree clean: the minimum back to `isNotEmpty()` — the one-character test; the
  Escape handler off — the Escape test; the outside press not dismissing — the outside test; the field counted as
  outside — the outside test (the press on the field closed the panel); the swallowing `pressable` scrim put back —
  the outside test (the refresh never sent); the minimum back in the copy — the empty-page test.
- **Goldens**: none changed — no fixture draws the search page with nothing typed or presses the panel's scrim.
