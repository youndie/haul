---
id: B-147
title: "fix: a guest's sign-in presses offline say the store could not be reached"
status: wip
priority: P3
size: XS
stage: stage-12-qa-run
epic: feature-identity
---

# B-147 — fix: a guest's sign-in presses offline say the store could not be reached

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B and D independently). Offline, a guest's «Sign in»,
«Orders», «Saved», «HAUL PLUS» and the heart send `GET /api/v1/sign-in` → `ERR_INTERNET_DISCONNECTED`, then ask the
current page's tree again (also failing): no message, no Retry, nothing moves — on a loaded page and on a failed one.
Every other press offline says «Couldn’t reach the store».

- **A press that cannot reach the store says so**, as every other press does (B-92's message), with Retry where the
  press can be repeated.
- AC: offline, each of the presses above shows «Couldn’t reach the store»; online again, the press goes on as today; a
  client test.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`.
