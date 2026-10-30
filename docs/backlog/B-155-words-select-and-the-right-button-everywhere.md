---
id: B-155
title: "feat: words select and the right button opens the store's menu on every page"
status: done
priority: P2
size: M
stage: stage-13-web-habits
blocked_by: [B-154]
---

# B-155 — feat: words select and the right button opens the store's menu on every page

B-154 made the product page's words selectable and gave it the store's right-button menu (`Selectable`,
`ui/ContextMenus.kt`); the owner asked for the product page first and then the whole store (2026-10-30). This is
the whole store.

- **Every page's tree** is drawn selectable: `SELECTABLE_PAGES` in `shell/Storefront.kt` goes, and every page the
  shell scrolls — the trees, the placeholders, the sign-in page and the pages the shell draws itself (refused, not
  found) — is a `Selectable`.
- **The header, the footer and what opens over the page** — the header's words and links (the category row, the
  account menu), the dialogs and sheets a `present` draws, the notices — select and open the menu too, or the item
  says why one does not.
- **Links that are words in a sentence** — the crumbs and the category row are `TextLink`s (`ui/Links.kt`), a press
  with no address — carry their action's address, so the right button over one offers «Open in new tab» and «Copy
  link» like any other link.
- **Fields keep a field's menu**: the right button over the search box, the promo code, the address form opens Cut,
  Copy, Paste and Select all for the field, drawn as the store's menu — Compose's own field menu on the web is the
  old one, whose presses did nothing on the product page (B-154). Paste reads the clipboard only when pressed.
- **Phones**: a long press selects, as on any page; what the selection's toolbar offers there is walked and decided.
- AC: on each page of the catalogue (`docs/qa`) a drag selects and ⌘C copies, and the right button opens the menu
  over words, over a link and over a field; a press, a drag on a slider or a carousel, and the page's scroll do what
  they did; the QA case B-154 added for the product page covers every page; tests in `desktopTest`.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ContextMenus.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Layers.kt`.

## Findings (2026-10-30)

- **Every page** the shell scrolls is a `Selectable` (`Scrolled`, `shell/Storefront.kt`): the trees, their placeholders,
  the sign-in page and the pages the shell draws itself. The header and the footer are nodes of the page's tree, so
  they are in it. **Every sheet** too — a dialog a `present` draws, the phone's menu and filter sheet, the review
  photos (`SheetOverPage`, `ui/Layers.kt`): its menu opens over the sheet. Not in it: the notices (they are drawn over
  everything, B-145, and a menu opened from one would sit under it) and the search's suggest panel (a list of presses).
- **Links in a sentence** carry their action now (`TextLink.action`), so a crumb or a word of the category row offers
  «Open in new tab» and «Copy link» as any link does.
- **Fields keep a field's menu** — «Cut», «Copy», «Paste», «Select all», each only where it can act — through the
  store's own field (`HaulTextField`, `ui/HaulTextField.kt`), which the six fields (the search, the price bounds, the
  promo code, the address form, the review's title and text) now use. It keeps the string field's bookkeeping, so it
  behaves as `BasicTextField(value: String, …)` did, and keeps its selection itself, which the menu reads; the menu
  is asked for when the right button presses (`offersMenu`). «Paste» reads `navigator.clipboard.readText` only when
  pressed, which the browser may ask the shopper to allow; refused, nothing is pasted. «Copy» gives the field its
  selection back — the menu took the focus, and the field let go of it.
- **The right button off an open menu opens the menu where it pressed**, as a browser's does; the left closes it.
- **Pages where a text drew past its box took the presses under it**: once every text could be selected, each reads
  presses over its box, and the footer's «Haul.» — its 420 px line runs past the box it measures — took the column
  words' above it: «Haul Plus» opened nothing (`DrawnActionsTest` «the strip names no page it lacks…», red; the word's
  box was found under the press, logged, and the press reached no control). The name and the home hero's «70%» are
  pictures, not words to copy, and are out of the selection (`DisableSelection`). Not every such overlap is known:
  a text drawn over a control now takes its press, and the suite and the walk found this one.
- **A double press across the page**: Compose's selection counts presses on each text and between the texts too
  (`isMouseSelectionBetweenTextEnabled`, read in the sources), so the page marks a repeat press itself, before
  anything on it (`marksRepeatPresses`), and `pressesAgain` presses a control on a press the page marked. The marks
  live on the page's `ContextMenus`, read by the control through the composition, not in a global.
- **What was tried and is not in**: `remember { SelectionState() }` in place of `rememberSelectionState`, thought to
  stop a selection saved with a page from coming back after Back — the mutation with the saved state kept every test
  green, so the claim was not shown and Compose's own is kept.
- **Phones: not walked.** A long press selects there and Compose's own touch toolbar (`WebTextToolbarArea`) shows; the
  browser pane drives the page with a mouse even in its phone size, so neither the long press nor the toolbar could be
  pressed here. B-156 walks it on a phone.
- **Walked on a local stand** (the branch's bundle, a seeded PostgreSQL, Chromium at 1280): the crumb «Electronics»
  offers its link; the lowest price field's menu offers «Paste» alone while empty, and «Paste» put a stubbed «120» in
  it, drawn «$120»; a right press over the heading off an open menu opened the words' menu there.
- **Tests**: `ContextMenusTest` — a field's cut, copy, paste and select all; a link in a sentence; a sheet's words; the
  press off a menu, of either button; `DrawnActionsTest` «every page opens the store's menu and the footer's words
  offer their links». **Mutation**, each on the committed change, restored: the inline link without its menu, the
  sheet without `Selectable`, the pages without it, the field without its menu — each red on its test; the footer's
  name back in the selection — red on «the strip names no page it lacks…».
