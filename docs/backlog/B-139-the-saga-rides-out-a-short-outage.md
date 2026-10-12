---
id: B-139
title: "server: the order saga rides out a short database outage"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
---

# B-139 — server: the order saga rides out a short database outage

B-127 made a database outage reach the shopper in about two seconds: the pool waits 2 s for a connection, and for 2 s
after a timeout that carries a connection failure `FailFastDataSource` refuses every request for a connection at
once. The saga's steps use the same pool, so an outage from 2 s up to a step's phase timeout (petich: 1–10 s, 30 s
for authorisation) now fails the step and compensates the order, where before the step waited for the database and
went on (B-127's findings).

- **Decided (owner, 2026-10-12): the saga rides out short outages.** Saga work — the placement's steps, their
  compensations, the sweeper — waits for a connection within its step's own phase timeout instead of failing fast;
  pages and commands keep B-127's bound. Rejected: dropping B-127's fail-fast — a shopper would wait 97 s again.
- AC: with PostgreSQL stopped for about 5 s during a placement, the order goes on and is placed once the database is
  back; stopped longer than the phase timeout, it is compensated as today; a page during the same outage still
  answers 503 within B-127's bound; a test holds both.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/db/FailFastDataSource.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/db/ConnectionPatience.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/db/ReconnectingDriver.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/SagaEngine.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementOutageTest.kt`.

## Findings

- **Saga work is marked by a coroutine-context element the pool honours** — `ConnectionPatience`
  (`db/ConnectionPatience.kt`), a `ThreadContextElement`, because the asks are made by blocking JDBC code inside
  `withContext(Dispatchers.IO)` on whatever thread the coroutine runs. `FailFastDataSource` gives an ask under it
  no refusal and, when the pool times out, asks again while the asking coroutine is active (petich cancels a
  member at its phase timeout — the bound the owner named), for at most `SAGA_CONNECTION_PATIENCE` per ask
  (EXECUTION's 10 s, read off the engine's own config) and, for placement, `PLACEMENT_PATIENCE` (20 s) per pass.
  Its failed asks still open the window for everybody else, and once its time is up an ask is an ordinary one —
  so Exposed's retries of a member cut at its phase cost nothing. Placement wraps its two `engine.process` calls
  (`Placement.pass`), the sweeper is started in a scope carrying it (`HaulModule`).
- **Options weighed.** A second pool (or a second view of it) with a longer `connectionTimeout` and no window:
  rejected — a second set of connections against PostgreSQL's 100, and Hikari's timeout is per pool, not per
  member, so it would still not end at the member's phase. Retrying inside each member until its deadline:
  rejected — the engine's own writes between the members (the step's progress, the rollback's mark) are not
  members and would fail fast just the same — an outage that begins between two members meets one of them first. The marker covers both.
- **The placement request is a command until its saga starts, saga work from there.** Its reads before the saga
  (the existing saga, the checkout, the key, the order number) and the answer after it fail fast: a database
  that is away when «Place order» is pressed is a `503` in about 2 s and spends no key. Under the saga the shopper
  waits, because failing then compensates an order they meant to place — but at most `PLACEMENT_PATIENCE` in all:
  a member waits up to its 10 s, the rollback's mark up to what is left, and the `unwind` petich tries after a
  failed mark finds the pass's time spent and fails at once. Without that bound each write's own wait (10 s, three
  times with Exposed's retries) would put a dead database's answer past 30 s (reasoned, not measured).
- **Hikari's recovery was the real limit, and is changed with it.** Refused, Hikari's connection creator sleeps
  10 ms doubling to 5 s between tries (`PoolEntryCreator`, read off 7.1.0's bytecode; no setting changes it), and
  it alone opens connections: with PostgreSQL down 5 s its tries fell at 5.1 s and 10.1 s, so a database back at
  6 s was found at 10.1 s — after the member's 10 s phase. A saga that waits within the phase still compensated a
  5 s outage. The pool now opens connections through `ReconnectingDriver` (`db/ReconnectingDriver.kt`): a refused
  connection (SQLState class `08`, `57P03`) is asked again every 200 ms for up to 1 s, so Hikari's sleeps grow
  only after about ten seconds of outage. 1 s and not the pool's 2 s: Hikari names its last failed try as the
  cause of a timeout, and B-127's window opens only on a timeout with a cause — a first try still asking when a
  page's 2 s ran out would leave it without one. Pages come back sooner for it too (not measured).
- **Measured** (`PlacementOutageTest`, WSL box, the pool as `main` builds it, PostgreSQL stopped by Docker while
  `reserve-stock` waits on SKU rows the test holds locked): 5 s down — the member's retry had its connection
  ~1.4 s after the restart, placed, answered `202` 6.4 s after the stop; 12 s down — the member cut at its phase,
  the rollback's mark waited for the database, the stock released, the saga `FAILED`, answered `500` 13.4 s
  after the stop; `/ui/home` in either outage `503` in 2.0 s. `DatabaseOutageTest` unchanged: readiness 0.76 s,
  the first page 2.13 s, the next 8 ms.
- **Left as they are.** A database away for longer than the pass's 20 s leaves the saga `PROCESSING` for the
  sweeper, as before B-127 and after it. The sweeper's asks wait per ask (it has no pass to bound), so one of its
  passes can outlast `STUCK_AFTER` during a long outage; a second replica's sweeper taking that saga meanwhile
  is what petich's version check refuses, and the members are idempotent by the order — noted, not changed.
  A patient ask may end one pool wait (2 s) past the bound that ended it: Hikari cannot be told a shorter wait.
