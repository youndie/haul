---
id: endpoint-account
title: Account overview
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared AccountBody
  - haul:shared ErrorCode
parent_feature: feature-account
---

# API: Account overview

> The overview and the orders' history exist since B-19, their tiles read from the points ledger and
> the membership since B-23; both are described as the code has them. There are no route classes in
> `shared`: the paths are the server's strings (`AccountPaths` in
> `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt`, the storefront's
> addresses in `Frame`), and the contract is the account's body
> (`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/AccountComponents.kt`). The Saved list, drawn
> in the same body, is [endpoint-saved](endpoint-saved.md); the order pages and reorder are
> [endpoint-orders](endpoint-orders.md).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: Account — Content, NotMember and NoOrders are this one tree, drawn from the customer's orders, points and membership |
| `GET` `/ui/account/orders` | haul-server | customer (shildik bearer) | yes | request: query `status` (`active`, `delivered`, `returned`, `cancelled`; none or an unknown one is all); answers tree: Account with Orders selected — the history, the orders on their way first, then newest first |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/account` | `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt` |
| `GET` `/ui/account/orders` | `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt` → `AccountScreen`, over `OrderRepository.orders` (the customer in the query's filter) and `OrderTracking.of` |
| the tiles' sources | `Loyalty` and `SavedLists` in `server/src/main/kotlin/io/github/youndie/haul/feature/account/domain/Account.kt`, bound to `PlusCommands` (`server/src/main/kotlin/io/github/youndie/haul/feature/membership/domain/PlusCommands.kt`) and the Saved list (`server/src/main/kotlin/io/github/youndie/haul/feature/saved/`) |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/AccountComponents.kt` (`AccountBody`, `AccountTileKind`, `ActiveOrders`, `OrderHistory`) |

## Request and response bodies

Not copied here; what the server does with them: one `AccountBody` — the profile («Joined <month>»
while a non-member has never ordered), the menu (Overview; Orders with the active orders' count; Saved
with the saved count), the title, the overview's tiles (`AccountTileKind`: Points, Plus, PlusOffer —
whose button presents the trial dialog — and PriceDrops), up to three `ActiveOrders` cards drawn by the
order page's own builder (`OrderScreen.card`), and the `OrderHistory` — its filter chips (each a
`navigate` to its own address), its rows with «Track», «Details» or «Reorder», the empty state, or a
sentence for a filter that matches nothing. The overview's history is the last four orders not active,
with «All orders» whenever that leaves one out.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account` | `401` unauthenticated — no token, a token that does not verify, or a guest id alone (`AccountRoutesTest.the account and its history need a sign-in`, `IdentityRoutesTest.the account needs a sign-in`) |
| `GET` `/ui/account/orders` | `401` unauthenticated (`AccountRoutesTest.the account and its history need a sign-in`) |
