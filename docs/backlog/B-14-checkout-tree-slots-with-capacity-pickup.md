---
id: B-14
title: "server: checkout tree, slots with capacity, pickup points, quote, the address form"
status: done
priority: P1
size: M
stage: stage-5-order
epic: feature-checkout
blocked_by: [B-12]
---

# B-14 — server: checkout tree, slots with capacity, pickup points, quote, the address form

Checkout gathers delivery method, address or point, slot and payment into one quote the placement trusts.

Feature: `feature-checkout` — its scenarios are this item's acceptance where it names them.

- Not covered: placing the order (B-16).

- AC: quote scenarios pass; a full slot is refused.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt`, `server/src/main/resources/db/migration/V9__checkout.sql`.

## Done (2026-10-08)

- **The quote** (`server/.../feature/checkout/domain/`, research D5 «Decided in B-14»): the cart's
  counted lines — selected, in stock, unchanged — through B-11's `CartCommands.priced` and `Totals.of`,
  with the method, address or point, window and way to pay (`CheckoutCommands.state`, `Quote`). Its
  fingerprint (`Quote.fingerprint`, a SHA-256 over every value, the total included) travels in the tree
  as `CheckoutSummary.quote`; placement (B-16) calls `CheckoutCommands.quote` again and places only that
  fingerprint. `Quote.complete`: courier needs an address and a window, a point or a locker the point.
  A promo code that expired after it was applied is not honoured and the tree says so.
- **Windows with capacity** (`domain/Slots.kt`, `data/ExposedDeliverySlots.kt`): five days from tomorrow
  × 09:00 / 12:00 / 15:00 / 18:00, three hours, 20 orders each; a full window is drawn unavailable and
  choosing it is `409 slot_unavailable`. **The place is taken at placement, not held at quote time**
  (feature-checkout's «Slot filled meanwhile» needs the window to fill between page and order): a
  window that filled after it was chosen is cleared and the shopper told (`Checkout_PlaceError`).
  `DeliverySlots.reserve(slot, holder)` — one conditional update, idempotent per holder — and `release`
  are built and raced here for B-16's saga to call.
- **Pickup points and lockers**: research §6's two points and the «Wythe & N 7th» locker, seeded; a
  point decides the method. **Ways to pay**: the simulator's cards ···· 4821 and ···· 0002 for every
  customer (v1 has no card form), Haul Pay for totals from $50 to $2,000, pay on delivery except to a
  locker (`422 payment_method_not_allowed`); a method that no longer allows the choice falls back to the
  card.
- **The address form**: research §5's fields, street, city and a five-digit ZIP required
  (`domain/AddressRules.kt`). A form at fault is `400 validation_failed` with every field at fault in
  `ErrorBody.fields` (`FieldError`: `field_required`, `field_invalid`), and is stored and drawn again
  with the values and an error under each field (`Checkout_Validation`); a valid one becomes the
  checkout's address.
- **Routes** (`CheckoutRouting.kt`, the paths in `CheckoutPaths`, customer tier): `GET /ui/checkout`
  (`401 unauthenticated`, `409 cart_empty`); `PUT /api/v1/me/checkout` with `CheckoutChoice` (`refresh`;
  `400 validation_failed`, `404 slot_not_found` / `pickup_point_not_found` / `address_not_found` — another
  customer's included, `409 slot_unavailable` / `cart_empty`, `422 payment_method_not_allowed`);
  `POST /api/v1/me/addresses` with `AddressEntry` (`refresh`; `400 validation_failed` with `fields`).
- **Wire** (`shared/`): `feature/checkout/CheckoutCommands.kt` (`DeliveryMethod`, `CheckoutChoice`,
  `AddressEntry`); `ui/CheckoutComponents.kt` — `haul_checkout_header`, `haul_checkout_notice`,
  `haul_delivery_methods`, `haul_checkout_address`, `haul_delivery_slots`, `haul_pickup_points`,
  `haul_payment_methods`, `haul_checkout_summary` (no renderers: B-15); `ErrorBody.fields`, `FieldError`
  and eight `ErrorCode`s (`cart_empty`, `slot_unavailable`, `slot_not_found`, `pickup_point_not_found`,
  `address_not_found`, `payment_method_not_allowed`, `field_required`, `field_invalid`).
- **The tree** (`screen/CheckoutScreen.kt`): `CheckoutHeader` instead of the store's frame, the notices,
  `PageTitle`, `DeliveryMethods`, then `CheckoutAddress` and `DeliverySlots` by courier or `PickupPoints`
  otherwise, `PaymentMethods`, `CheckoutSummary`. Content, PickupPoint, ParcelLocker, Validation and
  PlaceError are this one tree; PointsApplied's toggle (`CheckoutSummary.redeem`) stays empty until B-23.
- **Storage** (`V9__checkout.sql`): `addresses`, `pickup_points`, `delivery_slots` (a row from the first
  place taken, `taken <= capacity` checked), `slot_reservations`, `checkouts`. Seed: the points and
  Maya's address, in the seed digest. `HaulPay` moved out of the product page for the quote to share.
- Where it ran: the server suite (118 tests; PostgreSQL and shildik in Testcontainers), `check
  :server:installDist :composeApp:wasmJsBrowserDistribution` (client 37 tests and `viddikVerify`
  untouched) and `scripts/image-check.sh` (V9 migrated in the training run, 629 of 629 classes from the
  cache) on the Linux build machine; `make check` on the Mac.
- Scenarios, automated in `server/src/test/.../feature/checkout/CheckoutRoutesTest.kt` against PostgreSQL
  and shildik (each test signs a person in through `ShildikHarness`, which can now create Maya by her
  seeded id): «Quote as drawn» (`Maya's quote is the cart's totals by courier to her address`: $652.00,
  −$140.00, Free, $512.00, «Place order · $512.00», 1,024 points, Wed 8 … Sun 12), only the selected
  lines (`checkout quotes only the lines selected in the cart`, against the cart's own summary; `409
  cart_empty` when none is), sign-in required, «a full slot is refused» (`a full window is drawn
  unavailable and refused`), the quote's half of «Slot filled meanwhile» (`a window that filled after it
  was chosen is cleared and the shopper told`), PickupPoint, ParcelLocker without pay on delivery, Haul
  Pay's range, Validation (`the address form is refused field by field and drawn again`), «not yours»
  for an address. The race in `DeliverySlotsTest` (two customers on the last place, twenty rounds: one
  wins; sixteen on three places: three win; one holder, one place; release once); the expired code in
  `CheckoutQuoteTest`; `SchemaTest` and `KoinGraphTest` extended. Eleven mutations, each seen failing
  the test written for it: no capacity condition, every cart line quoted, pay on delivery to a locker,
  Haul Pay at any total, a full window chosen, a filled window kept, no ZIP format, a default onto a full
  window, an expired code honoured, a courier quote complete without a window, another's address chosen.

## Findings (2026-10-08)

- **feature-checkout names no quote scenario**: its four are placement's («Place by courier», «Same key
  twice», «Points redeemed», «Slot filled meanwhile»). What this item takes as its quote scenarios are
  the screen's states and the business rules, listed in Done; PR #2's feature-checkout should gain them
  as scenarios with their `**Automated:**` lines. «Points redeemed» is B-23's (no balance is stored).
- **The windows start tomorrow, not at the items' delivery day.** feature-checkout says «the next 5 days
  from tomorrow» and the canvas offers Maya Wed 8, while the cart says her duvet and mugs arrive Thu 9
  (they dispatch in a day, D7). Followed the document; a Wed 8 window for a parcel that cannot leave
  the seller before Thursday is B-17's to reconcile, or the owner's.
- **Placement's half of the window is B-16's**: `reserve` is atomic and raced here, but nothing calls it
  yet, and «Slot filled meanwhile» (`409 slot_unavailable` at `POST /api/v1/orders`) is B-16's to wire.
  `CheckoutSummary.placeUrl` is empty until then; `placeEnabled` already says whether the quote is
  complete.
- **No tier test can tell the mount apart**: checkout's routes resolve a customer themselves, so mounted
  in the public tier they still answer a guest `401` — the mount is defence in depth, not what the
  sign-in test holds.
- **The suite's PostgreSQL is near its connection limit.** Every `freshDatabase()` is a Hikari pool that
  holds its four connections and is never closed; with this item's first tests (eight pools more) the
  container refused clients and `SeedTest` failed with «too many clients». The checkout tests now close
  their pools (`seededFreshDatabase().use`) and share one database for the slots; the older tests still
  leave theirs open, so the next item with a few fresh databases will meet it again.
- **Not built here**: the points toggle (B-23), placement (B-16), editing or deleting a saved address
  (no artboard), a card form (no artboard; the simulator's two cards are every customer's).
- **Copy the tree had to choose, for B-15 to check against the artboards** (the `Checkout_*` sources are
  not on disk): the section titles («How to receive it», «Delivery address», «Delivery window», «Pickup
  points nearby», «Parcel lockers nearby», «Payment») and the page title «Checkout · 3 items»; a method's
  detail (the delivery fee, «Free») — or is it the earliest day?; the window hours (09, 12, 15, 18) and
  the default window (the first with room, 09:00–12:00 for Maya, where Content shows 15:00–18:00 — the
  fixture's choice or a rule?); the pickup list — screen-checkout says «3 nearby points» where research §6
  has two points and one locker, and gives the locker no distance; the payment options' labels, and
  whether the test card ···· 0002 is listed at all; the address line «148 Wythe Avenue, Apt 4F» /
  «Brooklyn, NY 11211» against screen-checkout's «148 Wythe Avenue 4F»; the form's fields (no state),
  labels, error sentences, «Save address» and «Add an address»; the header's steps and which is current;
  «Code AUTUMN10 has expired and is not in the total»; whether the summary lists the items; «You'll earn
  1,024 points» and «Your card is charged when the order ships» under the button.
- PR #2's drafts that this changes: **endpoint-checkout** (`PUT /api/v1/me/checkout` with
  `CheckoutChoice`; the errors above; no `CheckoutRoutes` — the contract is `CheckoutCommands.kt` and
  `CheckoutComponents.kt`, the paths `CheckoutPaths`), **endpoint-identity** (`POST /api/v1/me/addresses`
  is built in `feature/checkout/`, not `identity/`; `400 validation_failed` carries `fields`),
  **feature-checkout** (the scenarios above; windows, capacity, the fee per method and the payment set
  as in research D5), **screen-checkout** (the state per tree as in Done), **haul-shared** (the eight
  components, `ErrorBody.fields`, `FieldError`, the eight codes), **haul-server** (`checkout/` in the
  feature list and the customer tier).
