---
id: B-29
title: "live order tracking through kompot-realtime"
status: done
priority: P3
size: M
stage: stage-9-ship
epic: feature-orders
blocked_by: [B-18]
---

# B-29 — live order tracking through kompot-realtime

Live tracking through kompot-realtime, available on the JVM (research consequence 3).

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered in v1.

- AC: the order screen moves to «In transit» without a refresh.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/LiveUpdates.kt`.

## Done (2026-10-09)

- **What kompot-realtime is** (at the pinned `0.40.0.210`, no bump): a frame contract (`kompot-realtime`:
  `UpdateComponentMessage(componentId, component)`, `KompotScreenResponse(screen, realtimeTopic)`, the
  `KompotRealtimeSource` port), delivery to one process's subscribers (`kompot-realtime-server`:
  `KompotUpdateBroadcaster` over an in-memory bus, jvm only) and the client's half (`kompot-client`:
  `KompotRealtimeProvider`, which swaps a node by its id in `RenderNode`). The transport is the
  application's; kompot's reference is server-sent events with the topic in the query (SPEC §16.6).
- **Decisions** (research consequence 3, «Decided in B-29»): the order's tree comes in `KompotScreenResponse`
  naming `order:<id>`; `GET /ui/updates?topic=` is the stream, in the customer tier, refused like the page
  (`404 order_not_found` for a topic that is not an order's or an order that is not the caller's, `401` with
  no token); the broadcaster delivers by the order **and its customer**, so a listener only ever hears its
  own customer's order; every stream opens with the order as it is (a move between the page's load and its
  listening, or during a reconnect, is not lost), then one frame per move, a `ping` every 20 s. The browser
  reconnects by itself — a second, doubling to thirty — and stops only on a refusal (`401`/`404`), which
  kompot reports while the page keeps what it drew; no reload is needed, because the next stream starts
  where the order is.
- **Server**: `OrderMoves` (`feature/order/domain/`) — the fulfilment simulator, the return simulator and Haul
  Pay's plans tell each order a pass moved; never holding a move up. `LiveOrders` draws the moved order's
  body again with `OrderScreen` (the customer's first name from `Customers.customer`) and broadcasts it, only
  when this process has a listener; started with the application. Ktor's `ktor-server-sse` and
  `kompot-realtime-server` added.
- **Client**: `HaulTransport.screen` reads a bare tree or the envelope, by shape; `ktorRealtime`
  (`shell/LiveUpdates.kt`) streams through `Identity.send`; `Storefront` listens while a page that named a
  channel is shown and starts over from the tree a refresh brings.
- **Tests written**: `LiveOrderTest` (a move reaches the subscribers of that order and of no other; over HTTP
  against the application on a real port — the test engine's virtual time never lets the stream start — the
  page hears its order go in transit, and Sam, a topic that is no order's and no token are refused);
  `LiveOrderPageTest` (a pushed update redraws the order page in transit without loading it again; leaving
  the page stops listening, a page with no channel opens none); `LiveUpdatesTest` (events and pings, a
  stream that ends or never answered opened again, a refusal ends it, the backoff); the e2e listens from
  placement and asserts the refund is pushed. `OrderRoutesTest` and `ReturnRoutesTest` read the envelope.
- **Not covered**: the saga's own moves (placing → placed or cancelled) are not pushed — they happen inside
  the placement request, before the page opens; a second replica would need kompot's Redis bus
  (`charts/haul/values.yaml` says so beside `replicas: 1`).
