# Checkout — `TC-CHK`

Checkout is a customer's (a guest is asked to sign in, TC-SIGN-06). What it shows is what it places: every choice
is the server's, stored per customer, and the summary is the server's quote (B-14, B-97).

### TC-CHK-01 — Maya's checkout quotes her cart by courier to her address

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** her seeded cart, all ticked; no choice stored.
- **Steps:**
  1. Open `/checkout`.
  2. Read the steps in its header.
- **Expected:**
  - Step 1: courier chosen, its day the cart's earliest line's («Tomorrow, Oct 8» on the canvas); her address in the
    inline form; windows «9:00 – 12:00» to «18:00 – 21:00» on five days; the card ···· 4821 chosen, no test card listed;
    the points toggle; the summary's lines with their photos where stored, the rows the cart's (Total $512), the
    delivery day, and «Your card is charged when the order ships.» — no «Terms of Sale».
  - Step 2: «Delivery» marked until the order can be placed (a window chosen, the address complete), then
    «✓ Delivery ✓ Payment 3 Review»; «Payment» is never the step waiting.
- **Decided in:** B-14, B-15, B-79, B-98, B-107, B-108
- **Coverage:** `CheckoutRoutesTest` «Maya's quote is the cart's totals by courier to her address»;
  `CheckoutRoutesTest` «checkout quotes only the lines selected in the cart»;
  `CheckoutWiringTest` «the steps follow what is filled in»;
  `LinePhotosTest` «checkout's lines draw their photos»; golden `Checkout_Content`

### TC-CHK-02 — A method, a window and a point are chosen on the server

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** checkout open.
- **Steps:**
  1. Press the method already chosen.
  2. Choose a window.
  3. Choose «Pickup point» and a point; then a parcel locker.
  4. Look at a full window (a local stack with a window filled to 20).
- **Expected:**
  - Step 1: nothing is sent.
  - Step 2: the choice is sent and the page drawn again; the summary's delivery day follows.
  - Step 3: a pickup needs no address and no window, its day a day after the courier's («Thu, Oct 9 · 240 m away»);
    a locker does not offer pay on delivery (the card is chosen again if that was the way to pay).
  - Step 4: drawn unavailable; choosing it is refused.
- **Decided in:** B-14, B-15, B-98
- **Coverage:** `CheckoutWiringTest` «the method already chosen sends nothing»;
  `CheckoutWiringTest` «a method is chosen and the answer redraws the checkout»;
  `CheckoutWiringTest` «a window is chosen»; `CheckoutWiringTest` «a pickup point is chosen»;
  `CheckoutRoutesTest` «a pickup point needs no address and no window»;
  `CheckoutRoutesTest` «a parcel locker is not paid on delivery»;
  `CheckoutRoutesTest` «a full window is drawn unavailable and refused»;
  golden `Checkout_PickupPoint`; golden `Checkout_ParcelLocker`

### TC-CHK-03 — Another day's tile holds «Place order» until one of its windows is chosen

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a window chosen on the first day, so «Place order» is live.
- **Steps:**
  1. Press the tile of another day.
  2. Press «Place order».
  3. Press the first day's tile again; or choose a window of the other day.
- **Expected:**
  - Step 1: that day's windows; «Place order» held with «Pick a delivery window»; nothing sent; the summary still
    names the chosen window's day. (The header's steps still read «Review» meanwhile — recorded in B-97.)
  - Step 2: nothing is placed.
  - Step 3: the button is live again; a window of the other day is a choice like any other and moves the summary.
- **Decided in:** B-97
- **Coverage:** `CheckoutWiringTest` «another day shows its windows»;
  `CheckoutWiringTest` «another day holds place order until one of its windows is chosen»;
  `CheckoutWiringTest` «a full window and a held button send nothing»

### TC-CHK-04 — The address is sent by any press outside the form, and placed only once quoted

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** courier, a window chosen, the address form incomplete («Place order» held with «Fill in the
  street address and ZIP»).
- **Steps:**
  1. Type the street and ZIP, then press the held «Place order» straight away.
  2. Press «Place order» again.
  3. Clear the ZIP and press blank space beside the form.
  4. Press inside the form (a label, another field).
- **Expected:**
  - Step 1: the address is sent, nothing is placed; the next tree quotes the new address and the button is live.
  - Step 2: the order is placed (TC-CHK-08).
  - Step 3: the form is sent and refused, each field at fault with its error under it; no message over the page.
  - Step 4: nothing is sent.
  - A save edits the address delivered to in place; a form equal to one of his addresses chooses it and stores
    nothing.
- **Decided in:** B-14, B-40, B-92, B-97
- **Coverage:** `CheckoutWiringTest` «a press on the held button sends the address typed just before it»;
  `CheckoutWiringTest` «place order with an address not yet sent sends the address and places nothing»;
  `CheckoutWiringTest` «a press on blank space sends the address»;
  `CheckoutWiringTest` «a press inside the form sends nothing»;
  `CheckoutWiringTest` «a refused form says why under its fields alone»;
  `CheckoutRoutesTest` «the address form is refused field by field and drawn again»;
  `CheckoutRoutesTest` «saving the address form twice edits the one address in place»;
  `CheckoutRoutesTest` «an address equal to a saved one is chosen and not stored again»;
  golden `Checkout_Validation`;
  manual: the press arriving before the typed keys is a throttled browser's; the desktop tests hold the order, no browser walk was made when it was built

### TC-CHK-05 — Checkout's days agree with the product page and the cart

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** a cart of only the duvet cover set (Brooklyn Home Co. dispatches a day later); then her whole
  cart; then the same after 23:30 store time.
- **Steps:**
  1. Read the product page's courier day, the cart's day for the seller, checkout's courier method and windows.
  2. With the whole cart, read checkout's first day.
  3. After the cut-off, read all three again.
- **Expected:**
  - Step 1: one day everywhere («Thu, Oct 9» on the canvas): windows from that day, the pickup a day later.
  - Step 2: the earliest line's day («Tomorrow, Oct 8»); the slower seller's shipment comes on its own day, which
    the cart names for it.
  - Step 3: every page moves a day together.
- **Decided in:** B-98
- **Coverage:** `DeliveryAgreesTest` «checkout offers its windows from the day the product page and the cart promise»;
  `DeliveryAgreesTest` «a cart of two sellers is offered windows from the earlier one's day, as the canvas draws Maya's»;
  `DeliveryAgreesTest` «after the cut-off checkout moves with the product page»

### TC-CHK-06 — Points pay for the items, all or nothing

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** her 2,480 points, her seeded cart.
- **Steps:**
  1. Turn the points toggle on.
  2. Turn it off.
  3. Read the toggle with a screen reader (or the accessible mirror, `scripts/a11y-check.py`), off and on.
- **Expected:**
  - Step 1: −$24.80, Total $487.20 (capped at the items after discounts — never the delivery); Haul Pay's offer
    follows the new total.
  - Step 2: back to $512.
  - Step 3: «Use 2,480 points (−$24.80), off. 100 points = $1 · all or nothing», and «…, on. …» once turned on.
- **Decided in:** D6, B-23, B-151
- **Coverage:** `CheckoutWiringTest` «the points toggle turns the points on and off»;
  `PointsTest` «points are capped at the items after discounts»;
  `PointsTest` «Maya pays with her 2480 points and her balance is 0 until the order earns»;
  `NamedPressesTest` «boxes and switches say what they do and whether they are on»; golden `Checkout_PointsApplied`

### TC-CHK-07 — Haul Pay is four payments, offered from $50 to $2,000

- **Who:** Maya; Sam
- **Width:** 1440
- **Preconditions:** a total over $50 (Maya's cart); a total under $50 (Sam with the mug).
- **Steps:**
  1. As Maya, read the Haul Pay option and choose it.
  2. As Sam, look for it.
- **Expected:**
  - Step 1: «4 payments of $128» (a quarter of the total, the last taking what rounding left); chosen like any way
    to pay.
  - Step 2: not offered under $50; choosing it is refused.
- **Decided in:** D6, B-14, B-24
- **Coverage:** `CheckoutRoutesTest` «Haul Pay is refused for a total under fifty dollars»;
  `CheckoutWiringTest` «a way to pay is chosen»;
  `InstalmentScheduleTest` «the last payment takes what rounding left»

### TC-CHK-08 — «Place order» places once and lands on the order

- **Who:** Maya; Sam
- **Width:** 1440, 390
- **Preconditions:** a placeable checkout.
- **Steps:**
  1. Press «Place order».
  2. Press back to checkout, then reload it.
  3. Open the cart.
- **Expected:**
  - Step 1: «Placing order…» with the page's links inert, then `/account/orders/HL-…`, «Placed». A double press or a
    retry is the same order (the key).
  - Step 2: the cart — nothing is left selected to check out (TC-CHK-11).
  - Step 3: the bought lines are gone, and so is a promo code the order was placed with; lines that were not ticked
    are still there.
- **Decided in:** B-16, B-18, B-94, B-129
- **Coverage:** `CheckoutWiringTest` «place order sends the quote and draws placing until the answer»;
  `CheckoutWiringTest` «the summary's links do nothing while the order is placed»;
  `PlacementRoutesTest` «Maya places her cart by courier and the saga takes stock window and payment»;
  `PlacementRoutesTest` «the same key places once and a different request under it is refused»;
  `PlacementRoutesTest` «placing takes the order's promo code off the cart»;
  `OrderRoutesTest` «the page placement lands on is the order just placed»; golden `Checkout_Placing`

### TC-CHK-09 — A placement refused, or unanswered, says so and can be pressed again

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a local stack: a window filled meanwhile by another customer; a price changed meanwhile; the
  server stopped.
- **Steps:**
  1. With the chosen window filled since the page was drawn, press «Place order».
  2. With the quote changed since (a line's price), press «Place order».
  3. With the server unreachable, press «Place order», then «Retry».
- **Expected:**
  - Step 1: the checkout drawn again with the window cleared and its notice (golden `Checkout_PlaceError`) — no message over
    the page on top of it.
  - Step 2: the checkout drawn again, the server's words in a message at the error level; the button pressable.
  - Step 3: «Couldn't reach the store» with «Retry»; Retry places under the same key — one order at most.
- **Decided in:** B-14, B-16, B-92
- **Coverage:** `PlacementRoutesTest` «a window that filled since the page was drawn is refused and the checkout says so»;
  `PlacementRoutesTest` «a quote that changed since the page was drawn is refused»;
  `CheckoutRoutesTest` «a window that filled after it was chosen is cleared and the shopper told»;
  `CheckoutWiringTest` «a refused placement says why and the button is pressable again»;
  `CheckoutWiringTest` «a placement with no answer says so and Retry places under the same key»;
  golden `Checkout_PlaceError`

### TC-CHK-10 — «Back to cart» and the summary's tiles leave checkout

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** checkout open.
- **Steps:**
  1. Press a line's tile in «Your order».
  2. Back; press «Back to cart» beside «Your order».
- **Expected:**
  - Step 1: the product's page. (The tile opens the product's default SKU, not the line's — recorded in B-100.)
  - Step 2: `/cart`.
- **Decided in:** B-79
- **Coverage:** `CheckoutWiringTest` «the summary's tiles open their products and back to cart goes to the cart»

### TC-CHK-11 — A checkout with nothing selected lands on the cart

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** nothing selected in the cart (just after placing an order, or every line unticked).
- **Steps:**
  1. Open `/checkout`.
  2. Press back.
- **Expected:**
  - Step 1: the cart, in place of checkout — not «Checkout didn't load» with a Retry that cannot help.
  - Step 2: the page before checkout (checkout's history entry was given to the cart).
- **Decided in:** B-94
- **Coverage:** `RefusedPagesTest` «a checkout with nothing selected lands on the cart»;
  `RecoveryTest` «an empty checkout is the cart»

### TC-CHK-12 — Nobody but the stand's shoppers can pay with the declining card

- **Who:** Sam
- **Width:** n/a
- **Preconditions:** Sam signed in; a REST client with his access token.
- **Steps:**
  1. Read checkout's ways to pay.
  2. `PUT /api/v1/me/checkout` naming `card-0002` without `X-Haul-Test-Card`.
- **Expected:**
  - Step 1: card ···· 4821, Haul Pay and pay on delivery where allowed; never ···· 0002.
  - Step 2: `422 payment_method_not_allowed`, «This way to pay is not offered»; the checkout keeps ···· 4821.
- **Decided in:** B-14, B-89
- **Coverage:** `CheckoutRoutesTest` «the test card is chosen only with the stand's key»;
  `CheckoutRoutesTest` «without a key on the server the test card is nobody's»
