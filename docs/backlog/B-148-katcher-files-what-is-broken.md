---
id: B-148
title: "ops: katcher and the error log get what is broken, not a client's malformed link or an outage per page"
status: done
priority: P2
size: S
stage: stage-12-qa-run
---

# B-148 — ops: katcher and the error log get what is broken, not a client's malformed link or an outage per page

Found by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md)). With katcher on (B-122), the stand's «Haul» application filed four groups in an hour, each
a guest's malformed link — `BadRequestException: Url decode failed for query parameters of /search?q=%zz` — though
the store answers those addresses with its page (B-117); and on a local stack every `503` during a database outage
was logged `ERROR … ErrorAnswers - unhandled: GET /ui/deals` with the full `ExposedSQLException` stack (11 in one walk,
walker D) — on the stand, one katcher report per page shown during an outage. B-32 decided that the catch-all reports
every failure it answers; on a public stand that makes katcher a list of crawlers' typos and one outage's echoes.

- **katcher gets what the store got wrong**: a `5xx` the store did not expect. A client's `4xx` (a malformed query,
  an unknown path) is not reported; an outage is reported once (or as a WARN line with its cause) rather than per
  request, and the 503 a shopper sees is logged without a stack. Writes the decision into research next to B-32's.
- AC: a malformed link and a 404 file nothing in katcher; a database outage answered 503 to N pages files at most one
  report and logs no stack per page; an unexpected 500 is still filed (B-122's test); server tests with a fake katcher.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/db/FailFastDataSource.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/ops/ObservabilityTest.kt`.

## Findings (2026-10-13)

- **The decision** (research, «Decided in B-148», beside B-122's): katcher is sent an unexpected `500` and nothing
  else. Ktor's own `4xx` and a `503` are lines at `info` without a stack. The outage's one `warn` is the one
  `FailFastDataSource` already logged when a connection first could not be had (B-127), not again until one is had;
  it now carries what the database's address answered (`(Connection to 127.0.0.1:1 refused. …)`), which it lacked —
  Hikari's message alone is «Connection is not available, request timed out». katcher gets no outage report at all:
  the item allowed «once, or a `WARN` with its cause», and the second needs no state in the catch-all, while one
  report per outage would need the catch-all to tell where an outage begins and ends, which the pool knows already.
- **Why the page at `/p/…?sku=%zz` still reached katcher after B-117.** `PageAtAnyQuery` answered from an `onCall`
  hook, which cannot end the call; Ktor 3.6's `RoutingRoot.interceptor` does not check `isHandled`, resolves the
  page's route, and builds the `RoutingPipelineCall` with the call's parameters — the query decoded there, after the
  page had gone, threw `BadRequestException("Url decode failed for query parameters of …")` into the catch-all, which
  logged a `400` and reported it for a call answered `200`. Reproduced by the new `WebBundleTest` with the `finish()`
  taken out: the four addresses of TC-ERR-04 each leave `GET … → 400: Url decode failed …`. The plugin now
  intercepts `ApplicationCallPipeline.Plugins` itself and `finish()`es after the page (and leaves a call already
  answered alone).
- **Tests** (server, WSL box): `ObservabilityTest` against a fake katcher endpoint — «a client's malformed link and
  an unknown address file nothing in katcher» (over a socket to CIO with the bundle: the page `200`, its tree `400`,
  an unknown address `404`, then an unexpected `500` filed as the control and the marker) and «a database outage
  answered 503 to every page files nothing and logs no stack per page» (an unreachable pool, five pages, one `warn`
  from the pool with its cause, five `info` lines without a stack, only the control `500` filed). The fake is drained
  with `Katcher.flush` before and after, so a report another test JVM left on disk cannot pass or fail it.
  `ErrorAnswersTest`: the malformed `400` and a `404` not reported, each `503` not reported and logged at `info` with
  no stack (one with its cause in the line), and the `500` logged at `error` with its stack. `WebBundleTest`: the
  four malformed page addresses leave the catch-all nothing.
- **Mutation check:** client errors reported again → 4 red (`ErrorAnswersTest` ×2, `ObservabilityTest` malformed
  link — katcher received `Url decode failed for query parameters of /p/p-…?sku=%zz`, the stand's group);
  `finish()` dropped → `WebBundleTest` red; the cause dropped from the pool's `warn` → the outage test red; the
  `503` reported → 3 red; the `503` logged with its stack → 3 red.
- **Seen on the way, not changed:** with a pool of one connection, the outage test's first two pages waited about ten
  seconds each and were answered in the millisecond the saga sweeper's patient ask (B-139) gave up — read off the
  log's timestamps, not traced further; the existing `ErrorAnswersTest` «a database
  that cannot be reached makes a screen 503 unavailable» runs on the same shape. The stand's pool is larger; the
  `DatabaseOutageTest` bound is unaffected. The sweeper itself logs `saga sweeper failed at …` at `warn` once a pass
  during an outage — per pass, not per page, and outside this item.
