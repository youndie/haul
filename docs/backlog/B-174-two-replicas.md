---
id: B-174
title: "feat: the server runs as two replicas — an external bus, one leader for the simulators"
status: done
priority: P3
size: L
stage: stage-15-showcase
---

# B-174 — feat: the server runs as two replicas — an external bus, one leader for the simulators

The server is one replica by construction: the live-order bus is in memory, the simulators and the sweeper run in
every process, some facets are cached in memory. The architect's review (2026-11-01): «the saga survives a killed
pod» is the claim worth demonstrating, and today it cannot be.

- The bus goes through PostgreSQL `LISTEN/NOTIFY` (already in the stack) or booblik; the simulators and the sweeper
  run in the leader only (vojak advisory locks); the chart allows `replicas: 2`. A demonstration — a pod killed under
  the shoppers' traffic, no order lost or doubled — recorded with its numbers.

- AC: two replicas on a local stand pass the e2e; killing one mid-saga loses and doubles no order (a test or a
  recorded run); the chart's checks hold for both counts.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`, `server/src/main/kotlin/io/github/youndie/haul/ops/`, `charts/`.

## Findings

**Done 2026-11-01.** The server runs as any number of replicas on one database; the chart allows it and keeps 1 as its
default — whether the stand runs two is the deploy's call (below). Decisions in the research, «Decided in B-174» (D4);
the runs in [measurements/b-174](../research/measurements/b-174/README.md).

- **The bus is PostgreSQL's `LISTEN`/`NOTIFY`, carrying order ids** (`ops/PostgresBus.kt`): each replica draws the frame
  for its own listeners from the rows, so a notification never nears its 8,000 bytes (a burst goes in 4,000-byte
  batches), and a replica drops its own echo by origin. One connection of its own listens, checked every 10 s,
  reopened after a second. booblik and kompot's Redis bus were the alternatives: both a pod of their own for a
  durability the pages do not need — a page that misses a move catches up on its next connection, by design since
  B-29.
- **The leader is vojak** (`io.github.youndie.vojak:vojak-jdbc` 0.1.0.6 from the snapshots repository, the JVM store
  over a `DataSource`; `ops/Leader.kt`): the fulfilment runner (sellers, couriers, returns, Haul Pay), the saga's
  sweeper — petich's has no claim, so it must be one — the sale's re-dating and the demo shoppers' sweep run on it
  alone, in the leadership's scope. A stopping replica closes its election first, so the next takes over within a
  poll (0.18 s in `LeaderTest`); a killed one's successor waits out vojak's local lease (1.7 s at the test's timing,
  7.8–9.5 s at the stand's).
- **What a process holds** was listed and each kept or shared (research): the moves are now shared; «Live orders»'s
  kept drawing is dropped by a move from any replica, and its 200 listeners are per replica; the rest is the same in
  every process, or rightly per replica, or a row.
- **`main` is always a shared replica**, whatever the count: one replica is a leader of one, and costs the bus's
  connection and the election's two. `HAUL_REPLICA` names a replica (else `HOSTNAME`), its pool's sessions are
  `haul <replica>` and its lease `vojak <replica>` in `pg_stat_activity`.
- **The chart** (0.4.0): `server.replicas` any count from 1, refused at 0 and where `(replicas + 1) × (dbPoolSize + 3)`
  passes PostgreSQL's 97 connections — seven at the default pool. `chart-check.sh` renders 1 and 2 and both refusals.
- **The e2e runs on two replicas** behind a front that takes turns (`RoundRobinFront`), so every e2e walks both; the
  storefront client in the walks sends a cut read, choice or keyed placement again (`Storefront.resend`, on in the
  synthetic shoppers too, so a rollout or a dead pod on the stand no longer stops a walk at a placement).
- **The demonstration** (`TwoReplicasTest`): a page on the follower hears the leader's moves; and the leader killed
  with SIGKILL while 2–6 of its sagas and 2–6 of the survivor's wait inside `reserve-slot` (the test holds the
  windows' table lock): four runs green, every saga `COMPLETED`, one order a person, every cut placement answered its
  one order on the survivor, the unsent one carried on by the survivor's sweeper 68–70 s after the kill, every
  delivered order captured its total, the stock as the walks left it. A walk cut at `POST /api/v1/me/addresses` (not
  safe to send again) stops, with no order, in two runs of four — expected, and counted.
- **Control and mutations**: the image without the bus fails the bus test («no frame of live:orders … within 30s»);
  the bus's origin check inverted fails all three `PostgresBusTest`s, no reconnect fails the reopening one, a dropped
  overflowing id fails the burst one; work run outside the election fails all three `LeaderTest`s. Each restored, the
  tree as committed.
- **A shildik sign-in refused under concurrent sign-ins**: in two runs of six, one walk of nine was answered `401` on
  the provider's own login form (`LoginOutcome.Wrong`: no such person or no matching password), the person made and
  their password set a moment before. Not a replica's path — the form goes to shildik — and not chased here; the kill
  test says such a walk and requires it placed nothing. Worth a look in shildik.
- **Where it ran**: everything on the shared WSL build box — `./gradlew check :server:installDist
  :composeApp:wasmJsBrowserDistribution` (601 server tests), `scripts/image-check.sh`, `scripts/shoppers-check.sh`,
  `scripts/e2e.sh`, `scripts/chart-check.sh`, `make check`.

**For the owner — two replicas on the stand** is a values change in the stand's deploy (`server.replicas: 2`, with
the shoppers on to see it): nothing else changes, the database has the connections. Not done here: no deploy, no
infra change. The stand has been one pod since B-27; the cost is a second 768 MiB pod.

## Decided (2026-11-01)

The owner allowed two on the stand: `server.replicas: 2` in infra's values for haul, with the deploy of v0.8.0.
