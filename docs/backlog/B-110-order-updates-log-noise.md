---
id: B-110
title: "server: a client leaving the order page is not an error"
status: wip
priority: P3
size: XS
stage: stage-11-qa
---

# B-110 — server: a client leaving the order page is not an error

Found in the QA pass of 2026-10-10 (v0.3.0). Whenever a client leaves the order page the server logs `ERROR … unhandled: GET /ui/updates` (Broken pipe) —
an expected disconnect reported as an unhandled error; on the stand the synthetic shoppers make one per walk.

- AC: a closed update stream ends quietly (DEBUG at most); a test.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`.
