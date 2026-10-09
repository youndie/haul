---
id: screen-order
title: Order
type: client_screen
platform: [web]
status: active
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

The account's «Details», «Track» and active order cards, the header's «Orders» history and «Place
order» all lead here. The address is `/account/orders/{orderId}` (`StorefrontPage.Order`), its tree
`GET /ui/account/orders/{id}` — under the account, as the canvas's crumbs «Account / Orders / #HL-48302»
say (research D4, «Decided in B-18»); placement navigates here (`OrderPaths.page`).

The page is drawn from `OrderTracking.track(customer, order)`
(`server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/domain/OrderTracking.kt`), which answers
the order's own customer only, with its `OrderProgress` — `placing`, `placed`, `packed`, `in_transit`,
`ready_for_pickup`, `delivered`, `picked_up`, `cancelled`, `returning`, `returned` — and per shipment its
history, its share, what was captured and, only while it waits at a point, the pickup code and the day it
is held until. Placed, InTransit, ReadyForPickup, Delivered, Returned and Cancelled have artboards;
`packed` is drawn as Placed with its step reached, `picked_up` as Delivered, and a partly returned order
or a return in flight («Return requested», «Return picked up») with no artboard of its own, from the
Returned artboard's pieces (B-21). A Haul Pay order draws its plan under the payment fact (B-24, no
artboard: built from the summary's own pieces; goldens `Order_HaulPay` / `Order_HaulPay_Phone` from the
server's tree of #HL-48230, no parity reference). A courier order's address is the order's own copy (B-40). The
sample orders of these states are fixtures (`server/src/test/kotlin/io/github/youndie/haul/testing/SampleOrders.kt`),
not seeded; #HL-48302 is the first number a fresh store gives, placed through placement itself.

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderViews.kt`, the return dialog `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/ReturnDialog.kt` |
| The live page | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/LiveUpdates.kt` (`ktorRealtime`), kompot's `KompotRealtimeProvider` around the page in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`; the server's `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt` |
| Client shell: Loading, NotFound and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`OrderLoading`), `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderNotFound.kt` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt` |
| The contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/OrderComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account/orders/{orderId}` in the browser.
- **Shown when:** signed in. A guest who opens the address directly gets the tree's `401`, which the
  shell draws as the sign-in prompt («This order needs a sign-in», «Sign in to continue»), returning here
  once signed in ([feature-identity](../features/feature-identity.md), B-44).

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** header, placeholder progress and lines (`OrderLoading`)
- [x] **Placed:** «Thanks, Maya — order #HL-48302 is placed», $512.00, two shipments (Sony tomorrow, Brooklyn Home Co. Thu Oct 9), progress at Placed
- [x] **InTransit:** #HL-48211: progress Placed · Packed · **In transit** · Delivered, «Arriving tomorrow, 15:00–18:00»
- [x] **ReadyForPickup:** #HL-47960: point, hours, «kept until Oct 10», pickup code 4821
- [x] **Delivered:** #HL-46102: delivered, per line «Write a review» (the review dialog), «Return items» (presents the return dialog while a line is inside its window, then «Returns closed on …»), «Reorder»
- [x] **ReturnDialog:** dialog over Delivered: lines with checkboxes, reason, the refund the ticked lines add up to, «Request return»; the rules are checked before sending and a refusal is drawn over the buttons. For an order paid partly with points the box reads the card amount and beneath it «+ N points back», both summed from the ticked lines' `refundCents` and `pointsBack` (B-50; `order_delivered_with_points.json`, no artboard); a Haul Pay order reads «Refund $x to your Haul Pay plan»
- [x] **Returned:** #HL-44019: return refunded, $87.50 back to the card, «−174 points»; for a points-paid order the heading, the payment fact and «Refunded» name the card's money and «+ N points back» (B-55, server-built, no artboard)
- [x] **Cancelled:** #HL-48303 cancelled: «Your card ···· 0002 was declined», nothing charged, «Back to cart»
- [x] **NotFound:** «Order not found»; «Go to your orders» follows the last drawn header's «Orders» to `/account/orders`
- [x] **Error:** header, «This order didn't load», Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1205 / 1348; Placed 1446 / 1814; InTransit 1345 / 1662; ReadyForPickup 1074 / 1481; Delivered 1125 / 1581; ReturnDialog 1125 / 1581; Returned 1146 / 1446; Cancelled 1193 / 1550; NotFound 719 / 545; Error 900 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-orders | [endpoint-orders](../api/endpoint-orders.md) |

## 5. Navigation (summary)

The server builds every target and the shell follows it; a command's refusal draws the page again.

- «Reorder» → `POST /api/v1/me/orders/{id}/reorder` (`CartCommand.Reorder`), then the answer's `navigate /cart`
- «Write a review» → the review dialog (kompot `present`, [feature-reviews](../features/feature-reviews.md))
- «Return items» → the return dialog (`OrderTotals.returnAction`, kompot `present`); «Request return» → `POST /api/v1/me/orders/{id}/returns`, answered `201` with `close` then `refresh`
- «Back to cart» on a cancelled order → `/cart`
- nothing pressed: while the page is shown the client listens to `GET /ui/updates?topic=order:<id>` (the channel the tree names) and swaps the order's body in place on each frame (B-29); leaving the page stops listening
- the «Orders» crumb, «Go to your orders» → `/account/orders` (screen-account)

## 6. Quirks

- `Order_Placed_Phone` reads 4.83 % in parity, inside the 5 % tolerance since the Sony line writes its
  listing name as the canvas does (B-45; 5.11 % before); the title still breaks where `BalancedText` puts
  the narrowest balanced break and Chrome's `text-wrap: balance` keeps a wider one.
- «Go to your orders» on an order reloaded at a missing number has no header drawn yet, so it does
  nothing (as Product_NotFound's link).
- The live page has not been walked in a browser: streaming through Ktor's JS engine is proven by the
  server's test on a real port, the client's on the desktop and the e2e (B-29's findings).
- The Haul Pay schedule has no artboard; its look is the summary's own pieces (B-24).
