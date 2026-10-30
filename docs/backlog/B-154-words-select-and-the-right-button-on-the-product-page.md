---
id: B-154
title: "feat: the product page's words select, and the right button opens the store's menu"
status: done
priority: P2
size: M
stage: stage-13-web-habits
---

# B-154 — feat: the product page's words select, and the right button opens the store's menu

Asked by the owner (2026-10-30): nowhere in the store can a word be selected, and the right button does nothing —
a browser's page does both, and a shopper copies a product's name, a model number, an address. First a part of the
store, then all of it (B-155). The part is the product page: the most words worth copying, and links of every kind
on it (the brand, the crumbs, the related products).

- **Words select**: dragged over, double-pressed, as on any page; ⌘C / Ctrl+C copies them.
- **The right button opens the store's menu, at the pointer**: over the words, «Copy» (with words selected) and
  «Select all»; over a link, «Open in new tab» and «Copy link», as a browser offers for a link. Drawn as the store's
  other menus (`MenuColumn`), closed by a press of either button off it or by Escape.
- AC: on the product page a drag selects and ⌘C copies; the right button opens the menu at the pointer over words
  and over the brand link; «Copy», «Open in new tab» and «Copy link» do what they say; no other page changes; tests
  in `desktopTest`.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ContextMenus.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/shell/ClipboardLinks.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/ui/ContextMenusTest.kt`.

## Findings (2026-10-30)

- **What Compose 1.12 does on the web, read in its sources (`foundation` 1.12.1) and walked on a local stand.**
  `SelectionContainer` selects by drag and double press. Its right-button menu on skiko is the *old* one — the new,
  customisable text menu (`LocalTextContextMenuDropdownProvider`) is off there (`isNewContextMenuInitiallyEnabled =
  false`), and the old one's look and items are internal. Walked: «Copy» and «Select all» drawn in Compose's own
  look, and pressing either did nothing. ⌘C after a drag copied nothing either: on the web Compose's copy waits for
  the browser's `copy` event (`skipCopyKeyEvent`), which never came.
- **So the store reads the right button itself** (`Selectable`, `ui/ContextMenus.kt`): one reader over the page on
  the first pass, which takes the press — Compose's menu waits for a press nobody took (`changedToDown`) and never
  opens — and opens `PointMenu`. What it offers comes from `SelectionState` (1.12's public selection state: the
  selected texts, `selectAll()`); a link under the pointer is looked up among the links the page drew
  (`offersLink`, wired into `follows`, so every followed `NavigateAction` offers its address). A reader on each link
  missed presses the page's reader saw; one reader and a lookup do not. The reader reads events one by one, never a
  gesture waiting for its release: the menu covers the page before the release comes.
- **The copy shortcut** is read by the same page (`onPreviewKeyEvent`): ⌘C or Ctrl+C with words selected writes them
  through the page's clipboard, as «Copy» does — `LinkCopier.copyText`, the same `navigator.clipboard` path, with
  the selected-textarea fallback, the share button uses (B-71, B-104). «Open in new tab» is `window.open(…,
  '_blank', 'noopener')` with the page's origin.
- **Walked on a local stand** (the image's server and a seeded PostgreSQL on the build box, the bundle of this
  branch, Chromium at 1280): a drag over the title and «Copy» put «WH-1000» on the clipboard; ⌘C after a drag over
  the battery line put «Up to 40 hours, 3 min»; over «Sony» «Copy link» put
  `…/c/electronics/audio/headphones?brand=Sony` and «Open in new tab» opened that address apart; a right press off
  an open menu closed it. The clipboard and `window.open` were read through stubs in the page, not the machine's.
  Two artefacts of the walk, not of the store: a hidden browser pane draws no frame between two presses, so a menu
  closed by one is still there for the next; and the automation's ⌘C carries no key code (`code: ""`), which
  Compose maps to no key — a real keyboard's `KeyC` is `Key.C`, checked with a dispatched `KeyboardEvent`.
- **Left for B-155**: the crumbs' and the category row's words are `TextLink`s — a press with no address — so the
  right button over them opens the words' menu, not a link's; fields; the other pages, the header and the dialogs.
- **Tests**: `ContextMenusTest` — the menu over words has «Select all» and no «Copy» with nothing selected; «Copy»
  after «Select all» writes the words; the copy shortcut copies them; over a link «Open in new tab» and «Copy link»
  get the link's path and the right button follows nothing; a right press off the menu and Escape close it.
  **Mutation**, each on the committed change, restored, `git status` clean: the page's reader not taking the press
  (the link test still green — the menu is the store's either way — but Compose's menu would open under it, which
  only the browser shows); the copy shortcut's handler removed (red); the link lookup returning nothing (red).
