---
id: endpoint-orders
title: Orders and returns
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared OrderBody
  - haul:shared ReturnForm
  - haul:shared ReturnEntry
  - haul:shared ErrorCode
parent_feature: feature-orders
---

# API: Orders and returns

> The order page and reorder exist since B-18, returns since B-21; all are described as the code has
> them. There are no route classes in `shared`: the paths are the server's strings (`OrderPaths` in
> `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt`, `ReturnPaths` in
> `server/src/main/kotlin/io/github/youndie/haul/feature/returns/ReturnsRouting.kt`), handed to the
> client inside the tree — `OrderTotals.reorderUrl`, `ReturnForm.url` — and the contract is the
> components (`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/OrderComponents.kt`), the return's
> body (`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/returns/ReturnCommands.kt`) and
> `ErrorCode`. The orders' history, `GET /ui/account/orders`, is the account's
> ([endpoint-account](endpoint-account.md)).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account/orders/{id}` | haul-server | customer (shildik bearer) | yes | request: —; answers tree: Order — Placed, InTransit, ReadyForPickup, Delivered, Returned and Cancelled are this one tree, drawn from the order's progress; the storefront's address is `/account/orders/{id}` (`StorefrontPage.Order`) |
| `POST` `/api/v1/me/orders/{id}/reorder` | haul-server | customer (shildik bearer) | yes | request: —; puts the order's SKUs back into the cart, selected; answers kompot's `navigate` to `/cart` |
| `POST` `/api/v1/me/orders/{id}/returns` | haul-server | customer (shildik bearer) | yes | request: `ReturnEntry` (`lines` by their position in the order, `reason` by its id); answers `201` with kompot's `sequence` of `close` and `refresh` |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/account/orders/{id}` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt` over `OrderTracking.track` (`server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/OrderTracking.kt`) |
| `POST` `/api/v1/me/orders/{id}/reorder` | `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt` |
| `POST` `/api/v1/me/orders/{id}/returns` | `server/src/main/kotlin/io/github/youndie/haul/feature/returns/ReturnsRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/returns/domain/RequestReturn.kt`; collected and refunded by `server/src/main/kotlin/io/github/youndie/haul/feature/returns/domain/ReturnSimulator.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/OrderComponents.kt` (`OrderBody`, `OrderTotals`, `ReturnForm`), `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/returns/ReturnCommands.kt` (`ReturnEntry`, `returnProblems`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |
| storage | `server/src/main/kotlin/io/github/youndie/haul/feature/order/data/ExposedOrders.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/returns/data/ExposedReturns.kt`; `server/src/main/resources/db/migration/V14__order_address.sql`, `server/src/main/resources/db/migration/V17__returns.sql` |

## Request and response bodies

Not copied here; what the server does with them:

* **The order's tree** — one `OrderBody`: crumbs, the meta line, the title with its accent and lead,
  `OrderSteps`, a cancelled order's `OrderNotice`, the `PickupCode` while a shipment waits at a point,
  the `OrderShipment`s with their `OrderItem`s (and a «Returned items» card once a return is asked for),
  and `OrderTotals` with its facts and its ways on: «Reorder» (`reorderUrl`), «Return items»
  (`returnAction`, kompot's `present` of a `ReturnForm`, offered while some line is inside its window).
  A courier order's address is the order's own copy (`orders.address`, B-40), not the saved address.
* **Reorder** — each SKU of the order set in the cart through the cart's own command
  (`CartCommands.changeLine`), selected, at the order's quantity capped at ten and at the stock; a line
  already holding that many is only selected, so a second press adds nothing; a SKU gone or out of
  stock is left out and the answer does not name it (no artboard draws a message — decided as product
  owner, B-18).
* **`ReturnEntry`** — the lines to return, whole, by `ReturnLine.position`, and a reason id
  (`doesnt_fit`, `not_as_described`, `damaged`, `changed_mind`). `returnProblems` is the rule both sides
  hold the form to: the client checks it before sending, the server refuses by it. One return per order;
  each line within 30 store days of its own shipment's arrival; the refund is each line's share of the
  items as paid, without delivery, and is paid when the parcel is back
  ([feature-orders](../features/feature-orders.md)).

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account/orders/{id}` | `401` unauthenticated; `404` order_not_found — another customer's order and a missing one alike (`OrderRoutesTest.another customer's order and a missing one get the same answer`) |
| `POST` `/api/v1/me/orders/{id}/reorder` | `401` unauthenticated; `404` order_not_found, for another customer's order too |
| `POST` `/api/v1/me/orders/{id}/returns` | `400` validation_failed — every field at fault (`lines`, `reason`, or `request` for a body that is not JSON); `401` unauthenticated; `404` order_not_found (not yours, or none); `409` already_returned; `422` not_delivered; `422` return_window_closed (`ReturnRoutesTest.late return`) |
