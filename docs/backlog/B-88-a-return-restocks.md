---
id: B-88
title: "server: a returned unit goes back to stock"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-88 — server: a returned unit goes back to stock

B-31 found that a refund does not put the returned unit back: every synthetic shopper's walk (one per 10 minutes
on the stand) takes a unit for good, and at that rate a category's first page runs dry in months.

- AC: a completed return adds its units back to stock once (idempotent under a replayed saga step); a test
  through the saga; the shoppers' walk leaves stock unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/`.

## Done (2026-10-11)

- **Decision: the units come back with the refund, not at «picked up».** B-21 put the refund at the moment the
  seller has the parcel back (the canvas's «Requested · Picked up · Refunded»); at `picked_up` the unit is in the
  courier's van and cannot be sold, and restocking there would sell a unit that has not arrived. So the returns'
  simulator restocks in the step that refunds, after the points and before the move — the same place and the same
  discipline as the refund and the points: each keyed, so a pass that dies before the move and runs again is
  answered with what was done. A return held by a refused refund puts nothing back until it is refunded: the AC's
  «a completed return». Recorded in research D4, «Decided in B-21».
- **Decision: a restock of its own, not a release of the reservation.** The order's `stock_reservations` rows
  stay what placement took; `release(order)` would put back every line, the kept ones too (returns are of whole
  lines, often not all of them). The new port `StockReturns.restock(key, quantities, at)` (`Order.kt`), over
  `ExposedStock`, claims `stock_restocks` rows under `return:<order>` (`ReturnSimulator.restockKey`, one return
  per order) with `INSERT … ON CONFLICT DO NOTHING` and adds a SKU's units only for a row it inserted — as
  `reserve` claims before it takes; two passes at once wait on each other's row and the second adds nothing.
  Table in `V28__stock_restocks.sql`, declared in `OrderTables.kt` beside the reservations.
- **Every reason restocks**, «Arrived damaged» included: the simulated world writes nothing off, and the shoppers
  pick a reason at random — a write-off by reason would keep draining the stand.
- **B-91's findings, read**: an order line's money fields are totals; the restock reads only `quantity` and
  `skuId`, never money, so the rename does not touch it. A SKU appearing on two lines is summed.
- **Tests.** `ReturnRestocksTest` (through the saga: `FulfilmentWorld` places Maya's cart with `Placement`, the
  saga takes the units, the fulfilment simulator delivers, `RequestReturn` asks): a returned line's units back at
  the refund and not at the door, the kept lines still sold, nothing more on later passes; an order returned
  whole leaves every SKU as before placement (the shoppers' walk in small); a pass that died between the restock
  and the move restocks once; two passes held at the move until both have restocked put the units back once.
  `SyntheticShopperTest` (e2e): the stock of every SKU, read off the stack's PostgreSQL (the wire carries only
  in/out of stock — `ComposedStack.stock`, `reserved`), is the same after the two walks as before, and each walk's
  order holds its reservation (it did take a unit). `SchemaTest` covers V28 (16 tables), `KoinGraphTest` the port.
- **Mutations** (each red, restored, tree clean): no units restocked — all four `ReturnRestocksTest` red; the
  claim ignored (adding on every run) — the died-between and two-passes tests red (the two-passes test needed the
  barrier at the move: written first without it, the second pass lost the race at the move to `picked_up`, never
  reached the restock, and the test passed under this mutation); the restock moved to the pickup step — the first test red («nothing
  is back on the shelf while the courier has it»); no units restocked, in the image — `SyntheticShopperTest` red,
  «the walks left the stock changed … {p-016-12-0=82 → 81, p-029-07-1=142 → 141}».
- **Where it ran**: `make check` and `make docs-against BASE=origin/main` on the Mac; on the Linux build machine,
  after the rebase onto B-92 and B-94, `:composeApp:wasmJsBrowserDistribution`, `check :server:installDist`
  (server and client tests, `viddikVerify`, ktlint) and `scripts/e2e.sh` (all eight e2e tests) green.
- **Not done, for the owner**: the units the stand's walks took before this change stay taken (the seed runs only
  on an empty catalog, and B-91 read 50 refunded returns there; a refunded return is never active again, so the
  simulator will not reach them). A backfill — each refunded return's lines into `stock_restocks` under
  `return:<order>` and onto the SKUs — would put them back; it rewrites the stand's data, so it is the owner's
  call and not part of this change.
