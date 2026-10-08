---
id: endpoint-account
title: Account overview
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared AccountRoutes
parent_feature: feature-account
---

# API: Account overview

> Drafted from the product brief. Since B-12 `GET /ui/account` exists as a **placeholder**: in the
> customer tier, it answers the frame and a `PageTitle` «Hi, <first name>» so that «Account needs a
> sign-in» has a route and the header's account shortcut a destination. The Account tree below —
> overview, orders, Plus — is *target* (B-19); the contract classes in `shared` will be the truth,
> and this document is checked against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: today the frame and «Hi, <first name>»; *target* (B-19): Account (Content, NotMember, NoOrders) |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/account` | `server/src/main/kotlin/io/github/youndie/haul/feature/account/AccountRouting.kt` — the placeholder; the Account tree is planned there (B-19) |
| contract | planned in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` — the Account tree's components; there are no route classes in `shared` (the paths are the server's strings), so `AccountRoutes` in the frontmatter names the planned contract, not a class |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account` | `401` unauthenticated — no token, a token that does not verify, or a guest id alone (`IdentityRoutesTest.the account needs a sign-in`) |
