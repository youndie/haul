---
id: endpoint-identity
title: Guests, cart merge, addresses
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared GuestDto
parent_feature: feature-identity
---

# API: Guests, cart merge, addresses

> Drafted from the product brief. `POST /api/v1/guests` exists since B-11 and is described as the
> code has it; the cart merge and the addresses are still *target* (B-12, B-14). There are no route
> classes in `shared`: the paths are the server's strings (`GUESTS` in `IdentityRouting.kt`), and
> the contract is `GuestDto`, `GUEST_HEADER` and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/guests` | haul-server | public (none) | yes | request: —; answers `201` with `GuestDto` (`id`, `g-` and a random UUID), which the client sends as `X-Haul-Guest` from then on |
| `POST` `/api/v1/me/cart/merge` | haul-server | customer (shildik bearer) | yes | *planned, B-12*: request: header `X-Haul-Guest`; answers action: refresh |
| `POST` `/api/v1/me/addresses` | haul-server | customer (shildik bearer) | yes | *planned, B-14*: request: the address form; answers action: refresh Checkout |

`X-Haul-Guest` is read by the cart's routes ([endpoint-cart](endpoint-cart.md)), which answer
`401 unauthenticated` without an id the server issued; the catalog and search routes do not read it
yet.

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST` `/api/v1/guests` | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/IdentityRouting.kt` → `Guests.create` (`server/src/main/kotlin/io/github/youndie/haul/feature/identity/data/ExposedGuests.kt`) |
| `POST` `/api/v1/me/cart/merge` | not built (B-12); planned in `server/src/main/kotlin/io/github/youndie/haul/feature/identity/` |
| `POST` `/api/v1/me/addresses` | not built (B-14); planned in `server/src/main/kotlin/io/github/youndie/haul/feature/identity/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt` (`GuestDto`, `GUEST_HEADER`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

`GuestDto` is in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt`;
not copied here. The guests are stored in `guests` (`server/src/main/resources/db/migration/V4__cart.sql`).
The merge's and the address form's bodies do not exist yet.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/guests` | none of its own; `429` rate_limited is *target* — nothing limits guest creation yet, and `ErrorCode` has no `rate_limited` |
| `POST` `/api/v1/me/cart/merge` | `401`, `404` guest_not_found (planned) |
| `POST` `/api/v1/me/addresses` | `400` validation_failed, `401` (planned) |
