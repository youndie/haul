---
id: B-155
title: "feat: words select and the right button opens the store's menu on every page"
status: open
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
