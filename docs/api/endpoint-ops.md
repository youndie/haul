---
id: endpoint-ops
title: Probes
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - none — the probes are the server's own
parent_feature: feature-browse
---

# API: Probes

> The orchestrator's three probes, described as the code has them. They answer plain text or a
> one-field JSON object written in place; nothing of them is in `shared`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/healthz` | haul-server | infra (none) | no | liveness: `200` `ok` while the process answers; touches nothing else |
| `GET` `/readyz` | haul-server | infra (none) | no | readiness: `200` `ready` while the database gives a valid connection within two seconds, otherwise `503` `not ready` |
| `GET` `/version` | haul-server | infra (none) | no | `200` `{"commit":"<HAUL_COMMIT>"}`, `dev` when the variable is unset |

Liveness does not look at the database on purpose: a database that is gone makes the pod not ready,
and restarting the server would not bring it back. The chart points start-up and liveness at
`/healthz` and readiness at `/readyz` (`charts/haul/templates/server.yaml`).

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/healthz`, `/readyz`, `/version` | `server/src/main/kotlin/io/github/youndie/haul/ops/Probes.kt`, mounted by `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt` with `databaseAnswers` from `server/src/main/kotlin/io/github/youndie/haul/Application.kt` |
| contract | none |

## Request and response bodies

None beyond the table above: `text/plain` for `/healthz` and `/readyz`, `application/json` for
`/version`.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/healthz` | none |
| `GET` `/readyz` | `503` with the text `not ready` — no `ErrorBody`, no `code` |
| `GET` `/version` | none |

Tests: `server/src/test/kotlin/io/github/youndie/haul/ops/ProbesTest.kt`.
