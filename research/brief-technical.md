# Technical brief: Haul — an open, end-to-end marketplace storefront

| | |
|---|---|
| Date | 2026-10-08 |
| Repository | new — `youndie/haul` (public), this file on branch `docs/product-brief` |
| Platforms | web (Compose Multiplatform, wasmJs): desktop 1440 wide and phone 390 wide |
| Stack | decided by the owner: Ktor on the JVM with a zavarnik AOT cache, PostgreSQL, petich, shildik, kompot (every screen server-driven); Compose Multiplatform wasmJs client — §7, §8 |
| Design | Claude Design project «E-commerce витрина», `Storefront.dc.html` (7 screens, desktop only) plus the `Header` and `ProductCard` components |
| Documentation | `docs/` inside the repository, docs-bootstrap format, English (§8) |
| Status of this document | a branch artefact under `research/`; deleted before the branch merges |

How the sections become documentation: §8 and §10 are the research document
(`docs/research/research-architecture.md`, the one layer that can exist before the code); §3
feeds the features' business rules; §4 → `features/`, §5 → `screens/`, §6 → `api/`, §7 →
`services/` are drafted in a pull request that stays open and gets its code anchors as the code
lands; §9 becomes the backlog, one file per item (it is past a few dozen).

## 1. Problem and audience

There is no open, complete reference for a marketplace built end to end in Kotlin: catalog,
search, cart, checkout, payment, the order's life after it is placed, and an account that shows
it. The pieces exist as libraries, and every demo stops at the catalog. Haul is the whole path
on one stack, small enough to read and big enough to have the seams a real shop has (several
sellers in one order, an order that is paid when it ships, a payment that fails after the stock
was reserved).

The audience is a Kotlin developer evaluating the stack, and a shopper on the demo stand who
should be able to find a product, buy it and watch it arrive without reading anything. Everything
outside the system — the card processor, the couriers, the sellers — is simulated inside it, so
the whole path runs on one machine.

## 2. Scope

**In the first version** (the user's answer: everything the canvas shows):

- browse a home page of campaigns, categories, deals of the day with a countdown and a Haul Plus block;
- browse a category with facet filters (subcategory, price, brand, delivery, rating, colour, features), sort and pagination;
- search with autocomplete: query suggestions, matching categories with counts, recent searches, top products;
- a product page: variants (colour, bundle), price with the old price and discount, delivery estimate per method, seller card, description, specifications, reviews with a rating histogram, questions;
- write a review, mark a review helpful, ask a question;
- a cart grouped by seller: quantities, select and delete, save for later, promo code, totals and the points the order earns;
- a guest can browse and fill a cart; signing in merges the guest cart into the customer's;
- checkout: courier / pickup point / parcel locker, address, a delivery day and window, payment by card, Haul Pay instalments or pay on delivery, place the order;
- the order's life: stock reserved and payment authorised at placement, one shipment per seller, packed → in transit → delivered or ready for pickup, card charged when a shipment ships, compensation when a step fails;
- an account overview: points, Haul Plus savings, price drops in the Saved list, active orders with progress and the pickup code, order history with reorder;
- an order page: progress, shipments, pickup code, a return request on a delivered order;
- a Saved list with price-drop marks;
- Haul Plus: a 30-day trial, free and next-day delivery, double points, early access to sales — billing simulated;
- points earned on delivery and redeemed at checkout; Haul Pay — 4 payments, simulated;
- recommendations «Picked for you» from recent views;
- every screen at desktop 1440 and phone 390 widths.

**Out, on purpose** (defaults taken in this brief — the canvas draws them only as labels; reopen
by naming them):

- a seller cabinet, «Sell on Haul», «Open a store», seller centre, fulfilment, ads — the user chose a marketplace without a seller side; sellers, products and answers to questions are seed data;
- account sections other than Overview, Orders and Saved: the Returns list, My reviews, Addresses, Payment methods, Settings — drawn only as menu items; addresses are added in checkout, the one card is seed data;
- gift cards, «Get the app», careers/press/about pages, help and contact, the EN · USD switcher — footer and header labels without a screen;
- changing the «Deliver to» location from the header — the address is chosen in checkout;
- product photography — the canvas marks photos as striped placeholder tiles, and v1 ships those tiles (§8);
- e-mail and push notifications, including price-drop alerts outside the app — price drops are shown in the account and the Saved list only;
- real payment, real couriers, real money for Haul Plus.

## 3. Domain

| Entity | Identified by | Owned by / tenant | Notes |
|---|---|---|---|
| `Category` | slug (`headphones`) | — | a tree, three levels (`electronics/audio/headphones`); 32 top-level |
| `Seller` | server id | — | seed data; name, rating, positive share, years on Haul |
| `Product` | server id | `Seller` | title, brand, category, description, specifications (ordered key → value), rating summary |
| `Sku` | server id | `Product` | one combination of the product's options (colour × bundle); price, old price, stock |
| `Campaign` | slug | — | home banners and sale windows (`autumn-mega-sale`, 2025-10-07 … 2025-10-14); a Plus early-access start |
| `Deal` | server id | `Sku` | a price that ends at a fixed instant; «deals of the day» end at local midnight |
| `Customer` | the shildik `sub` claim | — | name, Plus membership, points balance |
| `Guest` | server-issued guest id | — | owns a cart until sign-in, then merged and forgotten |
| `Address` | server id | `Customer` | street, apt, city, ZIP, door code, courier note |
| `PaymentMethod` | server id | `Customer` | closed set of kinds: `card`, `haul_pay`, `pay_on_delivery`; a card is seed data (`···· 4821`) |
| `Cart` / `CartLine` | the owner (customer or guest); line by `Sku` | `Customer` or `Guest` | quantity 1…10, selected flag |
| `PromoCode` | code (`AUTUMN10`) | — | seed data; one per order |
| `SavedItem` | (`Customer`, `Product`) | `Customer` | price at the moment it was saved — the base for «price dropped» |
| `ProductView` | (`Customer`, `Product`, time) | `Customer` | input to recommendations; the last 20 are kept |
| `RecentSearch` | (`Customer`, query) | `Customer` | the last 10 |
| `Order` | `HL-<5 digits>` | `Customer` | totals, payment, delivery method; status derived from its shipments |
| `Shipment` | server id | `Order` | one per seller; closed set of statuses: `placed`, `packed`, `in_transit`, `ready_for_pickup`, `delivered`, `picked_up`, `cancelled` |
| `DeliveryMethod` | closed set | — | `courier`, `pickup_point`, `parcel_locker` |
| `PickupPoint` | server id | — | pickup points and lockers: address, opening hours, coordinates; seed data |
| `DeliverySlot` | (date, window) | — | courier only; next 5 days × 4 windows (9–12, 12–15, 15–18, 18–21); capacity per slot |
| `Return` | server id | `Order` | lines, reason; closed set of statuses: `requested`, `picked_up`, `refunded` |
| `Review` | server id | `Product`, author `Customer` | rating 1–5, title, body, verified flag, helpful count |
| `Question` | server id | `Product`, author `Customer` | an answer is seed data only (no seller side) |
| `Membership` | `Customer` | `Customer` | Haul Plus: `trial`, `active`, `none`; since, renews on, delivery savings this year |
| `PointsEntry` | server id | `Customer` | ledger: earned on delivery, redeemed at checkout, returned on cancellation, reversed on return |
| `InstalmentPlan` | `Order` | `Customer` | Haul Pay: 4 equal simulated charges, two weeks apart |

Tenancy: **none**. One marketplace; a `Customer` sees only their own cart, orders, saved list,
views and searches. Every repository method for those takes the customer id, and «not yours»
answers `404`, the same as «does not exist». There are no roles inside the customer tier: Plus
is an attribute that changes prices and benefits, not access.

## 4. Features

The scenarios are *target* — intended behaviour, to be verified against the code before each
feature document goes `active`. Every number in them (fees, thresholds, limits) is a design
decision of this brief (§8), not an observation.

### feature-identity: Sign-in, guests and the guest cart

**Overview.** A shopper browses and fills a cart without an account; checkout, saving, reviews
and the account need a sign-in through shildik (OIDC). Signing in keeps what the guest put in the
cart.

**Business rules:**

- the client gets a guest id from the server on first start and sends it with every request until sign-in;
- a request with both a valid bearer and a guest id acts as the customer; the guest id is used only by the merge;
- on sign-in the guest cart is merged into the customer's: same `Sku` → quantities summed and capped at 10 and at stock; the guest cart is then deleted;
- the first authenticated request of an unknown `sub` creates the `Customer` from the token's name claim.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** none of its own — the `Header` shows «Sign in» or the customer's first name on every screen. **Endpoints:** endpoint-identity.

**Scenarios (target):**

- *Guest cart survives sign-in* — Given a guest with 1 × Stoneware Mug in the cart and a customer whose cart holds 1 × the same mug, when the guest signs in, then the customer's cart holds 2 × the mug and the guest cart no longer exists.
- *Account needs a sign-in* — Given no bearer token, when the client opens `/ui/account`, then the server returns `401` with `unauthenticated`.

### feature-browse: Home and category catalog

**Overview.** The home page sells: a campaign, two side banners, categories, deals of the day
with a countdown, Haul Plus, and recommendations. A category page lists products with facet
filters, sort and pages.

**Business rules:**

- deals of the day end at local midnight in the store's time zone (`America/New_York`); the countdown is computed by the client from the server's `endsAt`, and an ended deal disappears on the next load;
- a Plus member sees a campaign's prices from its early-access start (24 hours before the public start);
- the Haul Plus block offers the trial to guests and non-members, and shows a member their delivery savings this year and the renewal date;
- facet counts are computed over the current filter set minus the facet itself (the usual «what you would get if you ticked this»);
- 24 products per page; sort: popular (default), price ascending, price descending, rating, newest;
- a product card shows the cheapest in-stock `Sku`'s price, its old price and discount, rating, reviews count and the earliest delivery day to the customer's default address (guest: the store's default ZIP).

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-home, screen-catalog. **Endpoints:** endpoint-catalog, endpoint-membership (the trial), endpoint-recommendations.

**Scenarios (target):**

- *Facets narrow the list* — Given the `headphones` category, when the client asks for brands Sony and Bose, price 80…400 and feature «noise cancelling», then every product returned matches all four and the total is the count shown above the grid.
- *A filter set with no products* — Given the same category, when the client asks for brand Marshall and colour «Pink» together, then the server returns `200` with an empty list and total 0, and the facets still list their values.
- *An unknown category* — when the client opens `/ui/c/no-such-thing`, then the server returns `404` with `category_not_found`.

### feature-search: Search and autocomplete

**Overview.** One field in the header searches everything; while typing, a panel suggests
queries, categories and top products, and shows recent searches.

**Business rules:**

- suggestions start at 2 characters; up to 5 query suggestions, 3 categories with counts, 3 top products;
- a search is recorded in the customer's recent searches (last 10, de-duplicated, newest first); a guest has none;
- «Clear» empties recent searches;
- results group by category with counts (the chips above the grid) and use the catalog's card, sort and pages;
- no results → the page says so and offers the suggestions for the query.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-search. **Endpoints:** endpoint-search.

**Scenarios (target):**

- *Typing suggests* — Given the seed catalog, when the client asks for suggestions for «running sh», then the first query suggestion is «running shoes» and «Sports › Running shoes» is among the categories.
- *Recent searches are personal* — Given a customer who searched «wireless earbuds», when another customer opens the suggestions, then «wireless earbuds» is not in their recent list.
- *Too short* — when the client asks for suggestions for «r», then the server returns `400` with `query_too_short`.

### feature-product: Product page

**Overview.** Everything needed to decide: photos (placeholders in v1), variants, price, delivery
options with dates, returns, the seller, description, specifications, reviews and questions.

**Business rules:**

- choosing a colour and a bundle selects one `Sku`; price, old price, discount and stock follow it;
- delivery estimate per method for the customer's default address: courier «tomorrow» when ordered before the seller's cut-off (23:30 local) and in stock, otherwise the day after; pickup point and locker one day later than courier; free over $35 or for Plus (§8);
- «Order within 3 h 42 min» counts down to the cut-off;
- a `Sku` with stock 0 cannot be added to the cart; the page says «Out of stock» and keeps «Save»;
- «12K bought this month» is the count of delivered and in-transit units in the last 30 days, rounded down to thousands above 1,000;
- opening the page as a customer records a `ProductView`;
- under the price, for prices between $50 and $2,000: «or 4 payments of $87.25 with Haul Pay» (the price ÷ 4, rounded to cents).

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-product. **Endpoints:** endpoint-catalog, endpoint-reviews, endpoint-cart, endpoint-saved.

**Scenarios (target):**

- *Variant changes the price* — Given Sony WH-1000XM6, when the shopper picks «+ Travel case», then the price shown is that `Sku`'s price and «Add to cart» adds that `Sku`.
- *Out of stock* — Given a `Sku` with stock 0, when the client adds it to the cart, then the server returns `409` with `out_of_stock`.
- *Unknown product* — when the client asks for a product id that does not exist, then the server returns `404` with `product_not_found`.

### feature-reviews: Reviews and questions

**Overview.** Reviews with a rating histogram and helpful votes; questions to the seller. Answers
exist only in the seed data — there is no seller side to write new ones.

**Business rules:**

- one review per customer per product; rating 1…5, title 1…120 characters, body 20…5,000;
- «Verified purchase» when the author has a delivered shipment containing the product;
- one helpful vote per customer per review; the author cannot vote on their own;
- the histogram and the average are recomputed when a review is written;
- reviews sort: most helpful (default), newest; 10 per page;
- a question is 10…1,000 characters; a new question shows «Not answered yet».

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-product (Reviews and Questions tabs, the two dialogs). **Endpoints:** endpoint-reviews.

**Scenarios (target):**

- *A verified review* — Given Maya with delivered order #HL-46102 containing the product, when she posts a 5-star review, then it is marked verified and the product's review count grows by one.
- *A second review* — Given Maya already reviewed the product, when she posts another, then the server returns `409` with `review_exists`.
- *Too short* — when a review body is 5 characters, then the server returns `400` with `validation_failed` naming `body`.

### feature-cart: Cart

**Overview.** The cart groups lines by seller, each group with its delivery day; it shows what
the discounts and the promo code saved, the total, and the points the order will earn.

**Business rules:**

- quantity 1…10 per line and never above stock;
- «Select all» / per-line selection; «Delete selected» removes the selected lines; checkout takes the selected lines only;
- «Save for later» moves the line's product to the Saved list and removes the line;
- totals: Items = Σ old price (or price when no old) × qty; Discount = Σ (old − price) × qty + promo; Delivery per §8; Total = Items − Discount + Delivery;
- one promo code per cart; an invalid, expired or inapplicable code is refused with its reason;
- a line whose `Sku` went out of stock or changed price since it was added is marked and excluded from selection until the shopper acknowledges it;
- «You'll earn N points»: whole dollars of the total, ×2 for Plus (Maya: $512 → 1,024);
- a guest sees the cart; «Checkout» asks to sign in.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-cart. **Endpoints:** endpoint-cart, endpoint-saved.

**Scenarios (target):**

- *Totals as drawn* — Given Maya's cart (headphones $349 was $449, duvet cover $139 was $179, mugs $24), when the client loads the cart, then Items is $652.00, Discount −$140.00, Delivery Free, Total $512.00.
- *Promo applies once* — Given `AUTUMN10` applied, when the client applies another code, then the server returns `409` with `promo_already_applied`.
- *Expired promo* — when the client applies `SUMMER5`, then the server returns `422` with `promo_expired`.

### feature-checkout: Checkout

**Overview.** One page: how to receive, the address or the point, the delivery window, the
payment method, the summary, «Place order».

**Business rules:**

- courier needs an address and a slot; pickup point and locker need a point, no slot;
- slots: the next 5 days from tomorrow, 4 windows a day; a slot at capacity is shown and not selectable; choosing a full slot at placement → refused;
- pay on delivery is not offered for parcel lockers;
- Haul Pay is offered for totals between $50 and $2,000: 4 equal payments two weeks apart, the first when the first shipment ships;
- «Use N points» redeems the whole balance, capped at the items total after discounts; 100 points = $1; redeemed points come back if the order is cancelled, and come back pro rata as points on a return;
- placing is idempotent by an `Idempotency-Key` the client generates once per checkout page;
- placement answers `202` with the order in `placed` and the client goes to the order page; the rest happens in the saga (feature-orders);
- «Your card is charged when the order ships» — authorisation at placement, capture per shipment at ship time.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-checkout. **Endpoints:** endpoint-checkout.

**Scenarios (target):**

- *Place by courier* — Given Maya's cart, courier to 148 Wythe Avenue, Wed Oct 8 15:00–18:00, card ···· 4821, when she places the order, then the server returns `202` with an `HL-` id, status `placed`, total $512.00, and two shipments (Sony Official Store, Brooklyn Home Co.).
- *Same key twice* — when the client repeats the placement with the same `Idempotency-Key`, then the server returns the same order and no second order exists.
- *Points redeemed* — Given Maya with 2,480 points and the same cart, when she places it with «Use 2,480 points», then the total is $487.20 and her balance is 0 until the order earns.
- *Slot filled meanwhile* — Given the chosen slot reached capacity after the page loaded, when she places the order, then the server returns `409` with `slot_unavailable`.

### feature-orders: Order lifecycle, tracking and returns

**Overview.** After placement a saga reserves stock and authorises payment; a fulfilment
simulator then moves each seller's shipment along. The order page shows it; a delivered order can
be returned and reordered.

**Business rules:**

- the saga (petich): reserve stock → authorise payment (card / Haul Pay; none for pay on delivery) → confirm; a failed step compensates the previous ones (release stock, void authorisation) and the order ends `cancelled` with a reason;
- the simulated card processor declines the test card ending `0002` and approves the others (§5a);
- the fulfilment simulator advances each shipment `placed → packed → in_transit → delivered` (courier) or `… → ready_for_pickup → picked_up` (point, locker) on a configurable clock; `in_transit` triggers the capture for that shipment;
- a pickup shipment keeps a 4-digit pickup code and is held 5 days from `ready_for_pickup`;
- the order's status is derived from its shipments (the least advanced one wins);
- a return can be requested for delivered lines within 30 days of delivery; the simulator picks it up and refunds; points earned on those lines are reversed;
- «Reorder» puts the order's still-available `Sku`s into the cart and reports the ones that are not.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-order, screen-account (active orders and history). **Endpoints:** endpoint-orders.

**Scenarios (target):**

- *Declined card cancels and releases* — Given a `Sku` with stock 3 and an order of 1 paid with the card ending 0002, when the saga runs, then the order is `cancelled` with `payment_declined` and the stock is 3 again.
- *Charged when shipped* — Given an order with two shipments, when the first reaches `in_transit`, then exactly that shipment's amount is captured and the second is still only authorised.
- *Late return* — Given a shipment delivered 31 days ago, when the customer requests a return, then the server returns `422` with `return_window_closed`.

### feature-account: Account overview and the Saved list

**Overview.** One page answers «what is coming, what did I save, what do I have»: points, Plus
savings, price drops, active orders, history. The Saved list holds hearted products and lines
saved for later, and marks the ones that got cheaper.

**Business rules:**

- the overview shows up to 3 active orders (not delivered, not cancelled) and the last 4 of the history;
- «Price drops N» counts saved products whose cheapest in-stock price is below the price at saving time;
- saving is idempotent; the heart on a card toggles it;
- the Saved list filters «All» / «Price dropped», 24 per page, newest first.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-account, screen-saved. **Endpoints:** endpoint-account, endpoint-saved, endpoint-orders.

**Scenarios (target):**

- *Price drop counted* — Given Maya saved the Robot Vacuum S8 at $499 and it now costs $299, when she opens the account, then «Price drops» counts it and the Saved list marks it.
- *Saved twice* — when the client saves the same product twice, then the second call returns `200` and the list holds it once.

### feature-membership: Haul Plus, points and Haul Pay

**Overview.** The loyalty layer: a subscription that removes delivery fees and doubles points,
points that turn into money off, and paying in instalments. All money here is simulated.

**Business rules:**

- the trial starts on request, lasts 30 days, then «renews» at $4.99/month with no real charge; the account shows the renewal date;
- Plus: free delivery on every order, courier next day regardless of the order total, points ×2, campaign prices from the early-access start;
- points: 1 point per whole dollar of an order's total, ×2 for Plus, credited when a shipment is delivered, proportional per shipment; 100 points = $1 (2,480 → $24.80);
- Delivery savings this year = Σ the fee a non-member would have paid on the member's orders since January 1;
- Haul Pay: 4 equal payments two weeks apart, simulated; a declined instalment is retried once and then marks the plan overdue (no collections in v1).

**Modules:** `shared`, `server`, `composeApp`. **Screens:** none of its own — screen-home (Plus block, trial dialog), screen-cart and screen-checkout (points, Haul Pay), screen-account (tiles). **Endpoints:** endpoint-membership.

**Scenarios (target):**

- *Trial* — Given Sam (no membership), when he starts the trial on 2025-10-07, then his membership is `trial` until 2025-11-06 and his next cart shows free delivery.
- *Already a member* — Given Maya (active), when she starts the trial, then the server returns `409` with `already_member`.
- *Points on delivery* — Given Sam's $103.00 order without Plus, when its only shipment is delivered, then his balance grows by 103.

### feature-recommendations: Picked for you

**Overview.** Six products the customer is likely to want, from what they viewed recently.

**Business rules:**

- from the customer's last 20 product views take their categories by frequency; return the top-rated in-stock products of those categories that were neither viewed nor bought, at most 2 per category, 6 in total;
- fewer than 3 views → the block shows popular products and its subtitle says «Popular right now»;
- a guest gets no block.

**Modules:** `shared`, `server`, `composeApp`. **Screens:** screen-home. **Endpoints:** endpoint-recommendations.

**Scenarios (target):**

- *From views* — Given Maya viewed three headphones, when she opens the home page, then «Picked for you» holds at most two headphones and none of the three she viewed.
- *Guest* — Given no bearer, when the client opens `/ui/home`, then the tree holds no «Picked for you» block and no request for recommendations was made.

## 5. Screens

The screen and state names are written once, here. The canvas started as one state per screen,
desktop only; since 2026-10-08 it draws every row below at both widths, as static artboards made
from the design briefs. All screens share the `Header` component (states of the header are not screen states: it
shows the signed-in first name or «Sign in», the cart count, and the query on Search).

**Sizes:** desktop `1440×H`, phone `390×H`, `H` being the full height of that state's page as
drawn; each screen's `Sizes` row lists them, copied from the canvas's `canvas/canvas.json`
(Claude Design project «E-commerce витрина», 2026-10-08). The «Canvas» column says where each
artboard lives: every one of the 125 is drawn as `canvas/<Artboard>.dc.html`. The table names the desktop artboard; the phone artboard
of the same state is `<Screen>_<State>_Phone` — the same state at another size, so not a row here,
in the way a dark variant would be `_Dark`. A row marked *phone only* has only the `_Phone`
artboard. Dark variants: **none** — the canvas is light only.

`Loading` and `Error` are drawn by the client while it has no tree or after a failed request;
every other state is a tree the server returns (§5b, §6), rendered by the client's registry.

### screen-home: Home (`Home`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/` |
| Parent feature | feature-browse |
| Calls | endpoint-catalog (`/ui/home`), endpoint-recommendations, endpoint-membership (trial), endpoint-saved (heart), endpoint-cart («+») |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 2129 / 2010; Content 3263 / 3875; Guest 2627 / 2721; PlusTrialDialog 3134 / 3850; Error 900 / 692 |
| Source | filled in by the implementer |

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Home_Loading` | header, placeholder blocks for hero, categories and two product rows | `canvas/Home_Loading.dc.html` |
| Content | `Home_Content` | Maya signed in: campaign «Autumn mega sale», «Tech week», «Free delivery» banners, 8 categories, 6 deals with the countdown, the Plus block **in its member form** («You saved $186 on delivery this year · renews Nov 2»), «Picked for you» 6 products, footer | `canvas/Home_Content.dc.html` |
| Guest | `Home_Guest` | header «Sign in»; Plus block offers the trial; no «Picked for you» | `canvas/Home_Guest.dc.html` |
| PlusTrialDialog | `Home_PlusTrialDialog` | Sam signed in, dialog over Content: the benefits, «30 days free, then $4.99/month», Start trial / Not now | `canvas/Home_PlusTrialDialog.dc.html` |
| Error | `Home_Error` | header, message that the page could not load, Retry | `canvas/Home_Error.dc.html` |

**Actions:** category tile → screen-catalog; card → screen-product; «+» on a card → adds the cheapest `Sku`; heart → save; «Try 30 days free» → PlusTrialDialog (guest → sign-in); search field → screen-search.

### screen-catalog: Category (`Catalog`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/c/{categoryPath}` |
| Parent feature | feature-browse |
| Calls | endpoint-catalog (`/ui/c/{categoryPath}`), endpoint-cart, endpoint-saved |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1717 / 2356; Content 1940 / 2789; Empty 1646 / 852; Error 900 / 692; FiltersSheet — / 1467 |

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Catalog_Loading` | breadcrumbs and title, placeholder facet column and 12 card placeholders | `canvas/Catalog_Loading.dc.html` |
| Content | `Catalog_Content` | Headphones, 12,408 items, facets with Sony + Bose + $80–$400 + Noise cancelling applied, chips, sort «Popular», 12 cards, «Show 24 more», pages 1 2 3 … 517 | `canvas/Catalog_Content.dc.html` |
| Empty | `Catalog_Empty` | same filters plus Marshall + Pink: «No items match these filters», Clear all, facets still visible | `canvas/Catalog_Empty.dc.html` |
| FiltersSheet | `Catalog_FiltersSheet_Phone` | **phone only**: the facet column as a full-height sheet with «Show 48 items» | `canvas/Catalog_FiltersSheet_Phone.dc.html` |
| Error | `Catalog_Error` | header, message, Retry | `canvas/Catalog_Error.dc.html` |

**Actions:** facet tick → reload; chip × → remove; sort → reload; page → reload; card → screen-product.

### screen-search: Search (`Search`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/search?q=` |
| Parent feature | feature-search |
| Calls | endpoint-search |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1288 / 1984; Results 1359 / 2196; Autocomplete 1359 / 2196; NoResults 869 / 1010; Error 900 / 692 |

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Search_Loading` | header with «running shoes», placeholder chips and cards | `canvas/Search_Loading.dc.html` |
| Results | `Search_Results` | «Running shoes», 14,870 results, category chips with counts, 10 cards; no panel | `canvas/Search_Results.dc.html` |
| Autocomplete | `Search_Autocomplete` | Results with the panel open: suggestions, «In categories», «Recent», «Top products», «All 14,870 results ↵» | `canvas/Search_Autocomplete.dc.html` |
| NoResults | `Search_NoResults` | «Nothing found for "xqzt"», suggestions to try, popular categories | `canvas/Search_NoResults.dc.html` |
| Error | `Search_Error` | header, message, Retry | `canvas/Search_Error.dc.html` |

**Actions:** suggestion → Results for it; category → screen-catalog; top product → screen-product; «Clear» → empties Recent.

### screen-product: Product (`Product`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/p/{productId}` |
| Parent feature | feature-product |
| Calls | endpoint-catalog (`/ui/p/{productId}`), endpoint-reviews, endpoint-cart, endpoint-saved |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 835 / 1547; Description 1494 / 2720; Specifications 1624 / 2869; Reviews 1572 / 3067; Questions 1723 / 2930; ReviewDialog 1572 / 3067; QuestionDialog 1723 / 2930; OutOfStock 1494 / 2680; NotFound 799 / 653; Error 900 / 641 |

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Product_Loading` | breadcrumbs, photo placeholder, placeholder lines for title, price and delivery | `canvas/Product_Loading.dc.html` |
| Description | `Product_Description` | the page with the Description tab open | `canvas/Product_Description.dc.html` |
| Specifications | `Product_Specifications` | Specifications tab: the full key → value list | `canvas/Product_Specifications.dc.html` |
| Reviews | `Product_Reviews` | Reviews tab: 4.8, histogram 78/14/4/2/2, two reviews | `canvas/Product_Reviews.dc.html` |
| Questions | `Product_Questions` | Questions tab: two answered questions, one «Not answered yet», «Ask a question» | `canvas/Product_Questions.dc.html` |
| ReviewDialog | `Product_ReviewDialog` | dialog over Reviews: stars, title, body, Post | `canvas/Product_ReviewDialog.dc.html` |
| QuestionDialog | `Product_QuestionDialog` | dialog over Questions: text, Send | `canvas/Product_QuestionDialog.dc.html` |
| OutOfStock | `Product_OutOfStock` | colour «Silver» chosen: «Out of stock», Add to cart and Buy now disabled, Save kept | `canvas/Product_OutOfStock.dc.html` |
| NotFound | `Product_NotFound` | «This product is no longer available», link home | `canvas/Product_NotFound.dc.html` |
| Error | `Product_Error` | header, message, Retry | `canvas/Product_Error.dc.html` |

**Actions:** colour / bundle → another `Sku`; Add to cart → header count +1; Buy now → adds and opens screen-checkout; seller card → nothing in v1; helpful → vote.

### screen-cart: Cart (`Cart`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/cart` |
| Parent feature | feature-cart |
| Calls | endpoint-cart, endpoint-saved |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1041 / 1220; Content 1179 / 1668; Empty 1216 / 1722; PromoApplied 1179 / 1705; PromoError 1179 / 1688; ItemChanged 1263 / 1776; Guest 1179 / 1607; Error 900 / 617 |

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Cart_Loading` | title, placeholder groups and summary | `canvas/Cart_Loading.dc.html` |
| Content | `Cart_Content` | 3 items in 2 seller groups, all selected, summary $652.00 / −$140.00 / Free / $512, empty promo field, «You'll earn 1,024 points» | `canvas/Cart_Content.dc.html` |
| Empty | `Cart_Empty` | «Your cart is empty», link to deals, «Picked for you» row | `canvas/Cart_Empty.dc.html` |
| PromoApplied | `Cart_PromoApplied` | Content with `AUTUMN10` applied: a promo line in the summary, the code with Remove | `canvas/Cart_PromoApplied.dc.html` |
| PromoError | `Cart_PromoError` | Content with `SUMMER5` in the field and «This code has expired» | `canvas/Cart_PromoError.dc.html` |
| ItemChanged | `Cart_ItemChanged` | the mug line marked «Price changed: now $26» (or «Out of stock»), unselected, «OK» | `canvas/Cart_ItemChanged.dc.html` |
| Guest | `Cart_Guest` | Content without the points line; button «Sign in to check out» | `canvas/Cart_Guest.dc.html` |
| Error | `Cart_Error` | header, message, Retry | `canvas/Cart_Error.dc.html` |

**Actions:** − / + → quantity; Remove; Save for later; Select all / Delete selected; Apply; Checkout → screen-checkout (guest → sign-in).

### screen-checkout: Checkout (`Checkout`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/checkout` |
| Parent feature | feature-checkout |
| Calls | endpoint-checkout, endpoint-identity (addresses) |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1467 / 2204; Content 1564 / 2362; PointsApplied 1564 / 2390; PickupPoint 1267 / 2005; ParcelLocker 1193 / 1802; Validation 1608 / 2438; Placing 1564 / 2362; PlaceError 1678 / 2509; Error 804 / 529 |

The checkout page has its own minimal header (logo, steps «Delivery · Payment · Review»,
«Secure checkout») instead of `Header`.

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Checkout_Loading` | the minimal header, placeholder sections and summary | `canvas/Checkout_Loading.dc.html` |
| Content | `Checkout_Content` | courier, 148 Wythe Avenue 4F, Wed 8 / 15:00–18:00, card ···· 4821, «Use 2,480 points (−$24.80)» off, summary $512, «Place order · $512.00» | `canvas/Checkout_Content.dc.html` |
| PointsApplied | `Checkout_PointsApplied` | Content with the points toggle on: a «Points −$24.80» line, total $487.20, «Place order · $487.20», Haul Pay «4 payments of $121.80» | `canvas/Checkout_PointsApplied.dc.html` |
| PickupPoint | `Checkout_PickupPoint` | «Pickup point» chosen: list of 3 nearby points with distance and hours, 214 Bedford Ave selected; no slot section | `canvas/Checkout_PickupPoint.dc.html` |
| ParcelLocker | `Checkout_ParcelLocker` | «Parcel locker» chosen: list of lockers; «Pay on delivery» absent | `canvas/Checkout_ParcelLocker.dc.html` |
| Validation | `Checkout_Validation` | courier with empty street and ZIP: field errors, button disabled | `canvas/Checkout_Validation.dc.html` |
| Placing | `Checkout_Placing` | Content with the button in progress, inputs disabled | `canvas/Checkout_Placing.dc.html` |
| PlaceError | `Checkout_PlaceError` | Content with a banner «That delivery window just filled up — pick another», the slot cleared | `canvas/Checkout_PlaceError.dc.html` |
| Error | `Checkout_Error` | the minimal header, message, Retry | `canvas/Checkout_Error.dc.html` |

**Actions:** method / point / slot / payment → re-quote totals; Place order → screen-order (`Order_Placed`).

### screen-order: Order (`Order`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/account/orders/{orderId}` |
| Parent feature | feature-orders |
| Calls | endpoint-orders |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1205 / 1348; Placed 1446 / 1814; InTransit 1345 / 1662; ReadyForPickup 1074 / 1481; Delivered 1125 / 1581; ReturnDialog 1125 / 1581; Returned 1146 / 1446; Cancelled 1193 / 1550; NotFound 719 / 545; Error 900 / 641 |

Not on the canvas at all; the account's «Details» link and «Place order» both lead here.

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Order_Loading` | header, placeholder progress and lines | `canvas/Order_Loading.dc.html` |
| Placed | `Order_Placed` | «Thanks, Maya — order #HL-48302 is placed», $512.00, two shipments (Sony tomorrow, Brooklyn Home Co. Thu Oct 9), progress at Placed | `canvas/Order_Placed.dc.html` |
| InTransit | `Order_InTransit` | #HL-48211: progress Placed · Packed · **In transit** · Delivered, «Arriving tomorrow, 15:00–18:00» | `canvas/Order_InTransit.dc.html` |
| ReadyForPickup | `Order_ReadyForPickup` | #HL-47960: point, hours, «kept until Oct 10», pickup code 4821 | `canvas/Order_ReadyForPickup.dc.html` |
| Delivered | `Order_Delivered` | #HL-46102: delivered, per line «Write a review», «Return items», «Reorder» | `canvas/Order_Delivered.dc.html` |
| ReturnDialog | `Order_ReturnDialog` | dialog over Delivered: lines with checkboxes, reason, «Request return» | `canvas/Order_ReturnDialog.dc.html` |
| Returned | `Order_Returned` | #HL-44019: return refunded, $87.50 back to the card | `canvas/Order_Returned.dc.html` |
| Cancelled | `Order_Cancelled` | #HL-48303 cancelled: «Your card ···· 0002 was declined», nothing charged, «Back to cart» | `canvas/Order_Cancelled.dc.html` |
| NotFound | `Order_NotFound` | «Order not found», link to orders | `canvas/Order_NotFound.dc.html` |
| Error | `Order_Error` | header, message, Retry | `canvas/Order_Error.dc.html` |

### screen-account: Account (`Account`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/account`, `/account/orders` |
| Parent feature | feature-account |
| Calls | endpoint-account, endpoint-orders |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1014 / 1403; Content 1629 / 2255; NotMember 1112 / 1378; Orders 1067 / 1332; NoOrders 708 / 693; Error 900 / 641 |

Menu items other than Overview, Orders and Saved are not shown in v1 (§2).

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Account_Loading` | side menu, placeholder tiles and order cards | `canvas/Account_Loading.dc.html` |
| Content | `Account_Content` | «Hi, Maya», Points 2,480 / $24.80, Haul Plus $186 / renews Nov 2, Price drops 6, two active orders, 4 history rows | `canvas/Account_Content.dc.html` |
| NotMember | `Account_NotMember` | Sam: the Plus tile offers the trial, points 0, no active orders, history with one order | `canvas/Account_NotMember.dc.html` |
| Orders | `Account_Orders` | «Orders» selected: the full history with a status filter (All / Active / Delivered / Returned / Cancelled) | `canvas/Account_Orders.dc.html` |
| NoOrders | `Account_NoOrders` | a customer with no orders: «No orders yet», link to deals | `canvas/Account_NoOrders.dc.html` |
| Error | `Account_Error` | header, message, Retry | `canvas/Account_Error.dc.html` |

### screen-saved: Saved (`Saved`)

| | |
|---|---|
| Platforms | web (desktop, phone) |
| Entry | web: `/account/saved` |
| Parent feature | feature-account |
| Calls | endpoint-saved, endpoint-cart |
| Sizes | width 1440 desktop, 390 phone; heights desktop / phone: Loading 1659 / 2422; Content 1890 / 2727; PriceDrops 1415 / 1685; Empty 948 / 1316; Error 799 / 641 |

Not on the canvas; reuses `ProductCard` with a «Price dropped −$200» mark.

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Saved_Loading` | side menu, 12 card placeholders | `canvas/Saved_Loading.dc.html` |
| Content | `Saved_Content` | «Saved 48», filter All / Price dropped 6, 12 cards, two with the price-drop mark | `canvas/Saved_Content.dc.html` |
| PriceDrops | `Saved_PriceDrops` | filter «Price dropped», the 6 cards | `canvas/Saved_PriceDrops.dc.html` |
| Empty | `Saved_Empty` | «Nothing saved yet», how the heart works | `canvas/Saved_Empty.dc.html` |
| Error | `Saved_Error` | header, message, Retry | `canvas/Saved_Error.dc.html` |

### 5a. Sample data

The values every artboard, preview, scenario and screenshot fixture shows. Taken from the canvas
verbatim; values the canvas does not have are marked *new*. The seed database is a separate,
larger generated set; these are the fixtures.

| Entity | Values |
|---|---|
| Now | **2025-10-07 19:47:23 America/New_York (Tuesday)** — the canvas's weekdays (Wed 8, Thu 9) fit 2025, not 2026; the deals countdown 04:12:37 is the time to midnight |
| `Customer` | Maya Kowalski — Plus since 2023, renews 2025-11-02, points 2,480, delivery savings $186, 48 saved, 6 price drops; Sam Ortiz *new* — no membership, 0 points, one delivered order |
| `Address` | 148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211; header «Deliver to Brooklyn, NY 11211» |
| `PaymentMethod` | Card ···· 4821, expires 08/28 (approves); test card ···· 0002 *new* (declines); Haul Pay «4 payments of $128» on $512, «4 payments of $87.25» on $349 *new*; points «Use 2,480 points (−$24.80)» → total $487.20 *new* |
| `PickupPoint` | 214 Bedford Ave, 240 m, open until 21:00; *new*: 96 N 6th St, 650 m, until 22:00; locker «Wythe & N 7th», 24/7 |
| `Seller` | Sony Official Store — 4.9, 98 % positive, 6 yrs on Haul; Brooklyn Home Co. *new rating* — 4.8, 97 %, 3 yrs |
| `Product` (hero) | Sony WH-1000XM6 Wireless Noise Cancelling Headphones — $349, was $449, −22 %, Bestseller, 4.8, 2,341 reviews, 86 questions, 12K bought this month; colour Midnight Black (*new*: Silver, out of stock); bundles Headphones only / + Travel case / + 2-year care; specs: battery up to 40 h, 3 min charge = 3 h; adaptive noise cancelling, 12 microphones; Bluetooth 5.4 · LDAC · multipoint; 250 g; histogram 78/14/4/2/2 % |
| `Review` | Daniel R., Sep 28, verified, 5.0, «The quietest flight I've had»; Aisha K., Sep 21, verified, 4.0, «Great sound, tight at first», 48 found helpful |
| `Cart` (Maya) | Sony WH-1000XM6, Midnight Black · Headphones only, 1 × $349 (was $449), courier tomorrow; Linen Duvet Cover Set, Queen, Oat · 3 pieces, 1 × $139 (was $179), Brooklyn Home Co., Thu Oct 9; Stoneware Mug, 12 oz, Sage · Set of 2, 1 × $24, Brooklyn Home Co.; Items $652.00, Discount −$140.00, Delivery Free, Total $512 |
| `PromoCode` *new* | `AUTUMN10` — 10 % off items, up to $50, valid 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211, placed Oct 5, $512.00, in transit, arriving tomorrow 15:00–18:00; #HL-47960, Oct 3, $58.00, ready for pickup at 214 Bedford Ave until Oct 10, code 4821; history: Sep 24 #HL-46102 $103.00 delivered; Sep 11 #HL-45277 $299.00 delivered; Aug 30 #HL-44019 $87.50 returned; Aug 12 #HL-42860 $42.00 delivered; *new*: #HL-48302 — the order placed from the checkout fixture (Oct 7, $512.00); #HL-48303 — cancelled, card ···· 0002 |
| `Category` | Electronics, Home & Kitchen, Fashion, Beauty, Kids & Toys, Sports, Grocery, Pets (tiles); header row adds Auto, Books; Headphones: 12,408 items; brands Sony 1,204, Bose 611, JBL 1,980, Apple 842, Sennheiser 530, Marshall 204 |
| Search | «running shoes»: 14,870 results; Men's shoes 6,204, Women's shoes 5,880, Kids' shoes 1,412, Insoles 830, Socks 544; suggestions «running shoes women», «running shoes men wide», «trail running shoes», «running shoes waterproof»; Sports › Running shoes 8,214, Fashion › Sneakers 5,102; recent «wireless earbuds», «standing desk»; top products Pace Runner 3 Men's $89, Cloudline Daily Trainer Women's $112, Ridge Trail GTX $72 |
| Product lists | the four lists in the canvas script (`renderVals`): deals 6, recs 6, catalog 12, search 10 — titles, prices, old prices, badges, ratings, review counts, delivery days and tile tones, copied verbatim into the fixtures |
| Campaigns | «Autumn mega sale · Oct 7 — 14, Up to −70 %, 1.2 million items marked down across 32 categories»; «Tech week — Laptops from $399»; «This weekend — Free delivery on everything, 600K items from local sellers» |

### 5b. Components on the wire

Every screen is a kompot tree the server builds; the client renders it through a registry. The
standard set (`kompot-standard`: text, containers, lists, tabs, pagination) covers layout; what
the canvas draws as a recognisable piece is a Haul component, declared once in `shared` with
`@KompotComponentMarker` and rendered by `composeApp`. The names go into the code unchanged; the
wire type is the snake-case form with a `haul_` prefix.

| Component | Wire type | Where | Carries |
|---|---|---|---|
| `HaulHeader` | `haul_header` | every screen but Checkout | address line, signed-in first name or sign-in action, cart count, query |
| `CheckoutHeader` | `haul_checkout_header` | Checkout | current step |
| `HaulFooter` | `haul_footer` | Home | link columns (labels only in v1) |
| `CampaignHero`, `PromoBanner` | `haul_campaign_hero`, `haul_promo_banner` | Home | campaign copy, tone, target action |
| `CategoryTile` | `haul_category_tile` | Home | name, tone, label |
| `DealsRow` | `haul_deals_row` | Home | `endsAt` (the client counts down), product cards |
| `PlusBlock` | `haul_plus_block` | Home | member form (savings) or offer form (trial action) |
| `ProductCard` | `haul_product_card` | Home, Catalog, Search, Cart (empty), Saved | the canvas's `ProductCard` props plus product id, save state, price-drop mark |
| `Breadcrumbs` | `haul_breadcrumbs` | Catalog, Product | path |
| `FacetPanel`, `FilterChips`, `SortSelect` | `haul_facet_panel`, `haul_filter_chips`, `haul_sort_select` | Catalog, Search | facets with counts, applied filters, sort |
| `SearchSuggestPanel` | `haul_search_suggest` | Search | suggestions, categories, recent, top products |
| `ProductGallery`, `VariantPicker`, `PriceBlock` | `haul_product_gallery`, `haul_variant_picker`, `haul_price_block` | Product | photo tiles, options and the chosen `Sku`, price / old / discount / Haul Pay line |
| `DeliveryOptions`, `SellerCard` | `haul_delivery_options`, `haul_seller_card` | Product | estimates per method, cut-off; seller summary |
| `RatingSummary`, `ReviewItem`, `QuestionItem` | `haul_rating_summary`, `haul_review_item`, `haul_question_item` | Product | histogram; one review; one question |
| `CartGroup`, `CartLine`, `OrderSummary`, `PromoField` | `haul_cart_group`, `haul_cart_line`, `haul_order_summary`, `haul_promo_field` | Cart, Checkout | seller and delivery day; line; totals and points; promo state |
| `DeliveryMethodPicker`, `PickupPointList`, `SlotPicker`, `PaymentMethodPicker` | `haul_delivery_method_picker`, `haul_pickup_point_list`, `haul_slot_picker`, `haul_payment_method_picker` | Checkout | the choices and which is selected; the address is a `kompot-forms` form |
| `OrderProgress`, `OrderCard`, `PickupCode` | `haul_order_progress`, `haul_order_card`, `haul_pickup_code` | Order, Account | stepper; order row; code and hold date |
| `AccountMenu`, `AccountTile` | `haul_account_menu`, `haul_account_tile` | Account, Saved | menu with counts; points / Plus / price-drops tile |
| `EmptyState` | `haul_empty_state` | every Empty / NoResults / NotFound | title, text, action |

Dialogs (`Home_PlusTrialDialog`, `Product_ReviewDialog`, `Product_QuestionDialog`,
`Order_ReturnDialog`) are routes shown over the screen (`kompot-navigation`); the review, question,
return and address inputs are `kompot-forms` forms, validated on the client by the same rules the
server checks.

## 6. API

Wire conventions, once for the product:

- **screens** are `GET /ui/...` routes answering a kompot tree through `respondKompotComponent`
  (never `call.respond`, which drops the root's type discriminator — kompot README); the client
  never asks for a screen's data as JSON;
- **commands** are `POST` / `PUT` / `DELETE` under `/api/v1/...`, taking JSON and answering a kompot
  action (refresh the screen, navigate, show a route over it) or an error;
- JSON with `kotlinx.serialization`, `explicitNulls = false`; money as integer cents plus `USD`;
  instants as ISO-8601 UTC; route classes in `shared`;
- an error is `{ "code": "<error_code>", "message": "…", "field": "…"? }` with `code` from a closed
  `ErrorCode` enum in `shared`; a screen route that cannot be built answers the status with that
  body, and the client draws the screen's `NotFound` or `Error` state;
- «not yours» and «does not exist» are both `404`;
- a guest is the `X-Haul-Guest` header; a customer is `Authorization: Bearer <shildik access token>`.

Tiers: **public** (no credentials, or a guest id), **customer** (a valid shildik token), **infra**
(the orchestrator's probes, not in the public schema). There are no roles inside a tier. Response
column: *tree* is a kompot screen, *action* a kompot action.

### endpoint-identity: Guests, cart merge, addresses

Contract class: `shared: GuestRoutes, AddressRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `POST` | `/api/v1/guests` | public | — | — | `GuestDto` (id) | `429` rate_limited | yes |
| `POST` | `/api/v1/me/cart/merge` | customer | — | header `X-Haul-Guest` | action: refresh | `401`, `404` guest_not_found | yes |
| `POST` | `/api/v1/me/addresses` | customer | — | the address form | action: refresh Checkout | `400` validation_failed, `401` | yes |

### endpoint-catalog: Home, category, product

Contract class: `shared: CatalogRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/home` | public | — | — | tree: Home (with recommendations and the Plus block for a customer) | `503` unavailable | yes |
| `GET` | `/ui/c/{categoryPath}` | public | — | filters, sort, page | tree: Catalog | `400` validation_failed, `404` category_not_found | yes |
| `GET` | `/ui/p/{productId}` | public | — | `sku`, `tab` (description / specifications / reviews / questions), page | tree: Product; records a view for a customer | `404` product_not_found | yes |

### endpoint-search: Search

Contract class: `shared: SearchRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/search` | public | — | `q`, category, sort, page | tree: Search results or NoResults | `400` query_too_short | yes |
| `GET` | `/ui/search/suggest` | public | — | `q` | tree: `SearchSuggestPanel` | `400` query_too_short | yes |
| `DELETE` | `/api/v1/me/recent-searches` | customer | — | — | action: refresh the panel | `401` | yes |

### endpoint-reviews: Reviews and questions

Contract class: `shared: ReviewRoutes`. Service: `server`. Reading them is the Product
screen's `tab` parameter.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `POST` | `/api/v1/products/{id}/reviews` | customer | — | the review form | action: close the route, refresh | `400` validation_failed, `401`, `404` product_not_found, `409` review_exists | yes |
| `PUT` | `/api/v1/reviews/{id}/helpful` | customer | — | — | action: refresh | `401`, `404`, `409` own_review | yes |
| `POST` | `/api/v1/products/{id}/questions` | customer | — | the question form | action: close the route, refresh | `400` validation_failed, `401`, `404` | yes |

### endpoint-cart: Cart

Contract class: `shared: CartRoutes`. Service: `server`. Public tier means «a guest id or
a customer token»; neither is `401`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/cart` | public | — | — | tree: Cart (Content, Empty, ItemChanged, Guest, promo states) | `401` | yes |
| `PUT` | `/api/v1/cart/lines/{skuId}` | public | — | `quantity`, `selected` | action: refresh (and the header count) | `400` validation_failed, `401`, `404` sku_not_found, `409` out_of_stock | yes |
| `DELETE` | `/api/v1/cart/lines` | public | — | `skuIds` | action: refresh | `401` | yes |
| `POST` | `/api/v1/cart/lines/{skuId}/acknowledge` | public | — | — | action: refresh | `401`, `404` | yes |
| `PUT` | `/api/v1/cart/promo` | public | — | `code` | action: refresh | `401`, `404` promo_not_found, `409` promo_already_applied, `422` promo_expired / promo_not_applicable | yes |
| `DELETE` | `/api/v1/cart/promo` | public | — | — | action: refresh | `401` | yes |

### endpoint-saved: Saved list

Contract class: `shared: SavedRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/account/saved` | customer | — | filter, page | tree: Saved | `401` | yes |
| `PUT` | `/api/v1/me/saved/{productId}` | customer | — | — | action: refresh the card | `401`, `404` product_not_found | yes |
| `DELETE` | `/api/v1/me/saved/{productId}` | customer | — | — | action: refresh the card | `401` | yes |
| `POST` | `/api/v1/cart/lines/{skuId}/save-for-later` | customer | — | — | action: refresh | `401`, `404` | yes |

### endpoint-checkout: Checkout and placement

Contract class: `shared: CheckoutRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/checkout` | customer | — | the current selection | tree: Checkout (methods, points, slots with capacity, payment methods, quote) | `401`, `409` cart_empty | yes |
| `POST` | `/api/v1/orders` | customer | — | `PlaceOrderRequest`, header `Idempotency-Key` | `202`, action: navigate to `/ui/orders/{id}` | `400` validation_failed / idempotency_key_missing, `401`, `409` slot_unavailable / out_of_stock / cart_changed / points_balance_changed / idempotency_key_reused, `422` payment_method_not_allowed | yes |

### endpoint-orders: Orders and returns

Contract class: `shared: OrderRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/orders/{id}` | customer | — | — | tree: Order (per status) | `401`, `404` order_not_found | yes |
| `GET` | `/ui/account/orders` | customer | — | status filter, page | tree: Account with Orders selected | `401` | yes |
| `POST` | `/api/v1/me/orders/{id}/reorder` | customer | — | — | action: navigate to the cart, with the unavailable lines named | `401`, `404` | yes |
| `POST` | `/api/v1/me/orders/{id}/returns` | customer | — | the return form | action: close the route, refresh | `400` validation_failed, `401`, `404`, `422` return_window_closed / not_delivered | yes |

### endpoint-account: Account overview

Contract class: `shared: AccountRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/account` | customer | — | — | tree: Account (Content, NotMember, NoOrders) | `401` unauthenticated | yes |

### endpoint-membership: Haul Plus

Contract class: `shared: MembershipRoutes`. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `POST` | `/api/v1/me/plus/trial` | customer | — | — | action: close the route, refresh | `401`, `409` already_member / trial_used | yes |

### endpoint-recommendations: Picked for you

Contract class: `shared: CatalogRoutes`. Service: `server`. Not a route of its own: the
block is part of the `/ui/home` tree for a customer; its scenarios run against the use case.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/ui/home` (the `PlusBlock` and «Picked for you» part) | public | — | — | tree fragment, absent for a guest | `503` unavailable | yes |

### endpoint-ops: Probes

Contract class: none. Service: `server`.

| Method | Path | Tier | Min role | Request | Response | Errors | Public schema |
|---|---|---|---|---|---|---|---|
| `GET` | `/healthz`, `/readyz`, `/version` | infra | — | — | probe body | `503` not ready | no |

Every screen calls at least one group; every group is called by a screen (ops by the orchestrator).

## 7. Modules and services

| Module | Role | Stack | Storage / config | Depends on | Publishes | New or existing |
|---|---|---|---|---|---|---|
| `shared` | contract: Haul kompot components (§5b), route classes, `ErrorCode`, money and time types | KMP: jvm, wasmJs | — | kompot | — (a module of this build) | new |
| `server` | screens as kompot trees, commands, use cases, the order saga, the payment and fulfilment simulators, seed loader | Ktor on the JVM, kompot-ktor, petich, shildik, zavarnik AOT cache | PostgreSQL; env: `HAUL_DB_URL`, `HAUL_OIDC_ISSUER`, `HAUL_FULFILMENT_SPEED`, `HAUL_SEED` | `shared`, PostgreSQL, a shildik issuer | container image | new |
| `composeApp` | the storefront shell: navigation, the kompot renderer and the Haul renderers, Loading / Error, sign-in, guest id | Compose Multiplatform wasmJs (+ `jvm("desktop")` for screenshot tests), kompot client | browser storage: guest id | `shared`, `server` (HTTP) | the web bundle, served by `server` | new |
| `e2e` | the whole path over HTTP: browse → cart → sign-in → checkout → delivered → return | JVM tests against a composed stack | — | `shared` | — | new |

Deploy: a Helm chart `charts/haul` and a public demo stand on the owner's domain, the host chosen in §9 item 27. Local:
`docker compose` with PostgreSQL and a shildik instance.

## 8. Decisions and hypotheses

The stack is the owner's decision; what is left open here is for the research document to settle
against the artefacts, not for this brief.

| Decision / claim | Why | Verified against / *hypothesis* |
|---|---|---|
| Ktor on the JVM, kompot for every screen, petich for the order, shildik for sign-in, PostgreSQL, zavarnik for start-up | the owner's choice, 2026-10-08 | decision |
| Kotlin 2.4.20, Compose Multiplatform 1.12.1, toolchain 25 from `io.github.youndie.sborka.settings` 0.5.0.113 | the stack the sibling repositories are held at until Kotlin 2.5.20 | shashki `settings.gradle.kts`, origin/main, 2026-10-08 |
| zavarnik 0.1.0 (Gradle Plugin Portal) on the `application` plugin; needs JDK 25 | a cold start fast enough for the stand | zavarnik README, origin/main `ba2d6e8`; how training reaches a database during the image build is a *hypothesis* for the research |
| kompot's server and client modules exist for jvm and wasmJs; `kompot-theme` is not used (the palette is compiled into the client) | the server builds trees, the browser renders them | kompot build files, origin/main `d35ae36` |
| PostgreSQL access: Exposed with `petich-postgres` (as the sibling services) or sqlx4k with `petich-sqlx4k-postgres` — both stores exist for jvm | the saga and the orders share one database | petich README module table, origin/main `7f711e6`; the choice is the research's |
| shildik `oidc-auth-server` checks tokens on the server; the browser sign-in flow on wasmJs | — | shildik build files, origin/main `18488c4`; the wasm client side is a *hypothesis* |
| Screen fixtures are the server's recorded bodies for §5a, rendered by the client's registry | the parity screenshot tests the tree and the renderers together | *hypothesis* |
| viddik for screenshot parity (`viddikDesignParity`) | client items are accepted against the canvas | *hypothesis* — version read at scaffold |
| Fonts bundled: Bodoni Moda (headlines, prices), Archivo (UI), JetBrains Mono (labels) | goldens and references must share families | the canvas loads all three from Google Fonts; licence read before bundling — *hypothesis* |
| Theme: Material 3 roles from the canvas palette — Cobalt `#2F2BFF`, Acid `#DFFF3A`, Hot `#FF3D2E`, Ink `#0F0F0F`, Paper `#F5F3EE`; the greys and the Deals red `#D9230F` need roles | the code maps every value to a role | canvas `Storefront.dc.html`, `Header.dc.html` |
| Search on PostgreSQL full text + `pg_trgm` | one store; the seed is thousands of products | *hypothesis* — suggest latency measured on the seed |
| Seed: a deterministic generated catalog (≈ 2,000 products, 32 categories, ≈ 40 sellers) plus the §5a fixtures | the canvas's counts are copy for fixtures | decision |
| Product images: placeholder tiles as on the canvas | the canvas marks photos as placeholders | decision |
| Delivery fee $5.99 below $35 for non-members; free over $35 or for Plus | the canvas says «Free delivery over $35» and shows no fee | the fee is a decision; the threshold is the canvas |
| Points: 1 per whole dollar, ×2 for Plus, credited on delivery, redeemed whole at checkout; 100 points = $1 | «2,480 — worth $24.80» and «Double points» on the canvas | the rate is the canvas; the cart copy becomes 1,024 (§10 Q2) |
| Haul Pay: 4 interest-free payments two weeks apart | checkout's «4 payments of $128» is the plan the canvas commits to at payment time | decision (§10 Q1) |
| Courier cut-off 23:30 local; deals end at local midnight | «Order within 3 h 40 min» and «04:12:37» at one fixed «now» | decision; at 19:47:23 the cut-off gives «3 h 42 min» |
| Documentation in English | the sibling reference projects document in English, and this one is read from outside | decision (§10 Q7) |
| No SEO, no server rendering | a Compose canvas page is not indexable | decision; first-load size measured, not assumed |

## 9. Backlog seeds

Ordered by dependency. Sizes S < 1 day, M 1–3 days, L > 3 days.

| Stage id | Stage | What it is |
|---|---|---|
| `stage-1-skeleton` | Skeleton | the repository builds, the docs check passes, the canvas is complete, the theme and the first components render |
| `stage-2-browse` | Browse | home, category and product screens from the server on the seed, against the canvas |
| `stage-3-search` | Search | search and autocomplete |
| `stage-4-cart` | Cart and sign-in | guest cart, sign-in, merge, cart screen |
| `stage-5-order` | Checkout and orders | placement, the saga, simulators, the order screen |
| `stage-6-account` | Account | overview, history, Saved, price drops, returns |
| `stage-7-reviews` | Reviews | reviews and questions |
| `stage-8-loyalty` | Loyalty | Plus, points, Haul Pay, recommendations |
| `stage-9-ship` | Ship | the end-to-end suite, the chart, observability, first-load numbers |

| # | Title | Priority | Size | Stage | Feature | Blocked by | Acceptance |
|---|---|---|---|---|---|---|---|
| 1 | design: canvas per the design briefs — static artboards, every state of §5, desktop and phone, the three disagreements resolved — **done 2026-10-08**: 125 static artboards in the Claude Design project «E-commerce витрина», `canvas/` | P1 | L | `stage-1-skeleton` | — | — | every `<Screen>_<State>` and `_Phone` of §5 exists as a static artboard with its height fixed; §5 Sizes updated |
| 2 | scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check | P1 | M | `stage-1-skeleton` | — | — | CI builds the server, the wasm bundle and the docs check green on an empty feature set |
| 3 | server: schema migrations, seed generator and §5a fixtures, probes, the image with a zavarnik cache | P1 | M | `stage-1-skeleton` | feature-browse | 2 | a fresh database is seeded deterministically (same hash twice); the image starts with its cache accepted |
| 4 | contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers | P1 | M | `stage-1-skeleton` | feature-browse | 2 | both components render in desktop screenshot tests from a recorded body; fonts embedded |
| 5 | server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees | P1 | L | `stage-2-browse` | feature-browse | 3, 4 | feature-browse and feature-product scenarios pass against PostgreSQL |
| 6 | design refs: `Home_*`, `Catalog_*`, `Product_*` PNGs | P1 | S | `stage-2-browse` | feature-browse | 1 | one PNG per artboard, sizes as in §5 |
| 7 | client: renderers for the Home and Catalog components; Loading / Error shells | P1 | L | `stage-2-browse` | feature-browse | 5, 6 | `viddikDesignParity` within tolerance for every `Home_*` and `Catalog_*` artboard |
| 8 | client: renderers for the Product components (without the dialogs) | P1 | L | `stage-2-browse` | feature-product | 5, 6 | parity for `Product_*` except the two dialogs |
| 9 | server: search, suggest, recent searches; suggest latency measured on the seed | P1 | M | `stage-3-search` | feature-search | 5 | feature-search scenarios pass; the latency number is in the research document |
| 10 | design refs + client: Search screen and `SearchSuggestPanel` | P1 | M | `stage-3-search` | feature-search | 1, 7, 9 | parity for every `Search_*` artboard |
| 11 | server: guests, cart, promo codes, changed lines, the Cart tree | P1 | M | `stage-4-cart` | feature-cart | 5 | feature-cart scenarios pass |
| 12 | server + client: shildik sign-in in the browser, customer creation, cart merge, header states | P1 | L | `stage-4-cart` | feature-identity | 4, 11 | feature-identity scenarios pass against a local shildik; a guest signs in and keeps the cart |
| 13 | design refs + client: Cart renderers | P1 | M | `stage-4-cart` | feature-cart | 1, 11, 12 | parity for every `Cart_*` artboard |
| 14 | server: checkout tree, slots with capacity, pickup points, quote, the address form | P1 | M | `stage-5-order` | feature-checkout | 12 | quote scenarios pass; a full slot is refused |
| 15 | design refs + client: Checkout renderers and the address form | P1 | L | `stage-5-order` | feature-checkout | 1, 13, 14 | parity for every `Checkout_*` artboard except PointsApplied |
| 16 | server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator | P1 | L | `stage-5-order` | feature-orders | 14 | feature-checkout and «declined card» pass; a saga killed mid-way finishes after a restart |
| 17 | server: fulfilment simulator, capture per shipment, pickup codes | P1 | M | `stage-5-order` | feature-orders | 16 | «charged when shipped» passes; an order reaches delivered on the fast clock |
| 18 | server + client: Order tree and renderers, reorder | P1 | M | `stage-5-order` | feature-orders | 1, 15, 17 | parity for `Order_*` except ReturnDialog and Returned |
| 19 | server + client: Account overview and orders history | P1 | M | `stage-6-account` | feature-account | 1, 18 | parity for every `Account_*` artboard |
| 20 | server + client: Saved list, save for later, price drops | P2 | M | `stage-6-account` | feature-account | 1, 13, 19 | parity for every `Saved_*`; the price-drop scenario passes |
| 21 | server + client: returns and refunds | P2 | M | `stage-6-account` | feature-orders | 18 | «late return» passes; parity for `Order_ReturnDialog`, `Order_Returned` |
| 22 | server + client: reviews and questions, the two dialog routes and forms | P2 | L | `stage-7-reviews` | feature-reviews | 8, 17 | feature-reviews scenarios pass; parity for the remaining `Product_*` |
| 23 | server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings | P2 | M | `stage-8-loyalty` | feature-membership | 15, 17, 19 | feature-membership and «points redeemed» pass; parity for `Home_PlusTrialDialog`, `Account_NotMember`, `Checkout_PointsApplied` |
| 24 | server + client: Haul Pay, 4 payments two weeks apart | P2 | M | `stage-8-loyalty` | feature-membership | 15, 16 | an instalment order shows its schedule; captures follow it on the simulator clock |
| 25 | server: recommendations from views in the Home tree | P2 | M | `stage-8-loyalty` | feature-recommendations | 7, 19 | feature-recommendations scenarios pass |
| 26 | e2e: the whole path over HTTP against the composed stack | P1 | M | `stage-9-ship` | — | 21, 22 | one run browses, buys, receives and returns, green in CI |
| 27 | ops: Helm chart, tracy / metrik / katcher wiring, the public demo stand | P2 | M | `stage-9-ship` | — | 3 | the chart installs on a clean namespace, the server turns ready, the stand answers at its public host |
| 28 | measure: first-load size and time of the wasm bundle | P2 | S | `stage-9-ship` | — | 7 | the numbers are in the research document with how they were taken |
| 29 | live order tracking through kompot-realtime | P3 | M | `stage-9-ship` | feature-orders | 18 | the order screen moves to «In transit» without a refresh |
| 30 | product images through object storage | P3 | M | `stage-9-ship` | feature-product | 8 | cards and the product page show stored images; the placeholder tiles remain the fallback |
| 31 | synthetic shoppers: a traffic generator that walks the e2e path continuously on the stand | P2 | M | `stage-9-ship` | — | 26, 27 | a stand under it shows sagas and orders in tracy and metrik (§10 Q9) |

## 10. Questions, decided by the product owner (2026-10-08)

- [x] **Q1. Haul Pay: which plan?** The canvas offered «12 × $29.08 / mo» on the product page and «4 payments of $128» at checkout. **4 interest-free payments, two weeks apart** — the plan the canvas shows at the moment of paying, and one that needs no credit check to be believable. The product page reads «or 4 payments of $87.25 with Haul Pay».
- [x] **Q2. Points for a Plus member.** **Double points stay** — they are one of the four benefits the Plus block sells. The cart's copy for Maya becomes «You'll earn 1,024 points».
- [x] **Q3. The Plus upsell shown to a member.** **A member never sees the upsell.** Maya's home shows «You saved $186 on delivery this year · renews Nov 2»; the trial offer is for guests and non-members (`Home_Guest`, `Home_PlusTrialDialog`).
- [x] **Q4. Redeeming points.** **In v1.** The account tile promises «Worth $24.80 on your next order», and a promise the checkout cannot keep is worse than no tile. One toggle in checkout's payment section, «Use 2,480 points (−$24.80)», all or nothing; new state `Checkout_PointsApplied`.
- [x] **Q5. Artboard heights.** **Full-page artboards at both widths**, the height fixed by the designer per state and written next to the artboard; §5 Sizes take those numbers before the first reference PNG.
- [x] **Q6. Out-of-scope menu items.** **Hidden in v1**: Returns list, My reviews, Addresses, Payment methods, Settings. Returns themselves are in v1 — from the order page.
- [x] **Q7. Documentation language.** **English**, as the sibling reference projects; the product is read from outside.
- [x] **Q8. A public demo stand.** **Yes** — an open reference nobody can click through sells nothing. On the owner's domain; the host is picked in §9 item 27.
- [x] **Q9. Synthetic traffic.** **Keep, raised to P2** (§9 item 31). Without it the stand shows the libraries idle, and nothing about petich, tracy or metrik can be measured on it.
