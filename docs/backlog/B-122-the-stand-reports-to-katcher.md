---
id: B-122
title: "ops: the stand's server reports to katcher"
status: open
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

- AC: katcher lists a `haul-server` application with the stand's release; a `500` forced on a local stack with
  katcher on is filed there.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`, `charts/haul/values.yaml`.
