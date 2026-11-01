---
id: b-173-measurements
title: "B-173 — the stack in numbers: the record, the pins, the results"
type: research
status: active
date: 2026-11-01
---

# B-173 — the stack in numbers

> **Role of this document: what the measurement decided around its frozen pre-registration.** The questions, their
> green and red, the thresholds, the kill criteria and the budgets are in [brief.md](brief.md), and `make check`
> holds every byte of it below its marker (`scripts/brief_freeze.py`). This file carries the record of that freeze,
> the pins, the answers to what was believed, the amendments — appended, dated, with a reason; nothing is edited
> away — and the results, each number with its file under [raw/](raw/).

<!-- frozen: path=docs/research/measurements/b-173/brief.md bytes=13094 sha256=fe48adef84de079d902da39602969a432202caa8fc8403ca2b19afa89cce28de scope=after-marker -->

**Owner:** the repository's owner.
**Window:** open until the first measurement; closed on 2026-11-01 by `57621e00` (the first file under `raw/`,
`raw/startup/`), which descends from the freeze `a932d3ea`.

## Pins

| Row | Pin | Where it came from |
|---|---|---|
| Subject | the branch `feat/b-173-stack-in-numbers` at `647b8b74f5311b165cfe9dfe83d26b2e99c041b9`, built by `scripts/image-build.sh haul/server:b173` | `git rev-parse HEAD`, the image built 2026-11-01 11:31 CET |
| Image | `sha256:e2e5512c80300869f62f3b6599424d1e4b3f9953b93f988dd878e2196945e2e7` (`eclipse-temurin:25.0.4_7-jre`, the cache `lib/app.aot` trained in the build) | `docker image inspect` |
| Start script's AOT flags | `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching -XX:AOTCache=$APP_HOME/lib/app.aot`, then `JAVA_OPTS` | `server/build/install/server/bin/server:205-208` |
| Host | WSL2 on the shared build box, kernel `6.6.87.2-microsoft-standard-WSL2`, 20 logical CPUs, Docker 29.1.3; `nproc` inside the server 2 | every run's `*-header.txt`, `load-*/load-nproc.txt` |
| Generator | k6 v1.4.1 (`commit/8cc106e574`, go1.25.4) | `k6 version` on the box |
| Offered rate R | campaign 1: 200 rps (400 unsaturated); campaign 2: 100 rps (400 saturated, 24 % dropped) — chosen by the rule in each | `raw/load-*/load-rate.txt`, `load-rounds.csv` (`step-*`) |
| Clocks | the box's wall and monotonic clocks against the Mac's over twelve minutes of campaign 1: 727.82 s against 727.63 s, inside the ssh bracket (up to 0.94 s); no run moved `time.time() − time.monotonic()` by 0.05 ms | `raw/clock-check.txt`, `clock_drift_ms` in every CSV |

## What was believed, answered

| Belief | Answer |
|---|---|
| 1. `Opened AOT cache` is printed even on a refusal | not tested: no counted run was refused, so no run could show it. Every cache run printed both `Opened AOT cache` and `Using AOT-linked classes: true`, no `[error][aot`/`[warning][aot` (`raw/startup/startup.csv`, `aot_*`) |
| 2. `-XX:AOTMode=off` overrides the start script's cache | **true**: every `no-aot` run printed no `[aot` line at all, and started ~0.7 s slower (`aot_lines` 0) |
| 3. `Server-Timing` excludes the wait before the application | consistent, not proven: the server's p50 sits 0.9–1.8 ms under the client's on every route of campaign 2, and in campaign 1's collapsed pairs 0.6–1.5 s under a client p50 of 3–4 s — the queue is outside it |
| 4. Docker's own start is the same in every arm | **true**: `docker run -d` returned in a median 147–167 ms in every arm, 160 ms in `empty` |
| 5. The paired CPU difference cancels the shoppers | the shoppers alone cost the server 0.06–0.07 of a core; it is subtracted per pair, and the healthz control (0.7–1.0 ms a request) stays under the mix |

## Amendments

None inside the window.

## After the window

These are not amendments to the brief — nothing below the marker moved, and no threshold did — but they change
what was run, so they are written here, dated, before the results that depend on them.

### A2.1 — a second load campaign (2026-11-01, after campaign 1)

The owner's instructions for this item say to measure only while no Gradle or CI job runs, waiting and retrying.
Campaign 1's load block (11:44–12:13) ran into other sessions' work from its fourth pair on: the one-minute load
average reached 13.9 (rounds `p4-gets`, `p6-gets`, `p7-gets`), from Kotlin/Native compiles and Gradle daemons of two
other sessions. The gate (K7) samples the two seconds before a round and matched only Gradle and the CI runner by
name, so it marked no round busy; by the load average, three of its six counted pairs ran beside other work. Campaign
1 is kept as measured and reported with its verdicts. The whole load block was then run once more, unchanged, as
campaign 2 (12:24–12:53). It was meant to wait for a quiet box first; the wait script was empty on the box (it was
written through the wrapper's own stdin), so campaign 2 started at once, with a load average of 2.7, and its gate
waited 32–152 s before five of its rounds. Both campaigns are reported; neither replaces the other.

### A2.2 — harness fixes after campaign 1

- The survival control crashed (`Popen` given `capture_output`) after the counted block, so campaign 1 has no
  survival control; fixed, and campaign 2's ran.
- The gate also matches the Kotlin/Native compiler (`konan.home`) and binaries it built (`.kexe`): the «other builds»
  of K7, which the first pattern missed.
- The raw latency is kept as a histogram of three significant figures (`load-latency.csv`) instead of a line per
  request (3.8 MB a campaign); a percentile read from it is off by under 0.5 %. The per-request lines stay on the box.
- A run whose server came up between the poller's `/healthz` and `/readyz` has no `/healthz` time of its own (4 of 44
  counted runs); `/healthz` decides nothing and is reported over the runs that have one, with their count.

### A2.3 — reading «the shoppers' walks shown completing during the rounds»

The reader requires walks to finish in **every** counted pair, not somewhere in the block. From campaign 1's fifth
pair and campaign 2's fourth the walks began to fail at checkout with «no window has room», and from campaign 1's
sixth and campaign 2's fifth none finished: the shoppers placed about 450 orders in fifteen minutes (one walk every 2
s, three hundred times the stand's rate) and filled every delivery window the store offers. The GETs went on; the load
beside them was no longer the shoppers'. This reading can only move RQ2 away from green; read as «somewhere in the
block», campaign 2's RQ2 would be green.

## Results

Measured 2026-11-01 on the shared build box, the image `sha256:e2e5512c…` built from `647b8b74`. Same host, same
image in every row; the start-up and the two load campaigns are three runs of the morning.

| Question | Verdict | The number | Raw |
|---|---|---|---|
| RQ1a — the cache shortens the time to `/readyz` | **GREEN** | 1,006 ms with the cache against 1,713 ms without (medians, 11 counted rounds); paired difference −710 ms ± 109 ms, **41 % ± 6 %** of the start without it; ruler (aot − aot-again) 78 ms ± 74 ms, 4.2 %; control (sleep 1 s) +960 ms ± 52 ms | `raw/startup/` |
| RQ1b — … to the first `/ui/home` 200 | **GREEN** | 1,133 ms against 1,929 ms; −811 ms ± 112 ms, **41 % ± 6 %**; ruler 3.5 % | `raw/startup/` |
| RQ2 — p50/p99 of the main routes, campaign 1 (R = 200 rps, a loaded box) | **RED** | 93 % of the arrivals answered; pooled p99 4.4–5.2 s on every route. Pairs 2–5 (loadavg 5.1–13.9): every round's p99 ≤ 184 ms, p50 6–25 ms; pairs 6–7 (loadavg 11–12): p50 3.1–4.1 s, 76–80 % answered | `raw/load-1/` |
| RQ2 — campaign 2 (R = 100 rps) | **GREY** | every route inside its lines: p50 7.1–18.7 ms, p99 53–97 ms, no round's p99 over 190 ms, 100 % answered, no non-2xx — but the shoppers' walks completed in pairs 2–4 only (A2.3) | `raw/load-2/` |
| RQ3 — server CPU per request of the mix, campaign 2 | **GREEN** | **3.4 ms ± 0.6 ms** (6 pairs); PostgreSQL 4.6 ms beside; control `/healthz` 0.7 ms | `raw/load-2/` |
| RQ3 — campaign 1 | **GREY** | 4.4 ms ± 2.1 ms: 2.9–3.5 ms in pairs 2–5, 6.6–7.2 ms in pairs 6–7; the interval is wider than ±20 % | `raw/load-1/` |
| RQ4 — memory inside the 768 MiB limit, campaign 2 | **GREY** | `memory.peak` 685 MiB, **89 %** of the limit (the line was 80 %); no `oom_kill`, never exited; anon at most 650 MiB, VmRSS 686 MiB, 83 threads. The control died under 128 MiB (`OutOfMemoryError: Java heap space`, exit 3) and lived under 192 MiB | `raw/load-2/` |
| RQ4 — campaign 1 | **GREY** | 668 MiB, 87 %; no `oom_kill`, never exited; no survival control (A2.2) | `raw/load-1/` |

**How.** `scripts/measure-stack.py startup` and `load` on the box, as the brief describes: the server pinned to
cores 16–17 with `--memory=768m` and the chart's JVM options, PostgreSQL, shildik, the shoppers and k6 on cores of
their own; `summarise` reads the raw files into `server/src/main/resources/numbers/stack-numbers.json`, which the page
«The stack in numbers» draws and `make check` holds to the raw files (`summarise --check`). Estimators: RQ1 the paired
per-round difference with its 95 % interval (`t·sd/√n`), as a share of the no-cache mean; RQ2 nearest-rank percentiles
of the client-observed time pooled over the six counted rounds, each round's beside; RQ3 the paired per-round marginal
CPU (`gets` − `shoppers`, as rates, times the `gets` round's length, over its requests); RQ4 the cgroup's counters.

**Controls.** The start-up harness saw the one-second delay as 960 ms ± 52 ms (K2 not fired); the ruler is 4.2 %, far
under the 15 % floor (K1 not fired); the generator offered and delivered 400 rps of `/healthz` with nothing dropped in
both campaigns (K3 not fired); `/healthz` came out faster (p50 1.1–1.6 ms) and cheaper (0.7–1.0 ms of CPU) than every
route of the mix, with no `Server-Timing` on it (K4 not fired); the survival control died at 128 MiB in campaign 2 (K5
not fired there). No run moved the clocks (K6); K7 marked nothing busy, which campaign 1 shows was the gate's blind
spot (A2.1), not a quiet box.

**What it does not show.**

- **Why campaign 1 collapsed.** In pairs 6–7 both the server's and PostgreSQL's CPU per request doubled (3 → 7 ms and
  4.7 → 11 ms), PostgreSQL's cores ran at 1.7 of 2, and the queue grew past what 200 rps could drain. The box was
  loaded by other work (load average 11–12) and the shoppers' checkouts had begun to fail. Not separated, so no
  mechanism is named. One observation weighs against a cost that grows with the data: campaign 2's pairs 5–7 ran on as
  many orders, after the windows filled, at 3.1 ms of server and 3.8–4.0 ms of PostgreSQL CPU per request — but at half
  the rate, on a quiet box.
- **A tail on another host.** The generator shared the host on disjoint cores, open-loop, over loopback (the brief,
  «The tail on one host»).
- **The stand.** One shared WSL2 machine, two pinned cores, observability agents off, sign-in through a local
  shildik, the fulfilment clock at 86 400× and the shoppers 300 times the stand's rate. The stand's pod has no CPU
  limit and its own node.
- **A start against an empty database.** Every counted start is a restart against the seeded database; the first,
  seeding start (3.6 s with the cache) was not counted.
- **A memory number under a lower heap.** The JVM sizes its heap at 60 % of the limit and grows into it; the peak
  says where the JVM settled with that setting, not what the server needs.
