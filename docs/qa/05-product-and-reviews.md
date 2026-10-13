# Product and reviews — `TC-PDP`

The product page: variants and tabs, the links on it, share, the buy box and its prices, reviews and questions,
their dialogs and «Helpful». The sample product is the Sony WH-1000XM6 (`/p/…` from the home page's deal or a
search for `sony`).

### TC-PDP-01 — The page opens on the cheapest SKU in stock, and a variant changes the price

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Open the headphones.
  2. Press «+ Travel case», then «Silver».
- **Expected:**
  - Step 1: the cheapest SKU in stock is shown; the title under the brand is «WH-1000XM6 Wireless Noise Cancelling
    Headphones», the hyphen drawn; «12K bought this month» (a product under 50 a month says nothing).
  - Step 2: the price follows the bundle; Silver is drawn out of stock, with neither «Add to cart» nor «Buy now».
- **Decided in:** B-45, B-52, B-106
- **Coverage:** `ProductRoutesTest` «the product page opens on the cheapest SKU in stock»;
  `ProductRoutesTest` «a variant changes the price»; `ProductRoutesTest` «an out of stock SKU is drawn out of stock»;
  `ProductRoutesTest` «the headphones carry the bought line the canvas draws»;
  `BoughtThisMonthTest` «below fifty the page says nothing»

### TC-PDP-02 — A variant picked on a tab keeps the tab

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the headphones.
- **Steps:**
  1. Open «Reviews», then press «+ Travel case».
  2. Press «Add to cart».
  3. Open «Specifications», press «Midnight Black».
  4. Press the option already chosen — the chosen colour, the chosen bundle.
- **Expected:**
  - Step 1: the page stays on Reviews (`?sku=…&tab=reviews`), loaded in place, not back to Description.
  - Step 2: the buy box answered with stays on Reviews; the variants still keep the tab.
  - Step 3: still Specifications.
  - Step 4: nothing is sent and the page stays as it is, as for checkout's chosen way to pay.
- **Decided in:** B-62, B-104, B-152
- **Coverage:** `ProductRoutesTest` «a variant picked on a tab keeps the tab»;
  `LineAnswersTest` «add to cart answers the header and the buy box and says so»;
  `ProductPageLinksTest` «the chosen variant presses nothing and another opens its sku»

### TC-PDP-03 — The brand, the rating and «All specifications» lead somewhere

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones, scrolled to the top.
- **Steps:**
  1. Press the brand, «Sony».
  2. Back; press the stars or «2,341 reviews».
  3. Press «All specifications».
  4. Look at the seller's card.
  5. Open the Reviews tab by its address (`?tab=reviews`), scrolled to the top, and press «2,341 reviews».
- **Expected:**
  - Step 1: headphones with «Sony» ticked (`/c/electronics/audio/headphones?brand=Sony`).
  - Step 2: the Reviews tab, on the same page, the tab row brought into view with the reviews under it.
  - Step 3: the Specifications tab, brought into view the same way.
  - Step 4: no chevron — there is no seller page. (The review photos and the histogram's bars are controls since
    B-86: TC-PDP-18 and TC-PDP-19.)
  - Step 5: the tab row brought into view with the reviews under it; the address is the one shown, loaded again in
    place (B-95).
- **Decided in:** B-71, B-121, B-132
- **Coverage:** `ProductPageLinksTest` «the brand opens its products in the category»;
  `ProductPageLinksTest` «the reviews count opens the reviews tab and brings the tabs into view»;
  `ProductPageLinksTest` «all specifications opens the specifications tab and brings the tabs into view»;
  `ProductPageLinksTest` «the reviews count on the reviews tab brings the tabs into view»;
  `ProductRoutesTest` «the brand, the rating and all specifications lead somewhere and share names the product»;
  `ProductRoutesTest` «the page's own links name no sku when its address names none»;
  `LineAnswersTest` «the buy box redrawn names the sku as the page's address does»

### TC-PDP-04 — Share copies the product's link, or shows it to copy by hand

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones at a SKU other than the default; a browser that allows the clipboard, and one
  that refuses it (an insecure origin, or the permission denied).
- **Steps:**
  1. Press share; paste into the address bar.
  2. In the browser that refuses, press share.
- **Expected:**
  - Step 1: the icon turns to a check for two seconds; the link pasted is the page's own origin with
    `/p/<id>?sku=<sku>`.
  - Step 2: under the photo, «Couldn’t copy the link. Copy it from here:» over the whole link, selectable, with a
    close button.
- **Decided in:** B-71, B-104
- **Coverage:** `ProductPageLinksTest` «share copies the product's link and says it did»;
  `ProductPageLinksTest` «share the browser refuses shows the link to copy by hand»; golden `Product_ShareRefused`;
  manual: the clipboard and its refusal are the browser's — the tests drive a seam, and neither path was walked in a
  real browser when it was built

### TC-PDP-05 — «Add to cart» says it added, and the buy box says what the cart holds

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones, not in the cart.
- **Steps:**
  1. Press «Add to cart».
  2. Press it again; then add until the line is at ten (or at the stock).
  3. Press «View cart» beside «2 in your cart» (or the count shown) — the words say, «View cart» leads (B-75).
- **Expected:**
  - Step 1: no page loads; the header's count and the buy box change; «Added to your cart» with «View cart» at the
    bottom; under the button «1 in your cart».
  - Step 2: the count follows; at the limit «Add to cart» is greyed and presses nothing.
  - Step 3: `/cart`.
- **Decided in:** B-48, B-63, B-75
- **Coverage:** `BuyBoxWiringTest` «add to cart shows its message and the buy box says the line is in the cart»;
  `ProductButtonsTest` «add to cart puts one more of the SKU shown into the cart»;
  `ProductButtonsTest` «at the line's limit add to cart is gone and buy now only selects the line»;
  golden `Product_InCart`; golden `Product_InCart_AtLimit`

### TC-PDP-06 — «Buy now» checks out that product alone, at the quantity the cart holds

- **Who:** Maya; guest
- **Width:** 1440
- **Preconditions:** Maya's cart with the mug at 2 and two other lines ticked.
- **Steps:**
  1. As Maya, open the mug and press «Buy now».
  2. Go back to the cart.
  3. As a guest with an empty cart, press «Buy now» on a product.
- **Expected:**
  - Step 1: checkout with the mug alone, at 2 (one when the cart holds none) — never one more than the cart held —
    at the price and stock the page showed.
  - Step 2: the other lines are still in the cart, unticked.
  - Step 3: the sign-in, then checkout. Known and recorded: a guest whose account already has a ticked cart reaches
    checkout with those lines too (the merge keeps them, B-100's findings).
- **Decided in:** B-48, B-100
- **Coverage:** `ProductButtonsTest` «a customer's buy now checks out the SKU shown alone at the quantity the cart holds»;
  `ProductButtonsTest` «a guest's buy now puts the SKU shown in once and goes to checkout through sign-in»;
  `CartRoutesTest` «a line bought alone is the only one selected»;
  `ChangedLinesTest` «a changed line bought alone is accepted at the price the page showed»;
  `BuyBoxWiringTest` «a customer's buy now sends its line change and opens checkout»;
  `BuyBoxWiringTest` «a guest's buy now signs in and then opens checkout»

### TC-PDP-07 — The delivery lines agree with the cart and the viewer

- **Who:** guest; Maya; Sam
- **Width:** 1440
- **Preconditions:** a product under $35 (the mug) and one over (the headphones).
- **Steps:**
  1. As a guest, read the mug's courier and pickup lines.
  2. As Maya (Plus), read the same.
  3. As Sam with an address saved at checkout, read «To …».
- **Expected:**
  - Step 1: the courier's day from the delivery calendar (the day after, plus the seller's dispatch days, one more
    after 23:30), the pickup a day after it; $5.99 on both lines; the headphones «Free» on both.
  - Step 2: «Free» on both, the mug included.
  - Step 3: «To» his own city and ZIP; a guest and a customer with no address read «Brooklyn, NY 11211».
- **Decided in:** B-98
- **Coverage:** `DeliveryAgreesTest` «the product page charges for delivery what the cart would, by the viewer's membership»;
  `DeliveryAgreesTest` «the header and the product page name the customer's own place»

### TC-PDP-08 — A price is the same on the card, the page, the cart and checkout

- **Who:** guest; Maya
- **Width:** 1440
- **Preconditions:** a live deal of the day; the sale live.
- **Steps:**
  1. Note a deal card's price; open it; add it; open the cart; as a customer, open checkout.
- **Expected:**
  - Step 1: one price everywhere, the price it beats struck through on the card and the page. After the deal ends
    (on a local stack: ended in its database — a seeded stand re-dates the same deals at each midnight, B-70)
    the card is gone and a line added at the deal's price is marked changed in the cart (TC-CART-06); after the sale
    ends every SKU is at its regular price with nothing struck through.
- **Decided in:** B-53, B-57, B-58
- **Coverage:** `DealPriceTest` «a generated deal is charged the price its card shows»;
  `DealPriceTest` «after its window the deal card is gone and the cart charges the regular price»;
  `CampaignEndTest` «at the sale's end the cart the checkout and placement charge the regular price»;
  `PlusEarlyAccessTest` «inside the early window a member sees the sale price and a non-member the regular one»

### TC-PDP-09 — Reviews and questions list ten, and ten more in place

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones (2,341 reviews, 86 questions on the seed).
- **Steps:**
  1. Open «Reviews»; read the histogram and the count; press «Show more reviews» twice.
  2. Open «Questions»; press «Show more questions».
- **Expected:**
  - Step 1: the count, the histogram and the rows agree, and the rating is the bars' average (B-86: the
    headphones' 84 / 12 / 2 / 1 / 1 % under «4.8»); ten reviews, then twenty, then thirty — loaded in place
    (`shown=` in the address), the page kept.
  - Step 2: the answered questions first, ten more in place; the open one reads «Not answered yet».
  - Both: a date in the store's current year has no year («Sep 28»); one of an earlier year carries it
    («Sep 28, 2025»).
- **Decided in:** B-71, B-99, B-129, B-86
- **Coverage:** `ReviewPagesTest` «the headphones' tabs offer more on the seed itself»;
  `SeedReviewsTest` «every product of the seed has reviews and the rating they give»;
  `ReviewPagesTest` «a review written moves the rating to the histogram's average»;
  `ReviewPagesTest` «the reviews tab lists ten and shows ten more in place until there are no more»;
  `ReviewPagesTest` «the questions tab lists ten and shows ten more in place until there are no more»;
  `ProductPageLinksTest` «show more reviews loads ten more in place»;
  `SeedReviewsTest` «every product's counts and histogram are its stored rows»;
  `ReviewRoutesTest` «the questions tab lists the answered ones first and the open one as not answered yet»;
  `ReviewRoutesTest` «a review or a question from an earlier year carries its year»

### TC-PDP-10 — A customer writes one review, and then sees it instead of the button

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** a product Sam has not reviewed (on the stand, one of his delivered orders' products is
  «Verified»).
- **Steps:**
  1. Press «Write a review»; press low in the text box.
  2. Fill in the stars and a title, a body of five characters; press «Post».
  3. Write a proper body and press «Post» straight after typing.
  4. Read the tab.
- **Expected:**
  - Step 1: the dialog over the page, named by the product's listing name; the whole box focuses.
  - Step 2: nothing is sent; the body is named as at fault.
  - Step 3: the dialog closes, the page is drawn again: the count up by one, his review first.
  - Step 4: where the button was, «You reviewed this on <date>»; no second «Write a review».
- **Decided in:** B-22, B-60, B-83, B-99
- **Coverage:** `ReviewWiringTest` «a review is posted where the form says and the answer closes the dialog and redraws the page»;
  `ReviewWiringTest` «a form at fault by the server's rules sends nothing and says why»;
  `ReviewWiringTest` «post sends a review typed just before the press»;
  `ReviewRoutesTest` «a reviewer sees their review first and no second write a review»;
  `ReviewRoutesTest` «a verified review - marked verified, and the product's count grows by one»;
  `ReviewStatesTest` «a reviewer's tab says when they reviewed in place of the button»;
  `ReviewStatesTest` «a press low in the review box focuses it»; golden `Product_Reviews_Mine`

### TC-PDP-11 — A question says what happens to it

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** any product.
- **Steps:**
  1. Press «Ask a question»; read the note.
  2. Send a question of nine characters, then one of twenty.
- **Expected:**
  - Step 1: «Your question appears on this page for other shoppers, marked «Not answered yet». Sellers can't answer
    new questions on Haul yet.»
  - Step 2: the short one is refused, the text named; the long one closes the dialog and is listed «Not answered
    yet», the count up by one.
- **Decided in:** B-22, B-99
- **Coverage:** `ReviewRoutesTest` «a question asked shows as not answered yet and is counted»;
  `ReviewRoutesTest` «a question shorter than ten characters is refused naming text»;
  `ReviewWiringTest` «a question is sent where its form says»;
  `ReviewWiringTest` «send sends a question typed just before the press»;
  `ReviewStatesTest` «a press low in the question box focuses it»; golden `Product_QuestionDialog`

### TC-PDP-12 — The review and question dialogs: a guest, cancel, a refusal, no answer

- **Who:** guest; Sam
- **Width:** 1440
- **Preconditions:** a product.
- **Steps:**
  1. As a guest, press «Write a review».
  2. As Sam, open the dialog, type, press «Cancel»; open it again and press ×.
  3. Open it, type, take the network away (DevTools offline) and press «Post».
  4. Back online, open it, read the page with a screen reader (or the accessible mirror, `scripts/a11y-check.py`),
     type in a field and press Escape.
- **Expected:**
  - Step 1: the sign-in instead of the dialog.
  - Step 2: the dialog goes, nothing sent.
  - Step 3: «Couldn't reach the store» in the dialog, what was typed kept; a refusal of the server's is drawn in the
    dialog, the field it names under it, and the page is not fetched again.
  - Step 4: only the dialog is read — nothing of the page under it, and the dialog is not one button holding its
    text; Escape takes it away, with the caret in a field too, and nothing is sent.
- **Decided in:** B-22, B-92, B-151
- **Coverage:** `ReviewWiringTest` «a guest is sent to sign in»;
  `ReviewWiringTest` «cancel and the close button take the dialog away and send nothing»;
  `ReviewWiringTest` «escape takes the dialog away and sends nothing»;
  `NamedPressesTest` «an open dialog or sheet leaves only itself in the tree»;
  `ReviewWiringTest` «no answer keeps the dialog and what was typed»;
  `ReviewWiringTest` «the server's refusal is drawn in the dialog and the page is not fetched again»;
  `ReviewWiringTest` «a field the server refused is drawn under it»

### TC-PDP-13 — «Helpful» counts one vote, looks voted, and is taken back by a second press

- **Who:** Sam; guest
- **Width:** 1440
- **Preconditions:** the headphones, opened on Description.
- **Steps:**
  1. As Sam, open «Reviews» (in place) and press «Helpful» on someone else's review.
  2. Press it again.
  3. Find Sam's own review (TC-PDP-10).
  4. As a guest, press «Helpful».
- **Expected:**
  - Step 1: the count up by one, drawn at once (not after a reload); the pill filled with a tick.
  - Step 2: the vote taken back, the count down, the pill plain.
  - Step 3: «Your review» in place of the pill; it presses nothing.
  - Step 4: the sign-in; no vote sent.
- **Decided in:** B-71, B-93, B-99
- **Coverage:** `RefreshInPlaceTest` «helpful after switching to the reviews tab in place draws the count the server answers»;
  `ReviewRoutesTest` «a vote moves the count by one and the second press moves it back»;
  `ReviewStatesTest` «a voted helpful is selected and an unvoted one is not»;
  `ProductPageLinksTest` «the viewer's own review reads your review and presses nothing»;
  `ReviewWiringTest` «a guest's helpful signs in and sends no vote»

### TC-PDP-14 — Out of stock: no buy buttons, and «Save» is the heart's twin

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a product whose every SKU is out of stock, on Maya's Saved list.
- **Steps:**
  1. Open it; read the buy box.
  2. Press «Saved».
- **Expected:**
  - Step 1: neither «Add to cart» nor «Buy now» is offered; «Saved» with a filled heart; no courier or pickup day,
    and no cut-off — the stock advice stands where the delivery lines were.
  - Step 2: the product leaves her list; the button reads «Save».
- **Decided in:** B-48, B-71, B-120
- **Coverage:** `ProductPageLinksTest` «saved out of stock reads saved and takes the product off the list»;
  `OutOfStockPromiseTest` «a product with nothing in stock promises no day on its page»;
  `BuyBoxWiringTest` «out of stock neither button sends anything»;
  `ProductButtonsTest` «out of stock neither add to cart nor buy now is offered»; golden `Product_OutOfStock`

### TC-PDP-15 — Prices, ratings and dashes in the display face are legible

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a phone at full resolution, or the page zoomed; the headphones and an order page.
- **Steps:**
  1. Read the product's title, its price and its rating; a card's price with cents; an order's title.
- **Expected:**
  - Step 1: «WH-1000XM6» with its hyphen, «4.8» not «1.8», «$31.49» not «$31.19», the order title's dash and
    «#HL-48303» — figures and punctuation in the legible cut. Letters' hairlines (an H's bar) may still vanish at
    the largest sizes: recorded, not a failure (B-106).
- **Decided in:** B-106
- **Coverage:** `LegibleFiguresTest` «a display style draws its figures and punctuation in the legible cut and its letters as the canvas does»;
  `LegibleFiguresTest` «the legible cut moves no figure, dash or price at any size the canvas sets the face in»;
  golden `DisplayFace_Characters`;
  manual: whether the browser honours the optical-size axis was never checked in a browser; a golden is drawn on the desktop

### TC-PDP-16 — The loading page and the fact tiles do not jump or cut

- **Who:** guest
- **Width:** 1440, 1280, 390
- **Preconditions:** a slow network (DevTools «Fast 4G»); the headphones and a product whose facts are long
  specification names.
- **Steps:**
  1. Open the product from a card and watch the placeholder turn into the page.
  2. Read the fact tiles on Description.
- **Expected:**
  - Step 1: the photo's place does not move when the page arrives — not sideways (no thumbnail column in the
    placeholder) and not down (the placeholder's crumbs row is as tall as the crumbs).
  - Step 2: no tile's title is cut («Noise ca»); a long name wraps between words and shrinks to fit; the canvas's
    «40 h», «3 min» keep their display size.
- **Decided in:** B-71, B-104, B-143
- **Coverage:** `ProductPageLayoutTest` «the loading photo stands where the photo arrives»;
  `ProductPageLayoutTest` «the loading photo stands where the photo arrives at 1280»;
  `ProductPageLayoutTest` «the loading photo stands where the photo arrives on a phone»;
  `ProductPageLayoutTest` «a fact's title is never cut»;
  `ProductPageLayoutTest` «the canvas's facts keep their display size»;
  golden `Product_Loading`; golden `Product_Description_SpecFacts`

### TC-PDP-17 — A product is drawn with its stored photo, or its picture, everywhere it is drawn

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a server with object storage (`HAUL_S3_*`), so the sample products' photos are stored; a product
  without one; and a server without object storage (the stand: «no object storage: seed pictures only» in its log).
- **Steps:**
  1. Find a sample product on a card, its page, the cart, checkout's «Your order», an order's line, the suggest
     panel's «Top products», the review and question dialogs, and — once ordered — the account's order card and its
     history row.
  2. Find a product with no photo, in the same places.
  3. On the server without storage, open the headphones' page and pick the other colour.
  4. Block `/images/` in DevTools and reload.
  5. At 800, open the headphones' page; widen the window to 1440 and narrow it back.
- **Expected:**
  - Step 1: the same photo in each place, served from the store's own origin (`/images/products/<key>`, SVG) — the
    product's own kind: the headphones a pair of headphones, the mug a mug, the duvet bedding, not one tinted box for
    every sample product (B-149).
  - Step 2: the same picture in each place — the seed's flat drawing of the product's category, in the shown SKU's
    colour, over its tile tone — served from the store's own origin (`/images/art/<name>.svg`).
  - Step 3: every product carries its picture; the page's picture changes colour with the SKU.
  - Step 4: the striped placeholder tile, with no caption, and nothing broken — the tile stays while a photo or a
    picture is absent, loading or failed.
  - Step 5: the picture is sharp at every width — its edges as crisp at ≈768 px as on a card (B-149).
- **Decided in:** B-30, B-108, B-78, B-149
- **Coverage:** `PhotoRoutesTest` «a product with a stored photo carries its address and one without carries its picture»;
  `PhotoRoutesTest` «the cart, checkout, an order and the suggest panel carry the photo the card carries»;
  `PhotoRoutesTest` «without object storage every product carries its picture»;
  `SeedArtTest` «one category's products share a set and differ by colour»;
  `PhotoFallbackTest` «a picture sent as SVG replaces the placeholder tile»;
  `PhotoFallbackTest` «a photo that failed to load leaves the placeholder tile»;
  `PhotoFallbackTest` «a photo still loading shows the placeholder tile»;
  `LinePhotosTest` «cart lines draw their photos»; golden `ProductCard_Photo`; golden `Product_Description`;
  `PhotoRoutesTest` «a sample product's stored photo is its own picture»;
  `SeedPhotosTest` «a photo the seed stored before is replaced by today's and one that is not the seed's is kept»;
  `PhotoRoutesTest` «the review and question dialogs and a review's photos carry pictures»;
  `LinePhotosTest` «the review and question dialogs draw their product's picture»;
  `LinePhotosTest` «the account's order cards and history rows draw their pictures»;
  `PhotoFallbackTest` «a picture whose box grows is decoded again at its new size»

### TC-PDP-18 — A bar of the histogram lists the reviews of its stars

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones on the Reviews tab (`?tab=reviews`).
- **Steps:**
  1. Press the «4» bar of the histogram.
  2. Press «Show more reviews».
  3. Press «All»; then press the «4» bar again, and the «4» bar once more.
  4. Back.
- **Expected:**
  - Step 1: only four-star reviews, Aisha's first; «Showing 4-star reviews» and «All» under the bars; the «4» bar
    drawn in bold, the others' fill grey; the rating, «out of 5 · 2,341 reviews» and the percentages unchanged; the
    address `?tab=reviews&stars=4`, loaded in place (the page kept, the header not redrawn).
  - Step 2: ten more four-star reviews in place, `&stars=4&shown=20`.
  - Step 3: «All» lists every review again (`?tab=reviews`); the bar pressed again does the same.
  - Step 4: the address before, and the reviews it lists.
  - A bar nobody gave (on a product with few reviews) is not a press.
- **Decided in:** B-86
- **Coverage:** `ReviewPagesTest` «a bar lists the reviews of its stars in place and pressed again or all lists every review»;
  `ReviewPagesTest` «a bar nobody gave presses nothing»;
  `ReviewPagesTest` «the stars listed are one to five»;
  `ReviewControlsTest` «a bar lists the reviews of its stars in place»;
  `ReviewControlsTest` «all and the pressed bar list every review again»;
  `ReviewControlsTest` «the pressed bar lists every review again»;
  `ReviewControlsTest` «a bar without an action presses nothing»

### TC-PDP-19 — A review's photo opens the review's photos over the page

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the headphones on the Reviews tab; Daniel R.'s review has two photos.
- **Steps:**
  1. Press Daniel's first photo; press «›», then «‹»; press the arrow keys.
  2. Press «×». Open it again and press Escape. Open it again and press outside the photo's card, over another
     review's «Helpful».
  3. With a screen reader (or the accessible mirror, `scripts/a11y-check.py`), read the tiles and the viewer.
- **Expected:**
  - Step 1: each tile and the viewer draw a picture of the headphones, a different one for each photo (B-149); the
    photo large over the dimmed page, «Daniel R.» and «Photo 1 of 2»; «›» shows the second, «‹» the first; «‹» on the first and «›» on the last are faded and press nothing; the arrows do the same, after a press on
    «›» or «‹» as well (B-144).
  - Step 2: each closes the viewer; the press outside votes nothing; the address does not change and nothing loads.
  - Step 3: «Open photo 1 of 2 by Daniel R.» on a tile; «Close photos», «Previous photo», «Next photo» in the viewer;
    after it closes the mirror holds the page again (B-126).
- **Decided in:** B-86, B-144, B-149
- **Coverage:** `ReviewControlsTest` «a review's photo opens its viewer which steps through its photos and closes by the cross»;
  `ReviewControlsTest` «a review's photos are drawn as their pictures on the tiles and in the viewer»;
  `ReviewControlsTest` «escape closes the photo viewer and the arrow keys step through it»;
  `ReviewControlsTest` «after a press on an arrow the arrow keys and escape still work»;
  `ReviewControlsTest` «on a phone after a press on an arrow the arrow keys and escape still work»;
  `SheetOverPageTest` «a sheet keeps the keys after the control pressed is disabled»;
  `ReviewControlsTest` «a press outside the photo closes the viewer and one on it does not»;
  `ReviewControlsTest` «on a phone a press outside the photo closes the viewer»;
  `NamedPressesTest` «every press and field in the dialogs has a name»
