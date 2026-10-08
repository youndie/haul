---
id: endpoint-orders
title: Orders and returns
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared OrderRoutes
parent_feature: feature-orders
---

# API: Orders and returns

> Drafted from the product brief. None of the four routes below exists yet (B-18, B-19, B-21);
> what exists since B-16 is the order they will show — placed through `POST /api/v1/orders`
> ([endpoint-checkout](endpoint-checkout.md)) and stored in `orders`, `order_lines` and `shipments`
> (`server/src/main/resources/db/migration/V10__orders.sql`). There will be no route classes in
> `shared` (the paths are the server's strings), so `OrderRoutes` in the frontmatter names the planned
> contract, not a class.

## The order page's address — not decided (for B-18)

Placement answers `navigate` to **`/orders/{id}`** (`CheckoutPaths.order` in
`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt`): the storefront's
address convention puts this document's `GET /ui/orders/{id}` at `/orders/{id}`. [screen-order](../screens/screen-order.md)
names **`/account/orders/{orderId}`** instead. B-16 followed this document; neither address is in
`StorefrontPage` (`shared/src/commonMain/kotlin/io/github/youndie/haul/StorefrontPage.kt`), so today a
reload of either is `404`, and the client loads `/ui/orders/{id}`, which answers `404` too. B-18 — or
the owner — picks one, changes the other document, `CheckoutPaths.order` if needed, and adds the
address to `StorefrontPage`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/orders/{id}` | haul-server | customer (shildik bearer) | yes | *planned, B-18*: request: —; answers tree: Order (per status) |
| `GET` `/ui/account/orders` | haul-server | customer (shildik bearer) | yes | *planned, B-19*: request: status filter, page; answers tree: Account with Orders selected |
| `POST` `/api/v1/me/orders/{id}/reorder` | haul-server | customer (shildik bearer) | yes | *planned, B-18*: request: —; answers action: navigate to the cart, with the unavailable lines named |
| `POST` `/api/v1/me/orders/{id}/returns` | haul-server | customer (shildik bearer) | yes | *planned, B-21*: request: the return form; answers action: close the route, refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/orders/{id}` | planned in `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `GET` `/ui/account/orders` | planned in `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `POST` `/api/v1/me/orders/{id}/reorder` | planned in `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| `POST` `/api/v1/me/orders/{id}/returns` | planned in `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` — the Order tree's components (planned) |
| the order today | `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Order.kt` (`OrderStatus`, `CancelReason`, `ShipmentStatus`), `server/src/main/kotlin/io/github/youndie/haul/feature/order/data/ExposedOrders.kt` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` once it exists; not copied here.
What the tree will draw from: an order's status is `placing`, `placed` or `cancelled` with a
`cancel_reason` (`payment_declined`, `failed`); its lines as bought; one shipment per seller, written
`placed` (or `cancelled`) by placement and moved on by the fulfilment simulator (B-17).

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/orders/{id}` | `401`, `404` order_not_found (planned: `ErrorCode` has no `order_not_found` yet) |
| `GET` `/ui/account/orders` | `401` |
| `POST` `/api/v1/me/orders/{id}/reorder` | `401`, `404` |
| `POST` `/api/v1/me/orders/{id}/returns` | `400` validation_failed, `401`, `404`, `422` return_window_closed / not_delivered |
