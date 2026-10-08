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
