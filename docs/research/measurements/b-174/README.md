---
id: b-174-measurements
title: "B-174 — two replicas: a replica killed in the middle of sagas, the record"
type: research
status: active
date: 2026-11-01
---

# B-174 — two replicas, a replica killed in the middle of sagas

> **Role of this document: the record of the runs that show B-174's claim** — that two replicas of the server on one
> database pass the e2e, and that killing one in the middle of sagas, under the shoppers' traffic, loses no order and
> doubles none. Why the server is built that way is the research («Decided in B-174», D4); the runs are
> `e2e/src/test/kotlin/io/github/youndie/haul/e2e/TwoReplicasTest.kt`, their output under [raw/](raw/), unedited.

## What was run

- **The stand:** the composed stack of the e2e (`ComposedStack.kt`) — PostgreSQL 18, shildik, and the server's image
  twice, replicas `a` and `b`, behind `RoundRobinFront`, which hands each request to the next replica as a Service
  does. The image is `scripts/image-build.sh haul/server:b174`, the server's code as of `21a74607` (unchanged after it on
  the branch); the fulfilment clock runs 86,400 times the store's pace. On the shared WSL2 build box (Docker on
  Linux; the server's containers on the host's network), 2026-11-01.
- **A run:** `E2E_SKIP_BUILD=true GRADLE_ARGS="--tests *TwoReplicasTest" scripts/e2e.sh haul/server:b174`.
- **The kill:** the test takes `LOCK TABLE delivery_slots IN EXCLUSIVE MODE`, so every placement waits inside the
  saga's `reserve-slot` with its stock already reserved; starts one walk of its own straight at the leader that never
  sends anything again («the lone walk»), waits until it is waiting inside its saga, then starts eight walks through
  the front, a quarter of a second apart; once the front has placements of theirs on the leader (or six seconds have
  passed, inside the saga member's ten), it sends the leader SIGKILL, takes it out of the front, and releases the lock
  0.3 s later. The walks send a cut read, choice or keyed placement again (`Storefront.resend`); the lone walk's
  placement is left to the survivor's sweeper. Afterwards the test reads the database: every saga finished and none
  failed, no order placing, one order a person, one authorisation an order, its captures its total, its reservation
  its lines, the stock what the walks left.
- **The bus:** «Live orders» listened to on the follower while a walk places and receives an order on the leader.

## The runs

| Run | Kill: the leader's sessions inside a saga / the survivor's | Sagas unfinished at the kill | Cut by the kill (sent again / not) | Survivor holds the lock / leads, after the kill | The lone order carried on by the sweeper, after the kill | Sagas afterwards | Walks through the front refunded | Result |
|---|---|---|---|---|---|---|---|---|
| [kill-1](raw/kill-1.log) | 6 / 2 | 8 | 5 placements / 1 `POST /api/v1/me/addresses` | 1.80 s / 9.34 s | 69.7 s, placed | 8 COMPLETED | 7 of 8 (the eighth: the address cut) | pass |
| [kill-2](raw/kill-2.log) | 2 / 6 | 8 | 1 placement, 1 choice / — | 0.31 s / 7.77 s | 68.2 s, placed | 9 COMPLETED | 8 of 8 | pass |
| [kill-3](raw/kill-3.log) | 4 / 4 | 8 | 3 placements / 1 address | 1.90 s / 9.45 s | 70.2 s, placed | 8 COMPLETED | 7 of 8 (the address cut) | pass |
| [kill-4](raw/kill-4.log) | 6 / 2 | 8 | 5 placements, 1 choice / — | 1.68 s / 9.22 s | 69.9 s, placed | 9 COMPLETED | 8 of 8 | pass |

In every run each cut placement was sent again under its key to the survivor and answered the one order: every person
has one order, every order one authorisation, every delivered order captured its total, every reservation its lines,
and the stock after the walks is what they left — every returned unit back, the lone order's held. The bus test passed
in every run: the follower's page drew the order the leader placed («Placed»).

**The handover** is vojak's arithmetic: the dead leader's session ends with its process, the survivor takes the lock
on its next poll (every 2 s: 0.3–1.9 s here), and, the release not having been clean, waits out the 7.5 s the dead one
could still have believed it led — 7.8–9.5 s from the kill to «b leads». **The lone order** waits the sweeper's
minute untouched (`STUCK_AFTER`) and its poll: 68–70 s from the kill.

## Not counted, and why

- **[e2e-0-full-suite](raw/e2e-0-full-suite.log)** — the whole e2e on two replicas, the first run of the kill test, with
  the eight walks started together. Every other test passed (the whole path, the demo shopper, the synthetic shoppers,
  the bus); the kill test's database checks were not reached: one walk of eight stopped at its sign-in, refused by
  shildik (`401` on its own login form, the person made and their password set a moment before), so the test counted
  it as a walk the kill had stopped. After it the walks arrive a quarter of a second apart, and a sign-in refused by
  the provider is said and required to have placed nothing (`TwoReplicasTest`), rather than counted against the
  replicas. The refusal recurred once in the control below: two runs of six, one sign-in of nine each time. Not
  chased here: it is the provider's, and no replica is on that path.
- **[control-no-bus](raw/control-no-bus.log)** — the control: the image built with the bus's start removed from
  `HaulModule.shareWithReplicas`. The bus test failed as it must («no frame of live:orders for which it holds within
  30s»): without the bus, a page on the follower never hears a move made on the leader. Its kill test stopped on the
  provider's refusal above; the leader still moved (8.03 s).
