# Stand-only — `TC-STAND`

What only a running stand has: the release deployed behind its host and its provider, the synthetic shoppers
walking it, and what tracy and the server's log show of them. Walked by the operator after each deploy and once
the shoppers have walked for an hour. No case here asks for the cluster itself: the stand is read through its
host, its tracy and its logs.

### TC-STAND-01 — A release is up behind its host and signs in

- **Who:** operator; Maya
- **Width:** 1440
- **Preconditions:** a release tag deployed from the infrastructure repository.
- **Steps:**
  1. Open `https://haul.kotlin.website/` in a fresh profile.
  2. Open a product, add it, open the cart.
  3. Sign in as Maya.
  4. Read the release in the page's network answers or the deploy record.
- **Expected:**
  - Step 1: the static frame, then home with today's deals; no certificate warning.
  - Step 2: the line in the cart.
  - Step 3: the popup goes to `haul-id.kotlin.website` and back; her name, her cart.
  - Step 4: the image is the tag's, the one `stand.yaml` published after its e2e passed.
- **Decided in:** B-27
- **Coverage:** script `scripts/image-check.sh`; script `scripts/e2e.sh`; script `scripts/stand-kind.sh`;
  `ProbesTest` «readiness answers 200 while the database answers»;
  manual: the public host, TLS, Traefik and the stand's own provider exist only on the stand

### TC-STAND-02 — The synthetic shoppers walk the whole path and leave the stock as they found it

- **Who:** operator
- **Width:** n/a
- **Preconditions:** the shoppers enabled; an hour since the deploy (one walk per ten minutes, each about twenty).
- **Steps:**
  1. Read the shoppers' log.
  2. Sign in as Sam and open his history.
  3. Note the stock line of a product the walks bought (in stock / out), an hour apart.
- **Expected:**
  - Step 1: a line per walk — a product, sign-in, a courier order placed, delivered, a line returned and refunded;
    walks that outlive the five-minute token renew it and reach the refund.
  - Step 2: an order per walk, each returned.
  - Step 3: the walks leave no product sold out by themselves: each walk's unit comes back with its refund.
- **Decided in:** B-31, B-88
- **Coverage:** `SyntheticShopperTest` «two overlapping walks of one customer each place receive and return an order»;
  `SyntheticShopperTest` «a walk whose bearer expires while it waits renews it and reaches the refund»;
  `ReturnRestocksTest` «an order returned whole leaves the stock as it was before it was placed»;
  `WholePathTest` «a shopper browses buys receives and returns an order over HTTP»;
  script `scripts/shoppers-check.sh`;
  manual: the stand's own pace (288×, a walk every ten minutes) over an hour is only on the stand

### TC-STAND-03 — Every tenth walk pays with the declining card, and nobody else can

- **Who:** operator; Sam
- **Width:** n/a
- **Preconditions:** the shoppers with `declineEvery: 10` (the chart's default); at least ten walks done.
- **Steps:**
  1. Find the tenth walk's line in the shoppers' log.
  2. Open that order as Sam.
  3. Open Sam's checkout.
- **Expected:**
  - Step 1: «… paid with the declining card and refused …, its lines back in the cart and removed».
  - Step 2: cancelled, «Your card ···· 0002 was declined» (TC-ORD-10).
  - Step 3: the card ···· 4821 chosen, ···· 0002 not listed.
- **Decided in:** B-89
- **Coverage:** `SyntheticShopperTest` «a walk that pays with the declining card sees the order refused and the stock released»;
  `ShoppersConfigTest` «declining walks need the test card's key and none need none»;
  `CheckoutRoutesTest` «the test card is chosen only with the stand's key»; script `scripts/chart-check.sh`;
  manual: the key derived from the stand's master keys exists only in the stand's release

### TC-STAND-04 — An order's saga is one list in tracy

- **Who:** operator
- **Width:** n/a
- **Preconditions:** the stand's tracy; a shoppers' order delivered and refunded, and a declined one.
- **Steps:**
  1. `get_entity orderId=<the delivered order>`.
  2. `get_entity orderId=<the declined order>`.
  3. `top_templates` for `haul-server`.
- **Expected:**
  - Step 1: the placement's trace (`POST /api/v1/orders` 202) with its seven `saga …` spans and «order saga step
    done» lines; the shipments `placed`, `packed`, captured, `in_transit`, `delivered` (the capture before
    `in_transit`); the return requested, `picked_up`, refunded, `refunded`.
  - Step 2: «order saga step refused» for `authorise-payment` with `payment_declined`, then «order saga step undone»
    for `open-order`, `redeem-points`, `reserve-slot`, `reserve-stock`.
  - Step 3: the order lines among the templates.
- **Decided in:** B-89, B-109
- **Coverage:** `OrderInTracyTest` «placement then delivery then a return are filed under the order as they happened»;
  `OrderInTracyTest` «a declined card's refusal and every member it undid are filed under the order»;
  `OrderInTracyTest` «inside the placement request each member is a span of the request's trace»;
  `OrderInTracyTest` «a placement trace the agent drops still leaves the order's reference»;
  manual: the stand's tracy, its sample rate and its template windows; `top_templates` was never seen filled locally

### TC-STAND-05 — A shopper leaving an order page is not an error

- **Who:** operator
- **Width:** n/a
- **Preconditions:** an hour of shoppers' walks (each leaves an order page).
- **Steps:**
  1. Read the server's log (the pod's, and tracy's from `warn` up) and katcher for the hour.
- **Expected:**
  - Step 1: no `ERROR … unhandled: GET /ui/updates` and no broken pipe reported; a stream that fails while its
    client listens is still reported. katcher holds no report of a client's request — a link whose query does not
    decode, an address that is not there — and none of a `503`: a database outage is one `WARN` with its cause in
    tracy, not a report or an `ERROR` per page (B-148).
- **Decided in:** B-110, B-148
- **Coverage:** `ErrorAnswersTest` «a client that leaves a stream is not reported whichever write meets the closed connection»;
  `ErrorAnswersTest` «a stream that fails while its client listens is still reported»;
  `ObservabilityTest` «a 500 is filed in katcher»;
  `ObservabilityTest` «a client's malformed link and an unknown address file nothing in katcher»;
  `ObservabilityTest` «a database outage answered 503 to every page files nothing and logs no stack per page»;
  `ObservabilityTest` «a warning logged at start-up is shipped to tracy»;
  manual: the stand's pod log, its tracy and its katcher

### TC-STAND-06 — The stand keeps the sale and its code moving day after day

- **Who:** operator; guest
- **Width:** 1440
- **Preconditions:** a stand that has run more than a week without a restart.
- **Steps:**
  1. Open home; read the hero's dates.
  2. Apply `AUTUMN10` to a cart.
  3. Apply `SUMMER5`.
- **Expected:**
  - Step 1: the sale's week starts today; it never reaches its eighth day.
  - Step 2: applied.
  - Step 3: «This code has expired» — it names no campaign and keeps the canvas's window.
- **Decided in:** B-58, B-61, B-70
- **Coverage:** `SaleCalendarTest` «a running stand moves its sale at each midnight»;
  `SaleCalendarTest` «a stand seeded on another day takes the sale's code during its sale and refuses it after»;
  manual: a week of a real clock on the stand

### TC-STAND-07 — A link to the stand previews as the demo, and crawlers are let in

- **Who:** operator
- **Width:** n/a
- **Preconditions:** the release on the stand.
- **Steps:**
  1. `curl -s https://haul.kotlin.website/p/p-sony-wh-1000xm6 | grep -E '<title>|<meta (name|property)="(description|og:|twitter:)'`.
  2. Paste `https://haul.kotlin.website/` into an Open Graph validator (or a chat that unfurls links).
  3. `curl -si https://haul.kotlin.website/robots.txt`.
- **Expected:**
  - Step 1: the title «Haul — a demo store built end to end in Kotlin», a description that names Kotlin, Ktor, kompot
    and Compose Multiplatform, `og:type` `website`, `og:url` the stand's root, `og:image`
    `https://haul.kotlin.website/preview.png` at 1200 × 630, and a `summary_large_image` Twitter card — the same at
    every storefront address.
  - Step 2: the card shows the title, the description and the home page's top: the header, the sale and two banners.
  - Step 3: `200`, `text/plain`, `User-agent: *` and `Allow: /`.
- **Decided in:** B-161
- **Coverage:** `PagePreviewTest` «the page says what the demo is, under the home's title»;
  `PagePreviewTest` «the preview's picture is a file the store serves, at the size it announces»;
  `PagePreviewTest` «a storefront address carries the same preview»;
  `PagePreviewTest` «robots txt lets every crawler fetch everything»; script `scripts/image-check.sh`;
  manual: how a validator or a chat draws the card is theirs, and only the public stand can be fetched by them

### TC-STAND-08 — Demo shoppers older than a day are gone, and so is their way in

- **Who:** operator
- **Width:** n/a
- **Preconditions:** a demo shopper made on the stand more than a day and an hour ago, with an order, returned in
  part; its tab still open (the tokens in its session storage); a demo shopper made within the day.
- **Steps:**
  1. Read the server's log for the hour.
  2. In the old shopper's tab, reload any page.
  3. In the new shopper's tab, open «Orders».
- **Expected:**
  - Step 1: «demo shoppers swept: N» once an hour with N ≥ 1, and no warning that one was not turned off.
  - Step 2: the tab is a guest again, with an empty cart: the store refuses the token and shildik its renewal.
  - Step 3: the new shopper's orders are as they left them.
- **Decided in:** B-162
- **Coverage:** `server:DemoShopperRoutesTest` «a demo shopper older than a day is swept with everything they left,
  and their sign-in ends at both ends»;
  `server:DemoShopperRoutesTest` «a demo shopper whose order is still being placed is left for the next sweep»;
  `IdentityTest` «a token that cannot be renewed signs the customer out, back to the guest»;
  script `scripts/chart-check.sh`;
  manual: the stand's own day and its realm
