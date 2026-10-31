# Header and navigation — `TC-HDR`

The header every storefront page draws (checkout has its own), its menus, the footer, and how a press moves
between pages. The header over a page with no tree (a placeholder, an error, a page not there) is in
[errors and loading](11-errors-and-loading.md); the widths between a phone and 1440 in [widths](12-widths.md).

### TC-HDR-01 — A guest's wide header leads where each word says

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a fresh browser profile on the home page.
- **Steps:**
  1. Read the strip above the header and the search field's placeholder.
  2. Press «Deals», then the cart, each from the home page.
  3. Press «Orders», then (back on home) «Saved».
  4. Press a word of the category row, e.g. «Electronics».
  5. Press «Catalog», pick a category the row does not show.
- **Expected:**
  - Step 1: the strip reads «DELIVERING TO BROOKLYN, NY 11211» and «FREE DELIVERY OVER $35» in the strip's own
    type; no «Sell on HAUL», «Help» or «EN · USD». The field reads «Search products», with no count.
  - Step 2: `/deals`, then `/cart`.
  - Step 3: each opens the sign-in (popup or the sign-in page), and once through lands on `/account/orders`,
    `/account/saved` respectively.
  - Step 4: the category's page at its whole path (`/c/electronics`; a subcategory's is every slug from the top).
  - Step 5: «Catalog» opens a menu of all 32 top-level categories; the one picked opens its page.
- **Decided in:** B-37, B-49, B-66, B-68, B-72, B-107
- **Coverage:** `server:DrawnActionsTest` «the header links every top-level category and its deals cart and orders»;
  `composeApp:DrawnActionsTest` «deals and the cart in the header open their pages»;
  `composeApp:DrawnActionsTest` «a category in the header's row opens its page»;
  `composeApp:DrawnActionsTest` «catalog opens every category as a menu and the one picked is opened»;
  `composeApp:DrawnActionsTest` «the strip names no page it lacks and the footer's words lead where they say»;
  `SignInEverywhereTest` «the header the shell draws itself signs in and lands on its next»

### TC-HDR-02 — A customer's header is hers

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** Maya signed in, her seeded cart (three lines) untouched, her address in Brooklyn.
- **Steps:**
  1. Read the account slot, the cart's count and the strip.
  2. Press the account slot.
  3. Press «Orders».
- **Expected:**
  - Step 1: her name in the account slot, the cart's count of what her cart holds, «DELIVERING TO» her own city and
    ZIP (a customer with no address yet reads the store's default, «BROOKLYN, NY 11211»).
  - Step 2: a menu with «Account» and «Sign out».
  - Step 3: `/account/orders` straight away, no sign-in.
- **Decided in:** B-66, B-98, B-105
- **Coverage:** `IdentityRoutesTest` «every screen's header greets whoever is looking and counts their cart»;
  `DeliveryAgreesTest` «the header and the product page name the customer's own place»;
  `composeApp:DrawnActionsTest` «orders in the header opens the customer's orders»;
  `SignInEverywhereTest` «a customer signs out from the header and leaves a customer's page for home»

### TC-HDR-03 — «HAUL PLUS» is an offer, a way to sign in, or a mark, by who is looking

- **Who:** guest; Sam; Maya
- **Width:** 1440, 390
- **Preconditions:** none. On the stand, read the warning on TC-ACC-08 before pressing «Start trial» as Sam.
- **Steps:**
  1. As a guest, press «HAUL PLUS» at the end of the category row.
  2. As Sam (not a member), press it; then press «Not now».
  3. As Maya (a member), press it; read the footer's «Haul Plus» on the home page.
- **Expected:**
  - Step 1: the sign-in.
  - Step 2: the trial's dialog over the page; «Not now» closes it and sends nothing.
  - Step 3: the pill is the membership's mark and presses nothing (no hand cursor, no page); the footer's
    «Haul Plus» is text.
- **Decided in:** B-23, B-49, B-108
- **Coverage:** `server:DrawnActionsTest` «the plus pill asks a guest to sign in»;
  `composeApp:DrawnActionsTest` «haul plus presents the trial's dialog»;
  `PlusTrialWiringTest` «not now closes the dialog and sends nothing»;
  `MembershipRoutesTest` «the plus pill offers the trial to a non-member and is a mark for a member»

### TC-HDR-04 — The phone header's menu reaches what the wide header does

- **Who:** guest; Maya
- **Width:** 390
- **Preconditions:** any storefront page but checkout.
- **Steps:**
  1. Press the menu button left of the logo.
  2. Press a category under «Catalog» that the category row does not show (e.g. «Pets»).
  3. Open the menu again and press «Orders»; then «Saved», «Deals», «HAUL PLUS» the same way.
  4. Open the menu and press the browser's back.
  5. As Maya, open the menu and press «Sign out».
- **Expected:**
  - Step 1: a full-screen menu: the account («Sign in» for a guest), «Orders», «Saved», «Deals», «HAUL PLUS», then
    «Catalog» with all 32 top-level categories; Maya's also has «Sign out».
  - Step 2: the menu closes before the category's page opens.
  - Step 3: each entry closes the menu and goes where the wide header's word goes (a guest's «Orders» and «Saved»
    to the sign-in returning to them; «HAUL PLUS» presents the trial's dialog with the menu already closed, for
    Sam; a sign-in for a guest).
  - Step 4: back closes the menu.
  - Step 5: signed out, on the home page if the page was a customer's, otherwise the same page as a guest.
- **Decided in:** B-66, B-73
- **Coverage:** `composeApp:DrawnActionsTest` «on a phone the menu opens a category the row does not show»;
  `composeApp:DrawnActionsTest` «on a phone the menu opens the account orders saved and deals»;
  `composeApp:DrawnActionsTest` «on a phone the menu's haul plus presents the trial's dialog»;
  `composeApp:DrawnActionsTest` «on a phone a page visited closes the menu»;
  `SignInEverywhereTest` «on a phone a customer signs out from the header's menu»;
  golden `HaulHeader_Menu_Phone`; golden `HaulHeader_Menu_Guest_Phone`

### TC-HDR-05 — The phone's category row scrolls to its last category

- **Who:** guest
- **Width:** 375, 390
- **Preconditions:** the home page.
- **Steps:**
  1. Swipe the category row under the search field to its end.
  2. Press the last word.
- **Expected:**
  - Step 1: the row scrolls sideways. At rest it is the canvas's row, ended by the edge; at 375, 390 and 800 the next
    word starts just past it, so no word shows cut — as on the canvas (B-152). Every category is also in the menu.
  - Step 2: the last top-level category's page («Pets» on the seed) opens — the word under the finger, not another.
- **Decided in:** B-73, B-152
- **Coverage:** `composeApp:DrawnActionsTest` «on a phone the category row scrolls to its last category»;
  golden `HaulHeader_Guest_Phone375`

### TC-HDR-06 — The cart's count appears without moving the header

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** an empty cart.
- **Steps:**
  1. Note where the search field's right edge (1440), the heart and the account (390) are.
  2. Press a card's «+» on the home page; then add more until the count has two digits.
- **Expected:**
  - Step 1: the empty cart's button already holds the count's place: the bag and «Cart» sit in its middle.
  - Step 2: the count appears in that place; nothing else in the header moves, at one digit or two. On a phone the
    button reads «Cart 3» and nothing else, and «Cart» once the cart is emptied — no count left in its mirror.
- **Decided in:** B-75, B-131, B-136
- **Coverage:** `HeaderCountTest` «at 1366 the search field keeps its width when the count appears»;
  `HeaderCountTest` «on a phone the account keeps its place when the count appears»;
  `HeaderCountTest` «on a phone the cart button reads its name and nothing else»

### TC-HDR-07 — A menu closes on Escape

- **Who:** guest; Maya
- **Width:** 1440
- **Preconditions:** a category page.
- **Steps:**
  1. Open the sort's menu, press Escape.
  2. Open «Catalog», press Escape.
  3. As Maya, open the account slot's menu, press Escape.
- **Expected:**
  - Every step: the menu closes, the address is unchanged, nothing is opened.
- **Decided in:** B-75
- **Coverage:** `composeApp:DrawnActionsTest` «escape closes the sort's menu»;
  manual: «Catalog» and the account menu share the sort's popup, and only the sort's is driven by a test

### TC-HDR-08 — The footer names only what has a page

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** the home page, `/c` and `/deals` (the three pages that draw the footer).
- **Steps:**
  1. Read the footer's columns.
  2. As a guest, press «Deals», «Haul Plus» and «Track an order».
  3. Open a product, the cart, the account — look for the footer.
- **Expected:**
  - Step 1: «Shop» and «Help» with «Deals», «Haul Plus», «Track an order»; no «Get the app» block, no «Sell» or
    «Company» columns, no «Returns», «Gift cards», «Contact us». At 1440 the footer keeps four column places (the
    app block's place is empty); on a phone two rows of two.
  - Step 2: `/deals`; the sign-in (a guest's «Haul Plus»); the sign-in returning to `/account/orders`. Maya's
    «Haul Plus» is text.
  - Step 3: no footer on the other pages — kept on purpose (every word it holds is in the header).
- **Decided in:** B-72, B-107, B-108
- **Coverage:** `server:DrawnActionsTest` «every word of the footer leads to its page»;
  `composeApp:DrawnActionsTest` «the strip names no page it lacks and the footer's words lead where they say»;
  `MembershipRoutesTest` «the plus pill offers the trial to a non-member and is a mark for a member»;
  golden `Home_Content`

### TC-HDR-09 — A press to the page already shown loads it again, in place

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the cart with a line; a search shown with its suggest panel open.
- **Steps:**
  1. On `/cart`, press the header's cart button.
  2. Press the browser's back.
  3. On a search's results, type the same query and press Enter.
- **Expected:**
  - Step 1: the cart is loaded again behind itself — the thin line under the header, no placeholder, the scroll
    kept.
  - Step 2: back leaves the cart for the page before it (the press took no history entry).
  - Step 3: the results load again and the suggest panel closes.
- **Decided in:** B-62, B-95
- **Coverage:** `KeptPageTest` «a press to the address shown loads it again behind the page»;
  `KeptPageTest` «a press to the address shown closes what was presented over it»;
  `StorefrontTest` «the query already shown submitted again loads the results again and closes the panel»

### TC-HDR-10 — Another page starts at the top; back returns to the page left

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a category page scrolled half way down.
- **Steps:**
  1. Press a card.
  2. Press the browser's back.
  3. Press forward.
- **Expected:**
  - Step 1: the product's placeholder, then the product at the top of the page.
  - Step 2: the category page again, with the results it had.
  - Step 3: the product again.
- **Decided in:** B-62
- **Coverage:** `KeptPageTest` «opening a product draws its placeholder at the top and back returns to the catalog»;
  `StorefrontTest` «a card follows its action to the product page and back returns home»

### TC-HDR-11 — Every page's words select, and the right button opens the store's menu

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a mouse; any page — home, a category, a product, the cart, a search's results.
- **Steps:**
  1. Drag over a heading or a line of text, press ⌘C (Ctrl+C) and paste somewhere else.
  2. Press the right button over the words; over a crumb («Electronics» on a category); over a product card; over a
     word of the footer that leads somewhere.
  3. Press the right button over the search box, the lowest price on a category and the promo code in the cart, with
     nothing typed, then with a word typed and selected; press each entry.
  4. Open a dialog (Haul Plus's trial on the home page), drag over its words and press the right button.
  5. With a menu open, press the right button somewhere else on the page; then the left.
  6. Press the footer's «Haul Plus», and a button twice quickly (a card's «+»).
- **Expected:**
  - Step 1: the words drawn selected; the paste is the words.
  - Step 2: the store's menu at the pointer, drawn as its other menus: «Copy» (with words selected) and «Select
    all» over words; «Open in new tab» and «Copy link» over a crumb, a card and a footer word.
  - Step 3: a field's menu — «Paste» alone while empty; «Cut», «Copy», «Paste», «Select all» with a word selected.
    «Paste» may make the browser ask to read the clipboard; allowed, the clipboard's text is put in the field, a
    price's digits only.
  - Step 4: the dialog's words select and its menu opens over the dialog.
  - Step 5: the right button opens the menu where it pressed; the left closes it and presses nothing.
  - Step 6: «Haul Plus» opens its page; the «+» adds two.
- **Decided in:** B-154, B-155
- **Coverage:** `composeApp:DrawnActionsTest` «every page opens the store's menu and the footer's words offer their links»;
  `composeApp:DrawnActionsTest` «the strip names no page it lacks and the footer's words lead where they say»;
  `ContextMenusTest` «a field's right button offers cut copy paste and select all for its own text»;
  `ContextMenusTest` «a link in a sentence offers its address»;
  `ContextMenusTest` «a sheet's words open the store's menu over the sheet»;
  `ContextMenusTest` «a press off the menu closes it and the right button opens the menu where it pressed»;
  `ReviewWiringTest` «cancel and the close button take the dialog away and send nothing»

### TC-HDR-12 — A control shows the hand over its words, and a crumb under a title takes the pointer's press

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a mouse; the home page, a category (Headphones) and a product page.
- **Steps:**
  1. Hold the pointer over the words of «Catalog», «Sign in», a category in the header's row, a banner's title, a
     kind chip, a card's name, the sort, a facet's option; then over a heading, a description and the search box.
  2. On the category, hold the pointer over the crumb «Electronics» right above the title, then press it.
  3. On the product page, press the crumb «Headphones».
- **Expected:**
  - Step 1: the hand over every control, its words included; the text cursor over the heading, the description and
    the field.
  - Step 2: the hand over the crumb; the press opens Electronics.
  - Step 3: the category Headphones opens.
- **Decided in:** B-157
- **Coverage:** `composeApp:DrawnActionsTest` «a crumb under the category's title opens its page when the pointer
  presses it»; manual: the hand — Compose keeps the pointer's icon behind an internal service, out of a test's reach

