# Order and returns — `TC-ORD`

An order's page (`/account/orders/HL-…`), which moves on its own while the simulated world fulfils it; returns,
refunds and reorder. On the stand the world runs 288 times the store's pace: an order is packed in under a
minute, on the road within five and delivered within fifteen; a return is collected and refunded a few minutes
later (B-27). A local stack runs at `HAUL_FULFILMENT_SPEED`.

### TC-ORD-01 — An order's page moves by itself as its shipments do

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** her seeded cart (two sellers) just placed by courier, its page open.
- **Steps:**
  1. Leave the page open and watch, without reloading.
  2. Leave the page for another and come back.
- **Expected:**
  - Step 1: «Placed», then packed, in transit, delivered, drawn as each happens; one shipment per seller, each with
    its own day (Sony a day before Brooklyn Home Co.); the progress is the least advanced shipment's. The card is
    charged per shipment as it goes on the road («Your card is charged when the order ships»).
  - Step 2: the page drawn as it now is; leaving it closes its update stream without an error in the server's log
    (TC-STAND-05).
- **Decided in:** B-17, B-18, B-29, B-56
- **Coverage:** `LiveOrderPageTest` «a pushed update redraws the order page in transit without loading it again»;
  `LiveOrderPageTest` «the order page stops listening when it is left»;
  `LiveOrderTest` «the order page hears its order go in transit without loading again»;
  `ShipmentLifecycleTest` «an order reaches delivered on the fast clock»;
  `ChargedWhenShippedTest` «charged when shipped»;
  manual: the stand's own pace over a quarter of an hour, watched in a browser, is what a visitor sees

### TC-ORD-02 — An order that arrived ahead of its window says the delivery was simulated

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a delivered order that was booked for a window days ahead (on the stand, any courier order:
  the simulator does not wait for the window).
- **Steps:**
  1. Read the line under the title.
- **Expected:**
  - Step 1: «Delivered Oct 9» (the real day it arrived), and under it «Deliveries here are simulated: this one came
    ahead of Sat, Oct 11, the window it was booked for.» — «…, the day it was due.» without a window; then the
    returns line, its window counted from the real day. An order delivered on its window's day reads as before.
- **Decided in:** B-98
- **Coverage:** `EarlyDeliveryTest` «an order delivered before its window says the delivery was simulated»;
  `EarlyDeliveryTest` «an order delivered before the day it was due says so without a window»;
  `EarlyDeliveryTest` «an order delivered on its window's day reads as before»

### TC-ORD-03 — A pickup order waits at its point with a code only its customer sees

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** an order placed to a pickup point, ready for pickup.
- **Steps:**
  1. Open the order.
  2. Open the account's overview.
- **Expected:**
  - Step 1: «Ready for pickup», the point, how long it is held (five days), the four-digit code.
  - Step 2: the active order's card with the same code. Nobody else is shown the code; once collected it is gone.
- **Decided in:** B-17, B-19
- **Coverage:** `ShipmentLifecycleTest` «a pickup shipment waits with a code only its customer sees»;
  `ShipmentLifecycleTest` «a locker shipment is collected not delivered»; golden `Order_ReadyForPickup`

### TC-ORD-04 — A return is asked from the order, collected, then refunded

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a delivered order of three lines, placed with `AUTUMN10`.
- **Steps:**
  1. Press «Return items».
  2. Tick one line, then two; read the amounts.
  3. Choose a reason and send.
  4. Watch the page.
- **Expected:**
  - Step 1: the return dialog over the order, each line with its tile.
  - Step 2: the card amount and the points back follow the lines ticked — each line's share of what was paid (the
    code shared over the lines, the delivery kept).
  - Step 3: the dialog closes and the page is drawn again: «Requested».
  - Step 4: «Picked up» about a store day later, «Refunded» a day after that; the returned lines in a card of their
    own; «Paid» net of the refund.
- **Decided in:** B-21, B-50, B-55, B-91
- **Coverage:** `ReturnRoutesTest` «a delivered order presents the return dialog»;
  `ReturnWiringTest` «ticking lines updates the card amount and the points back»;
  `ReturnWiringTest` «a return is sent where the form says and the answer closes the dialog and redraws the order»;
  `ReturnRefundsTest` «a discount is shared over the lines and the delivery is not given back»;
  `ReturnLifecycleTest` «a return is collected a day after it is asked for and refunded a day later»;
  `ReturnRoutesTest` «a returned line is refunded when the seller has it back»; golden `Order_ReturnDialog`

### TC-ORD-05 — A line of several shows the price of one and refunds what the line cost

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** an order with the headphones ×2 at $349, delivered.
- **Steps:**
  1. Read the line on the order page.
  2. Open «Return items» and tick only that line.
- **Expected:**
  - Step 1: «2 × $349», not «2 × $698».
  - Step 2: the refund is what the two cost as paid ($698.00 less its share of any code or points) — never more
    than was paid for them.
- **Decided in:** B-91
- **Coverage:** `LineOfSeveralTest` «a new order of two pairs shows the price of one and its return gives back what the two cost»;
  `ReturnRefundsTest` «a line of several gives back what it cost and any set of lines what the set cost»

### TC-ORD-06 — One return per order, and the page says so where the button was

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** the order of TC-ORD-04 with one line returned and refunded; another order returned whole.
- **Steps:**
  1. Read the partly returned order's summary.
  2. Read the wholly returned order's.
  3. Open the history.
- **Expected:**
  - Step 1: «One return per order: what you kept can't be sent back.» where «Return items» was; «Reorder» still
    offered (a return unbuys nothing).
  - Step 2: no rule; «Reorder» offered.
  - Step 3: the first row «Partly returned» at what was paid and kept; the second «Returned» at the order's total.
- **Decided in:** B-21, B-101
- **Coverage:** `OrderWiringTest` «a partly returned order says the one return rule where its button was and still reorders»;
  `OrderWiringTest` «a wholly returned order draws no rule and still reorders»;
  `AccountRoutesTest` «a returned order is returned in the history and under its filter»;
  golden `Order_PartlyReturned`; golden `Order_Returned`

### TC-ORD-07 — A return the rules refuse is refused in the dialog

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** a local stack: an order whose line arrived more than 30 store days ago; an order in transit.
- **Steps:**
  1. Ask a return of the old line.
  2. Ask a return with no reason.
  3. Take the network away and send a return.
- **Expected:**
  - Step 1: «Late return», in the dialog (the window is 30 store days from each line's own arrival, the last one
    whole).
  - Step 2: nothing sent; the reason named.
  - Step 3: «Couldn't reach the store» in the dialog, no «Retry» (its own button is the press again).
  - An order that has not arrived offers no «Return items».
- **Decided in:** B-21, B-92
- **Coverage:** `ReturnRoutesTest` «late return»;
  `ReturnRoutesTest` «each line is returnable to the last day of its own window»;
  `ReturnRoutesTest` «a return is refused for what has not arrived and what is not yours»;
  `ReturnWiringTest` «a late return is refused in the dialog»;
  `ReturnWiringTest` «a return at fault by the server's rules sends nothing and says why»;
  `ReturnWiringTest` «a return with no answer says so in the dialog»

### TC-ORD-08 — Reorder puts the order back and names what it could not

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** a delivered order; a local stack where one of its SKUs is out of stock and another has one left
  where the order had two.
- **Steps:**
  1. Press «Reorder» on an order whose SKUs are all in stock; press it again from the order.
  2. Press «Reorder» on the order with the shortfalls.
  3. Press «Reorder» on an order none of whose SKUs is in stock.
- **Expected:**
  - Step 1: the cart, each SKU ticked at the order's quantity (at most ten and the stock); the second press adds
    nothing.
  - Step 2: the cart, and a message at the error level: «Added what was in stock — … isn't available now; …: only
    1 of 2 went in.»
  - Step 3: «Nothing was added: … aren't available now.» — and the shopper stays on the order.
- **Decided in:** B-18, B-101
- **Coverage:** `OrderRoutesTest` «reorder puts a delivered order back into the cart and twice is once»;
  `OrderRoutesTest` «a reorder names what it could not put back»;
  `OrderWiringTest` «in the storefront a reorder says what it could not put back»;
  `AccountWiringTest` «reorder on a row sends its order's reorder and follows the server to the cart»

### TC-ORD-09 — A delivered line leads to its review

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** a delivered order with two lines, one product he has reviewed.
- **Steps:**
  1. Press «Write a review» on the line he has not reviewed.
  2. Press «Your review» on the other.
- **Expected:**
  - Step 1: the product's review dialog over the order; posted, it is marked «Verified».
  - Step 2: the product page's reviews tab, his review first.
- **Decided in:** B-18, B-22, B-99
- **Coverage:** `OrderRoutesTest` «a delivered line the customer has reviewed leads to their review»;
  `ReviewRoutesTest` «a verified review - marked verified, and the product's count grows by one»

### TC-ORD-10 — A declined card cancels the order and gives everything back

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** on the stand, one of the synthetic shoppers' declining walks (every tenth walk, as Sam); on a
  local stack, an order placed with the test card's key.
- **Steps:**
  1. Open the cancelled order from Sam's history.
  2. Press «Back to cart».
- **Expected:**
  - Step 1: «Cancelled», «Your card ···· 0002 was declined»; «Back to cart» once, in the notice beside the reason
    (not again in the summary).
  - Step 2: the cart (on the stand the walk has removed the lines again).
  - Behind it: the points, the window and the stock given back.
- **Decided in:** B-16, B-89, B-108
- **Coverage:** `OrderRoutesTest` «a declined order says so and leads back to the cart»;
  `OrderWiringTest` «a cancelled order leads back to the cart»;
  `PlacementRoutesTest` «a declined card cancels the order and gives everything back»;
  `PointsTest` «a declined card gives the redeemed points back»; golden `Order_Cancelled`

### TC-ORD-11 — An order's summary draws the promo code and the address it was placed to

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** an order placed with `AUTUMN10`; then her address edited at checkout.
- **Steps:**
  1. Read the order's summary.
  2. Edit her address at checkout; open the order again.
- **Expected:**
  - Step 1: «Discount» (the sale, read off the lines) and «Promo · AUTUMN10» on rows of their own, as in the cart.
  - Step 2: the order keeps the address it was placed to.
- **Decided in:** B-18, B-40, B-108
- **Coverage:** `OrderRoutesTest` «an order placed with a promo code draws its promo row»;
  `OrderRoutesTest` «an address edited after placement does not move a past order»

### TC-ORD-12 — A Haul Pay order draws its schedule

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** an order paid with Haul Pay.
- **Steps:**
  1. Open it before its first shipment ships, then after.
- **Expected:**
  - Step 1: under the payment fact, four payments — «When it ships», «In 2 weeks», … — then each payment's day,
    whether it is paid and what it charged; the fact says how much is paid and what comes next. A card order draws
    no schedule.
- **Decided in:** B-24
- **Coverage:** `OrderWiringTest` «a Haul Pay order draws its schedule under the payment fact»;
  `OrderWiringTest` «an order paid by card draws no schedule»;
  `HaulPayPlanTest` «an instalment order shows its schedule»; golden `Order_HaulPay`
