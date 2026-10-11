---
id: B-109
title: "server: an order's saga is visible in tracy"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-109 — server: an order's saga is visible in tracy

Found in the QA pass of 2026-10-10 (v0.3.0). With the synthetic shoppers walking the stand (B-31), tracy shows the HTTP side — `PUT /api/v1/cart/lines`,
`POST /api/v1/me/cart/merge`, `POST /api/v1/orders` 202, the order page polled — but no span or log for the saga's steps
(reserve, charge, fulfil, refund): `search_spans` with `saga` finds nothing and `top_templates` for `haul-server` is
empty. B-31's AC («sagas … in tracy») needs them.

- AC: each saga step is a span (or a log line with the order id as an entity key) under the order, so `get_entity` on an
  order id shows placement → delivery → return in tracy; checked on the stand.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/`.

## Done (2026-10-11)

- **Why tracy had no saga: nothing wrote to it.** haul installed tracy's server plugin and delivery loop
  (`ops/Observability.kt`), so each request left its span, and that was all — no code wrote a record through the
  agent and no appender was attached. `storedRecords: 0` and an empty `top_templates` were the wiring, not loss
  on the way; the sample rate and the level played no part.
- **Decision: an order writes its own trail through tracy's API** (`ops/OrderTrail.kt`, `TracyOrderTrail`), one
  line per thing that happened to it, with `orderId` as an indexed field — the entity key `get_entity` reads.
  The messages are constants (`OrderLines`): `order saga step done` / `refused` (with the reason) / `threw` /
  `undone`, `order shipment moved` (status, the moment it came due), `order shipment captured`, `order return
  requested` / `moved` / `refunded`. Each saga member runs as a span, `saga <key>` (`saga <key> undo` for a
  compensation), under the request it runs in — placement's `POST /api/v1/orders`. Without tracy the trail is
  `OrderTrail.NONE`.
- **Rejected: feeding it from petich's `PetichTracer`**, the engine's own per-saga hook. Its events name the saga,
  whose id is derived from the customer and their Idempotency-Key, not the order; only a member holds the payload.
  The members are wrapped in haul instead (`feature/order/saga/TracedMembers.kt`); the engine's `LinePetichTracer`
  at `debug` is unchanged.
- **Inside a request the lines join its trace**: the agent keeps one reference per order per trace whatever its
  tail decision, so a placement dropped by the sample rate still answers `get_entity`, without bodies. Outside
  one — the simulators' passes, the sweeper — each line is its own entry and always kept.
- **Tests**: `OrderInTracyTest` (4), against tracy's own agent and the batch it would send: Maya's order placed,
  delivered and partly returned — every line indexed under the order, the seven members done in order, the first
  shipment line `placed`, the capture before `in_transit`, `delivered`, then the return asked, collected, refunded,
  `refunded`; a declined card — four members done, `authorise-payment` refused `payment_declined`, the four undone
  in reverse (the refusing member has nothing of its own to undo); inside a request, seven spans `saga <key>`
  under the request's span and seven lines in its trace; a trace the agent drops leaves exactly the order's
  reference. `KoinGraphTest`, `PlacementRestartTest` and `FulfilmentWorld` bind the trail as `main` does.
- **Mutations**, each red then restored, `git status` clean: members not wrapped → three tests; `orderId` not
  indexed → the walk and the dropped trace; no shipment-move line → the walk; no refund line → the walk; a
  refusal written as done → the declined card; no span → the request test.
- **Checked against a running tracy**, not the stand (no access): `ghcr.io/youndie/tracy:0.3.8` beside the e2e
  stack, the server image of this branch reporting to it at sample rate 1.0, the e2e walked (synthetic shoppers
  included). `list_services`: `haul-server` with `storedRecords: 64` and `entityRefs: {orderId: 40}`.
  `get_entity orderId=HL-48304` (a synthetic shopper's order): ten touches — the placement trace (`get_trace`:
  `POST /api/v1/orders` 202 with the seven `saga …` spans and seven `order saga step done` lines), the shipment
  `placed`, `packed`, captured 34500, `in_transit`, `delivered`, the return requested (its own request trace),
  `picked_up`, refunded 34500, `refunded`.
- **Not seen locally: `top_templates`** stayed empty — the run lived under a minute after its last order, and the
  agent sends a template's count when its window closes. On the stand, after this deploys, `top_templates` for
  `haul-server` is the thing to look at, together with `get_entity` on a shopper's order.
- **Open, not done here: SLF4J lines still do not reach tracy.** The agent's `TracyLogbackAppender` would carry
  them, but it takes MDC as plain fields — never an entity key — and attaching it needs Logback at compile scope
  (`runtimeOnly` today). The saga's warnings (a rollback given up, a sweeper revival) and the simulators' held
  shipments stay in the pod's log. A candidate item if they are wanted in tracy.
