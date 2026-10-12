---
id: B-122
title: "ops: the stand's server reports to katcher"
status: done
priority: P3
size: XS
stage: stage-12-qa-run
---

# B-122 — ops: the stand's server reports to katcher

Found walking TC-STAND-05 in the catalogue's first run ([run](../qa/runs/2026-10-11-v0.4.0.md)). The case reads
the stand's errors for an hour of the shoppers' walks in its log and in katcher; katcher is off on the stand, on
purpose, per the infrastructure release's own comment: its key belongs to an application katcher creates, and no
`haul-server` application exists there yet. So a `500` on the stand is a log line in tracy and nothing else — the
half of TC-STAND-05 that reads katcher has nothing to read.

- **The decision:** the stand is where the wiring of B-27 is proved, so katcher is switched on there like tracy
  and metrik. It is an owner action first — the application is created in katcher, which issues its key — then
  one commit in the infrastructure repository: the secret `HAUL_KATCHER_KEY` and the address uncommented.
- Not covered: metrik — the run did not read the stand's metrik (the walker reached only another installation's),
  so nothing here says it is missing.

- AC (as of 2026-10-12, the owner's action done):
  - [x] katcher holds the stand's application and the stand reports to it — the owner registered it («Haul», id 12;
    the application is named «Haul», not `haul-server`: katcher files by the key, and the name is a label) and the
    infrastructure release carries its key and the address (vedutsya-raboty/infra#104, deployed 2026-10-12 18:56Z).
    Recorded as stated by the owner's side; not read from the stand here.
  - [x] a `500` forced on a local stack with katcher on is filed there — katcher's own image beside the image built
    from this branch, the `500` caused by a table gone under a real route (findings); kept by `ObservabilityTest`
    «a 500 is filed in katcher» against a fake katcher endpoint.
  - [x] a start-up `warn` reaches tracy — the line «katcher is off …» was logged at every start on the stand and
    never appeared in tracy; kept by `ObservabilityTest` «a warning logged at start-up is shipped to tracy» against
    a fake tracy endpoint.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/ops/ObservabilityTest.kt`, `charts/haul/values.yaml`.

## Findings

- **No SLF4J line of the server ever reached tracy — the start-up `warn` was only the most visible.** tracy's
  agent captures nothing by itself on the JVM: its Ktor plugin carries the spans, its API what is written through
  it (the order's trail, B-109), and the Logback appender it ships (`TracyLogbackAppender`) is the host's to attach.
  Nothing attached it (B-109 recorded as much and left it). So not «katcher is off», not «metrik is off», not
  `unhandled: GET …` with its stack, not a saga's rollback given up: every `log.warn`/`log.error` stayed in the
  pod's log. The cause was neither the order of start-up nor a filter nor a dropped buffer — there was no path.
- **The fix** (`ops/Observability.kt`, `shipWarnings`): the appender is attached to the root logger when tracy is
  installed — first of the three agents, so the lines saying metrik or katcher is off already ship — with a
  threshold at `warn`, and detached when the application stops (the logger context outlives an application; in a
  test JVM each application would leave one more appender behind). Logback moves from `runtimeOnly` to
  `implementation`, since it is now compiled against.
- **Why from `warn` and not the agent's `info`:** a captured line has no trace (SLF4J gives the agent none), so it
  skips the tail decision and is sent whole. At `info` that is `ErrorAnswers`' line for every refused request at the
  shoppers' rate, besides the start-up lines; at `warn` it is what tracy keeps from every request anyway.
- **Not shipped: what `main` logs before the application starts** — migrations, the seed, «sample photos not
  stored» (`Application.kt`). tracy is installed inside the module; shipping those means making the agent before
  the database. Left as it is; recorded in the research.
- **The v0.5.1 run's pass of TC-STAND-05 read nothing.** «None from `haul-server` at WARN and above» in tracy was
  true of every hour of the stand's life, failures included, so the case's log half could not fail there. From
  this change it can; TC-STAND-05 now reads tracy from `warn` up and katcher, both automated by `ObservabilityTest`.
- **What katcher receives is wider than a `500`:** the catch-all hands katcher every failure it answers — a `503`
  (database unreachable) and Ktor's own `4xx` (`BadRequestException`, `NotFoundException` …) included
  (`ErrorAnswers.kt`, B-32; `ErrorAnswersTest` asserts the `400` is reported). On the stand a malformed or unknown
  address from a crawler will appear in katcher as a group. Recorded, not changed: it is B-32's decision.
- **The local stack, 2026-10-12 on the WSL box** — katcher's own image (`ghcr.io/youndie/katcher:latest`, built
  2026-10-02) and tracy's (`ghcr.io/youndie/tracy:0.3.8`) beside the image `scripts/image-build.sh` built from this
  branch (its fix commit, 82490d2 before the rebase onto main — 3ccb94f), with PostgreSQL, on one Docker network; the application `haul-server` created in katcher
  through its own form (`POST /apps`, the proxy's user headers set by hand) and its key read from the key reveal.
  The server seeded, ready, `/ui/deals` `200`; then `ALTER TABLE deals RENAME TO deals_gone` in its database and
  `/ui/deals` answered `500 {"code":"internal",…}`. Within 8 s katcher's error list for the application held one
  group, `ExposedSQLException` «relation "deals" does not exist», its report with the release `b122-local`; tracy
  held «metrik is off …» at `WARN` (the start-up line — katcher was on, so «katcher is off» was not logged) and
  «unhandled: GET /ui/deals» at `ERROR`, both from `haul-server`. Nothing of the stand, the cluster or the
  infrastructure repository was touched.
- **The test's `500` goes through a real route:** a migrated database whose `deals` table was renamed, `/ui/deals`
  over it — PostgreSQL's «relation "deals" does not exist» through Exposed, answered `500 internal`, filed with the
  release and the environment the server was given. katcher's client is a process-wide object, started once
  per test JVM by this test; the other tests run with katcher off and never call it.
