---
id: B-174
title: "feat: the server runs as two replicas — an external bus, one leader for the simulators"
status: open
priority: P3
size: L
stage: stage-15-showcase
---

# B-174 — feat: the server runs as two replicas — an external bus, one leader for the simulators

The server is one replica by construction: the live-order bus is in memory, the simulators and the sweeper run in
every process, some facets are cached in memory. The architect's review (2026-11-01): «the saga survives a killed
pod» is the claim worth demonstrating, and today it cannot be.

- The bus goes through PostgreSQL `LISTEN/NOTIFY` (already in the stack) or booblik; the simulators and the sweeper
  run in the leader only (vojak advisory locks); the chart allows `replicas: 2`. A demonstration — a pod killed under
  the shoppers' traffic, no order lost or doubled — recorded with its numbers.

- AC: two replicas on a local stand pass the e2e; killing one mid-saga loses and doubles no order (a test or a
  recorded run); the chart's checks hold for both counts.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`, `server/src/main/kotlin/io/github/youndie/haul/ops/`, `charts/`.
