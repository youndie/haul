---
id: B-24
title: "server + client: Haul Pay, 4 payments two weeks apart"
status: done
priority: P2
size: M
stage: stage-8-loyalty
epic: feature-membership
blocked_by: [B-15, B-16]
---

# B-24 — server + client: Haul Pay, 4 payments two weeks apart

Haul Pay as decided: 4 interest-free payments two weeks apart, simulated.

Feature: `feature-membership` — its scenarios are this item's acceptance where it names them.

- Not covered: collections for an overdue plan.

- AC: an instalment order shows its schedule; captures follow it on the simulator clock.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` (the plan lives with the payments, not with
  membership), `server/src/main/resources/db/migration/V21__haul_pay_plans.sql`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderViews.kt`.

## Done (2026-10-09)

- **Decisions** (as product owner; recorded in research D6, «Decided in B-24»):
  - *Schedule*: the order's total in four payments of the checkout's amount (`HaulPay.paymentCents`, «4 payments of
    $128»), the last taking what rounding left so the four add up to the total to the cent; the **first when the first
    shipment ships** (feature-checkout's target line), the rest two weeks apart on the simulator's clock and pace
    (`FulfilmentPace.instalmentInterval`, sped up by `HAUL_FULFILMENT_SPEED` with everything else).
  - *Capture vs plan*: placement still authorises the whole total (B-16); the plan **replaces** the capture per
    shipment — its payments are four captures out of that authorisation, keyed `instalment:<order>:<n>`. A Haul Pay
    shipment waits for the first payment as a card shipment waits for its capture; the merchant side (the
    authorisation, its limit, the ledger) is B-17's, unchanged.
  - *Refunds*: a return's money comes off the payments still owed first, the last one first (one reduced to nothing is
    `covered`); only the rest is refunded through the processor out of what was paid. Made once per plan and stored.
  - *Points*: the plan is the total after points, so points make every payment smaller ($487.20 → 4 × $121.80); the
    points part of a refund goes back as points (B-23), not off the plan.
  - *Failure*: a declined payment is tried again a day later, once; declined again it is `overdue` and the plan stops
    — nothing later is asked for, nothing chases it (collections are not covered). A first payment that never lands
    holds the shipments `packed`.
  - *Account*: no «next payment» there — no document asks for it.
- **Storage** (`V21__haul_pay_plans.sql`; V20 is B-25's): `instalment_plans` (the plan, written when it starts, and
  the return's reduction) and `instalments` (`scheduled`, `collecting`, `paid`, `covered`, `overdue`; declined
  attempts, the next attempt, the frozen charge). Every move is a conditional update; a claim freezes the charge, so a
  return's reduction and a charge never overlap.
- **The server** (`feature/payment/`): `InstalmentPlan`, `InstalmentSchedule`, `InstalmentRepository` and
  `ExposedInstalments`; `HaulPayPlans` — `ship` (called by `FulfilmentSimulator` before a Haul Pay shipment's move to
  `in_transit`), `advance` (a third pass in `FulfilmentRunner`, after the shipments' and the returns') and `refund`
  (called by `ReturnSimulator`). `OrderTracking` carries the plan (stored, or the projected schedule before it starts).
- **Wire and client**: `OrderTotals.plan` (`PaymentPlan`, `PlanPayment`, `PlanPaymentState`); the order page draws
  it under the summary's payment fact, whose detail says how much is paid and what comes next («$128.00 of $512.00
  paid · next $128.00 on Oct 22»). **No artboard draws the schedule**: it is built from the summary's own pieces (the
  fact's caption style, the rows' label/amount layout), goldens `Order_HaulPay` / `Order_HaulPay_Phone` recorded on
  Linux from the server's tree of #HL-48230 (`order_haul_pay.json`, held equal by `OrderFixturesTest`), no design
  reference and so no parity number.
- **Acceptance**: «an instalment order shows its schedule» — `HaulPayPlanTest.an instalment order shows its schedule`
  (before the first shipment: «When it ships», «In 2 weeks»…; after: Oct 8 paid, Oct 22, Nov 5, Nov 19), and in the
  client `OrderWiringTest.a Haul Pay order draws its schedule under the payment fact`; «captures follow it on the
  simulator clock» — `HaulPayPlanTest.captures follow the schedule on the simulator clock` (nothing before the first
  shipment ships, each payment exactly two weeks after the last and not a millisecond before) and
  `FulfilmentRunnerTest.the application takes a Haul Pay plan's payments by itself when fulfilment runs`.
  feature-membership's rule «a declined instalment is retried once and then marks the plan overdue» —
  `HaulPayPlanTest.a payment declined twice marks the plan overdue and stops it`.
- **Tests written**: `HaulPayPlanTest` (the above; a card order shows no schedule; a pass after a pause takes every
  payment come due; two passes at once take each payment once; a payment is claimed once; a pass that died between
  the charge and the mark does not charge twice; a payment taken on its retry is paid and the plan carries on;
  shipments wait for the first payment; points reduce the plan's total; a return reduces the payments still owed
  before refunding any; a return bigger than what is owed refunds the rest of it; a reduction asked twice takes off
  once), `InstalmentScheduleTest`, the runner's Haul Pay test, `OrderWiringTest` (the schedule drawn; a card order
  draws none); changed: `SchemaTest` (V21's tables), `KoinGraphTest`, `OrderFixturesTest`, the two direct
  constructions in `ChargedWhenShippedTest` and `ReturnLifecycleTest`.
- **Mutations**, each against its tests on the Linux box and reverted: Haul Pay captured like a card again (12
  `HaulPayPlanTest` failures), the return not reducing the plan (the two return tests), a declined payment retried
  for ever (the overdue test), the reduction made again when asked again (its test), the schedule dropped from the
  tree (`OrderFixturesTest` and 4 `HaulPayPlanTest`), the schedule not drawn (the client wiring test), the runner not
  running the plans' pass (the runner test — after spacing its payments past the shipments' arrival: with payments
  1 ms apart the shipment's pass took all four and the mutation survived). A claim that ignored the payment's status
  failed nothing at first (the race it opens is narrow), so `a payment is claimed once` holds it at the repository.
- **Where it ran**: everything on the Linux box (WSL, 16 GB, shared, Gradle in a 5 GB scope, two workers), rebased on
  B-25 (V20): `:composeApp:wasmJsBrowserDistribution`, then `check :server:installDist` green — 305 server tests
  (PostgreSQL and shildik in Testcontainers), 154 desktop tests, 136 `viddikVerify` goldens; `scripts/image-check.sh
  haul/server:b24` on port 18124 (ready, 1007 of 1007 classes from the AOT cache, page 200, a fresh PostgreSQL
  migrating V1…V21); `scripts/e2e.sh` against that image (`WholePathTest`, green; it pays by card, so Haul Pay is not
  on its path).
- **Findings**:
  - The simulator never declines a payment — the authorisation always covers the plan — so `overdue` is reached only
    through a refusing processor in the tests; the stand cannot show it. A test card for Haul Pay would be a product
    decision, not this item's.
  - A Haul Pay shipment held by a declined first payment is stamped `in_transit` at the time it was due once the retry
    lands, as a card shipment held by a refused capture is (B-17's behaviour, kept).
  - An overdue first payment holds the shipments `packed` for good — with no collections nothing releases them.
  - The checkout still says only «4 payments of $128»; that the first is taken when the order ships is said on the
    order page, not before placement (the canvas draws the checkout's line as it is).
  - Flyway runs without `outOfOrder`: had V21 reached a database before V20, B-25's migration would have been refused
    on start. B-25 merged first, so the stand migrates V20 then V21.

