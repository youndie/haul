# Account and Saved — `TC-ACC`

The account's overview (`/account`), the orders' history (`/account/orders`), the Saved list (`/account/saved`),
hearts, Haul Plus and points. Sections drawn only as menu labels on the canvas (Returns, My reviews, Addresses,
Payment methods, Settings) are hidden in v1 (D6).

### TC-ACC-01 — The overview is the customer's standing and her orders

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** Maya with orders on their way and delivered ones (on the stand, place a few; none is seeded).
- **Steps:**
  1. Open `/account`.
  2. Press an active order's card anywhere — its number, its pickup code, blank space.
  3. Press a history row; press «All orders».
- **Expected:**
  - Step 1: «Plus member since 2023»; the tiles — points («worth $24.80 on your next order»), Haul Plus, Price drops
    (three tiles, B-19; Saved's count is the header menu's); at most three active orders as cards («Arriving tomorrow, 15:00 – 18:00» with the steps, or «Ready for
    pickup» with the code); the last four of the rest, «All orders» whenever one is left out. No other customer's
    order.
  - Step 2: that order's page, wherever pressed («Details ›» says what the press does).
  - Step 3: the order's page; the history.
- **Decided in:** B-19, B-74, B-108
- **Coverage:** `AccountRoutesTest` «the overview draws the customer's own orders and nobody else's»;
  `AccountRoutesTest` «the tiles are the member's standing or the trial's offer»;
  `AccountWiringTest` «an active order's card opens its order's page wherever it is pressed»;
  `AccountWiringTest` «an active order's card opens its order's page wherever it is pressed on a phone»;
  `AccountWiringTest` «the overview's links open the order pages and the history»;
  `AccountWiringTest` «every row of the overview's history opens its order's page»; golden `Account_Content`

### TC-ACC-02 — A tile is pressed only where it has a page

- **Who:** Maya; Sam
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. As Maya, press «Price drops»; press the points tile and the Haul Plus tile.
  2. As Sam, press «Try 30 days free» on the Haul Plus tile; press «Not now».
- **Expected:**
  - Step 1: `/account/saved?filter=price-dropped` (also when the count is 0 — the list then says nothing got cheaper
    yet); the points and a member's Haul Plus are read, not pressed.
  - Step 2: the trial's dialog; «Not now» closes it.
- **Decided in:** B-23, B-74
- **Coverage:** `AccountWiringTest` «the price drops tile opens the saved list's price drops»;
  `AccountWiringTest` «the price drops tile opens the saved list's price drops on a phone»;
  `AccountWiringTest` «the trial's button presents the trial dialog»; golden `Account_NotMember`

### TC-ACC-03 — The history: on the way first, chips that filter in place, rows that open

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** orders in several states.
- **Steps:**
  1. Open `/account/orders`.
  2. Press «Delivered»; press it again.
  3. Press a row anywhere; on a delivered row press «Reorder».
  4. Open `/account/orders?status=bogus`.
- **Expected:**
  - Step 1: orders on their way first, then newest first; chips All, Active, Delivered, Returned, Cancelled with
    counts of every order; each row's way on «Track», «Details» or «Reorder».
  - Step 2: the filtered history loaded in place (the address gains `?status=delivered`); the chip shown presses
    nothing.
  - Step 3: the row opens its order's page; «Reorder» sends only the reorder (TC-ORD-08).
  - Step 4: every order, not an error page.
- **Decided in:** B-19, B-63, B-74, B-102
- **Coverage:** `AccountRoutesTest` «the history filters by the status in its address»;
  `AccountWiringTest` «the history's chips and rows open their addresses»;
  `AccountWiringTest` «every row of the orders' page opens its order's page on a phone»; golden `Account_Orders`

### TC-ACC-04 — A customer with no orders is sent to the deals

- **Who:** a customer who has never ordered
- **Width:** 1440, 390
- **Preconditions:** a customer who has never ordered (a fresh person on a local realm).
- **Steps:**
  1. Open `/account`, then `/account/orders`.
  2. Press the empty history's «See today’s deals».
- **Expected:**
  - Step 1: «Joined <month year>», no orders, the trial's offer; the orders' page is «No orders yet».
  - Step 2: `/deals`.
- **Decided in:** B-19
- **Coverage:** `AccountRoutesTest` «a customer with no orders is sent to the deals»;
  `AccountWiringTest` «no orders leads to the deals»; golden `Account_NoOrders`

### TC-ACC-05 — The Saved list: newest first, price drops marked, page numbers only

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** her 48 seeded saved products, six of them cheaper than when saved.
- **Steps:**
  1. Open `/account/saved`.
  2. Press «Price dropped»; press page 2 at the foot of a phone page.
  3. Open `/account/saved?page=99`, then `/account/saved?page=x`.
- **Expected:**
  - Step 1: «Saved 48», newest first, 24 a page; the headphones «Price dropped −$100»; only page numbers, no
    «Show 24 more».
  - Step 2: the six loaded in place; page 2 at the top of the page.
  - Step 3: the last page; then the list without the bad `page` (TC-ERR-04).
- **Decided in:** B-20, B-63, B-108
- **Coverage:** `SavedRoutesTest` «a product cheaper than on the day it was saved is counted and marked»;
  `SavedRoutesTest` «the list is paged and filtered by its address»;
  `SavedRoutesTest` «the list's filters and pages load the body of the page they open»;
  `SavedWiringTest` «the chips and the pages follow their addresses»;
  `KeptPageTest` «a page number opens its page at the top and show more keeps the scroll»;
  golden `Saved_Content`; golden `Saved_PriceDrops`

### TC-ACC-06 — A heart saves a product once and lets it go

- **Who:** Maya; guest
- **Width:** 1440, 390
- **Preconditions:** a product not on her list.
- **Steps:**
  1. Press a card's heart; press the product page's heart.
  2. On the Saved list, press its heart.
  3. Save it again another day (or after its price changed).
  4. As a guest, press a heart.
- **Expected:**
  - Step 1: the heart filled; the product on her list once, saved at the price its card showed.
  - Step 2: the card leaves the list.
  - Step 3: saved again at that day's price — the old price forgotten.
  - Step 4: the sign-in (TC-SIGN-02).
- **Decided in:** B-20
- **Coverage:** `SavedRoutesTest` «a product saved twice is in the list once»;
  `SavedRoutesTest` «a product let go leaves the list and a second let-go changes nothing»;
  `SavedWiringTest` «a saved card's heart sends the let-go its tree fixed»;
  `SavedWiringTest` «an unsaved heart saves and a guest's heart goes to sign-in»

### TC-ACC-07 — An empty Saved list leads to the deals

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** nothing saved.
- **Steps:**
  1. Open `/account/saved`; press the heart drawn as its picture, then «Browse deals».
- **Expected:**
  - Step 1: both open `/deals`.
- **Decided in:** B-20, B-74
- **Coverage:** `SavedWiringTest` «an empty list sends the shopper to the deals»;
  `SavedWiringTest` «the empty list's heart sends the shopper to the deals»; golden `Saved_Empty`

### TC-ACC-08 — The Plus trial makes a member at once

- **Who:** Sam
- **Width:** 1440, 390
- **Preconditions:** a customer who is not a member. **v1 has no way to end a membership**: on the stand a trial
  started as Sam is for good, and Sam is the synthetic shoppers' person — walk this on a local stack, or with a
  person the owner can spare.
- **Steps:**
  1. Press «Try 30 days free» on home's Plus block; press «Start trial».
  2. Read the header's pill, the home page's Plus block and a cart under $35.
  3. Ask for the trial again (a second tab still showing the offer).
- **Expected:**
  - Step 1: the dialog closes and the page is drawn again for a member.
  - Step 2: the pill is the membership's mark; the Plus block shows savings and the renewal («Renews <30 days on>» ·
    «$4.99 / month»); delivery free under $35; member prices where the sale has early access.
  - Step 3: the dialog closes, the page is drawn again, and the server's words say he is already a member.
- **Decided in:** D6, B-23, B-92, B-108
- **Coverage:** `PlusTrialWiringTest` «start trial sends the dialog's post and the page is drawn again»;
  `MembershipRoutesTest` «Sam starts the trial and his next cart has free delivery»;
  `MembershipRoutesTest` «a member asking for the trial is refused as already a member»;
  `PlusTrialWiringTest` «a refused trial closes the dialog and says why over the page drawn again»;
  `PlusMembershipTest` «a trial is free for 30 days and renews monthly after it»;
  `PlusEarlyAccessTest` «a trial started mid-checkout redraws the quote at the member price»;
  golden `Home_PlusTrialDialog`

### TC-ACC-09 — Points are earned on delivery and taken back by a return

- **Who:** Sam
- **Width:** 1440
- **Preconditions:** a local stack (on the stand the shoppers' walks move Sam's points too); Sam's points tile
  read; an order of $103 placed by card.
- **Steps:**
  1. Read the points tile after the order is delivered.
  2. Return a line and read it again after the refund.
- **Expected:**
  - Step 1: up by 103 (a point per whole dollar paid; double for a member), credited per shipment as it arrives —
    not at placement.
  - Step 2: down by the returned line's share of the points.
- **Decided in:** D7, B-23
- **Coverage:** `PointsTest` «Sam's order of 103 dollars earns 103 points when its shipment is delivered»;
  `PointsTest` «a return takes back the points its line earned and gives back the points it was paid with»
