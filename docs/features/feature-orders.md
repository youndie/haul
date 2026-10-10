---
id: feature-orders
title: Order lifecycle, tracking and returns
type: feature
status: active
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-order
  - screen-account
api:
  - endpoint-orders
tags: []
---

# Order lifecycle, tracking and returns

## 1. Overview

After placement a saga reserves stock and authorises payment; a fulfilment simulator then moves each seller's shipment along. The order page shows it; a delivered order can be returned and reordered.

> Described as built: placement's saga and the payment simulator (B-16), the fulfilment simulator,
> capture per shipment and pickup codes (B-17), the order page and reorder (B-18), the orders' history
> (B-19, drawn by [feature-account](feature-account.md)), returns and refunds (B-21), the order's own copy
> of its address (B-40), the points an order earns and spends (B-23, [feature-membership](feature-membership.md)),
> Haul Pay's plan in place of capture per shipment (B-24), the listing name an order line keeps (B-45), the
> card amount and the points back of a points-paid return (B-50, B-55), and the page that moves by itself —
> live tracking through kompot-realtime (B-29), placement's own moves included (B-56).

## 2. Business rules

* the saga (petich, `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt`) runs inside the placement request, its members in this order:
  1. `reserve-stock` — every line's units off its `Sku`, all or nothing, held by the order; a shortage refuses with `out_of_stock`;
  2. `reserve-slot` — a place in the courier's window (`DeliverySlots.reserve`); a full window refuses with `slot_unavailable`; a pickup takes nothing;
  3. `redeem-points` — the points the checkout's toggle took, off the customer's balance under a lock; a balance spent meanwhile refuses with `cart_changed` (B-23, [feature-membership](feature-membership.md));
  4. `open-order` — the order `placing`, its lines, one shipment per seller, and **its own copy of the delivery address** (`orders.address`, B-40): editing the saved address later never moves a placed order;
  5. `authorise-payment` — the total, by card or Haul Pay; nothing for pay on delivery; a decline cancels the order with `payment_declined` and refuses;
  6. `confirm` — `placed`;
  7. the announcement `clear-cart` — the bought lines leave the cart (a failure there does not take the order back);
* a refusal or a failure compensates in reverse — void the authorisation, cancel the order and its shipments, give the redeemed points back (a `returned` ledger row), release the window, release the stock — each by the order's name and safe when its step never ran; an order undone by a failure ends `cancelled` with `failed`;
* every member is idempotent: a saga left `PROCESSING` or `COMPENSATING` by a process that died is carried on by the sweeper the application starts (stuck after 60 s, `STUCK_AFTER` in `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/SagaEngine.kt`), and nothing is taken twice; the saga's clock is a `PetichClock` apart from the store's «now»;
* an order's own status is the saga's: `placing`, `placed`, `cancelled` with `cancel_reason`;
* the simulated card processor (`server/src/main/kotlin/io/github/youndie/haul/feature/payment/`) declines the test card ending `0002` and approves the others (§5a);
* the fulfilment simulator (`server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/FulfilmentSimulator.kt`) moves each shipment of a `placed` order through every step that has come due — `placed → packed → in_transit → delivered` for a courier, `placed → packed → in_transit → ready_for_pickup → picked_up` for a point or a locker — and stamps each step at the moment it came due (`shipment_events`), so a pass after a pause writes the history an unbroken run would; every move is conditional on the status it leaves, so two passes at once move a shipment once;
* the store's pace (`FulfilmentPace.STORE` in `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/Fulfilment.kt`, research D4): packed 4 h after the seller sees the order, on the road 20 h later plus a day per dispatch day (the most of the shipment's products'), delivered or ready for pickup a day later, collected two days after that; `HAUL_FULFILMENT_SPEED` runs it that many times quicker (haul-server, section 7); the simulator's clock is the saga's `PetichClock`;
* **capture per shipment** — «your card is charged when the order ships»: the shipment's share is captured from the order's authorisation **before** it moves to `in_transit`, keyed by the shipment id, so a pass that dies between the capture and the move charges once; the share is the total in proportion to each seller's lines at the price paid, the last share taking the rounding, so the parts add up to the cent (`ShipmentShares`); captures never add up to more than the authorisation, nothing is captured without one, and an authorisation part of which was captured is no longer voided (`PaymentProcessor.capture` in `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`); a refused capture holds that shipment `packed` while the others move; pay on delivery captures nothing; **Haul Pay is not captured per shipment**: its plan's four payments are captures out of the same authorisation, keyed `instalment:<order>:<n>`, the first taken when the first shipment ships, and a Haul Pay shipment waits for that first payment as a card shipment waits for its capture (B-24, [feature-membership](feature-membership.md));
* a pickup shipment gets a 4-digit pickup code when it becomes `ready_for_pickup` — an HMAC-SHA256 of the shipment id keyed by the order's saga id, so it cannot be derived from what the page shows — and is held 5 days from then; the code and the day it is held until are shown only while it waits, and only to the order's customer;
* the order's progress (`OrderProgress` in `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/OrderTracking.kt`) is the saga's while `placing` or `cancelled`, `returning` while a return is in flight and `returned` once it is refunded, else the least advanced shipment's status; `OrderTracking.track` answers only the order's own customer;
* the order page is `/account/orders/{id}` (its tree `GET /ui/account/orders/{id}`), where placement lands; another customer's order and a missing one are both `404 order_not_found` — nobody learns that an order number exists;
* a shipment's day on the page is the earliest courier day for the placing time plus its seller's dispatch days (a pickup point a day later), or the chosen window's day when that is later; the card line follows what has been captured; a courier order's address is the order's own copy;
* **reorder** (`POST /api/v1/me/orders/{id}/reorder`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt`) sets each of the order's SKUs in the cart through the cart's own command, selected, at the order's quantity capped at ten and at the stock; a line already holding that many is only selected, so a second press adds nothing; a SKU gone or out of stock is **left out silently** (no artboard draws a message — decided as product owner, B-18); it answers `navigate /cart`;
* «Write a review» on a delivered line presents the review dialog ([feature-reviews](feature-reviews.md));
* **returns** (`POST /api/v1/me/orders/{id}/returns`, `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`, research D4 «Decided in B-21»): one return per order, of whole lines ticked one by one, with a reason (Doesn’t fit, Not as described, Arrived damaged, Changed my mind); each line is returnable for **30 store days from its own shipment's arrival**, the last day in full, on the saga's `PetichClock`; a line gives back its share of the items as paid — the sale and promo-code discount shared over the lines in proportion to the price paid — and delivery is not refunded;
* **the refund happens when the parcel is back**, not when the return is asked for: the simulator collects a return a day after it is asked (`picked_up`) and refunds a day later (`refunded`), at the fulfilment pace; the refund is taken before the move to `refunded`, keyed `refund:<order>`, so a pass that dies between the two pays back once; refunds never add up to more than the order's captures; a refused refund holds the return `picked_up`; pay on delivery is paid back by the courier, outside the processor;
* the points move with the refund (B-23): what the returned lines earned is taken back (`reversed`, the order's points in proportion to the refund, drawn on the page as «−N points»), and the returned lines' share of the points the order was paid with comes back as points (`returned`), not as money;
* **each line has its own share of the points paid** (`ReturnLine.pointsBack`, `ReturnRefunds.pointsBack` in `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`, B-50): its exact part rounded down, the few points left over given one each to the lines whose share was cut, from the last line back — so the shares add up to every point redeemed and none passes its line's refund; a return's points are the sum of its lines' shares, and its money is the lines' value less those shares (`ReturnRefunds.moneyBack`, which the refund pays and the page names, B-55);
* **the return dialog** names what the ticked lines give back: for an order paid partly with points the card amount, Σ(`refundCents − pointsBack`), and beneath it «+ N points back» (`ReturnForm.pointsBack`, sent only when the order redeemed points); a Haul Pay order reads «Refund $x to your Haul Pay plan» with the same points line — the plan takes it off what is still owed first, the processor refunds the rest; an order paid without points reads as before;
* the order page offers «Return items» while some line is inside its window, then «Returns closed on …»; a return in flight is drawn «Return requested» / «Return picked up», a finished one «Return refunded» with the returned lines in a «Returned items» card, «Refunded −$x» and «Paid»;
* **the page names the money the card gets back** (B-55): for a points-paid order the return's heading, the payment fact and «Refunded −$x» use `moneyBack`, with «· + N points back» beside the heading and the fact («$155.10 goes back to your card ···· 4821 once the seller has it · + 790 points back», then «$155.10 is back on your card …», «$155.10 refunded Oct 12 · + 790 points back»); «Paid» is the total less that money, so the summary rows add up; a Haul Pay order reads «goes back to your Haul Pay plan» / «is back on your Haul Pay plan»; an order paid without points reads as before;
* **an order line keeps the listing name it was bought under** (B-45): placement copies the product's listing name — its title unless it has one — into `order_lines.title`, so renaming the product later moves no past order;
* **the page moves by itself** (B-29, research consequence 3): `GET /ui/account/orders/{id}` answers kompot's `KompotScreenResponse` naming the channel `order:<id>`, and `GET /ui/updates?topic=order:<id>` streams it as server-sent events in the customer tier — a topic that is not an order's and another customer's order are `404 order_not_found` before the stream opens, no token is `401`; the broadcaster delivers by the order **and its customer**, so a listener hears only its own customer's order; every stream opens with the order as it is now, then one frame per move (`UpdateComponentMessage("order", body)`), a `ping` every 20 s; the moves told are the fulfilment simulator's, the return simulator's, Haul Pay's payments and the placement saga's last steps — `confirm` (placed) and `open-order`'s compensation (cancelled, by a declined card, a failure or the sweeper), each told after its transaction (B-56); the browser reconnects after 1 s, doubling to 30 s, and stops only on `401`/`404`, keeping what it drew; a subscriber that falls behind gets only the latest body; the bus is in memory, so the release runs one replica (`charts/haul/values.yaml`).

Numbers in these rules (fees, thresholds, limits, the 30 days) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D4 and D6–D7, and checked against the code
and the tests below.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/OrderComponents.kt` — the Order tree's components (`OrderBody`, `ReturnForm`); `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/returns/ReturnCommands.kt` — `ReturnEntry` and the rules both sides hold the form to; placement's request is in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` — placement, the saga, the order's storage, the page (`screen/OrderScreen.kt`), reorder and the live page (`server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/OrderMoves.kt`); `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` — the payment simulator, capture and refund; `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/` — the fulfilment simulator, its runner, pickup codes, `OrderTracking`; `server/src/main/kotlin/io/github/youndie/haul/feature/returns/` — the return, its simulator and refunds; `server/src/main/resources/db/migration/V11__fulfilment.sql`, `server/src/main/resources/db/migration/V14__order_address.sql`, `server/src/main/resources/db/migration/V17__returns.sql` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` — the page, its not-found page and the return dialog; the live stream `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/LiveUpdates.kt` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` — the orders' history |

## 5. Scenarios (BDD / test cases)

### Scenario: Declined card cancels and releases
* **Given:** a `Sku` with stock 3 and an order of 1 paid with the card ending 0002
* **When:** the saga runs
* **Then:** the order is `cancelled` with `payment_declined` and the stock is 3 again.
* **And:** its shipments are cancelled, the window's place is given back, the authorisation is recorded declined, the cart is as it was, and placement still answers `202` to the order.
* **Automated:** `PlacementRoutesTest.a declined card cancels the order and gives everything back` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`)

### Scenario: A saga killed mid-way finishes after a restart
* **Given:** a placement whose process dies inside the card processor's call, leaving the order `placing` and the saga `PROCESSING`
* **When:** a new application starts on the same database and its sweeper finds the saga stuck
* **Then:** the order is placed, nothing is taken twice, and the cart is cleared.
* **Automated:** `PlacementRestartTest.a placement killed inside the payment is finished by the next process`

### Scenario: A failure after the payment is undone in reverse
* **Given:** a member after `authorise-payment` fails
* **Then:** the authorisation is voided, the order cancelled, the window and the stock released.
* **Automated:** `OrderSagaTest.a failure after the payment undoes every member in reverse` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/OrderSagaTest.kt`)

### Scenario: Charged when shipped
* **Given:** an order with two shipments
* **When:** the first reaches `in_transit`
* **Then:** exactly that shipment's amount is captured and the second is still only authorised.
* **And:** nothing is captured at placement or packing; Sony's $349.00 is captured when it ships, then Brooklyn Home Co.'s $163.00, the two summing to the $512.00 authorised.
* **Automated:** `ChargedWhenShippedTest.charged when shipped` (`server/src/test/kotlin/io/github/youndie/haul/feature/fulfilment/ChargedWhenShippedTest.kt`); a death between capture and move, a refused capture, two passes at once and pay on delivery in the same file

### Scenario: An order reaches delivered on the fast clock
* **Given:** Maya's order by courier and the simulator at a day a minute (speed 1440)
* **When:** the clock moves on
* **Then:** the order goes placed → packed → in transit → delivered, the store's three days in at most four minutes.
* **Automated:** `ShipmentLifecycleTest.an order reaches delivered on the fast clock` (`server/src/test/kotlin/io/github/youndie/haul/feature/fulfilment/ShipmentLifecycleTest.kt`); the application doing it by itself, `FulfilmentRunnerTest.the application delivers an order by itself when fulfilment runs`; the server's image by itself at speed 86400, `WholePathTest.a shopper browses buys receives and returns an order over HTTP` (`e2e/src/test/kotlin/io/github/youndie/haul/e2e/WholePathTest.kt`)

### Scenario: The order page is its customer's alone
* **Given:** Maya's order #HL-48302
* **When:** Sam opens `/account/orders/HL-48302`, and anyone opens a number that does not exist
* **Then:** both are answered `404 order_not_found`, the same way; Sam's reorder of it is `404` and leaves his cart empty.
* **Automated:** `OrderRoutesTest.another customer's order and a missing one get the same answer` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/OrderRoutesTest.kt`)

### Scenario: Reorder twice is once
* **Given:** a delivered order of two SKUs
* **When:** the customer presses «Reorder» twice
* **Then:** each SKU is in the cart, selected, at the order's quantity — not twice it — and the answer is `navigate /cart`.
* **Automated:** `OrderRoutesTest.reorder puts a delivered order back into the cart and twice is once`

### Scenario: A past order keeps its address
* **Given:** an order placed by courier to «4F»
* **When:** the customer saves «5B» in the checkout's address form
* **Then:** the order page still reads «4F».
* **Automated:** `OrderRoutesTest.an address edited after placement does not move a past order`; `PlacementRoutesTest.an order keeps the address it was placed to when the saved address is edited`

### Scenario: A pickup code only its customer sees
* **Given:** Maya's order to a pickup point
* **When:** its shipment is ready for pickup
* **Then:** her order shows that shipment's 4-digit code and the day it is held until (5 days on), while the order's progress is still its other shipment's «in transit»; Sam is answered no order; once collected the code is no longer shown.
* **Automated:** `ShipmentLifecycleTest.a pickup shipment waits with a code only its customer sees`; a locker, `ShipmentLifecycleTest.a locker shipment is collected not delivered`

### Scenario: Late return
* **Given:** a shipment delivered 31 days ago
* **When:** the customer requests a return
* **Then:** the server returns `422` with `return_window_closed`, and nothing is written.
* **Automated:** `ReturnRoutesTest.late return` (`server/src/test/kotlin/io/github/youndie/haul/feature/returns/ReturnRoutesTest.kt`); each line to the last day of its own window, `ReturnRoutesTest.each line is returnable to the last day of its own window`

### Scenario: Refunded when the parcel is back
* **Given:** a delivered order and a return of one line
* **When:** the simulator collects it a day later and the seller has it back a day after that
* **Then:** the order reads «Return requested», then «Return picked up», then «Return refunded» with «Refunded −$x»; the card gets the line's price as paid, once, and only at the last step.
* **Automated:** `ReturnRoutesTest.a returned line is refunded when the seller has it back`; `ReturnLifecycleTest.a return is collected a day after it is asked for and refunded a day later`, `ReturnLifecycleTest.a pass that died between the refund and the move does not refund twice` (`server/src/test/kotlin/io/github/youndie/haul/feature/returns/ReturnLifecycleTest.kt`); over HTTP against the image, `WholePathTest.a shopper browses buys receives and returns an order over HTTP`

### Scenario: A points-paid return gives back card and points as the dialog said
* **Given:** Maya's order paid with 2,480 points, delivered
* **When:** she ticks lines in the return dialog, then returns the duvet cover and the mugs
* **Then:** each tickable set reads the card amount and «+ N points back» the refund then gives; the card gets $155.10 and 790 points come back; the page names $155.10 and «+ 790 points back» at requested, picked up and refunded, and «Refunded» and «Paid» add up to it.
* **Automated:** `ReturnRoutesTest.a points-paid order's dialog says what the card and the points get back for any lines ticked`, `ReturnRoutesTest.a points-paid order's page names what the card and the points get back at every step of its return`; the shares, `ReturnRefundsTest.the points paid with are shared over the lines and a set gives back the sum of its shares`, `ReturnRefundsTest.a line's points share is its exact part rounded and never more than its refund` (`server/src/test/kotlin/io/github/youndie/haul/feature/returns/ReturnRefundsTest.kt`); the client, `ReturnWiringTest.ticking lines updates the card amount and the points back` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/order/ReturnWiringTest.kt`)

### Scenario: The order page moves by itself
* **Given:** Maya on her order's page
* **When:** the simulator ships it
* **Then:** the page redraws «In transit» without loading again; Sam, a topic that is no order's and a request with no token are refused before any stream opens.
* **Automated:** `LiveOrderTest.the order page hears its order go in transit without loading again`, `LiveOrderTest.a move reaches the subscribers of that order and of no other` (`server/src/test/kotlin/io/github/youndie/haul/feature/order/LiveOrderTest.kt`); the client, `LiveOrderPageTest.a pushed update redraws the order page in transit without loading it again`, `LiveOrderPageTest.the order page stops listening when it is left` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/order/LiveOrderPageTest.kt`), the reconnect, `LiveUpdatesTest.the wait between attempts doubles from a second to thirty`, `LiveUpdatesTest.a refused stream is not asked for again` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/shell/LiveUpdatesTest.kt`); over HTTP against the image, the refund pushed, `WholePathTest.a shopper browses buys receives and returns an order over HTTP`

### Scenario: A page opened while placing hears where placement left the order
* **Given:** a page subscribed while the order is still `placing`
* **When:** the card is approved, or declined, or the process dies and the next one's sweeper rolls the placement back
* **Then:** the page hears «placed» with the points fact, or «Order cancelled» with the declined notice.
* **Automated:** `LiveOrderTest.a page that opened while the order was placing hears it placed`, `LiveOrderTest.a page that opened while the order was placing hears a declined card cancel it`, `LiveOrderTest.a page open on an order a dead process left placing hears the sweeper cancel it`

### Scenario: A return refused
* **Given:** an order with a line not yet arrived, an order already returned, another customer's order
* **When:** a return is asked for each
* **Then:** `422 not_delivered`, `409 already_returned`, `404 order_not_found`; a form at fault is `400 validation_failed` naming every field.
* **Automated:** `ReturnRoutesTest.a return is refused for what has not arrived and what is not yours`, `ReturnRoutesTest.a return form is refused with every field at fault`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.

## 7. Quirks

* The simulator's pace does not aim at the courier window the shopper chose or the day checkout
  promised: at speed 1 an order can arrive before or after its window. The order page reads the window
  from the order, not from the simulator (B-17's findings).
* A shipment whose capture was refused and later taken is stamped `in_transit` at its due time, not
  when it actually shipped; only the log says it was held.
* The chart passes no `HAUL_FULFILMENT_SPEED`, so a release runs at the store's pace (B-27).
* Research §6's sample orders (#HL-48211 in transit, #HL-47960 ready with code 4821, the delivered
  and returned ones) are not seeded — their products and sellers are not in the seed, and the simulator
  would move a seeded order on the stand: they are fixtures (`server/src/test/kotlin/io/github/youndie/haul/testing/SampleOrders.kt`).
* A line placed before V25 keeps the title it was bought under, which was its listing name then; no line
  was backfilled.
* A multi-line partial return can give back fewer points than B-23's per-return rule did, by fewer points
  than lines (the duvet cover and the mugs: 790, was 789) — the shares are per line since B-50.
* A fully points-paid order's dialog reads «Refund $0.00 to card» plus the points line; no special case.
* The copy of a partly returned order, of a return in flight, of «Returns closed on …» and the reason list
  are the server's: the canvas draws only a fully refunded order.
* The live page is proven by its parts — the server's stream over a real port, the client's provider on
  the desktop, the e2e listening from placement — not by a browser drawing it; SSE behind the stand's
  proxies is unverified (B-29's findings).
* The page draws a `placing` order exactly as a `placed` one except the points fact, so a page opened
  during placement misses only that fact or a cancellation until B-56's tells reach it.
* One replica only: a second needs kompot's Redis bus; during a rolling deploy the pages catch up on
  reconnect, because every stream starts with the order as it is.
* Two shipments of one order captured at the same instant by two processes are serialised by the
  authorisation's row lock, which no test exercises.
