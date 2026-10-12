---
id: B-127
title: "fix: a database outage reaches the shopper in seconds, not in a minute and a half"
status: done
priority: P3
size: S
stage: stage-12-qa-run
---

# B-127 — fix: a database outage reaches the shopper in seconds, not in a minute and a half

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)): with PostgreSQL stopped, `/ui/home` answered `503` after
96.8 s and `/readyz` after 32 s — Hikari's 30 s connection timeout, paid once per query of the page. The storefront
showed the page's placeholder for about 100 s before «Something went wrong on our side» (TC-ERR-05's words are
right; the wait is decided nowhere).

- **A page whose database is gone fails fast.** A short connection timeout for request work (a few seconds) and
  one failure per request rather than one per query; readiness answers within its probe's timeout. Rejected:
  leaving the default — a shopper leaves long before 97 s.

- AC: with the database stopped, a page answers `503` within five seconds and `/readyz` within the probe's
  timeout; a test with an unreachable database holds the bound.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/db/Database.kt`.

## Findings

- **The 97 s was Exposed's retries, not the page's queries.** `transaction` runs its block up to
  `defaultMaxAttempts` (3) times on any `SQLException`, and asking Hikari for a connection is inside the
  block: three waits of Hikari's default 30 s. Readiness asked the pool once: 30 s, then its own check.
- **A request now waits for a connection once.** The pool's `connectionTimeout` is 2 s (`DatabaseConfig`,
  `validationTimeout` 1 s under it), and every user of the database — Exposed and the search's raw JDBC —
  reaches the pool through `FailFastDataSource` (`db/FailFastDataSource.kt`, wrapped in `haulModule`): once
  an ask timed out *because the database could not be reached* (Hikari's timeout carrying the connection
  failure as its cause), every ask in the next 2 s is refused at once with that cause. The retries and the
  rest of the page fail on the spot, and so does every other request in the window. Exposed's retries are
  untouched for statements; a pool that merely ran out of connections (a timeout with no cause) and an
  interrupted wait refuse nobody else. Rejected: `defaultMaxAttempts = 1`, which would also drop the retry
  of a statement that met a deadlock or a connection that died under it.
- **Readiness gives up after 750 ms** (`READINESS_BUDGET`, `runInterruptible` under `withTimeoutOrNull`:
  Hikari answers the interrupt with an `SQLException`), under the readiness probe's timeout — Kubernetes'
  default second, now written out in `charts/haul/templates/server.yaml` beside a note naming the budget.
- **Measured, before → after.** `DatabaseOutageTest` (its own PostgreSQL, stopped under the running module,
  the pool as `main` builds it; WSL box): first `/readyz` after the stop 30.0 s → 0.76 s; `/ui/home`
  over 60 s (the test runner's limit cut it; 96.8 s in the v0.4.0 run) → 2.09 s; the next page 7 ms;
  `/readyz` after a failed page 2 ms. The installed distribution (CIO, seeded, PostgreSQL in Docker
  stopped under it): `/readyz` 0.83 s, `/ui/home` 2.22 s, then 7 ms, `/ui/search` 10 ms; 3 s later (window
  over) `/ui/home` 2.18 s again.
- **What runs in the process survives a brief outage as before, by its own next pass.** With PostgreSQL
  stopped for 15 s under the distribution at `HAUL_FULFILMENT_SPEED=3600`: twelve «fulfilment pass failed,
  the next one tries again» warnings, none after the database came back, the process alive and ready
  throughout. The saga's sweeper, the sale redater and the live order page catch a failed pass the same way
  and wait their interval, so failing in 2 s rather than 90 s makes no loop spin. The start is unchanged:
  the chart's init container waits for the database (B-27) and the server still exits when refused.
- **What changes for the order saga:** a saga step that meets an outage now fails in about 2 s instead of
  waiting it out up to its phase timeout (petich's defaults: 1–10 s, 30 s for authorisation) — an outage
  between 2 s and that timeout used to be absorbed and now compensates the order or leaves it to the
  sweeper. The item's decision (a request fails fast) covers it; noted, not changed.
- **Recovery takes a few seconds, Hikari's own.** With the database back, the first asks still fail until
  Hikari's connection creator wakes from its backoff (doubling, capped at 5 s): home answered 200 4.1 s
  after PostgreSQL accepted connections. Before, a request waiting 30 s hid that wait.
- **Documents:** TC-ERR-05's step 2 now names the bound, with `DatabaseOutageTest` in its coverage. The
  B-32 finding «during an outage a request waits Hikari's `connectionTimeout`» is history in a closed item
  and stays as written.
