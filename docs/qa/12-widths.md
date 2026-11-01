# Widths — `TC-WID`

The canvas draws 1440 and 390. Below 1024 the store is the phone's arrangement; from 1024 to 1279 the desktop
rearranged; from 1280 the desktop (research «Decided in B-82»). Walk with the browser's responsive mode at each
width, a fresh load per width.

### TC-WID-01 — At 800 the phone's arrangement reads

- **Who:** guest; Maya
- **Width:** 800
- **Preconditions:** a cart with three lines.
- **Steps:**
  1. Open home, a category, the headphones, the cart, checkout (as Maya).
- **Expected:**
  - Step 1: the menu button and the scrolling category row; one column; cards two across; the phone's cart lines —
    no word set one letter a line, no word cut at an edge or broken inside.
- **Decided in:** B-82
- **Coverage:** `WidthsTest` «the header reads at every width»; `WidthsTest` «the cart reads at every width»;
  `WidthsTest` «the product page reads at every width»; `WidthsTest` «every other page reads at every width»

### TC-WID-02 — At 1024 the desktop is rearranged, not squeezed

- **Who:** guest; Maya
- **Width:** 1024
- **Preconditions:** a cart with three lines; Maya's account with orders.
- **Steps:**
  1. Read the header.
  2. Open home, a category, the headphones, the cart, checkout, the account, a search.
- **Expected:**
  - Step 1: the search field on a row of its own under the logo's; the category row draws the words that fit whole
    and keeps «HAUL PLUS» at its end (one word, not one letter a line); «Catalog» lists every category.
  - Step 2: a category's grid three across beside its facets (golden `Catalog_Content_1024`); the cart's lines the phone's beside the summary; the product's buy box under its
    title beside the photo, its facts under the copy; checkout's summary under its sections; the account's tiles
    one under another, its history rows the phone's; a search's kinds scrolling sideways; a banner's title 32 px.
    Nothing cut or broken.
- **Decided in:** B-82
- **Coverage:** `WidthsTest` «the header reads at every width»; `WidthsTest` «every other page reads at every width»;
  golden `Home_Content_1024`; golden `Catalog_Content_1024`; golden `Product_Description_1024`;
  golden `Cart_Content_1024`

### TC-WID-03 — At 1280 long words and prices are fitted, not broken

- **Who:** guest
- **Width:** 1280
- **Preconditions:** a deal of the day with cents; four filters applied on a category.
- **Steps:**
  1. Open the headphones; read the title.
  2. Read the deals row on home.
  3. Read the category's applied filters and the sort.
- **Expected:**
  - Step 1: «SH-718N» whole — the title set as large as its width holds.
  - Step 2: a card's old price beside the price when the two fit, whole under it when they do not — never
    «$432.9» over «9».
  - Step 3: the chips wrap, the sort at the first line's end, «Sort: Popular» whole.
- **Decided in:** B-82, B-108
- **Coverage:** `WidthsTest` «a deals row keeps its prices whole»; `WidthsTest` «the product page reads at every width»;
  `WidthsTest` «every other page reads at every width»

### TC-WID-04 — Resizing across a band redraws the page in the new arrangement

- **Who:** guest
- **Width:** 800, 1024, 1280, 1440
- **Preconditions:** the cart, then a product, open at 1440.
- **Steps:**
  1. Drag the window narrower through 1280, 1024 and below, then wider again — without reloading.
- **Expected:**
  - Step 1: at each band's edge the page takes that band's arrangement at once; nothing is cut in between, nothing
    is lost (the cart's lines, the product's chosen SKU and tab).
- **Decided in:** B-82
- **Coverage:** manual: the tests draw each width fresh; a live resize across a band's edge is only seen in a browser

### TC-WID-05 — The narrowest phone keeps the top row whole

- **Who:** guest
- **Width:** 375
- **Preconditions:** a guest (the widest account slot, «Sign in»).
- **Steps:**
  1. Read the header's top row; press the heart and the account slot.
- **Expected:**
  - Step 1: the menu button, the logo, the heart, «Sign in» and the cart fit in one row; each press lands on its
    own control.
- **Decided in:** B-73
- **Coverage:** golden `HaulHeader_Guest_Phone375`;
  manual: whether each control's touch target is still its own is a finger's, not a golden's
