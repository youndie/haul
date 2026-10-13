---
id: B-148
title: "ops: katcher and the error log get what is broken, not a client's malformed link or an outage per page"
status: open
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
  `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`.
