---
id: B-82
title: "client: the store reads well between a phone and 1440"
status: done
priority: P2
size: M
stage: stage-10-review
---

# B-82 — client: the store reads well between a phone and 1440

Found by B-73 and B-75: between 768 and 1439 px the wide header is drawn as at 1440 and squeezed — at 1024 px the
search field is a sliver and «HAUL PLUS» is set vertically. B-76 saw the cart line's title drawn one letter per
line at 800 px. No artboard covers these widths.

- AC: at 800, 1024 and 1280 px every page's header, cart and product page read without clipped or stacked
  words; goldens at 1024 for home, catalog, product and cart, each looked at.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt`.

## Findings (2026-10-11)

- **Decided: three bands by the page's width** (research §2, «Decided in B-82», has the rejected options).
  Below 1024 the phone's arrangement (`COMPACT_BELOW`, was 768): menu button, scrolling category row, one column,
  the phone's cart lines — every phone layout already held 375 up, and at 800 nothing is cut or broken. From 1024
  to 1279 the desktop rearranged (`LocalHaulMedium`, `WIDE_FROM`, chosen in `HaulThemeAtWidth`, `App.kt`): the
  header's search field on a row of its own under the logo's (the header 261 px; `index.html`'s static frame
  draws that shape between 1024 and 1279 and the phone's below 1024); the cart's lines the phone's beside the
  summary; the product's buy box under its title beside the photo, its facts under the copy; checkout's summary
  under its sections; the account's tiles one under another and its history rows the phone's; a search's kinds
  scrolling sideways; a banner's title 32 px without its placeholder tile. At every desktop width: the header's
  category row draws the words that fit whole and keeps «HAUL PLUS» at its end (`FittingWords`; «Catalog» lists
  all 32), a grid takes as many 168 px cards as fit up to the tree's columns (four at 1024), the campaign's
  «−70%», the footer's wordmark and the product's title are set as large as their width holds (`FittedText`;
  at 1280 the title had broken «WH-1000XM6» before its 6), and the catalog's applied filters wrap with the sort
  at the first line's end (four filters cut «Sort: Popular» at 1024, and on the empty catalog at 1280).
- **Tests**: `WidthsTest` (desktop) draws through `HaulThemeAtWidth` — the real breakpoints — at 800, 1024 and
  1280 the header (customer, guest, placeholder, a query in the field), every cart state, every product tab and
  out of stock, and 18 other pages (home, catalog, search, account, order, saved, checkout), and reads every
  text's own layout: a line break inside a word, a one-line text wider than its room, a text past the window's
  edge; a row that scrolls sideways is not read for the edge.
- **Mutations**, each red then restored, `git status` clean: the phone from 768 again (home and the product's
  specifications at 800); the field back in the logo's row (the placeholder cut, every page at 1024); the row
  without fitting (`«HAUL PLUS»` cut); the cart's 1440 line at 1024 (its title broken, «Remove» cut); the title
  never smaller (`WH-1000XM6` at 1280); a grid of the tree's columns (old prices broken at 1024); the applied
  filters in one row («Sort:» and «Popular» cut); the campaign never smaller («−70%» cut); checkout's summary
  beside («Pickup point», «Optional» cut); the account's wide rows («Reorder» cut); the product's three columns
  («2,341 reviews» cut, «microphones» broken); the footer's wordmark never smaller («Haul.» cut).
- **Goldens**: new, without a reference (no artboard draws 1024), each looked at: `Home_Content_1024`,
  `Catalog_Content_1024`, `Product_Description_1024`, `Cart_Content_1024`. No other golden moved: every
  1440 and 390 golden verifies unchanged (the 1440 header, grids, hero, title and applied filters draw what they
  drew; the phone's footer keeps its wordmark running past the edge, as Home_Content_Phone draws it). Recorded
  on Linux in a copy outside the mutagen replica.
- **Seen, not changed**: on a phone layout at 800–1023 the cards are two across (376 px at 800) and the product
  photo as wide as the page — it reads, it is large. The product's review cards at 1024 set «Sep 28 · Verified
  purchase» on three lines beside the reviewer's name; the cart's change notice wraps its «OK» onto a line of
  its own. Words set one to a line between spaces («250 / g», a fact tile's title beside the copy at 1024 before
  this change) are not what `WidthsTest` reads — it reads words, not lines; the facts under the copy are held by
  `Product_Description_1024`, and the banners' titles by `Home_Content_1024`.
