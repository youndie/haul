---
id: B-32
title: "server: the catch-all answers 500 with the `unavailable` code"
status: done
priority: P2
size: S
stage: stage-3-search
blocked_by: []
---

# B-32 — server: the catch-all answers 500 with the `unavailable` code

`status(ErrorCode.Unavailable)` maps to `503`, but the only sender of `Unavailable` is the
`StatusPages` catch-all in `HaulModule.kt`, which answers it with `500`. A client reading the code
sees «try again later» on what is a bug, and a reader of the endpoint documents is promised a `503`
that is never sent (found while syncing the drafts after B-10).

Decide one meaning and make code and documents agree: either an unexpected failure is its own code
(`internal`, `500`) and `unavailable` stays for a dependency that is down (the database, `503`), or
the catch-all keeps `unavailable` and answers `503`. The first keeps a bug distinguishable from an
outage, which is what the error budget needs.

- AC: no response pairs a code with a status that `status(code)` would not give it; a test sends an
  unexpected exception through a route and asserts the pair.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`, `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt`, `server/src/test/kotlin/io/github/youndie/haul/ErrorAnswersTest.kt`.

## Findings

### 2026-10-08

- **Decision: the first option.** An unexpected failure is `internal` / `500`, reported to katcher,
  with a fixed sentence and nothing of the exception in the body; `unavailable` / `503` is kept for a
  database that cannot be reached. Every error body leaves through one function that takes the
  status from `status(code)`, so a code and a status can no longer be paired by hand.
- **Ktor's own client errors were `500` too.** Checked before the fix with a route throwing
  `BadRequestException`: it answered `500` with `unavailable`. Ktor 3.6.0 throws that exception
  itself for a query or a path it cannot decode, and the `Throwable` handler took it. They now keep
  the status Ktor's engine gives them: a `400` carries `validation_failed` («Malformed request»);
  `404`, `413` and `415` go out with the status alone and no code, because no code means them — and
  no route today can raise them (nothing calls `receive`, there are no typed resources). The code for
  one of those is the decision of the item that first makes it reachable.
- **A malformed URL cannot be sent from the test client.** Ktor's client decodes `%zz` itself, even
  when appended to `encodedParameters`, and throws `URLDecodeException` before the request leaves;
  the test throws `BadRequestException` from a test route instead.
- **«The database is down» is recognised by the exception, not guessed.** Through `/ui/home` with a
  pool that cannot connect, what arrives is Hikari's `SQLTransientConnectionException`, not wrapped
  by Exposed; an SQLSTATE of class `08` (connection exception) anywhere in the cause chain counts as
  well. Two consequences: a pool with every connection busy raises the same exception and is also a
  `503`, which reads right («overloaded, retry»); a closed pool (shutdown) raises a plain
  `SQLException` with no SQLSTATE and stays `500`.
- **During an outage a request waits Hikari's `connectionTimeout` before its `503`** — the default
  30 s, since `Databases.dataSource` does not set it. Not changed here.
- **A cancelled call was answered `500` and reported as a bug.** The catch-all now rethrows
  `CancellationException`, so a timeout is Ktor's `504` and a client that went away is not filed with
  katcher.
- **Everything that reaches the catch-all is still reported**, the `400`s included (the
  ktor-server-feature rule: if it got there, nobody expected it); a client error is logged at `info`,
  the rest at `error` with the stack, before the report. If scanners sending malformed URLs make the
  katcher group noisy, reporting only the `5xx` is the choice of whoever reads katcher.
- **Documents.** Nothing on `main` stated the old pair. The draft endpoint documents in the open
  documentation pull request do: `docs/api/endpoint-catalog.md` («any unhandled failure on every
  route is `500` unavailable») and `docs/api/endpoint-recommendations.md` (`503` unavailable for the
  personalised part of `/ui/home`, which nothing sends yet).
- **Mutation check** (`ErrorAnswersTest`, 8 tests): the catch-all answering `unavailable` again →
  3 red; Ktor's `BadRequestException` not recognised → 1 red; the database classification off → 3
  red; the report dropped → 2 red; the error answers not installed by `haulModule` → 2 red; the
  cancellation rethrow dropped → 1 red.
