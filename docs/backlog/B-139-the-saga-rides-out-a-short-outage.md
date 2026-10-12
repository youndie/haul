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
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/SagaEngine.kt`.
