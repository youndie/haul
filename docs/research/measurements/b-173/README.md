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
**Window:** open until the first measurement; it closes with the first file under `raw/`.

## Pins

| Row | Pin | Where it came from |
|---|---|---|
| Subject | the branch `feat/b-173-stack-in-numbers` at `647b8b74f5311b165cfe9dfe83d26b2e99c041b9`, built by `scripts/image-build.sh haul/server:b173` | `git rev-parse HEAD`, the image built 2026-11-01 11:31 CET |
| Image | `sha256:e2e5512c80300869f62f3b6599424d1e4b3f9953b93f988dd878e2196945e2e7` (`eclipse-temurin:25.0.4_7-jre`, the cache `lib/app.aot` trained in the build) | `docker image inspect` |
| Start script's AOT flags | `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching -XX:AOTCache=$APP_HOME/lib/app.aot`, then `JAVA_OPTS` | `server/build/install/server/bin/server:205-208` |
| Host | open until the first run writes its header | the harness |
| Generator | k6 v1.4.1 (`commit/8cc106e574`, go1.25.4) | `k6 version` on the box |
| Offered rate R | open until stage 0's saturation steps | the brief, «The operating point R» |

## What was believed, answered

| Belief | Answer |
|---|---|
| 1. `Opened AOT cache` is printed even on a refusal | open: read per run |
| 2. `-XX:AOTMode=off` overrides the start script's cache | open: read per run |
| 3. `Server-Timing` excludes the wait before the application | open |
| 4. Docker's own start is the same in every arm | open: the `empty` arm |
| 5. The paired CPU difference cancels the shoppers | open |

## Amendments

None yet.

## Results

Not measured yet.
