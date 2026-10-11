---
id: B-122
title: "ops: the stand's server shows up in metrik and katcher"
status: open
priority: P2
size: S
stage: stage-12-qa-run
---

# B-122 — ops: the stand's server shows up in metrik and katcher

Found walking TC-STAND-05 in the catalogue's first run ([run](../qa/runs/2026-10-11-v0.4.0.md)). The case reads
the stand's errors for an hour of the shoppers' walks; on v0.4.0 there was nothing to read them in:

- **metrik does not know the service.** The infrastructure release sets `observability.metrik.endpoint`
  (`metrik-metrik-ingest.metrik.svc.cluster.local:9999`), and `Observability.kt` installs the agent when it has an
  endpoint and a key — yet metrik's service list (hub, metrik-server, shildik, shop, shop-admin, skedl-server,
  stocker) has no `haul-server`, and asking for its 5xx answers «no such service». metrik ingests over UDP, so a
  wrong key or an unreachable address fails silently.
- **katcher is off on purpose**, per the release's own comment: its key belongs to an application katcher
  creates, and no `haul-server` application exists there yet. A `500` on the stand is a log line and nothing
  else.
- tracy has `haul-server` (the order trail, spans) but no line at WARN or above in a week — not even «metrik is
  off», which the server would log at start if the key were missing. Whether that means no warnings or warnings
  that do not ship is part of this item.

- **The decision:** the stand is the place these three are proved, so each is either reporting or says why not
  in the server's start-up line. Rejected: reading the pod's log instead — the case is meant to be walked by an
  operator without the cluster (TC-STAND intro).
- Not covered: alert rules in metrik.

- AC: metrik lists `haul-server` with the stand's traffic; katcher has a `haul-server` application and the
  release its key (an owner action: the application is created in katcher); a forced `500` on a local stack
  with all three on shows in each; tracy shows a WARN line the server logs at start.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`, `charts/haul/values.yaml`.
