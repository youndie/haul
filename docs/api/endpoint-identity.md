---
id: endpoint-identity
title: Guests, sign-in, cart merge, addresses
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared GuestDto
  - haul:shared SignInSettings
  - haul:shared AddressEntry
parent_feature: feature-identity
---

# API: Guests, sign-in, cart merge, addresses

> Describes the routes as they stand after B-11 (guests), B-12 (sign-in settings, the merge) and
> B-14 (the address form). There are no route classes in `shared`: the paths are the server's strings
> (`GUESTS` and `SIGN_IN_SETTINGS` in `IdentityRouting.kt`, `CartPaths.MERGE`,
> `CheckoutPaths.ADDRESSES`), and the contract is `GuestDto`, `GUEST_HEADER`, `SignInSettings`,
> `AddressEntry` and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/guests` | haul-server | public (none) | yes | request: —; answers `201` with `GuestDto` (`id`, `g-` and a random UUID), which the client sends as `X-Haul-Guest` from then on |
| `GET` `/api/v1/sign-in` | haul-server | public (none) | yes | request: —; answers `SignInSettings` — the shildik realm's `issuer`, the storefront's public `clientId`, the `scope` (`openid profile email offline_access`) and the `mergeUrl` |
| `POST` `/api/v1/me/cart/merge` | haul-server | customer (shildik bearer) | yes | request: header `X-Haul-Guest`, no body; moves the guest's cart into the customer's; answers kompot's `refresh` |
| `POST` `/api/v1/me/addresses` | haul-server | customer (shildik bearer) | yes | request: `AddressEntry`; saves the address and makes it the checkout's; answers kompot's `refresh` (the checkout redrawn, [endpoint-checkout](endpoint-checkout.md)) |

The tiers are decided at the mount in `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`:
the public routes take an optional bearer (one that does not verify is `401`, not ignored), the
customer tier a required one. A token is verified by shildik's `oidc-auth-server` (signature against
the realm's JWKS, lifetime, `iss`) and its `azp` must be the storefront's client (`installSignIn` in
`SignIn.kt`). With sign-in off — neither `HAUL_OIDC_ISSUER` nor `HAUL_OIDC_CLIENT_ID` set — every
bearer is refused and `GET /api/v1/sign-in` answers `503`.

`X-Haul-Guest` is read by the cart's routes ([endpoint-cart](endpoint-cart.md)), which accept a
customer or a guest (a token wins), and by the screen routes for the header's cart count; a guest id
the server never issued is nobody there.

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST` `/api/v1/guests` | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/IdentityRouting.kt` (`identityRouting`) → `Guests.create` (`server/src/main/kotlin/io/github/youndie/haul/feature/identity/data/ExposedGuests.kt`) |
| `GET` `/api/v1/sign-in` | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/IdentityRouting.kt` (`identityRouting`) → `SignInConfig.settings` (`server/src/main/kotlin/io/github/youndie/haul/feature/identity/SignIn.kt`) |
| `POST` `/api/v1/me/cart/merge` | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/IdentityRouting.kt` (`customerIdentityRouting`) → `CartCommands.merge` (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/CartCommands.kt`), one transaction in `ExposedCartRepository.merge` |
| `POST` `/api/v1/me/addresses` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt` → `CheckoutCommands.addAddress` (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`), rules in `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/AddressRules.kt` |
| who is calling | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/Callers.kt` — a verified token is the customer (created on first sight), else a guest id the server issued, else nobody |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt` (`GuestDto`, `GUEST_HEADER`), `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInSettings.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` (`AddressEntry`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

`GuestDto`, `SignInSettings` and `AddressEntry` are in the files above; not copied here. Guests are
stored in `guests` (`server/src/main/resources/db/migration/V4__cart.sql`), customers in `customers`
(`server/src/main/resources/db/migration/V8__customers.sql`), addresses in `addresses`
(`server/src/main/resources/db/migration/V9__checkout.sql`).

The merge: the same SKU's quantities summed and capped at ten and at the stock (never below one),
the guest's other lines appended after the customer's, the customer's promo code kept — else the
guest's comes along — and the guest's cart deleted; the guest itself is kept. Merging a guest with no
cart changes nothing.

The address form: `street`, `city` and `zip` required, the ZIP five digits; street up to 100
characters, city 60, apartment and door code 20, courier note 200. A refused form is kept and the
checkout tree draws it again with an error under each field.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/guests` | none of its own. `429 rate_limited` is *not built*: nothing limits guest creation, and `ErrorCode` has no `rate_limited` |
| `GET` `/api/v1/sign-in` | `503` unavailable — sign-in is off on this server |
| `POST` `/api/v1/me/cart/merge` | `401` unauthenticated (no token, or one that does not verify); `404` guest_not_found (no `X-Haul-Guest`, or an id the server never issued) |
| `POST` `/api/v1/me/addresses` | `401` unauthenticated; `400` validation_failed with `fields` — one `FieldError` per field at fault, `field_required` or `field_invalid` — and `field` the first of them; `400` validation_failed for a body that is not the JSON the command takes |

Automated in `server/src/test/kotlin/io/github/youndie/haul/feature/identity/IdentityRoutesTest.kt`
(`the merge needs a customer and a guest the server issued`, `the browser is told where to sign in,
and a server without sign-in says so`, `a token that does not verify is 401 unauthenticated`) and
`server/src/test/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRoutesTest.kt` (`the address
form is refused field by field and drawn again`).
