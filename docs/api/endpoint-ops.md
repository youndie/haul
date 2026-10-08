---
id: endpoint-ops
title: Probes
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - none — the probes are the server's own
parent_feature: feature-browse
---

# API: Probes

> Drafted from the product brief before any code exists. The routes, tiers and error codes are
> *target*; the contract classes in `shared` will be the truth, and this document is checked
> against them before it goes `active`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/healthz`, `/readyz`, `/version` | haul-server | infra (probes) | no | request: —; answers probe body |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler (planned) |
|---|---|
| `GET` `/healthz`, `/readyz`, `/version` | `server/src/main/kotlin/io/github/youndie/haul/feature/ops/` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/ops/` — `none` |

## Request and response bodies

In `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/ops/` once it exists; not copied here.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/healthz`, `/readyz`, `/version` | `503` not ready |
