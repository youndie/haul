# Catalog and filters — `TC-CAT`

The catalog's root, a category page and its filters, the deals page. Every press that only filters, sorts or
pages a page loads its parts in place (B-63): the header, the crumbs and the scroll stay, a thin line under the
header says a load is on its way. Pages of a search are in [search](04-search.md).

### TC-CAT-01 — A category has one address

- **Who:** guest
- **Width:** 1440
- **Preconditions:** none.
- **Steps:**
  1. Open `/c/headphones?brand=Sony` typed into the address bar.
  2. On the page, tick another brand.
  3. Open a product and press the category in its breadcrumbs.
- **Expected:**
  - Step 1: the address becomes `/c/electronics/audio/headphones?brand=Sony` (a `301`, the query kept) and the page
    draws.
  - Step 2: one request, the parts (`GET /ui/parts/c/electronics/audio/headphones?…`); the address keeps its form; the
    page is not loaded a second time.
  - Step 3: the category at its whole path.
- **Decided in:** B-68
- **Coverage:** `OneCategoryAddressTest` «another form of a category redirects to its address»;
  `OneCategoryAddressTest` «every load on a category page names the path the page was opened at»;
  `OneCategoryAddressTest` «every link to a category is its one address»;
  `WebBundleTest` «another form of a category address arrives at the page at its one address»

### TC-CAT-02 — A category with subcategories offers them; a leaf offers its kinds

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Open `/c/electronics` and read the row above the results.
  2. Open `/c/electronics?kind=Over-ear`.
  3. Open headphones (a leaf) and press a kind, then press it again.
- **Expected:**
  - Step 1: the row is Electronics' subcategories, each opening its page; no «All», none selected — no headphone
    kinds over speakers and TVs.
  - Step 2: the kind is not applied (no chip, no pill selected, the results unfiltered).
  - Step 3: the leaf's kinds with «All»; the kind pressed is selected and its results load in place; pressed again
    it sends nothing (what is chosen carries no action).
- **Decided in:** B-102
- **Coverage:** `AppliedFiltersTest` «a category with subcategories offers them and applies no kind»;
  `AppliedFiltersTest` «what is chosen carries nothing»

### TC-CAT-03 — A facet tick loads the results in place, and back undoes it

- **Who:** guest
- **Width:** 1440
- **Preconditions:** headphones, scrolled so the facets are in view.
- **Steps:**
  1. Tick «Sony».
  2. Tick a second brand straight after, before the first answer lands.
  3. Press back, then forward.
  4. Reload the page.
- **Expected:**
  - Step 1: one `GET /ui/parts/…`; the header, the crumbs and the scroll stay, no placeholder, the line under the
    header while it loads; the title, the counts and the results change; the address gains `brand=Sony`.
  - Step 2: the last tick wins.
  - Step 3: back draws the results before the tick; forward the tick's.
  - Step 4: the reload draws what was shown.
- **Decided in:** B-62, B-63, B-64
- **Coverage:** `KeptPageTest` «ticking a facet keeps the page and its scroll while the new results load»;
  `InPlaceAnswersTest` «ticking a facet loads its parts and draws them in place without a page»;
  `InPlaceAnswersTest` «back after a tick draws the page before it and forward the page of the tick»;
  `InPlaceAnswersTest` «the last tick wins»;
  `PartsRoutesTest` «every filter sort and page of a category answers the parts of the page it opens»;
  `PartsRoutesTest` «a tick sends the results and not the frame»

### TC-CAT-04 — A facet option with nothing to show is faded and presses nothing

- **Who:** guest
- **Width:** 1440
- **Preconditions:** headphones with a filter that leaves some brand at 0 (e.g. a high rating).
- **Steps:**
  1. Press a faded option at 0 that is not ticked.
  2. Press «Show N more» under the brand facet; then tick a brand.
- **Expected:**
  - Step 1: nothing is sent; the option is drawn faded. A ticked option at 0 keeps its action (it unticks).
  - Step 2: every brand is listed, the filters, sort and page kept; ticking a brand keeps the list expanded.
- **Decided in:** B-49, B-75
- **Coverage:** `CatalogRoutesTest` «an option with nothing to show presses nothing unless it is ticked»;
  `server:DrawnActionsTest` «show more lists every brand with the filters the sort and the page kept»;
  `composeApp:DrawnActionsTest` «show more on the brand facet opens the page with the facet expanded»;
  golden `Catalog_Facets_ZeroCounts`

### TC-CAT-05 — The price range applies what was typed or dragged, and its chip removes it

- **Who:** guest
- **Width:** 1440
- **Preconditions:** headphones, page 2, sorted by price, one brand ticked.
- **Steps:**
  1. Type `100` into «from» and press Enter at once.
  2. Type `400` into «to» and press Tab.
  3. Drag the upper thumb left and release; then grab a thumb at the very end of the track by its outer half.
  4. Type `500` into «from» and `200` into «to» and leave the field with a click on blank page space.
  5. Press × on the range's chip.
- **Expected:**
  - Step 1: the range loads in place from the first page, the brand and the sort kept; a chip «From $100».
  - Step 2: chip «$100 – $400»; one parts request, not one aborted and then another — however quickly the
    press into «to», the digits and Tab follow each other.
  - Step 3: the range the thumb was released at; the end thumb is grabbed.
  - Step 4: the caret leaves the field and the bounds are swapped (`price_min=200&price_max=500`) — one request, the
    address carrying the range. A press on the box around a field's digits keeps the field. However quickly
    `200` follows the press into «to», and however slow the answer to the load leaving «from» sent (a throttled
    link), «to» keeps `200` when that answer lands, and the click sends both bounds (B-134).
  - Step 5: the page without the range. A field left as it was loads nothing.
- **Decided in:** B-69, B-76, B-90, B-118, B-134
- **Coverage:** `PriceRangeTest` «a lower bound typed and entered loads the range in place»;
  `PriceRangeTest` «a bound typed just before enter is the one applied»;
  `PriceRangeTest` «leaving a price field applies what it holds»;
  `PriceRangeTest` «a click on blank page space leaves a price field and applies it»;
  `PriceRangeTest` «a press on the box around a price field keeps it»;
  `PriceRangeTest` «tab out of a price field sends one load»;
  `PriceRangeTest` «a bound typed while the other bound loads is kept and sent with it»;
  `PriceRangeTest` «bounds typed backwards while one loads end in one load swapped»;
  `PriceRangeTest` «a bound typed before the other's load reads it goes with that load once»;
  `PriceRangeTest` «a field left as it was loads nothing»;
  `PriceRangeTest` «a thumb released loads the range it was dragged to»;
  `PriceRangeTest` «a thumb at the end of the track is grabbed by its outer half»;
  `PriceRangeTest` «the applied range is a chip that removes it»;
  `PriceRangeTest` «a range makes its address from the bounds that are set»;
  `server:DrawnActionsTest` «the price range applies with a load and its chip removes it»

### TC-CAT-06 — Every filter applied has a chip, is counted once, and «Clear all» is there only with a chip

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** headphones, a kind selected, sorted by price, no filter.
- **Steps:**
  1. Look for «Clear all».
  2. Tick a brand, «Delivery: Tomorrow» and a rating («4.5 and up»).
  3. On a phone, read «Filters N» and the sheet's «N applied».
  4. Remove the rating's chip alone.
  5. Press «Clear all».
- **Expected:**
  - Step 1: no «Clear all» with nothing applied (a kind and a sort are not chips).
  - Step 2: three chips — the brand, «Delivery: Tomorrow», «Rating: 4.5 and up».
  - Step 3: «Filters 3», «3 applied».
  - Step 4: only the rating goes.
  - Step 5: every chip goes; the kind and the sort stay; the first page.
- **Decided in:** B-102
- **Coverage:** `AppliedFiltersTest` «every filter applied has a chip and is counted once»;
  `AppliedFiltersTest` «clear all is there only while a chip is and keeps the kind»;
  `server:DrawnActionsTest` «clear all drops every filter and keeps the kind and the sort»;
  `ClearAllTest` «clear all is drawn beside the chips only while a filter is applied»;
  `composeApp:DrawnActionsTest` «clear all opens the page without its filters»

### TC-CAT-07 — The sort opens its orders, and the order shown only closes the menu

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a category page, page 2, a brand ticked.
- **Steps:**
  1. Open the sort's menu and pick «Price: low to high» (or any other order).
  2. Open the menu again and pick the order shown.
- **Expected:**
  - Step 1: the results reload in place from the first page, the brand kept.
  - Step 2: the menu closes and nothing is sent.
- **Decided in:** B-37, B-102, B-121
- **Coverage:** `server:DrawnActionsTest` «the sort offers every order and keeps the filters»;
  `composeApp:DrawnActionsTest` «the sort opens its orders and the one picked is opened»;
  `composeApp:DrawnActionsTest` «picking the order shown closes the sort's menu and asks for nothing»;
  `AppliedFiltersTest` «no press on a category page loads the page it is on»

### TC-CAT-08 — «Show N more» appends, up to eight pages; a page number opens one page at the top

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a category or search with more than eight pages of 24 (the search «everyday» on the seed has
  eight).
- **Steps:**
  1. Scroll to the foot of the first page and press «Show 24 more».
  2. Reload the page.
  3. Keep pressing «Show N more».
  4. Press a page number.
- **Expected:**
  - Step 1: the next 24 cards appear under the ones already drawn; the scroll stays; the address is
    `?page=2&from=1`. «N» is what the next page holds (fewer on the last).
  - Step 2: the same two pages in one grid.
  - Step 3: once the grid holds eight pages there is no «Show N more»; the page numbers go on.
  - Step 4: that page alone, at the top of the page.
- **Decided in:** B-77, B-90, B-108
- **Coverage:** `PaginationTest` «show more goes on from the pages the grid holds and says how many are left»;
  `PaginationTest` «a grid holding eight pages offers no more of them»;
  `ReachablePagesTest` «show more appends the next page under the cards already drawn»;
  `server:DrawnActionsTest` «show more appends the next page and a page number opens its page alone with the filters kept»;
  `server:DrawnActionsTest` «a grid holds at most eight pages»;
  `KeptPageTest` «a page number opens its page at the top and show more keeps the scroll»

### TC-CAT-09 — The page numbers reach every page; one page draws none

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a listing of many pages; a listing of one page (a narrow filter).
- **Steps:**
  1. Open page 7.
  2. Press the next number, and so on, to the last page.
  3. Open the listing of one page.
- **Expected:**
  - Step 1: «1 … 6 7 8 … N» — the first, the last, the current with one on either side; at most seven numbers.
    Page 1 reads «1 2 3 … N».
  - Step 2: every page is one press from the one before.
  - Step 3: no page number at all.
- **Decided in:** B-77, B-108
- **Coverage:** `PaginationTest` «page seven of a long listing shows its neighbours and both ends»;
  `PaginationTest` «every page of a long listing is reachable from the one before it»;
  `ReachablePagesTest` «page seven of a long listing draws its neighbours and both ends and each opens its page»;
  `ReachablePagesTest` «a listing of one page draws no page number»

### TC-CAT-10 — The phone's filter sheet stays open over the results its ticks load

- **Who:** guest
- **Width:** 390
- **Preconditions:** headphones.
- **Steps:**
  1. Press «Filters».
  2. Tick two brands one after the other.
  3. Type `200` into the price «to» and press «Done».
  4. Press «Show N items».
  5. Open the sheet, press ×; open it again and press the browser's back.
- **Expected:**
  - Step 1: the sheet over the page, the facets drawn from the page's tree.
  - Step 2: each tick loads in place and the sheet stays open, drawn from the new results; while a tick is on its
    way the sheet's facets wait for it.
  - Step 3: the range applies and the sheet stays open; «Clear all» appears in the sheet. A tap on the sheet's
    blank space instead of «Done» applies it too. A bound typed into «to» while the load «from» sent is on its way
    stays in the field when that answer lands (B-134).
  - Step 4: the sheet closes over its results.
  - Step 5: × closes it without asking the server; back closes it too.
- **Decided in:** B-54, B-63, B-69, B-102, B-118, B-134
- **Coverage:** `composeApp:DrawnActionsTest` «ticking two facets keeps the filter sheet open over the new results»;
  `composeApp:DrawnActionsTest` «the filter sheet follows nothing while the page its tick opened is on its way»;
  `composeApp:DrawnActionsTest` «show N items closes the filter sheet over its results»;
  `composeApp:DrawnActionsTest` «the filter sheet's close closes it without asking the server»;
  `composeApp:DrawnActionsTest` «a page not opened from the filter sheet leaves it closed»;
  `PriceRangeTest` «the filter sheet applies a typed range and stays open over its results»;
  `PriceRangeTest` «a tap on the filter sheet's blank space applies a typed bound»;
  `PriceRangeTest` «the filter sheet keeps a bound typed while the other bound loads»;
  `ClearAllTest` «the filter sheet says clear all only while a filter is applied»;
  golden `Catalog_FiltersSheet_Phone`

### TC-CAT-11 — Filters that leave nothing say so

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a category.
- **Steps:**
  1. Apply filters no product meets (e.g. a brand and a price range that exclude each other).
- **Expected:**
  - Step 1: the empty state with its own «Clear all», the facets still drawn so a filter can be taken off.
- **Decided in:** B-102
- **Coverage:** `CatalogRoutesTest` «a filter set with no products answers an empty page with its facets»;
  golden `Catalog_Empty`

### TC-CAT-12 — The deals page: today's deals, then everything on sale

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a live deal.
- **Steps:**
  1. Open `/deals`.
  2. Press «Show 24 more» under the first page.
  3. Go to page 3 and press «Show 24 more» there.
- **Expected:**
  - Step 1: «Deals of the day» with the countdown, then «On sale»: every product whose price is under its old one,
    the deepest discount first, 24 a page.
  - Step 2: the page is kept (no placeholder, the scroll stays) and the next cards appear; today's deals stay above.
  - Step 3: the next page loads in place.
- **Decided in:** B-37, B-63, B-77
- **Coverage:** `server:DrawnActionsTest` «the deals page shows today's deals and everything on sale deepest first»;
  `PartsRoutesTest` «the deals load their pages in place except to and from today's deals»;
  `server:DrawnActionsTest` «page zero of the deals is refused»

### TC-CAT-13 — A card with nothing to add is greyed and takes the press

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a card whose SKU is out of stock, and one whose line is at ten or at the stock in the cart.
- **Steps:**
  1. Press the greyed «+» of each.
  2. Press «+» on the card of a SKU with one left in stock.
- **Expected:**
  - Step 1: nothing is sent, no message, and the product does not open (the press is the «+»'s). A card of a
    product with nothing in stock names no delivery day: it ends at its rating.
  - Step 2: it is added, «Added to your cart», and the card's «+» is greyed after it.
- **Decided in:** B-37, B-75, B-81, B-120
- **Coverage:** `server:DrawnActionsTest` «a card out of stock has no plus»;
  `OutOfStockPromiseTest` «a product with nothing in stock promises no day on its card»; `OutOfStockCardTest`
  «a card out of stock comes without a day and draws none»;
  `InPlaceAnswersTest` «a card's plus with nothing to add sends nothing and opens nothing»;
  `LineAnswersTest` «a card's plus that takes the last one says so and leaves nothing to add»;
  golden `ProductCard_NothingToAdd`

### TC-CAT-14 — The catalog's root lists every top-level category

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Open `/c` (home's «All N categories»).
  2. Press a tile.
- **Expected:**
  - Step 1: every top-level category as a tile, in the header's order; the footer under them.
  - Step 2: that category's page.
- **Decided in:** B-49
- **Coverage:** `composeApp:DrawnActionsTest` «all categories opens the catalog root»;
  `server:DrawnActionsTest` «all categories on the home page opens the catalog root»
