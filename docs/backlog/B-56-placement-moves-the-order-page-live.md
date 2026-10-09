---
id: B-56
title: "server: placement moves the order page live"
status: done
priority: P3
size: S
stage: stage-9-ship
blocked_by: [B-29]
---

# B-56 — server: placement moves the order page live

B-29 pushes the order page's body when the fulfilment, return and Haul Pay passes move an order, but not when
the placement saga does: a customer who lands on the page while the order is still placing sees «placing»
until something else moves it, and a declined card's cancellation (the saga's compensation, or the sweeper's)
is not pushed either. Found by B-29.

Decided: the saga's last steps — placed, and cancelled by compensation or the sweeper — tell `OrderMoves`
like the passes do, after their transaction commits.

- AC: a server test that a page subscribed while placing hears «placed», and one subscribed to an order whose
  card is declined hears it cancelled; the existing live tests green.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/OrderMoves.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt`.

## Done (2026-10-09)

- **Where it tells**: `orderSaga` takes the graph's `OrderMoves`; `Confirm.execute` tells once the order is
  placed, and `OpenOrder.compensate` once it is cancelled. Every cancellation of a placing order passes
  through that compensation — a declined card's rollback (the order already cancelled `payment_declined` by
  `authorise-payment`, which `cancel` keeps), a failure further on, and the sweeper carrying on a pass or a
  rollback a dead process left — so it is the one place a cancellation is told; `Confirm.compensate` is
  always followed by it.
- **After commit**: `ExposedOrders.confirm` and `cancel` are each a transaction of their own, committed when
  the call returns, and the tell follows the call, as the simulators tell after their pass. The tell comes
  before the saga's row says `COMPLETED` or `REJECTED`, and need not wait for it: the page draws the order,
  not the saga, and `LiveOrders` reads the order when it draws. petich's completion hook was read and not
  taken: `PetichTracer`'s `Finished` is sent after the terminal write, but it is synchronous and names the
  saga, not the order, so it would need a saga-to-order lookup of its own behind a second flow.
- **Found**: the page draws `placing` exactly as `placed` — the same title («Thanks, Maya — order … is
  placed»), the same steps — except the points the order earns, a fact shown only once it is placed. What
  a page opened while placing was missing is that fact, and a cancellation; the tests tell the frames apart
  by it.
- **Tests written** (`LiveOrderTest`, over the production graph): a page opened while the order was placing
  (the card processor holding its answer) hears it placed; one paying with the declined card hears it
  cancelled; and one open on an order a dead process left placing hears the sweeper cancel it
  (`sweepStuck` in the next process). Mutations: dropping `Confirm`'s tell fails the first only; dropping
  the compensation's tell fails the other two; dropping the wiring in `OrderModule` fails all three.
