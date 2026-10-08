---
id: B-23
title: "server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings"
status: done
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
  (`SampleLoyalty` is gone, and B-20 replaced `SampleSavedLists` with the list itself); `PlusOffer` — the trial's dialog and the home page's Plus
  block (the offer with the dialog for a customer and sign-in for a guest, the member's savings and renewal). Checkout
  reads the balance (`CheckoutCommands.ledger`), draws the toggle with its `url`, a «Points» summary row and the
  totals after points; the quote's fingerprint names the points only when some are taken, so every other quote keeps
  its fingerprint. The saga's `redeem-points` step sits after the window and before the order opens; the fulfilment
  simulator credits a shipment's share before moving it to `delivered`/`picked_up`.
- **The client**: `PlusTrialDialogView` and its renderer (`feature/home/PlusTrialDialog.kt`), «Start trial» sent
  through `LocalHaulCommands` (the storefront's `HaulCommands`), the home offer's and the account tile's buttons
  following their actions, the checkout's points toggle pressable (`CheckoutChoice.usePoints`), the dialog 140 px down
  on a phone (`DialogOverlay.compactTop`).
- **Returns settle points (B-21, merged first).** `ReturnSimulator` writes the ledger when it refunds, before the move
  to `refunded` and each once by its key: `reversed:<order>` for what the returned lines earned — B-21's stored
  `returns.points`, so the order page's «−N points» stays B-21's number and is the one the ledger holds — and
  `returned:return-<order>` for the returned lines' share of the points the order was paid with
  (`ReturnRefunds.pointsBack`: redeemed × refund ÷ what was paid for the items), which is no longer paid back in
  money. Orders paid without points refund exactly as before.
- **Parity** (`viddikDesignParity`, references committed before this item, rendered on Linux with grayscale text,
  tolerance untouched), all six within 5 %: `Home_PlusTrialDialog` 1.52 / 2.26 (wide / phone), `Account_NotMember`
  1.42 / 2.69, `Checkout_PointsApplied` 1.41 / 2.55. Two rounds, both on the phone dialog: its title wraps where the
  canvas's does («30 days» / «free» at 56 px in 310 px), and Sam's «Picked for you» says «Popular right now». The
  NotMember and PointsApplied goldens are unchanged: their bodies gained the trial's action and the toggle's `url`,
  which draw nothing. Goldens recorded for `Home - PlusTrialDialog*` only. Token notes: the dialog's «×» on black is
  white at 12 %, a new role `HaulColors.inverseControl`; everything else maps onto existing tokens (Ink
  `inverseSurface`, Acid `secondaryContainer`, Blue `primary`, `outline` for the benefit lines, `outlineVariant` for
  the hairline). The phone artboard's benefit grid writes `gap:16` without a unit, so the browser lays the four
  benefits with no gap; the client does the same.
- **Tests written**: `MembershipRoutesTest` (Sam starts the trial and his next cart has free delivery; a member asking
  for the trial is refused as already a member; the trial is offered to a non-member and the savings shown to a
  member), `PointsTest` (Maya pays with her 2480 points and her balance is 0 until the order earns; a declined card
  gives the redeemed points back; Sam's order of 103 dollars earns 103 points when its shipment is delivered; a balance
  cannot be spent twice; a return takes back the points its line earned and gives back the points it was paid with; a
  member's order under the threshold adds the waived fee to the year's savings; points are capped at the items after
  discounts), `PlusMembershipTest`, `PlusOfferFixturesTest` (the dialog's body is what the server sends),
  `SeedLoyaltyTest`, `SchemaTest` (V19's tables); changed: `CheckoutFixturesTest` (the toggle is the server's,
  PointsApplied is the toggle turned on), `CheckoutRoutesTest`, `AccountRoutesTest`, `AccountFixturesTest`, `KoinGraphTest`;
  in the client `PlusTrialWiringTest` (start trial sends the dialog's post and the page is drawn again; not now closes
  the dialog and sends nothing; a refused trial closes the dialog and draws the page again), `CheckoutWiringTest` (the
  toggle turns the points on and off), `AccountWiringTest` (the trial's button presents the trial dialog).
- **Mutations**, each run against its tests on the Linux box and reverted: no credit on arrival (3 `PointsTest`
  failures), redemption skipped (3), no give-back on compensation (declined card), no balance check (spent twice), the
  waived fee zeroed (savings), the store's conditional `plus` update dropped (the trial test), the return's points not
  settled (return test), the loyalty not re-seeded on a seeded database (`SeedLoyaltyTest`), the points line dropped
  from the fingerprint (`CheckoutFixturesTest`), the toggle and «Start trial» made inert (both wiring tests). Removing
  the use case's own member check failed nothing — the store's conditional update refuses the same requests — so the
  check was removed.
- **Where it ran**: everything on the Linux box (WSL, 16 GB, shared): `:composeApp:wasmJsBrowserDistribution`, then
  `check :server:installDist` green — 251 server tests (PostgreSQL and shildik in Testcontainers), 139 desktop tests,
  124 `viddikVerify` — and `scripts/image-check.sh` (tag `haul/server:b23`, port 18123): ready, 923 of 923 classes
  from the AOT cache, page 200 — and, after rebasing onto B-26, `:e2e:test` against that image (`WholePathTest`,
  green); the gate again after the rebase, its tests up to date. Rebased again onto B-20 (V18, the Saved list): the
  whole gate again — 262 server tests, 145 desktop tests, 134 `viddikVerify`, all green; the six artboards' parity
  unchanged — `scripts/image-check.sh` on a fresh PostgreSQL (V17, V18 and V19 migrate; 952 of 952 classes from the
  cache) and `scripts/e2e.sh` against that image, green.
- **Findings**: a saga in flight across the deploy meets the definition with one more step (`redeem-points`); petich's
  chain fingerprint is what decides its fate, as for any change to the saga. B-20 merged V18 before this item, so
  the numbers are in order and the stand migrates V18 then V19. The return dialog still states the refund as the lines' value; for an order paid partly in points the card gets
  that less the points' share (above), which the dialog does not yet say.

