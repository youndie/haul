---
id: B-75
title: "client: a press shows that it worked"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-75 — client: a press shows that it worked

Seen on the stand and in the code:
- «Add to cart» on the product page changes only the header's count — no message, the button stays «Add to cart»;
  the header's search field shrinks when the count badge appears (layout shift).
- A card's «+» is drawn the same when there is nothing to add (out of stock, at the limit) and the press then opens
  the product (`ui/ProductCardView.kt:82-93`).
- Brands with a count of 0 stay pressable after another filter is applied.
- The sort menu does not close on Escape.

- AC: «Add to cart» answers with a short message (`show_message` in its `sequence`) and the buy box shows the item
  is in the cart; the header does not move when the count appears; a «+» with nothing to add looks disabled and does
  nothing; zero-count facets are disabled; Escape closes the sort menu; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductCardView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.

## Findings (2026-10-10)

- **Nothing drew `show_message`** before this item: no tree sent it, and the shell's handler dropped any action it
  did not know. The decision, its look and what was rejected are in research §2, «Decided in B-75». In short: the
  client draws kompot's `show_message` in the frame the shell already had over a page (B-62's notice) —
  `shell/Messages.kt`, held by the storefront above its pages, four seconds or six with a button, a new message
  replacing the last, the button's action sent through the whole chain; kompot's Material snackbar was not taken
  (no Material theme here). «Add to cart» answers `sequence[update, show_message]` — «Added to your cart», «View
  cart» — and the buy box carries `ProductDetails.inCart` («2 in your cart», leading to `/cart`), a new optional
  field of the contract. A card's «+» keeps its bare `update`: the card stays under the finger and the count is its
  answer — a message there too is one line in `LineAnswers` if the owner wants it.
- **What has nothing to do looks it.** A «+» with no change in its tree is greyed (the buy box's out-of-stock
  colours) and takes the press; it used to fall through to the card and open the product. It is its own semantics
  node now: merged into the card's, a disabled «+» read as the card. «Add to cart» at the line's limit is greyed
  the same way — the same defect on the buy box, found with the note that now sits under it. A facet option another
  filter leaves at 0 and that is not ticked carries no action (server, `CatalogScreen.counted`) and is drawn at
  40 % (client); ticked, it keeps its action, which unticks it. Rating options have no count and are untouched.
- **The header.** The cart button keeps the count's place when the cart is empty (24 px and the gap: 34 px at
  1440, 32 on a phone), so the count appearing moves nothing around it; empty, the bag and «Cart» sit in the middle
  of that width, so it is the button's contents, not the header, that make room. The badge's padding is 3, not the
  canvas's 6, so two digits fit the 24 px place (with 4 they took 25 and the header moved a pixel at 12); one digit
  is drawn as before. A blank tail in place of the count was tried first and rejected on the phone, where an
  icon-only button with 32 px of nothing reads as broken.
- **Escape**: the link menu's popup took the focus and gave it to none of its nodes, so the key reached nothing;
  the menu now holds the focus and reads Escape before its entries. It is the sort's menu, «Catalog»'s and the account's (B-66).
- **Tests**: `LineAnswersTest` (the answer is the sequence with the message, the buy box says «1 in your cart»),
  `CatalogRoutesTest` (an empty unticked option presses nothing, an empty ticked one and every other does),
  `BuyBoxWiringTest` (the sequence draws the count, the line and the message without a page; «View cart» opens the
  cart and takes the message away; a message goes away on its own), `InPlaceAnswersTest` (a «+» with nothing to add
  is disabled and opens nothing), `DrawnActionsTest` (Escape closes the sort's menu), `HeaderCountTest` (at 1366 and
  390 «Orders», «Sign in» and the cart button stay put through counts 0, 1, 12, 99). Tests reading the old answer
  read the update inside the sequence (`PartsCalls.update()`; the e2e's «Add to cart» step also checks the
  message); `DrawnActionsTest` (server) ticks the last brand that can be ticked — the last one, at 0 under the
  rating, has no action now.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: the shell dropping
  `show_message` (the message test), no timeout (the «goes away» test), the buy box not drawing `inCart` (the message
  test, on `IN_CART_TAG`), «+» not taking the press (the card test), the empty cart without the count's place (both
  header tests), Escape not read (the Escape test — the desktop popup does not close on Escape by itself either);
  server: the bare `update` (`LineAnswersTest`), no `inCart` (the same, on the link), an action on every option
  (`CatalogRoutesTest`).
- **Goldens**: 42 re-recorded — every page drawn with an empty cart's header, a guest's or the placeholder's
  (Loading, Error, Home_Guest, the account's guest-less states, Cart_Empty, the Plus trial), the cart button wider
  and its contents centred; looked at, wide and phone. New, without a reference: `Shell_Message`,
  `Shell_Message_Phone`, `ProductCard_NothingToAdd`, `Product_InCart`, `Product_InCart_AtLimit`,
  `Catalog_Facets_ZeroCounts`. The client's wire bodies for cards and buy boxes now carry the `add` (and the buy
  box's `buy`) the server sends in stock — without them every fixture card would have drawn the disabled «+»; no
  golden changed for it. Recorded on Linux, in a copy outside the mutagen replica.
- **Parity** (`viddikDesignParity`, Linux): 122/125 within 5 % — the three phone artboards B-73 left over the
  line, unchanged. The empty-cart artboards rise 0.1–1.1 points (Account_NoOrders_Phone 4.21 → 5.00, still in).
  On a copy of `origin/main` rebuilt on the replica the Search_* artboards read 5–35 % (offset sections in the
  DIFF images) where this branch reads 2–3 %, with no search file in this change: suspected order-dependent state
  between fixtures (the six new ones move the others between the generated classes); not chased.
- **In the browser**: this branch's `wasmJsBrowserDistribution` served from the Mac with `/ui` and `/api` passed
  through to the stand (main's server, so no message and no «in your cart» there), a guest at 1024 px: «Add to
  cart» drew the count with the search field, «Orders», «Saved», «Sign in» and the cart button's edges where they
  were; the sort's menu opened and Escape closed it, the address unchanged. At 1024 the 1440 header is squeezed —
  the search field a sliver, «HAUL PLUS» set vertically — a tablet-width problem B-73 left unexamined and this item
  does not touch.

