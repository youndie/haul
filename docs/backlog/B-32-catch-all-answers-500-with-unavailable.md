---
id: B-32
title: "server: the catch-all answers 500 with the `unavailable` code"
status: open
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
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt`.
