---
id: B-17
title: "server: fulfilment simulator, capture per shipment, pickup codes"
status: done
priority: P1
size: M
stage: stage-5-order
epic: feature-orders
blocked_by: [B-16]
---

# B-17 — server: fulfilment simulator, capture per shipment, pickup codes

The fulfilment simulator is the outside world that moves an order; capture per shipment is «your card is charged when the order ships».

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: returns (B-21).

- AC: «charged when shipped» passes; an order reaches delivered on the fast clock.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/`, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`.

## Done (2026-10-08)

- **The simulator** (`server/.../feature/fulfilment/domain/FulfilmentSimulator.kt`): one pass, `advance()`, reads the
  shipments of `placed` orders still on their way and moves each through every step that has come due —
  `placed → packed → in_transit → delivered` for a courier, `… → in_transit → ready_for_pickup → picked_up` for a
  point or a locker — each stamped at the moment it came due in `shipment_events`, so a pass after a pause catches up
  with the history an unbroken run would write. A shipment not seen yet is stamped `placed` by the first pass that
  finds it. Every move is conditional on the status it leaves, so two passes at once move each shipment once.
- **The clock and the pace** (`domain/Fulfilment.kt`, `FulfilmentRunner.kt`): the clock is the saga's `PetichClock`
  (the wall clock, read in `Application.kt`); the pace `FulfilmentPace.STORE` — packed 4 h after the seller sees it,
  on the road 20 h later plus a day per dispatch day, delivered or ready a day later, collected two days after that —
  run `HAUL_FULFILMENT_SPEED` times quicker (`ServerConfig.kt`; default 1, `1440` is a day a minute; anything but a
  positive number refuses the start), polled every tenth of the shortest step between 1 s and 1 min.
  `haulModule(fulfilment = …)` runs the pass in the application's scope; the tests get `FulfilmentSettings.MANUAL`
  and call the pass themselves on a clock they hold.
- **Capture per shipment** (`feature/payment/`): `PaymentProcessor.capture(key, Capture)` takes a part of the order's
  authorisation, named by the shipment id, into `payment_captures`; the authorisation is locked while it is taken, a
  key captured once answers what it took, the parts never add up to more than the hold (`Exceeds`), and nothing is
  taken without one (`NotAuthorised`). An authorisation part of which was captured is no longer voided. The simulator
  captures the shipment's share (`ShipmentShares`: the total in proportion to each seller's lines at the price paid,
  the last share taking the rounding) **before** moving it to `in_transit`; a refused capture holds it `packed`; pay
  on delivery has nothing to capture.
- **Pickup codes** (`PickupCodes`): four digits of an HMAC-SHA256 of the shipment id keyed by the order's saga id,
  written to `shipments.pickup_code` when it becomes `ready_for_pickup`.
- **The order page's state** (`domain/OrderTracking.kt`): `OrderTracking.track(customer, order)` answers only the
  order's own customer (anyone else: `null`, the route's `404`), with `OrderProgress` (the saga's `placing` /
  `cancelled`, else the least advanced shipment's status) and per shipment its history, share, captured amount, and —
  only while it waits at a point — the code and the day it is held until (5 days). B-18 builds the tree from it.
- **Storage** (`V11__fulfilment.sql`): `shipments.pickup_code`, `shipment_events`, `payment_captures`.
- **Tests written** (`server/src/test/.../feature/fulfilment/`, `.../feature/payment/CaptureRulesTest.kt`), on a
  fresh seeded database each, the production graph (`testing/FulfilmentWorld.kt`) and a test clock moved by the test —
  no sleeps: `ShipmentLifecycleTest` (each step at the store's pace and not a millisecond early, Sony a day before
  Brooklyn Home Co., the least advanced shipment wins, no code for a courier; **an order reaches delivered on the fast
  clock** at a day a minute; a pass after ten days catches up with the right stamps; a pickup shipment's code is the
  deterministic one, shown only while it waits and only to Maya — Sam gets no order; a locker is collected),
  `ChargedWhenShippedTest` (**charged when shipped**: nothing captured at placement or packing, exactly Sony's $349.00
  when Sony ships with Brooklyn Home Co. still only authorised, then $163.00, the two summing to the $512.00
  authorised; a pass that died between the capture and the move charges once after the restart; a refused capture
  holds the shipment while the other one moves; two passes at once move and charge each shipment once; pay on
  delivery ships without a charge), `CaptureRulesTest` (the limit, the replay, no hold, no void after a capture),
  `ShipmentSharesTest` (shares to the cent), `FulfilmentRunnerTest` (the application delivers an order by itself
  when fulfilment runs — the one wall-clock test, bounded at 30 s), `ServerConfigTest` (the speed), `SchemaTest` (V11)
  and `KoinGraphTest` extended. Every test database is closed by its test.
- **Mutations**, each seen failing the test written for it and restored: capture at packing (2 tests), no capture for
  a card (5), no replay of a captured key (2), no limit on the hold (1), void after a capture (1), shares without the
  remainder (5), a shipment moved though its capture was refused (1), an unconditional move (the two-passes test), a
  capture from a voided hold (1), a step one millisecond early (1), the tracking answering any customer (the pickup
  test), the runner never started (the runner test). Not covered by a test: the authorisation's row lock (two
  shipments of one order captured in the same instant by two processes — a race no deterministic test makes).
- **Where it ran**: the Linux build machine (WSL), after a rebase onto `e218d6f` (the branch now sits on `734d516`,
  which adds a backlog item and nothing else): `./gradlew check :server:installDist
  :composeApp:wasmJsBrowserDistribution` green (the wasm distribution built alone first; `:server:test` 169 tests, 0
  failed, PostgreSQL in Testcontainers), `scripts/image-check.sh` green (V11 migrated in the training run, 752 of 752
  classes from the cache, page 200). `make check` on the Mac. `scripts/chart-check.sh` not run: the chart is
  unchanged.
- **Scenarios**: feature-orders «Charged when shipped» (`charged when shipped`); the AC's «an order reaches delivered
  on the fast clock» (`an order reaches delivered on the fast clock`). «Late return» is B-21's.

## Findings (2026-10-08)

- **The pace does not follow the promise.** The simulator's schedule is the store's fixed pace plus dispatch days; it
  does not aim at the courier window the shopper chose or the day the checkout promised, so at speed 1 an order can be
  delivered before or after its window. Good enough for a simulator; B-18's «Arriving tomorrow, 15:00–18:00» reads the
  window from the order, not from the simulator.
- **A held shipment is stamped when it was due, not when it shipped.** A capture refused and later taken moves the
  shipment with its `in_transit` stamped at its due time; only the log says it was held.
- **The stand's speed is not in the chart.** `HAUL_FULFILMENT_SPEED` is read by the server; `charts/haul` passes no
  such variable yet, so a stand runs at the store's pace until B-27 (or whoever deploys it) adds it to
  `values-stand.yaml`.
- **Haul Pay is captured like a card**, per shipment, out of its authorisation; its four payments are B-24's.
- **The sample orders** of research §6 (#HL-48211 in transit, #HL-47960 ready with code 4821, the delivered ones) are
  not seeded here: they are B-18's fixtures, and a seeded pickup shipment carries its code in `pickup_code` directly.
- PR #2's drafts that this changes: **feature-orders** (`**Automated:**` for «Charged when shipped»; the shipment's
  share rule and the capture before `in_transit`; a refused capture holds the shipment; pay on delivery captures
  nothing; the code shown only while it waits; the order's progress is `placing`/`cancelled` from the saga, else the
  least advanced shipment's), **screen-order** (the states map from `OrderProgress`; `Packed` has no artboard of its
  own), **endpoint-orders** (`GET /ui/orders/{id}` is built on `OrderTracking.track`, `404` for another customer's
  order — B-18), **haul-server** (`fulfilment/` built, not planned; `HAUL_FULFILMENT_SPEED` with its default and its
  refusal in section 7; the runner beside the sweeper; the saga clock shared by the simulator; `V11`).

