---
id: endpoint-membership
title: Haul Plus
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared PlusTrialDialog
  - haul:shared ErrorCode
parent_feature: feature-membership
---

# API: Haul Plus

> The trial exists since B-23 and is described as the code has it. There are no route classes in
> `shared`: the path is the server's string (`MembershipPaths` in
> `server/src/main/kotlin/io/github/youndie/haul/feature/membership/MembershipRouting.kt`), handed to the
> client inside the trial dialog (`PlusTrialDialog.url`,
> `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/MembershipComponents.kt`). Points have no route
> of their own: the balance is drawn into the cart, checkout, account and order trees, and the toggle is
> `CheckoutChoice.usePoints` on `PUT /api/v1/me/checkout` ([endpoint-checkout](endpoint-checkout.md)).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/me/plus/trial` | haul-server | customer (shildik bearer) | yes | request: no body; starts the customer's 30-day trial; answers `201` with kompot's `sequence` of `close` and `refresh` — the dialog goes and the page is drawn again for a member |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST` `/api/v1/me/plus/trial` | `server/src/main/kotlin/io/github/youndie/haul/feature/membership/MembershipRouting.kt` → `PlusCommands.startTrial` (`server/src/main/kotlin/io/github/youndie/haul/feature/membership/domain/PlusCommands.kt`) → `server/src/main/kotlin/io/github/youndie/haul/feature/membership/data/ExposedMemberships.kt` |
| the dialog and the home page's Plus block | `server/src/main/kotlin/io/github/youndie/haul/feature/membership/screen/PlusOffer.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/MembershipComponents.kt` (`PlusTrialDialog`, `PlusBenefit`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

Not copied here; what the server does: the trial writes a `memberships` row (free until 30 of the
store's days on, then «renewing» monthly at $4.99, never charged) and sets `customers.plus`, in one
transaction; a member is refused by the store's conditional update, so two requests racing start one
membership. The dialog («30 days free», four `PlusBenefit`s, «30 days free, then $4.99/month», «Start
trial», «Not now») is presented by the home page's «Try 30 days free» and the account's PlusOffer tile
for a customer; a guest's «Try 30 days free» is a `navigate` to `/sign-in`.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/me/plus/trial` | `401` unauthenticated; `409` already_member — a member on a trial or paying (`MembershipRoutesTest.a member asking for the trial is refused as already a member`) |
