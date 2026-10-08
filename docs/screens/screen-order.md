---
id: screen-order
title: Order
type: client_screen
platform: [web]
status: draft
entry:
  web: "/account/orders/{orderId}"
parent_feature: feature-orders
calls_api:
  - endpoint-orders
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Order)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Order_Loading
    Placed: Order_Placed
    InTransit: Order_InTransit
    ReadyForPickup: Order_ReadyForPickup
    Delivered: Order_Delivered
    ReturnDialog: Order_ReturnDialog
    Returned: Order_Returned
    Cancelled: Order_Cancelled
    NotFound: Order_NotFound
    Error: Order_Error
---

# Screen: Order

Not on the canvas at all; the account's «Details» link and «Place order» both lead here.

> **The address is not decided (for B-18).** This document's entry is `/account/orders/{orderId}`;
> placement (B-16) navigates to `/orders/{id}`, following [endpoint-orders](../api/endpoint-orders.md)
> (`GET /ui/orders/{id}`). B-18 or the owner reconciles the two — see endpoint-orders, «The order
> page's address». Until then neither address is in `StorefrontPage` and the order page is not built.

The order the page will show exists since B-16: `placing` / `placed` / `cancelled` with
`cancel_reason`, one shipment per seller (`server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Order.kt`).
The Placed state's #HL-48302 is the first order number a fresh store gives (`order_numbers` in
`server/src/main/resources/db/migration/V10__orders.sql`), and the Cancelled state's declined card
···· 0002 is the payment simulator's test card.

What the page will draw from exists since B-17: `OrderTracking.track(customer, order)`
(`server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/OrderTracking.kt`) answers the
order's own customer only, with its `OrderProgress` — `placing`, `placed`, `packed`, `in_transit`,
`ready_for_pickup`, `delivered`, `picked_up`, `cancelled` — and per shipment its history, its share,
what was captured, and, only while it waits at a point, the pickup code and the day it is held until.
The states below map from that progress: Placed, InTransit, ReadyForPickup, Delivered and Cancelled
have artboards; **`packed` has no artboard of its own** (B-18 decides how it is drawn), nor does
`picked_up` apart from Delivered. The sample orders of these states are B-18's fixtures, not seeded.

## 0a. Code anchors

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/order/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account/orders/{orderId}` in the browser.
- **Shown when:** signed in; a guest is sent to sign-in.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** header, placeholder progress and lines
- [ ] **Placed:** «Thanks, Maya — order #HL-48302 is placed», $512.00, two shipments (Sony tomorrow, Brooklyn Home Co. Thu Oct 9), progress at Placed
- [ ] **InTransit:** #HL-48211: progress Placed · Packed · **In transit** · Delivered, «Arriving tomorrow, 15:00–18:00»
- [ ] **ReadyForPickup:** #HL-47960: point, hours, «kept until Oct 10», pickup code 4821
- [ ] **Delivered:** #HL-46102: delivered, per line «Write a review», «Return items», «Reorder»
- [ ] **ReturnDialog:** dialog over Delivered: lines with checkboxes, reason, «Request return»
- [ ] **Returned:** #HL-44019: return refunded, $87.50 back to the card
- [ ] **Cancelled:** #HL-48303 cancelled: «Your card ···· 0002 was declined», nothing charged, «Back to cart»
- [ ] **NotFound:** «Order not found», link to orders
- [ ] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1205 / 1348; Placed 1446 / 1814; InTransit 1345 / 1662; ReadyForPickup 1074 / 1481; Delivered 1125 / 1581; ReturnDialog 1125 / 1581; Returned 1146 / 1446; Cancelled 1193 / 1550; NotFound 719 / 545; Error 900 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-orders | [endpoint-orders](../api/endpoint-orders.md) |

## 5. Navigation (summary)

- see the parent feature
