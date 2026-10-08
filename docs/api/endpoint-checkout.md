---
id: endpoint-checkout
title: Checkout and placement
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared CheckoutChoice
  - haul:shared PlaceOrderRequest
  - haul:shared CheckoutSummary
  - haul:shared ErrorCode
parent_feature: feature-checkout
---

# API: Checkout and placement

> The checkout routes exist since B-14, placement since B-16; both are described as the code has
> them. There are no route classes in `shared`: the paths are the server's strings (`CheckoutPaths`
> in `CheckoutRouting.kt`), handed to the client inside the tree — `DeliveryMethods.url`,
> `CheckoutAddress.url` and `choiceUrl`, `DeliverySlots.url`, `PickupPoints.url`,
> `PaymentMethods.url`, `CheckoutSummary.placeUrl` — and the contract is the command bodies
> (`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt`), the
> components (`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt`) and
> `ErrorCode`. The address form, `POST /api/v1/me/addresses`, is in
> [endpoint-identity](endpoint-identity.md).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/checkout` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: Checkout — Content, PickupPoint, ParcelLocker, Validation and PlaceError are all this one tree, drawn from what the customer chose |
| `PUT` `/api/v1/me/checkout` | haul-server | customer (shildik bearer) | yes | request: `CheckoutChoice` — only the fields given change, at least one; answers kompot's `refresh` (the checkout redrawn with a new quote) |
| `POST` `/api/v1/orders` | haul-server | customer (shildik bearer) | yes | request: `PlaceOrderRequest` (`quote`: the fingerprint `CheckoutSummary.quote` carried), header `Idempotency-Key`; runs the order saga ([feature-orders](../features/feature-orders.md)) and answers `202` with kompot's `navigate` to `/orders/{id}` |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/checkout` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt` over `CheckoutCommands.state` |
| `PUT` `/api/v1/me/checkout` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt` → `CheckoutCommands.choose` (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`) |
| `POST` `/api/v1/orders` | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt` → `Placement.place` (`server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`) → the saga in `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt` |
| the quote | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/Quote.kt` (`Quote.fingerprint`, `Quote.complete`); windows in `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/Slots.kt` and `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/data/ExposedDeliverySlots.kt`; ways to pay in `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/PaymentMethod.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` (`DeliveryMethod`, `CheckoutChoice`, `AddressEntry`, `PlaceOrderRequest`, `IDEMPOTENCY_KEY_HEADER`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

Not copied here; what the server does with them:

* **The quote** — the cart's *counted* lines (selected, in stock, unchanged) priced by the cart's own
  rules (`CartCommands.priced`, `Totals.of`), with the method, the address or the point, the window
  and the way to pay. Nothing is stored as «the quote»: it is computed again on every request, and
  `CheckoutSummary.quote` carries a SHA-256 fingerprint over everything priced. A quote is complete
  when a courier order has an address and a window, a point or locker order its point.
* **Defaults** — a choice not made, or no longer possible, is filled: the customer's first address,
  the first window with room, the first point of the method, the card ···· 4821. A promo code that
  expired after it was applied is not in the total and a notice says so.
* **Windows** — five days from tomorrow (store time), 09:00 / 12:00 / 15:00 / 18:00, three hours,
  20 orders each. A full window is drawn `available = false`. A window's place is **taken at
  placement**, not held by the quote (`DeliverySlots.reserve`, one conditional update, idempotent per
  holder; `release` is its compensation) — that is what lets a window fill between page and order.
  A window chosen that filled since is cleared and the tree draws a notice (PlaceError).
* **`CheckoutChoice`** — `method`, `addressId`, `pointId`, `slotId`, `payment`, the ids the tree's
  options carry. An address makes the method courier; a point decides the method (a locker's id is a
  locker order); a way to pay must be offered for the method and the total. A method that no longer
  allows the chosen way to pay falls back to the card.
* **Ways to pay** — `card-4821` (approved by the simulator), `card-0002` («Test card: always
  declined»), `haul_pay` (totals from $50 to $2,000, «4 payments of …»), `pay_on_delivery` (not for a
  parcel locker). There is no card form.
* **`PlaceOrderRequest`** and **`Idempotency-Key`** — checked in this order: no key, or a blank one,
  is `400 idempotency_key_missing`; one over 128 characters `400 validation_failed`. A key whose saga
  exists answers that saga — the same order for the same request (whatever the cart holds now),
  `409 idempotency_key_reused` for a different one. Otherwise checkout computes the quote again: a
  chosen window that filled is `409 slot_unavailable`, a fingerprint that is no longer the quote's
  `409 cart_changed`, an incomplete quote `400 validation_failed` (field `quote`). Only then is the
  key claimed, so a refused placement does not spend it. **The key is per quote**: a new quote under
  an old key is refused, so the client generates one per quote it places.
* **The answer** — `202` with `NavigateAction("/orders/{id}")` (`HL-` and a number from 48302) for a
  placed order **and** for one the saga cancelled because the card was declined; the order's status
  says which. Stock that ran out inside the saga is `409 out_of_stock`, a window that filled inside the
  saga `409 slot_unavailable`; both take nothing.

## Quirks

* The order page the answer navigates to, `/orders/{id}` (tree `GET /ui/orders/{id}`), is not built
  (B-18) and is not in `StorefrontPage`; screen-order names `/account/orders/{orderId}` instead —
  see [endpoint-orders](endpoint-orders.md).
* Placement runs the saga inside the request; a saga that failed and was undone (not a refusal) is
  answered `500 internal`.
* Points (`CheckoutSummary.redeem`, «Use N points») are not in the quote and nothing answers
  `points_balance_changed` (B-23).
* `GET /ui/checkout` with no selected line is `409 cart_empty`, not an empty checkout.

## Errors

Every refusal is an `ErrorBody`; a body that does not parse as the command's JSON is
`400 validation_failed` with field `body`.

| Route | Status and `code` |
|---|---|
| `GET` `/ui/checkout` | `401` unauthenticated, `409` cart_empty |
| `PUT` `/api/v1/me/checkout` | `400` validation_failed (no field given; a point not of the method given; an unknown way to pay; `body`), `401` unauthenticated, `404` slot_not_found / pickup_point_not_found / address_not_found (another customer's included), `409` slot_unavailable / cart_empty, `422` payment_method_not_allowed |
| `POST` `/api/v1/orders` | `400` idempotency_key_missing, `400` validation_failed (field `Idempotency-Key`, `quote` or `body`), `401` unauthenticated, `409` idempotency_key_reused / cart_changed / slot_unavailable / out_of_stock / cart_empty, `500` internal (a saga that failed and was undone). A declined card is not an error: `202` to the cancelled order |

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRoutesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/checkout/CheckoutQuoteTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/checkout/DeliverySlotsTest.kt` (the capacity
race), `server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/order/OrderSagaTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRestartTest.kt` — PostgreSQL
and shildik in Testcontainers.
