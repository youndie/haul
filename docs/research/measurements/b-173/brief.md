---
id: b-173-brief
title: "B-173 — the stack in numbers: the pre-registration, frozen"
type: research
status: active
date: 2026-11-01
---

<!-- Written by B-173 itself, before any measurement. Everything above the marker may be edited; nothing below it
may be, including where it turns out wrong — that is what the amendments in README.md (beside this file) are for.
`make check` hashes every byte below the marker (`scripts/brief_freeze.py`). -->

What follows is the pre-registration of B-173's measurements, written on 2026-11-01 before any of them was taken.
The record of the freeze, the pins, the answers to the believed list, the amendments and the results are in
[README.md](README.md); the raw runs are in [raw/](raw/).

<!-- ---8<--- everything after this line is the received text, byte for byte ---8<--- -->
# Pre-registration: what the Haul server costs — start-up with and without its AOT cache, latency, CPU and memory under the synthetic shoppers

**Question:** what does the image the stand runs actually cost a developer to start and to serve: does its zavarnik
AOT cache shorten the start, how fast are the main storefront routes under traffic, what CPU does a request take,
and does the server stay inside the chart's memory limit while serving?
**Budget:** stage 0 (the harness, the generator's ceiling, the controls, the operating point) 3 h; RQ1 1 h; RQ2–RQ4
together 2 h; the write-up and the page 3 h. A step that overruns is recorded as "not completed", with the reason,
and the next step starts.
**Output:** the raw runs under `docs/research/measurements/b-173/raw/`, a verdict per question in its README and in
the research (`docs/research/research-architecture.md`, «Measured in B-173»), and the page «The stack in numbers» on
the stand, whose every number is one of these.
**Non-goals:** no tuning of the server, the JVM or PostgreSQL; no comparison with a native build; no throughput
benchmark (the saturation search exists only to choose the operating point); no measurement of the public stand's
cluster; no change to zavarnik.

## Verdicts

- **Green:** the condition in the table below holds, its positive control came out as known, and the lever (for
  RQ1, the cache) is shown engaged in every counted run.
- **Red:** the red condition in the table holds.
- **Grey:** anything that is neither. The results say which green condition it missed.
- **Not measured:** with the reason — a kill criterion that fired, or a budget that ran out.

## The stand

One machine, the shared Linux build box (WSL2, 20 cores, 16 GB), Docker. Every process pinned to cores of its own
(`docker run --cpuset-cpus`, `taskset -c` at exec):

| What | Where | Limits |
|---|---|---|
| the server: the image `scripts/image-build.sh` builds from the branch, on the host's network | cores 16–17 | `--memory=768m` (the chart's `server.resources.limits.memory`), `JAVA_OPTS` = the chart's `server.jvmOptions` (`-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError`) plus `-Xlog:aot` in every arm |
| PostgreSQL 18 (`postgres:18-alpine`, default configuration) | cores 14–15 | none |
| shildik (`ghcr.io/youndie/shildik-sqlite:0.4.1`), for the shoppers' sign-in | cores 12–13 | none |
| the synthetic shoppers (`:e2e`'s `main`, from `installDist`) | cores 8–11 | none |
| the harness's poller and the load generator (k6) | cores 4–7 | none |

The server is configured as the e2e's `ComposedStack` configures it (seed on, sign-in through the realm, the test
card's key, fulfilment speed 86 400 so a walk's delivery and refund take seconds), observability agents off.

Every run or round records: the time, `/proc/loadavg`, the CPU that other builds on the box (Gradle, Kotlin
daemons, the CI runner's jobs) took in the two seconds before it, the busy time of cores 16–17 that was not the
server's (contamination), and `time.time() − time.monotonic()` at its start and its end.

## Research questions

| RQ | Question | Green | Red | Budget |
|---|---|---|---|---|
| RQ1a | Does the AOT cache shorten the time from `docker run` to the first `GET /readyz` 200? | the paired difference (no cache − cache) is distinguishable, at least 15 % of the no-cache mean **and** at least twice the ruler; every counted cache run logs `Using AOT-linked classes: true` and no `[error][aot` or `[warning][aot`; no no-cache run logs an `[aot` line; the positive control reads as known | the difference is inside its interval or the cache arm is slower; or any counted cache run fails to use the cache | 1 h with RQ1b |
| RQ1b | The same for the time from `docker run` to the first `GET /ui/home` 200, asked as soon as `/readyz` first answers 200 | as RQ1a, on this time | as RQ1a, on this time | — |
| RQ2 | How fast are the main routes at a fixed rate under the synthetic shoppers? `/ui/home`, a category, a product, search suggest, `/ui/live` | for every route, pooled over the counted rounds: p50 ≤ 50 ms and p99 ≤ 250 ms of the client-observed time; no round's p99 over 250 ms; non-2xx ≤ 0.1 %; delivered ≥ 99 % of offered; the shoppers' walks shown completing during the rounds | any route's pooled p99 > 1 000 ms, or non-2xx > 1 %, or delivered < 95 % of offered | 2 h with RQ3, RQ4 |
| RQ3 | What CPU does one of those requests take? | the server's marginal CPU per request of the mix ≤ 10 ms, its 95 % interval narrower than ±20 % of it, and the known-order control holds | the marginal CPU per request ≥ 50 ms | — |
| RQ4 | Does the server stay inside the chart's memory limit under that load? | the cgroup's `memory.peak` over the whole load block ≤ 80 % of 768 MiB (614 MiB), no `oom_kill` in `memory.events`, the container never exited; and the survival control died | an `oom_kill`, an exit, or `memory.peak` > 95 % of the limit (730 MiB) | — |

Why the lines sit where they do. RQ1: zavarnik's cache on the owner's other JVM applications halved the start
(kachok's `.deb` 936 → 388 ms); 15 % is far under that and still a gain a developer would notice. RQ2: 50 ms at the
median is a page that feels instant; 250 ms at p99 keeps the slowest of a hundred requests under a quarter of a
second; 1 s is a page that is broken. RQ3: 10 ms per request is a core serving a hundred requests a second, more
than the stand will see; 50 ms is twenty. RQ4: 80 % leaves the headroom a container limit is for.

## Units, estimators and resolution

- **RQ1.** Milliseconds of `time.monotonic()` on the driving machine, from just before `docker run` to the first
  answer 200, polled every 10 ms from cores 4–7. Docker's own start is inside every arm and is reported apart (the
  empty arm). Estimator: the paired per-round difference between arms run in the same round, with a 95 % interval
  (`t(0.975, n−1)·sd/√n`), as a share of the no-cache mean.
- **RQ2.** Milliseconds per request as k6 sees it (`http_req_duration`, over loopback), the server's own
  `Server-Timing: app;dur` beside it; p50 and p99 per route per round and pooled over the counted rounds. rps is
  recorded and decides nothing.
- **RQ3.** Microseconds of the server container's cgroup CPU (`cpu.stat` `usage_usec`) per request: the paired
  per-round difference between a round with the shoppers and the GETs and a round with the shoppers alone, divided
  by the GETs delivered in the first. PostgreSQL's cgroup CPU is read the same way and reported beside, not in the
  verdict.
- **RQ4.** The server container's cgroup: `memory.peak`, `memory.current` and the `anon` of `memory.stat` at every
  round's end, `memory.events`; the process's `VmRSS` beside, never `VmHWM`.
- **The ruler (RQ1).** The cache arm against itself — a second arm, the same image and options, interleaved — with
  the same estimator. A difference inside its interval is reported as «below resolution, under N %», never zero.
- **The tail on one host.** The skill this follows asks for a second host to read a tail; there is one machine
  here. The generator runs on disjoint cores, open-loop at a fixed arrival rate, over loopback. Interference from a
  shared host can only add to the server's latency, and loopback removes a network that is not the server's; so a
  p99 under its line here is read as the server's, and one over it is reported with the contamination beside it.

## Protocol

**RQ1, start-up.** One PostgreSQL, seeded once by an uncounted start of the image, then left: every counted start is
a restart against a seeded database, as a stand's pod restarts. Five arms, one run each per round, in this order:
`aot` (the image as built), `no-aot` (the same with `-XX:AOTMode=off` appended to `JAVA_OPTS`), `aot-again` (the
ruler, identical to `aot`), `aot-sleep` (the positive control: the same as `aot`, its entrypoint `sh -c 'sleep 1;
exec /opt/app/bin/server'`), `empty` (the same image with `--entrypoint true`: docker's own share, report only). A
run is `docker run -d` with the stand's limits, the poll, the server's log kept, `docker rm -f`. Twelve rounds; round
1 is discarded; eleven counted.

**RQ2–RQ4, load.** A fresh PostgreSQL and shildik; a realm with the storefront's public client and eight people with
one generated password (written to no file and printed nowhere); the server (`aot` arm) started and warmed by
round 1. The shoppers start before round 1 and walk throughout: one walk every 2 s as the eight people in turn, at
most 16 at once, the order read every second, every tenth walk paying with the declining card. Rounds alternate, 60
s each: `shoppers` (nothing else) and `shoppers+gets` (k6 at the rate R, constant arrival, the five routes in equal
shares in turn — `/ui/home`, `/ui/c/electronics/audio/headphones`, `/ui/p/p-sonora-sh-718n`,
`/ui/search/suggest?q=<prefix>` over the prefixes `scripts/suggest-latency.sh` walks, `/ui/live` — all
anonymous). Seven pairs; pair 1 is discarded; six counted. One line per request is kept: round, route, status,
client ms, server ms.

**The operating point R** is chosen in stage 0, before the counted rounds, by this rule and not again: with the
shoppers walking, k6 offers the mix at 25, 50, 100, 200 and 400 rps, 30 s each after 30 s at 25; a step is saturated
when k6 drops more than 1 % of its arrivals, or the step's p99 over all routes passes 1 000 ms, or more than 1 % of
answers are not 2xx. R is half the highest step below the first saturated one (all five unsaturated: R = 200), and
no step counts above the generator's ceiling.

**Stage 0, before any counted run:**

1. The generator's ceiling: k6 offers `GET /healthz` at 400 rps for 30 s; delivered ≥ 99 % with p99 < 50 ms is the
   ceiling cleared. Below it, R is half the ceiling's highest cleared step.
2. The start-up harness's positive control is the `aot-sleep` arm, inside the counted rounds: its paired difference
   from `aot` must read 1 000 ms ± 250 ms.
3. The latency and CPU readers' known-order control: one 60 s round of `GET /healthz` alone at R with the shoppers
   walking, beside a `shoppers+gets` round. `/healthz` must come out faster at p50 than every route of the mix and
   cheaper in marginal CPU per request than the mix; and the reader must find no `Server-Timing` on `/healthz` (it
   is not a tree).
4. The survival control: the same image and options under `--memory=192m`, the same load (shoppers and the mix at
   R) for up to 120 s, must die — `oom_kill`, or the JVM's exit on `OutOfMemoryError`. If it lives, once more at
   128 MiB.
5. The lever: every RQ1 run's log is read for its `[aot` lines (above); the load block's server is started with
   the same `-Xlog:aot` and read the same way.
6. Load delivered: every `shoppers+gets` round reports k6's iterations and dropped arrivals and the statuses; every
   round reports the walks that finished, and failed, in it.

## Kill criteria

| # | Fires when | What is still written up |
|---|---|---|
| K1 | the RQ1 ruler's interval (aot − aot-again) is wider than 15 % of the no-cache mean | RQ1 is not measured; the arms' numbers are written as a pilot |
| K2 | the `aot-sleep` control reads outside 1 000 ± 250 ms | RQ1 is not measured until the harness is fixed, inside its budget |
| K3 | the generator's ceiling is under 25 rps | RQ2 and RQ3 are not measured |
| K4 | the readers' known-order control inverts | RQ2 or RQ3, whichever reader inverted, is not measured |
| K5 | the survival control lives at both limits | RQ4 cannot be green; grey at best, said so |
| K6 | `time.time() − time.monotonic()` moves more than 50 ms within a run or a round | that run is void; past a third of a question's runs void, the question is not measured |
| K7 | other builds on the box take more than half a core in the two seconds before a run or a round, after waiting up to 15 minutes for them to stop | that run is marked busy and counted; past a third of a question's runs busy, its verdict is at most grey |

## Believed, and not yet checked

1. JDK 25 prints `Opened AOT cache` even when it then refuses the cache, so only `Using AOT-linked classes: true`
   with no `[error][aot` says it was used (the owner's notes).
2. `-XX:AOTMode=off` in `JAVA_OPTS` overrides the `-XX:AOTCache` the start script adds, because the script puts
   `JAVA_OPTS` after its defaults.
3. `Server-Timing` measures the tree's build and encoding and not the time a request waits before the application
   sees it.
4. Docker's own start is the same in every arm.
5. The shoppers at one walk every 2 s add a load of their own that the paired CPU difference cancels.

## Predictions

- RQ1a: the cache saves 30–60 % of the time to `/readyz`; RQ1b a larger share, because the first home is where the
  untrained JVM is slowest.
- RQ2: every route's p50 under 30 ms at R; suggest the slowest (it is PostgreSQL's), `/ui/live` and the home next.
- RQ3: 2–8 ms of server CPU per request of the mix.
- RQ4: `memory.peak` between 400 and 600 MiB, mostly the heap the JVM sized at 60 % of the limit.
