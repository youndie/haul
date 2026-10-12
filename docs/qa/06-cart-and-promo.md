# Cart and promo — `TC-CART`

The cart a guest and a customer fill: its lines, the selection, «Save for later», the promo code, the summary and
the empty cart. «Buy now» is walked on the product page (TC-PDP-06); a command that fails, cart included, in
[errors and loading](11-errors-and-loading.md) (TC-ERR-08).

### TC-CART-01 — The stepper stays within one and the line's limit, and looks it

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a cart with the mug at 1 and a line whose SKU has two left in stock.
- **Steps:**
  1. Press «−» on the mug.
  2. Press «+» on the mug.
  3. Press «+» on the other line until it stops.
  4. Press a line's title, and its tile.
- **Expected:**
  - Step 1: «−» at one is muted and presses nothing.
  - Step 2: the line goes to 2; the cart is drawn again with its totals.
  - Step 3: at the stock (or ten) «+» is muted and presses nothing.
  - Step 4: the product at the line's own SKU (`/p/<product>?sku=<sku>` — the travel case's line opens the travel
    case).
- **Decided in:** B-11, B-100
- **Coverage:** `CartWiringTest` «minus at one is idle and sends nothing»;
  `CartWiringTest` «plus asks for one more and the answer redraws the cart»;
  `CartWiringTest` «plus at the line's limit is idle and sends nothing»;
  `CartRoutesTest` «quantities stay within one to ten and the stock»;
  `CartRoutesTest` «a line opens the product at its own SKU»; `CartWiringTest` «a line's title opens the product»;
  golden `Cart_Content`

### TC-CART-02 — With nothing ticked, checkout and «Delete selected» look idle

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** a cart of three lines, all ticked.
- **Steps:**
  1. Untick one line's box; then press «Select all» to untick every line.
  2. Press «Checkout» (a guest: «Sign in to check out») and «Delete selected».
  3. Tick two lines and press «Delete selected».
  4. Press «Remove» on the last line.
- **Expected:**
  - Step 1: the totals count the ticked lines only; with none ticked, «Checkout» is grey under a muted label and
    «Delete selected» is muted, not Cobalt; no «Discount −$0.00» row, and no points line — the order earns none.
  - Step 2: nothing happens.
  - Step 3: the two lines go.
  - Step 4: that line goes.
- **Decided in:** B-11, B-100, B-129
- **Coverage:** `CartWiringTest` «with nothing ticked checkout and delete selected are idle»;
  `CustomerCartTest` «a cart with nothing ticked promises no points»;
  `CartWiringTest` «with lines ticked checkout and delete selected are live»;
  `CartWiringTest` «select all unticks every line in one change»;
  `CartWiringTest` «delete selected deletes the ticked lines»;
  `CartWiringTest` «remove deletes the line where the selection says»;
  `CartRoutesTest` «nothing selected disables checkout»;
  `CartRoutesTest` «totals count the selected lines and deleted lines go»; golden `Cart_NothingSelected`

### TC-CART-03 — «Save for later» moves the line to the Saved list and says so

- **Who:** Maya; guest
- **Width:** 1440, 390
- **Preconditions:** Maya's cart with a product not on her Saved list.
- **Steps:**
  1. Press «Save for later» on that line.
  2. Press «View saved».
  3. As a guest, press «Save for later».
- **Expected:**
  - Step 1: the line leaves the cart; «Moved to your Saved list» with «View saved». The cart has no section of saved
    products.
  - Step 2: `/account/saved`, the product first, saved at its price that day.
  - Step 3: the sign-in; once through, the cart drawn again for the customer.
- **Decided in:** B-20, B-100
- **Coverage:** `CartWiringTest` «in the storefront save for later says where the line went»;
  `SavedRoutesTest` «save for later moves a line from the cart to the list»;
  `SavedWiringTest` «save for later moves a customer's line and sends a guest to sign-in»

### TC-CART-04 — A promo code is applied, and a refused one says why at the field

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** a cart with the mug ticked, no code applied; the sale live (`AUTUMN10` moves with it, B-61).
- **Steps:**
  1. Press «Apply» with the field empty.
  2. Type `AUTUMN10` and press «Apply» at once, without a pause.
  3. Remove the code.
  4. Apply `SUMMER5`; then `NOPE`.
  5. Untick every line and apply `AUTUMN10`.
- **Expected:**
  - Step 1: nothing is sent.
  - Step 2: one `PUT /api/v1/cart/promo` on the first press; «10% off items, up to $50», the row
    «Promo · AUTUMN10 −$2.40» beside «Discount» (not inside it).
  - Step 3: the row goes; the totals go back.
  - Step 4: the field outlined with «This code has expired»; then «There is no such code». No message over the page
    — the field says it.
  - Step 5: refused (nothing is selected), said at the field.
- **Decided in:** B-11, B-61, B-76, B-92, B-100
- **Coverage:** `CartWiringTest` «apply with nothing typed sends nothing»;
  `CartWiringTest` «apply sends a code typed just before the press»;
  `CartWiringTest` «remove takes the applied code off»;
  `CartWiringTest` «a refused code still draws the cart again»;
  `CartRoutesTest` «a promo applies once»;
  `CartRoutesTest` «an expired promo is 422 promo_expired and the field says so»;
  `CartRoutesTest` «an unknown code is 404 and a code with nothing selected is 422»;
  `CartRoutesTest` «a customer applies a code and sees why another is refused»;
  `SaleCalendarTest` «a stand seeded on another day takes the sale's code during its sale and refuses it after»;
  golden `Cart_PromoError`;
  manual: the press reading the field before the keys reach it shows only in a throttled browser tab (B-76)

### TC-CART-05 — The summary's rows add up to the total

- **Who:** guest; Maya; Sam
- **Width:** 1440, 390
- **Preconditions:** Maya's seeded cart (the headphones $349, the duvet cover set $139, the mugs $24); `AUTUMN10`
  applied for the last step.
- **Steps:**
  1. As Maya, read the summary.
  2. As a guest with only the mug, read it.
  3. As Sam with $103 of items, read the points line.
  4. As Maya, apply `AUTUMN10`.
- **Expected:**
  - Step 1: Items $652.00, Discount −$140.00 (the sale alone), Delivery Free, Total $512; «1,024 points» (double, Plus).
  - Step 2: Delivery $5.99 (under $35, not a member); no points line; «Sign in to check out».
  - Step 3: 103 points (one per whole dollar).
  - Step 4: «Discount −$140.00» and «Promo · AUTUMN10 −$50.00» on rows of their own; the rows add up to the total.
    A row that takes nothing off is not drawn.
- **Decided in:** B-11, B-23, B-100
- **Coverage:** `CustomerCartTest` «Maya's seeded cart is the one the canvas draws»;
  `CustomerCartTest` «a Plus member earns double points on the total»;
  `CustomerCartTest` «a customer without Plus earns a point per whole dollar»;
  `CartRoutesTest` «totals as drawn»;
  `CartRoutesTest` «a guest sees no points and is asked to sign in at checkout»; golden `Cart_PromoApplied`

### TC-CART-06 — A line whose price or stock changed waits for «OK»

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a line added at a deal's price before the deal ended (on the stand: added before the store's
  midnight and opened after it), and a line whose SKU sold out since.
- **Steps:**
  1. Open the cart.
  2. Press «OK» on the changed price.
- **Expected:**
  - Step 1: «Price changed: now $…» on the line, left out of the totals and the selection until acknowledged; the
    sold-out line marked and not counted. Its box does nothing.
  - Step 2: the line at its new price, counted again.
- **Decided in:** B-11, B-57
- **Coverage:** `ChangedLinesTest` «a line whose price changed is marked and left out until acknowledged»;
  `ChangedLinesTest` «a line that went out of stock is marked and stays out of the selection»;
  `CartWiringTest` «OK accepts a changed line whose box does nothing»; golden `Cart_ItemChanged`

### TC-CART-07 — The empty cart names the sale and its picks put a line in

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** an empty cart; a deal live and the sale live.
- **Steps:**
  1. Open `/cart`.
  2. Press «+» on a card under «Picked for you».
  3. Empty the cart again and press «See today's deals».
- **Expected:**
  - Step 1: «Your cart is empty», the sale's line («Today's deals end at midnight — up to −70% in the …», the
    campaign's own name), «See today's deals», and today's deals as picks. With no live deal (or the sale over)
    the line and the picks are left out; the title and the button stay.
  - Step 2: the cart is drawn again with that line in it — no «Your cart is empty» under a count of one, and no
    «Added» message (the line is the answer).
  - Step 3: `/deals`.
- **Decided in:** B-13, B-59, B-95, B-107
- **Coverage:** `CartRoutesTest` «a pick's plus on the empty cart is answered with the cart and its line»;
  `SaleAnnouncementsTest` «the empty cart's sale line needs a live deal»;
  `SaleAnnouncementsTest` «the empty cart's sale line needs a live sale»;
  `SaleAnnouncementsTest` «the hero and the empty cart name the sale the campaign row is»; golden `Cart_Empty`

### TC-CART-08 — A guest's «Sign in to check out» lands on checkout with the cart merged

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a guest cart with the mug; the account signed in to already has the mug at 9 and a code.
- **Steps:**
  1. Press «Sign in to check out» and sign in as that customer.
  2. Repeat as another guest, closing the sign-in window instead.
- **Expected:**
  - Step 1: checkout; the cart is the customer's with the guest's lines merged — the mug summed and capped at ten
    (and at the stock), the guest's other lines after the customer's, the customer's code kept.
  - Step 2: still on the cart, nothing changed.
- **Decided in:** B-12, B-44
- **Coverage:** `CartWiringTest` «in the storefront a guest's Sign in to check out lands on the checkout»;
  `CartWiringTest` «in the storefront a sign-in that does not go through stays on the cart»;
  `CartMergeTest` «the same SKU sums and stops at ten»;
  `CartMergeTest` «the guest's code comes along only when the customer has none»;
  `IdentityRoutesTest` «a guest's other lines join the customer's after its own»
