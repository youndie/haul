---
id: B-16
title: "server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator"
status: wip
priority: P1
size: L
stage: stage-5-order
blocked_by: [B-14]
---

# B-16 — server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator

Placement is the seam this product exists to show: stock and payment across sellers, compensated when a step fails (research D4).

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: fulfilment after placement (B-17).

- AC: feature-checkout and «declined card» pass; a saga killed mid-way finishes after a restart.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`.

## Iteration 1 (2026-10-08) — written, not yet compiled or run

**Stopped by the build machine**: the Linux box every Gradle run goes to stopped answering mid-item
(`ssh` to the WSL guest times out at the banner; `wsl -e uptime` from its host fails with
`Wsl/Service/0x8007274c`), so nothing below has been compiled or run yet. Left for the next iteration:
`:server:test` (the suites listed last), the mutation checks, `check :server:installDist
:composeApp:wasmJsBrowserDistribution`, `scripts/image-check.sh`, then `done` with the scenarios ticked.

What the branch holds:


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
