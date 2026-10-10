---
id: feature-membership
title: Haul Plus, points and Haul Pay
type: feature
status: active
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-home
  - screen-cart
  - screen-checkout
  - screen-account
api:
  - endpoint-membership
tags: []
---

# Haul Plus, points and Haul Pay

## 1. Overview

The loyalty layer: a subscription that removes delivery fees and doubles points, points that turn into money off, and paying in instalments. All money here is simulated.

No screen of its own: screen-home (Plus block, trial dialog), screen-cart (the points an order will
earn), screen-checkout (the points toggle, Haul Pay), screen-account (tiles), screen-order (the points an
order earned or gave back).

> Described as built: the Haul Plus trial and membership, the points ledger, redemption at checkout and
> the delivery savings (B-23, research D6 «Decided in B-23»), points settled by a return (B-21 with
> B-23, shared per line since B-50), Haul Pay's four payments two weeks apart (B-24, research D6
> «Decided in B-24») and a member's early access to campaign prices (B-53,
> [feature-browse](feature-browse.md)).

## 2. Business rules

* **the trial** — `POST /api/v1/me/plus/trial` from the home page's Plus block or the account's tile (each
  presents the trial dialog to a customer; a guest's «Try 30 days free» goes to `/sign-in`): free for 30
  of the store's days from its date, then «renewing» monthly at $4.99 with nothing charged; a member — on
  a trial or paying — asking again is `409 already_member`, decided by the store's conditional update, so
  two requests racing start one membership; there is no way to end it in v1;
* **Haul Plus** is a `memberships` row plus `customers.plus` (which every price reads), written in one
  transaction: free delivery on every order and points ×2 from the next quote on; early access to
  campaign prices — a member, a trial included, gets a campaign's prices from its `plus_early_access_at`
  (B-53) — is [feature-browse](feature-browse.md)'s; the offer's «next-day courier whatever the
  order total» holds for every shopper (the courier's day never depends on the total);
* **points are an append-only ledger** (`points_entries`): one row per movement, the balance their sum,
  each row keyed so it happens once — `earned:<shipment>`, `redeemed:<order>`, `returned:<order or
  return>`, `reversed:<return>`, `opening:<customer>`; the sign is checked by the database; points do not
  expire;
* **earning** — 1 point per whole dollar **paid** (after points), ×2 for a member at placement (Maya's
  $512 earns 1,024; with her 2,480 points applied, the $487.20 order earns 974); each shipment's share is
  split like its capture and credited before the shipment moves to `delivered` or `picked_up`, once per
  shipment, on the simulator's clock; the cart's «You'll earn N points» is the same rule before points;
* **redemption** — the checkout's toggle («Use N points», `CheckoutChoice.usePoints`, stored with the
  checkout) takes the whole balance, capped at the items after discounts, so points never pay for
  delivery; 100 points = $1 (2,480 → $24.80); the total, Haul Pay's range and the quote's fingerprint
  follow it; the order saga's `redeem-points` step takes them under a lock on the customer — a balance
  spent meanwhile refuses with `409 cart_changed`; a declined card or any rollback gives them back as a
  `returned` row;
* **a return** takes back what the returned lines earned (`reversed`, the number the order page shows as
  «−N points») and gives back the returned lines' share of the points the order was paid with
  (`returned`), which is not refunded in money; each line's share is its own, rounded per line (B-50), and
  the return dialog and the order page name it as «+ N points back» beside the card's money
  ([feature-orders](feature-orders.md));
* **delivery savings this year** = the delivery fees Plus waived on the member's orders placed since
  January 1 (`orders.delivery_waived_cents`; a cancelled order saved nothing), plus a carried-in amount
  for the sample data (Maya's $186);
* the seed gives Maya a membership (a trial from Oct 3 2023, paid from Nov 2) and an `opening` row of
  2,480 points, written once — also into a database seeded before V19;
* **Haul Pay** (B-24, `server/src/main/kotlin/io/github/youndie/haul/feature/payment/`): the order's total
  — after points, so $487.20 is 4 × $121.80 — in four payments of the checkout's amount («4 payments of
  $128», `HaulPay.paymentCents`), the last taking what rounding left so the four add up to the cent
  (`InstalmentSchedule`); placement authorises the whole total (B-16) and the plan's payments are four
  captures out of that authorisation, keyed `instalment:<order>:<n>`, in place of capture per shipment;
  the first is taken **when the first shipment ships** (a Haul Pay shipment waits for it as a card's waits
  for its capture), the rest two weeks apart on the simulator's clock (`FulfilmentPace.instalmentInterval`,
  sped up by `HAUL_FULFILMENT_SPEED` like every step), by a third pass of the fulfilment runner
  (`HaulPayPlans.advance`); a payment is `scheduled`, `collecting`, `paid`, `covered` or `overdue`, each move
  a conditional update, the charge frozen when a pass claims it, so a payment is taken once;
* **a declined payment** is tried again a day later, once; declined again it is `overdue` and the plan
  stops — nothing later is asked for and nothing chases it; a first payment that never lands holds the
  shipments `packed`;
* **a return on a Haul Pay order** comes off the payments still owed first, the last one first (a payment
  reduced to nothing is `covered`); only what they cannot absorb is refunded through the processor out of
  what was paid; the reduction is made once per plan and stored; the points part goes back as points;
* the order page draws the plan under the summary's payment fact — each payment's day, state and amount —
  and the fact says what is paid and what comes next («$128.00 of $512.00 paid · next $128.00 on Oct
  22»); there is no «next payment» in the account.

Numbers in these rules (fees, thresholds, limits) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7, and checked against the code and
the tests below.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/MembershipComponents.kt` — the trial dialog (`PlusTrialDialog`, `PlusBenefit`); the points toggle's `CheckoutChoice.usePoints` in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/membership/` — the trial, the standing (`domain/PlusCommands.kt`), the ledger (`domain/Points.kt`), the offer (`screen/PlusOffer.kt`); `server/src/main/resources/db/migration/V19__plus_and_points.sql` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` — Haul Pay's plan (`InstalmentPlan`, `InstalmentSchedule`, `HaulPayPlans`, `ExposedInstalments`); `server/src/main/resources/db/migration/V21__haul_pay_plans.sql` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderViews.kt` — the plan drawn under the payment fact |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/` — the trial dialog (`PlusTrialDialog.kt`) |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` — the points toggle |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` — the tiles |

## 5. Scenarios (BDD / test cases)

### Scenario: Trial
* **Given:** Sam (no membership)
* **When:** he starts the trial on 2025-10-07
* **Then:** his membership is `trial` until 2025-11-06 and his next cart shows free delivery.
* **Automated:** `MembershipRoutesTest.Sam starts the trial and his next cart has free delivery` (`server/src/test/kotlin/io/github/youndie/haul/feature/membership/MembershipRoutesTest.kt`)

### Scenario: Already a member
* **Given:** Maya (active)
* **When:** she starts the trial
* **Then:** the server returns `409` with `already_member`.
* **Automated:** `MembershipRoutesTest.a member asking for the trial is refused as already a member`

### Scenario: Points on delivery
* **Given:** Sam's $103.00 order without Plus
* **When:** its only shipment is delivered
* **Then:** his balance grows by 103.
* **Automated:** `PointsTest.Sam's order of 103 dollars earns 103 points when its shipment is delivered` (`server/src/test/kotlin/io/github/youndie/haul/feature/membership/PointsTest.kt`)

### Scenario: Points spent once
* **Given:** Maya's 2,480 points, 2,000 of them taken by one order
* **When:** a second order asks for 481
* **Then:** its redemption is refused and nothing is written — the saga refuses it and placement answers `409 cart_changed`; the same redemption asked twice is the one written; a declined card gives the points back.
* **Automated:** `PointsTest.a balance cannot be spent twice`, `PointsTest.a declined card gives the redeemed points back`

### Scenario: A return settles the points
* **Given:** an order paid partly with points and a return of one of its lines
* **When:** the return is refunded
* **Then:** the points that line earned are taken back, and its share of the points paid comes back as points.
* **Automated:** `PointsTest.a return takes back the points its line earned and gives back the points it was paid with`

### Scenario: Haul Pay's schedule
* **Given:** Maya's $512.00 order paid with Haul Pay
* **When:** its first shipment ships, and the simulator's clock moves on
* **Then:** the order page shows four payments of $128.00 — the first taken when it ships, the others each exactly two weeks after the last and not before; with her points applied the four are $121.80; a card order shows no schedule.
* **Automated:** `HaulPayPlanTest.an instalment order shows its schedule`, `HaulPayPlanTest.captures follow the schedule on the simulator clock`, `HaulPayPlanTest.points reduce the plan's total`, `HaulPayPlanTest.shipments wait for the first payment`, `HaulPayPlanTest.a card order shows no schedule` (`server/src/test/kotlin/io/github/youndie/haul/feature/payment/HaulPayPlanTest.kt`); the runner, `FulfilmentRunnerTest.the application takes a Haul Pay plan's payments by itself when fulfilment runs`; the client, `OrderWiringTest.a Haul Pay order draws its schedule under the payment fact` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/order/OrderWiringTest.kt`)

### Scenario: A declined payment
* **Given:** a Haul Pay plan whose processor declines a payment
* **When:** it is tried again a day later and declined again
* **Then:** the payment is `overdue` and the plan stops; a payment taken on its retry is paid and the plan carries on.
* **Automated:** `HaulPayPlanTest.a payment declined twice marks the plan overdue and stops it`, `HaulPayPlanTest.a payment taken on its retry is paid and the plan carries on`

### Scenario: A return on a Haul Pay order
* **Given:** a Haul Pay order with payments still owed
* **When:** a return is refunded
* **Then:** what is owed is reduced first, the last payment first; only the rest is refunded; asked twice, it takes off once.
* **Automated:** `HaulPayPlanTest.a return reduces the payments still owed before refunding any`, `HaulPayPlanTest.a return bigger than what is owed refunds the rest of it`, `HaulPayPlanTest.a reduction asked twice takes off once`, `HaulPayPlanTest.a points-paid return gives back through the plan what the dialog said`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1 — real
  billing, ending a membership, collections for an overdue Haul Pay plan.

## 7. Quirks

* A saga in flight across a deploy that adds a step meets the new definition (`redeem-points` did);
  petich's chain fingerprint decides its fate.
* The simulator never declines a Haul Pay payment — the authorisation always covers the plan — so
  `overdue` is reached only through a refusing processor in the tests; the stand cannot show it (a Haul
  Pay test card would be a product decision, B-24's findings).
* An overdue first payment holds the shipments `packed` for good: with no collections nothing releases
  them.
* The checkout says only «4 payments of $128»; that the first is taken when the order ships is said on
  the order page, not before placement.
