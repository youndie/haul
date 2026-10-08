---
id: haul-server
title: Haul server
type: service
module: server
tech_stack: [Kotlin, Ktor, kompot-ktor, petich, shildik, PostgreSQL, JVM 25, zavarnik]
owner: unassigned
depends_on:
  - haul-shared
  - PostgreSQL
  - shildik (OIDC issuer)
publishes:
  - container image with a zavarnik AOT cache
---

# Haul server

> Drafted before the module exists: every path below is planned and every claim is *target*,
> checked against the code before this document lands on `main`.

## 1. Responsibility

Owns all data: catalog, carts, guests, customers, orders and their shipments, the saga's state,
reviews, questions, saved lists, memberships and points. Builds every screen as a kompot tree, takes
every command, runs the order saga, and simulates the outside world — the card processor and the
fulfilment of each seller's shipment.

Deliberately does **not**: render anything (the browser does, from the tree); talk to a real payment
provider or courier; host the seller side (sellers, products and answers to questions are seed
data); send e-mail or push notifications.

## 2. API contracts

* **Contracts:** [haul-shared](haul-shared.md) — the Haul components on the wire, route classes,
  `ErrorCode`.
* **Wire conventions**, once for every endpoint group:
  - **screens** are `GET /ui/...` routes answering a kompot tree through `respondKompotComponent`
    (never `call.respond`, which drops the root's type discriminator); the client never asks for a
    screen's data as JSON;
  - **commands** are `POST` / `PUT` / `DELETE` under `/api/v1/...`, taking JSON and answering a
    kompot action (refresh the screen, navigate, show a route over it) or an error;
  - JSON with `kotlinx.serialization`, `explicitNulls = false`; money as integer cents plus `USD`;
    instants as ISO-8601 UTC;
  - an error is `{ "code": "<error_code>", "message": "…", "field": "…"? }` with `code` from the
    closed `ErrorCode` enum; a screen route that cannot be built answers the status with that body,
    and the client draws the screen's `NotFound` or `Error` state;
  - «not yours» and «does not exist» are both `404`.
* **Auth tiers:**

  | Tier | Credential | Mounted on |
  |---|---|---|
  | public | none, or `X-Haul-Guest: <guest id>` | catalog, search, cart, `POST /api/v1/guests` |
  | customer | `Authorization: Bearer <shildik access token>`, checked by shildik `oidc-auth-server` | everything under `/api/v1/me`, checkout, orders, saved, reviews, membership |
  | infra | none, not in the public schema | `/healthz`, `/readyz`, `/version` |

  There are no roles inside a tier; a Plus membership changes prices and benefits, not access.

## 2a. Code anchors

| File (planned) | What is there |
|---|---|
| `server/src/main/kotlin/io/github/youndie/haul/Application.kt` | entry point, route composition and the tier of every mount |
| `server/src/main/kotlin/io/github/youndie/haul/di/` | dependency wiring and config bindings |
| `server/src/main/resources/db/migration/` | migrations |
| `server/src/main/kotlin/io/github/youndie/haul/seed/` | the generated catalog and the sample-data fixtures |
| `server/src/main/kotlin/io/github/youndie/haul/feature/order/` | the order saga |
| `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` | the payment simulator |
| `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/` | the fulfilment simulator |
| `server/build.gradle.kts` | the `application` plugin and zavarnik |

## 3. How it is built

* **One feature, one package**, each with its use cases, its repository, its routes and the tree
  builders of the screens it owns (`catalog/` builds Home, Catalog and Product).
* **The order is a petich saga** — reserve stock → authorise payment → confirm — that compensates
  the earlier steps when a later one fails; placement's `Idempotency-Key` is petich-idempotency's.
  Capture is per shipment, triggered by the fulfilment simulator at `in_transit`: «your card is
  charged when the order ships».
* **The outside world is in-process**: a payment simulator (every card approves except the one
  ending `0002`) and a fulfilment simulator on a clock whose speed is configuration.
* **The clock is injected**, so the seed and the fixtures can run at the canvas's «now»,
  2025-10-07 19:47:23 America/New_York.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Database | PostgreSQL | everything the server owns, the saga's state included |
| Module | [haul-shared](haul-shared.md) | the contract |
| External | shildik | the issuer whose tokens the customer tier accepts |

## 5. Infrastructure and deploy

* **Image:** a JVM image with a zavarnik AOT cache trained at build time.
* **Chart:** `charts/haul/` (planned, B-27); the server also serves the web bundle.
* **Health:** `GET /healthz`, `GET /readyz`; **Version:** `GET /version`.

## 6. Local setup

```bash
docker compose up -d   # PostgreSQL and a shildik instance (planned)
./gradlew :server:run
```

## 7. Configuration

| Key | Description | Required |
|---|---|---|
| `HAUL_DB_URL` | PostgreSQL | yes |
| `HAUL_OIDC_ISSUER` | the shildik issuer | yes |
| `HAUL_FULFILMENT_SPEED` | how fast the simulator moves shipments | no |
| `HAUL_SEED` | seed the database on start | no |
