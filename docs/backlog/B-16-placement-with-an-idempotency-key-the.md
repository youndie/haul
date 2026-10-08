---
id: B-16
title: "server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator"
status: done
priority: P1
size: L
stage: stage-5-order
epic: feature-orders
blocked_by: [B-14]
---

# B-16 — server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator

Placement is the seam this product exists to show: stock and payment across sellers, compensated when a step fails (research D4).

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: fulfilment after placement (B-17).

- AC: feature-checkout and «declined card» pass; a saga killed mid-way finishes after a restart.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`.

## Done (2026-10-08)

- **Placement** (`server/.../feature/order/domain/Placement.kt`, the route in `feature/checkout/CheckoutRouting.kt`,
  customer tier): `POST /api/v1/orders` with `PlaceOrderRequest(quote)` and an `Idempotency-Key` header
  (`IDEMPOTENCY_KEY_HEADER`, both in `shared/.../feature/checkout/CheckoutCommands.kt`). The quote is computed
  again by checkout (`CheckoutCommands.state`); a window the shopper chose that filled since is `409
  slot_unavailable` (the tree they return to is `Checkout_PlaceError`), a fingerprint that is no longer the
  quote's is `409 cart_changed`, an incomplete quote `400 validation_failed`. The answer is `202` with kompot's
  `navigate` to `/orders/{id}` (the order's page, B-18), for a placed order and for one cancelled because the
  card was declined. `CheckoutSummary.placeUrl` is `/api/v1/orders`.
- **The idempotency key** (petich-idempotency 0.4.0.112): the saga's id is a hash of the customer and the key,
  so a key is the customer's own. A key whose saga exists is answered from that saga's row — the same order,
  though the cart it was placed from is empty now — after `IdempotencyGuard` checks the request is the one
  first sent under it; another request under it is `409 idempotency_key_reused`. A key is claimed only by a
  placement that passed the quote checks, so a stale quote does not spend it. No key, or a blank one, is
  `400 idempotency_key_missing`; one over 128 characters `400 validation_failed`.
- **The saga** (petich 0.4.0.112, `feature/order/saga/OrderSaga.kt`): `reserve-stock` (every line's units off
  its SKU, all or nothing, held by the order; a shortage refuses with `out_of_stock`) → `reserve-slot` (B-14's
  `DeliverySlots.reserve` for a courier; `slot_unavailable`) → `open-order` (the order `placing`, its lines, a
  shipment per seller) → `authorise-payment` (the simulator, under the member's idempotency key; pay on delivery
  holds nothing; a decline cancels the order with `payment_declined` and refuses) → `confirm` (`placed`) →
  announcement `clear-cart` (the bought SKUs leave the cart). A refusal or a failure undoes in reverse: void the
  authorisation, cancel the order and its shipments, release the window, release the stock. Every member is
  idempotent and names what it holds by the order, so a re-run after a restart takes nothing twice.
- **The restart**: the application starts petich's `SuspendedPetichSweeper` in its own scope with `stuckAfter`
  60 s (`STUCK_AFTER`, twice petich's largest phase timeout); a saga left `PROCESSING` or `COMPENSATING` by a
  process that died is carried on from the member it died in. The saga's clock is a `PetichClock` passed to
  `haulModule` (the wall clock, read in `Application.kt`), apart from the store's «now».
- **The payment simulator** (`feature/payment/`): `PaymentProcessor` (`authorise`, `void`, by key) over a
  ledger table; every way to pay is approved except the test card ···· 0002; Haul Pay is authorised like a
  card. Capture is B-17's.
- **Storage** (`V10__orders.sql`): petich's `petiches`, `outbox_events`, `idempotency_keys`; `orders`
  (`placing`/`placed`/`cancelled`, `cancel_reason`, the quote copied), `order_lines`, `shipments`,
  `stock_reservations`, `payment_authorisations`, and the sequence `order_numbers` from 48302 (the checkout
  fixture's #HL-48302).
- **Wire** (`shared/`): `PlaceOrderRequest`, `IDEMPOTENCY_KEY_HEADER`; `ErrorCode` `idempotency_key_missing`
  (`400`), `idempotency_key_reused` and `cart_changed` (`409`).
- **Tests written** (`server/src/test/.../feature/order/`): `PlacementRoutesTest` over HTTP against shildik and
  a seeded database of each test's own — «Place by courier» (stock −1 per line, the window's place, the
  authorisation, the cart emptied, `HL-48302`, two shipments), «Same key twice» and key reuse, «Declined card
  cancels and releases», «Slot filled meanwhile», a stale quote, the sign-in tier; `OrderSagaTest` on the
  production engine — a failure after the payment undone in reverse (void, cancel, window, stock), the window
  filling inside the saga, stock running out all or nothing; `PlacementRestartTest` — the first process dies
  inside the card processor's call (the placement cancelled where it stands, the production graph), the next
  application on the same database, five minutes later by the saga's clock, finishes the order through its own
  sweeper with nothing taken twice. `SchemaTest` (V10 against petich's and placement's tables) and
  `KoinGraphTest` extended; `testing/Placements.kt` reads the rows past every repository.
- **Where it ran**: on the Linux build machine, after a rebase onto `a525f67` — `:server:test` (130 tests, 0
  failed; PostgreSQL and shildik in Testcontainers), `check :server:installDist
  :composeApp:wasmJsBrowserDistribution` green, and `scripts/image-check.sh` (V10 migrated in the training run,
  694 of 694 classes from the cache, page 200). `make check` on the Mac.
- **Scenarios**: feature-checkout «Place by courier» (`Maya places her cart by courier and the saga takes stock
  window and payment`), «Same key twice» (`the same key places once and a different request under it is
  refused`), «Slot filled meanwhile» (`a window that filled since the page was drawn is refused and the checkout
  says so`, and inside the saga `a window that fills inside the saga gives the stock back`); feature-orders
  «Declined card cancels and releases» (`a declined card cancels the order and gives everything back`); the AC's
  restart (`a placement killed inside the payment is finished by the next process`). Also: a stale quote, the
  sign-in tier, stock running out all or nothing, a failure after the payment undone in reverse, a failing card
  processor. «Points redeemed» is B-23's.
- **Mutations**, each seen failing the test written for it and restored: no stock release (3 tests), no window
  release (2), no void (1), no order cancel on `open-order`'s rollback (the failing-processor test, added for it),
  no `payment_declined` on a decline, no guard on a replayed key, no fingerprint check, no window-filled check, no
  cart clearing (2), no sweeper (the restart test). One survives and is equivalent: `confirm`'s own compensation
  cancels what `open-order`'s compensation cancels right after it.

## Findings (2026-10-08)

- **Initialisation cycle**: `sagaJson()` and petich's tables first lived in the file that also built
  `orderTables`, and the two file classes initialised each other — the list held a null whenever the saga's tables
  were touched first (the whole suite: `SchemaTest` failed with an NPE inside `MigrationUtils`; alone it passed).
  They are in `data/SagaTables.kt` now, which says why.
- **The key is per quote, not per page**: feature-checkout says the client generates one key «per checkout page»;
  a key whose saga exists answers that saga, and a new quote under it is `409 idempotency_key_reused`. A page whose
  quote changed (a refresh after a choice, a `409 slot_unavailable`) needs a new key — the client (B-15) generates
  one per quote it places. A refusal before the saga (`cart_changed`, `slot_unavailable` found by checkout) does
  not spend the key.
- **The order page's address**: endpoint-orders names `GET /ui/orders/{id}` and the storefront's address
  convention makes that `/orders/{id}`, which placement navigates to; screen-order says `/account/orders/{orderId}`.
  Followed endpoint-orders; B-18 or the owner reconciles screen-order.
- **The order's status is the saga's**: `placing` / `placed` / `cancelled` with `cancel_reason` (`payment_declined`,
  `failed`) on `orders`; what the shopper sees after placement — packed, in transit — is the shipments' (B-17), and
  research §5's «status derived from its shipments» applies from `placed` on.
- **Not built here**: capture per shipment (B-17), the order's tree (B-18), points (B-23), Haul Pay's four payments
  (B-24). The image's training workload does not place an order: it would need a signed-in customer.
- **Wall clock in tests**: `haulModule` takes the saga's `PetichClock` as a required parameter, so every test that
  assembles the application passes one (`SAGA_CLOCK`, the wall clock; the restart test five minutes ahead).
- **The build machine's OOM**: a rebased `check :server:installDist :composeApp:wasmJsBrowserDistribution` in one
  5 GB scope was killed by the OOM killer during `compileProductionExecutableKotlinWasmJs` after B-13's renderers
  landed; the wasm distribution built alone first, and the gate then passed in the same scope.
- PR #2's drafts that this changes: **endpoint-checkout** (`PlaceOrderRequest` and `IDEMPOTENCY_KEY_HEADER` in
  `CheckoutCommands.kt`, no `CheckoutRoutes`; `400 idempotency_key_missing` / `validation_failed`, `409
  slot_unavailable` / `out_of_stock` / `cart_changed` / `idempotency_key_reused`; a declined card is `202`, not an
  error; `points_balance_changed` and `422 payment_method_not_allowed` are not answered at placement — points are
  B-23's, the way to pay is checked when chosen), **feature-checkout** (`**Automated:**` for the three scenarios
  above; the key per quote), **feature-orders** (the members in their order, the order's statuses and
  `cancel_reason`, `**Automated:**` for «Declined card»), **screen-order** (the address), **haul-server**
  (`order/` and `payment/` built, the sweeper, `PetichClock`), **haul-shared** (the request, the header, three
  codes).
