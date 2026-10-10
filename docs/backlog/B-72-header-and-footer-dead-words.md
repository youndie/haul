---
id: B-72
title: "client + server: the header strip and the footer do not pretend to be links"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-72 — client + server: the header strip and the footer do not pretend to be links

Dead on every page: «Sell on Haul», «Help», «EN · USD» (`ui/HaulHeaderView.kt:201-204`, phone «Help» :301),
«Deliver to Brooklyn, NY 11211» (:198, :300, :406-421), the «All categories ▾» picker inside the search field
(:244-248), and all sixteen footer links (`ui/HaulFooterView.kt:104-112`; the wire carries `links: List<String>`).
Some have real destinations: footer «Deals», «Haul Plus», «Track an order» (→ orders).

- **Decided as product owner:** wire what has a page (Deals, Haul Plus, Track an order, the search category picker
  as a real scope), and draw the rest as plain, non-link text or remove it; the footer's wire grows an action per
  link.
- AC: every remaining word in the strip and the footer either navigates or reads as text; client tests; goldens.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulFooterView.kt`, `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`.

## Findings (2026-10-10)

- **Decided: words with no page are left out, not drawn as text** (research §2, «Decided in B-72», says why and
  what was rejected). B-49 had already drawn «Sell on HAUL», «Help», «EN · USD» and the footer's sixteen words as
  plain text with no click action; the stand walk read them as links all the same, because a utility strip and a
  footer link column look like links whatever their style. Now the strip says «DELIVERING TO BROOKLYN, NY 11211»
  in the strip's own type (no accent colour, so it no longer has a place picker's look) and «FREE DELIVERY OVER
  $35»; the phone strip the place alone. The footer keeps «Deals» → `/deals`, «Haul Plus» → the header's pill
  (the trial's dialog, a member's `/account`, a guest's sign-in) and «Track an order» → the orders (a guest's
  `/sign-in?next=%2Faccount%2Forders`), under «Shop» and «Help»; the other thirteen words and the «Sell» and
  «Company» columns are gone. «Returns» was left out with them: returns are asked for from an order's page, and
  the item named only the three.
- **The wire**: `FooterColumn.linked: List<Link> = []` — each word follows the entry with its label, as the
  header's row follows `catalog`; a word without one is drawn as text. The footer is built per viewer
  (`Frame.footer`), no longer a constant. `HaulHeader.scopes: List<SearchScope> = []` and `scope: String? = null`
  carry the search picker. All additions with defaults.
- **The search picker is a real scope.** `SearchRouting` took `category` already, but matched the leaf's own
  products only, so a top-level category found nothing. The search's `category` now holds the category and every
  one under it (`descendants`, moved from `CatalogScreen` to `Catalog.kt`), and a top-level scope is drawn as a
  chosen chip after «All». The picker is a client menu (`Menu`, the generalised `LinkMenu`); a choice sets
  `SearchInput.scope` and opens nothing, and Enter submits `Address.search(q, scope)`. A page arriving sets the
  picker to its header's `scope`. The search's parts now carry the header, so «All» or a leaf's chip after a
  scope sets the picker back, and a card's «+» keeps the scope in the header it answers with (`&category=`).
- **Not done here**: before any tree has arrived the shell's own header (B-67's `SHELL_HEADER`) has no scopes,
  so the picker there is drawn as «All categories ▾» and opens nothing — the search itself works. A scope with
  no match for the query draws its chip at 0 and an empty grid under «N results» (the count is every match);
  no artboard draws that state. Not walked in a browser: the stand runs main's server, which sends no scopes.
- **Tests**: client `DrawnActionsTest` — the strip names no page it lacks and the place has no click action;
  «Track an order» and «Haul Plus» open their pages, «Gift cards» is text; the picker chooses «Home & Kitchen»
  and Enter opens `/search?q=mug&category=home-kitchen`; a page's scope shows in the picker, «All categories»
  searches everywhere, and a header without scopes leaves the picker without a click action. `AddressTest`
  (`category`). Server — `DrawnActionsTest`: every footer word on home, `/c` and the deals is linked, to the
  deals, the pill's action and the guest's orders sign-in, «Deals» followed; `SearchRoutesTest`: the picker's
  32 choices, a `sports` search holds only products under Sports with the «Sports» chip chosen and counting
  what the leaves under it count, the header's `scope` set there and not for a leaf; `LineAnswersTest`: «+» on a
  scoped search keeps the header's scope; `PartsRoutesTest`: every load of a scoped search answers the page its
  address opens, header included. The 21 server-held bodies gained the header's `scopes`.
- **Mutations**, each red then restored, `git status` clean: footer words not followed (the footer test);
  the scope not submitted, «HELP» back in the strip (the picker and strip tests); the picker's menu empty (both
  picker tests); `show` not setting the scope (the page-scope test); a guest's «Track an order» to the orders
  page (server footer test); the scope matching the leaf alone, no scope chip (`SearchRoutesTest`); the scope
  left out of «+»'s header (`LineAnswersTest`); the header left out of the search's parts (`PartsRoutesTest`).
- **Goldens**: 115 re-recorded — every page with the shared header (the strip lost three words, or «HELP» on a
  phone, and the place its accent), Home's three states also for the footer; the checkout's pages (own
  header), the cards, the filter sheet, the phone menus and the message frames unchanged. New:
  `HaulHeader_Search_Scoped` (the picker reading «Home & Kitchen»), no reference. Recorded on Linux in a copy
  outside the mutagen replica; the strips, the scoped field and Home's footers looked at, wide and phone.
- **Parity** (`viddikDesignParity`, Linux): 116/125 within 5 %. Home's artboards 1.7–3.4 % with the footer at
  the canvas's height (9–13 % on the phone before it was held there). Four short phone artboards that sat on
  the line went over it by the strip's change: Catalog_Empty_Phone 5.06 → 5.19, Product_NotFound_Phone 5.21 →
  5.38, Order_Placed_Phone 5.06 → 5.12, Account_NoOrders_Phone 5.00 → 5.15. The five Search_* artboards read
  5–35 % on the replica, as B-75 found on main there (offset sections in the DIFF images); not chased here
  either. The tolerance is unchanged.
