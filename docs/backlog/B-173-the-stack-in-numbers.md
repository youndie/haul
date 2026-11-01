---
id: B-173
title: "feat: «The stack in numbers» — start-up with and without the AOT cache, latency, memory, measured"
status: done
priority: P2
size: M
stage: stage-15-showcase
---

# B-173 — feat: «The stack in numbers» — start-up with and without the AOT cache, latency, memory, measured

The image ships a zavarnik AOT cache and the research says why (D-section on the image), but the gain was never
measured on this server (research: «not measured»), and there is no number anywhere for latency or memory under
load. The architect's review (2026-11-01): a showcase a developer trusts is one that shows its numbers.

- **Measure, then publish.** Following `research-method:measurement-study`: start-up of the image with the cache and
  without, interleaved, N runs; p50/p99 of the main routes and RSS under the synthetic shoppers at a fixed rate on the
  WSL box; raw logs kept. The results go into the research and a page of the stand («The stack in numbers», from
  About), with the date, the commit and how to reproduce.

- AC: the study's verdicts written (green, grey or red per question) with raw logs in the repository; the page shows
  the numbers and links the method; nothing on it is unmeasured.
- Anchors: `docker/Dockerfile`, `docs/research/research-architecture.md`, `scripts/`.

## Findings

**Done 2026-11-01.** Pre-registered first (`docs/research/measurements/b-173/brief.md`, frozen byte for byte and checked
by `scripts/brief_freeze.py` in `make check`; the freeze `a932d3ea` precedes the first raw file `57621e00`), measured
with `scripts/measure-stack.py` on the shared WSL box, raw runs in `docs/research/measurements/b-173/raw/`, verdicts in
its README and in the research (risk 4, «Measured in B-173»). The page is `/numbers` (`NumbersScreen`, from About's
«The stack in numbers»), drawn as About's body from `server/src/main/resources/numbers/stack-numbers.json`, which
`summarise --check` in `make check` holds to the raw files and `NumbersRoutesTest` holds the page to.

| Question | Verdict |
|---|---|
| Start-up to `/readyz` with the AOT cache against without | **green**: 1,006 against 1,713 ms, −710 ± 109 ms (41 % ± 6 %), ruler 4.2 %, control 960 ± 52 ms |
| … to the first `/ui/home` | **green**: 1,133 against 1,929 ms, −811 ± 112 ms |
| Latency of five routes beside the shoppers | **red** at 200 rps (campaign 1, a loaded box: the last two pairs fell behind, p99 4–5 s); **grey** at 100 rps (campaign 2: every route p50 ≤ 18.7 ms, p99 ≤ 97 ms, but the shoppers' walks stopped finishing) |
| CPU per request | **green** at 100 rps, 3.4 ± 0.6 ms (PostgreSQL 4.6 ms); grey at 200, 4.4 ± 2.1 ms |
| Memory in 768 MiB | **grey**: peak 685 MiB (89 %, line 80 %), never killed; dies under 128 MiB, lives under 192 |

- **The box was not quiet.** Campaign 1's load block met two other sessions' Kotlin/Native compiles and Gradle daemons
  (load average to 13.9); the gate matched Gradle by name only and saw none. Kept as measured; the block was run again
  as campaign 2 (README, A2.1). The collapse of campaign 1's last pairs is not attributed.
- **The shoppers at one walk every 2 s fill every delivery window** after about 450 orders (fifteen minutes): from then
  on their checkouts fail «no window has room». It is the protocol's rate — three hundred times the stand's — not a
  defect, but it ended the shoppers' share of the load in both campaigns (README, A2.3).
- **The harness's own faults** (README, A2.2): the survival control crashed in campaign 1; the per-request lines were
  3.8 MB a campaign and are kept as a histogram; `/healthz` lacks a time in 4 of 44 starts.
- **Not done here:** a second host for the tail, a run against an empty database, and a lower `MaxRAMPercentage` — the
  89 % is where the JVM's heap setting put it, not what the server needs.
- The freeze's history check with `--window` runs on this branch (green at the last commit); it is not in `make check`,
  because a squash merge puts the freeze and the raw runs into one commit, which `--window` refuses by design.

