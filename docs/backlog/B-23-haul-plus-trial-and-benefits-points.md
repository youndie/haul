---
id: B-23
title: "server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings"
status: wip
priority: P2
size: M
stage: stage-8-loyalty
blocked_by: [B-15, B-17, B-19]
---

# B-23 — server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings

Haul Plus and points are the loyalty layer the canvas sells; redemption backs the account tile's promise (research D6).

Feature: `feature-membership` — its scenarios are this item's acceptance where it names them.

- Not covered: real billing.

- AC: feature-membership and «points redeemed» pass; parity for `Home_PlusTrialDialog`, `Account_NotMember`, `Checkout_PointsApplied`.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/membership/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/home/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/`.

- **From B-19:** the account's Points and Haul Plus tiles read `Loyalty`
  (`server/src/main/kotlin/io/github/youndie/haul/feature/account/domain/Account.kt`), bound in `AccountModule.kt` to
  `SampleLoyalty` (Maya's 2,480 points, Plus since 2023, renewing Nov 2, $186 saved on delivery; research §6). Bind it
  to the ledger and the membership here. A non-member's tile (`AccountTileKind.PlusOffer`, `Account_NotMember`) draws
  «Try 30 days free» with no action, as the home page's offer; it gets the trial's command here.

## Done (2026-10-08)

- **Decisions** (recorded in research D6, «Decided in B-23»): an append-only points ledger keyed per movement; an order
  earns a point per whole dollar *paid* (after points), ×2 for a member at placement, credited per shipment as it is
  delivered or collected; the toggle redeems the whole balance capped at the items after discounts (a point a cent),
  taken in the saga and given back when the order is undone; Haul Plus is a `memberships` row plus `customers.plus`,
  a 30-day trial from the store's date then «renewing» monthly with nothing charged, no way to end it in v1 (so no
  `trial_used`); savings this year are the waived fees on the member's placed orders plus a carried-in figure for the
  sample data. Points do not expire: no document says they do.
- **Storage** (`V19__plus_and_points.sql`): `memberships`, `points_entries` (kind and sign checked), `orders.
  delivery_waived_cents`, `orders.points_redeemed`, `checkouts.use_points`. The seed writes Maya's membership (a trial
  from Oct 3, 2023, paid from Nov 2, $186 carried into 2025) and her 2,480 points as an `opening` row — once, also into
  a database seeded before V19 (`Seeder.seedLoyalty`).
- **The server** (`feature/membership/`): `PlusCommands` — the trial (`POST /api/v1/me/plus/trial`, customer tier,
  `201` with `sequence[close, refresh]`, `409 already_member`) and the standing, bound as the account's `Loyalty`
  (`SampleLoyalty` is gone; `SampleSavedLists`, now in a file of its own name, stays B-20's); `PlusOffer` — the trial's dialog and the home page's Plus
  block (the offer with the dialog for a customer and sign-in for a guest, the member's savings and renewal). Checkout
  reads the balance (`CheckoutCommands.ledger`), draws the toggle with its `url`, a «Points» summary row and the
  totals after points; the quote's fingerprint names the points only when some are taken, so every other quote keeps
  its fingerprint. The saga's `redeem-points` step sits after the window and before the order opens; the fulfilment
  simulator credits a shipment's share before moving it to `delivered`/`picked_up`.
- **The client**: `PlusTrialDialogView` and its renderer (`feature/home/PlusTrialDialog.kt`), «Start trial» sent
  through `LocalHaulCommands` (the storefront's `HaulCommands`), the home offer's and the account tile's buttons
  following their actions, the checkout's points toggle pressable (`CheckoutChoice.usePoints`), the dialog 140 px down
  on a phone (`DialogOverlay.compactTop`).
