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

> Describes the server as it stands after B-11, B-27, B-32 and B-33: catalog, search, guests and the
> cart, the probes, the web bundle, the agents. What is still *target* — customers and shildik, the
> order saga, the simulators — is marked where it appears.

## 1. Responsibility

Owns all data: catalog, carts, guests, customers, orders and their shipments, the saga's state,
reviews, questions, saved lists, memberships and points. Builds every screen as a kompot tree, takes
every command, runs the order saga, and simulates the outside world — the card processor and the
fulfilment of each seller's shipment. Today it holds the catalog, search, guests and carts
(`server/src/main/kotlin/io/github/youndie/haul/feature/`); customers, orders and the rest are
*target*.

It also serves the browser bundle at `/` (section 5), so the page and the API come from one image
and one origin.

Deliberately does **not**: render anything (the browser does, from the tree); talk to a real payment
provider or courier; host the seller side (sellers, products and answers to questions are seed
data); send e-mail or push notifications.

## 2. API contracts

* **Contracts:** [haul-shared](haul-shared.md) — the Haul components on the wire, the command
  bodies, `ErrorCode`. There are no route classes: the paths are the server's strings, kept in each
  feature's routing file and handed to the client inside the tree.
* **Wire conventions**, once for every endpoint group:
  - **screens** are `GET /ui/...` routes answering a kompot tree through `respondKompotComponent`
    (never `call.respond`, which drops the root's type discriminator); the client never asks for a
    screen's data as JSON;
  - **commands** are `POST` / `PUT` / `DELETE` under `/api/v1/...`, taking JSON and answering a
    kompot action (refresh the screen, navigate, show a route over it) or an error — the cart's
    answer `refresh` (`respondKompotAction`);
  - JSON is `haulWireJson` (`shared/src/commonMain/kotlin/io/github/youndie/haul/HaulWire.kt`):
    discriminator `type`, `explicitNulls = false`, unknown keys ignored. Prices and dates travel as
    the strings the screen shows, formatted by the server; the deals' countdown is an ISO-8601
    instant;
  - an error is `{ "code": "<error_code>", "message": "…", "field": "…"? }` (`ErrorBody`) with
    `code` from the closed `ErrorCode` enum, and every one leaves through `respondError`, so its
    status is always the one `status(code)` gives it (`HaulModule.kt`); a screen route that cannot
    be built answers the status with that body, and the client draws the screen's `NotFound` or
    `Error` state;
  - «not yours» and «does not exist» are both `404`.
* **What no feature named** is answered by the catch-all (`ErrorAnswers.kt`, B-32): a cancelled or
  timed-out call is rethrown and left to Ktor (`504`), not reported; Ktor's own client errors keep
  their status — `400` as `validation_failed` «Malformed request», `404`/`413`/`415` with the status
  alone and no body; a database that cannot be reached (Hikari's connection timeout, or an
  SQLSTATE of class `08` anywhere in the cause chain) is `503 unavailable`; anything else is
  `500 internal` «Something went wrong», nothing of the exception in the body. Everything that
  reaches the catch-all is logged and reported to katcher when it is on, the `400`s included. A
  `503` comes after Hikari's `connectionTimeout`, the default 30 s, which is not set.
* **Auth tiers:**

  | Tier | Credential | Mounted on |
  |---|---|---|
  | public | none | catalog, search, `POST /api/v1/guests` |
  | public, guest | `X-Haul-Guest: <guest id>` the server issued, otherwise `401 unauthenticated`; a customer token joins it with B-12 | the cart |
  | customer | `Authorization: Bearer <shildik access token>`, checked by shildik `oidc-auth-server` — *target*, B-12 | everything under `/api/v1/me`, checkout, orders, saved, reviews, membership |
  | infra | none, not in the public schema | `/healthz`, `/readyz`, `/version` ([endpoint-ops](../api/endpoint-ops.md)) |

  There are no roles inside a tier; a Plus membership changes prices and benefits, not access.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server/src/main/kotlin/io/github/youndie/haul/Application.kt` | entry point: configuration, migrations, the seed when asked, the CIO engine |
| `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt` | the application as `main` and the tests assemble it: the agents, DI, error answers, every route, the web bundle |
| `server/src/main/kotlin/io/github/youndie/haul/ServerConfig.kt` | every environment variable (section 7) |
| `server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt` | the catch-all |
| `server/src/main/kotlin/io/github/youndie/haul/ops/` | the probes (`Probes.kt`) and metrik, tracy, katcher (`Observability.kt`) |
| `server/src/main/kotlin/io/github/youndie/haul/feature/` | `catalog/`, `search/`, `identity/`, `cart/` — each with its routing, use cases, storage and screens |
| `server/src/main/resources/db/migration/` | migrations (Flyway) |
| `server/src/main/kotlin/io/github/youndie/haul/seed/` | the generated catalog and the sample-data fixtures, promo codes included |
| `server/build.gradle.kts` | the `application` plugin, zavarnik, and the browser bundle copied into the distribution |
| `server/src/main/kotlin/io/github/youndie/haul/feature/order/` | the order saga (*planned*) |
| `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` | the payment simulator (*planned*) |
| `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/` | the fulfilment simulator (*planned*) |

## 3. How it is built

* **One feature, one package**, each with its use cases, its repository, its routes and the tree
  builders of the screens it owns (`catalog/` builds Home, Catalog and Product; `cart/` the Cart).
* **The order is a petich saga** (*target*) — reserve stock → authorise payment → confirm — that
  compensates the earlier steps when a later one fails; placement's `Idempotency-Key` is
  petich-idempotency's. Capture is per shipment, triggered by the fulfilment simulator at
  `in_transit`: «your card is charged when the order ships».
* **The outside world is in-process** (*target*): a payment simulator (every card approves except
  the one ending `0002`) and a fulfilment simulator on a clock whose speed is configuration.
* **The clock is injected** (`StoreClock`), so the seed and the fixtures can run at the canvas's
  «now», 2025-10-07 19:47:23 America/New_York; `Application.kt` reads the wall clock for the
  store's «now», and only tracy's record timestamps read it elsewhere (`ops/Observability.kt`).

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Database | PostgreSQL | everything the server owns, the saga's state included |
| Module | [haul-shared](haul-shared.md) | the contract |
| Module | [haul-web](haul-web.md) | the browser bundle it serves at `/` |
| External | shildik | the issuer whose tokens the customer tier accepts (*target*, B-12) |
| External | metrik, tracy, katcher | route metrics, traces and shipped logs, crash reports — each optional (section 7) |

## 5. Infrastructure and deploy

* **Image:** `docker/Dockerfile` — a JVM image with a zavarnik AOT cache trained at build time
  against a PostgreSQL of its own; it sets `HAUL_WEB_DIR=/opt/app/web` and runs as uid 1000.
  `scripts/image-check.sh` checks the image, its cache, the page (`200`) and the module's
  `application/wasm` type.
* **The web bundle:** the distribution carries the wasm bundle in its `web` directory
  (`server/build.gradle.kts`), and `HaulModule.kt` serves `HAUL_WEB_DIR` at `/` with `staticFiles`,
  beside the API. Every API route is more specific, so a screen route always wins, and there is no
  fallback to `index.html`: an unknown `/ui/...` stays a `404` (`WebBundleTest`). The bundle is
  served uncompressed today (B-34).
* **Chart:** `charts/haul/` (B-27) — the server Deployment (one replica, rolled with a surge of one
  and none unavailable; start-up
  and liveness on `/healthz`, readiness on `/readyz`; an init container that waits for the
  database; no service links), its Service, PostgreSQL in the release (Deployment, a PVC kept on
  uninstall, Service), one Secret, Traefik IngressRoutes. Rendering refuses a missing image tag,
  password or host, and half an agent; `scripts/chart-check.sh` makes each refusal fire.
  `charts/haul/values-stand.yaml` holds the public stand's choices; the stand is not deployed
  (`.github/workflows/stand.yaml` runs only by hand, B-27 waits for the owner).
* **Health:** `GET /healthz`, `GET /readyz`; **Version:** `GET /version` ([endpoint-ops](../api/endpoint-ops.md)).

## 6. Local setup

```bash
# PostgreSQL of your own; the variables of section 7
HAUL_DB_URL=jdbc:postgresql://127.0.0.1:5432/haul HAUL_DB_USER=haul HAUL_DB_PASSWORD=haul \
  HAUL_SEED=true ./gradlew :server:run
```

Without `HAUL_WEB_DIR` the server answers the API and serves no page. There is no compose file yet,
and no shildik instance to run against (B-12).

## 7. Configuration

All read in `server/src/main/kotlin/io/github/youndie/haul/ServerConfig.kt`; a required value has
no default, and the server refuses to start without it.

| Key | Description | Required |
|---|---|---|
| `HAUL_DB_URL` | PostgreSQL, a JDBC URL | yes |
| `HAUL_DB_USER`, `HAUL_DB_PASSWORD` | its credentials | yes |
| `HAUL_DB_POOL_SIZE` | the Hikari pool, 10 by default | no |
| `HAUL_SEED` | `true` seeds the catalog on start when it is empty; off by default | no |
| `HAUL_COMMIT` | what `/version` names, and the release every agent files under; `dev` when unset | no |
| `PORT` | the port, 8080 by default | no |
| `HAUL_WEB_DIR` | the browser bundle's directory, served at `/`; unset serves no page; named and missing refuses the start | no (the image sets it) |
| `HAUL_ENVIRONMENT` | the environment katcher files reports under; `local` by default | no |
| `HAUL_METRIK_ENDPOINT`, `HAUL_METRIK_KEY` | metrik: on when both are set, off when neither; one without the other refuses the start | no |
| `HAUL_TRACY_ENDPOINT`, `HAUL_TRACY_KEY` | tracy, the same rule | no |
| `HAUL_KATCHER_ENDPOINT`, `HAUL_KATCHER_KEY` | katcher, the same rule; an unhandled `500` is reported to it | no |
| `HAUL_TRACY_SAMPLE_RATE` | tracy's sample rate, a fraction from 0 to 1; tracy's own default when unset; outside 0…1 refuses the start | no |
| `HAUL_OIDC_ISSUER` | the shildik issuer (*planned*, B-12) | — |
| `HAUL_FULFILMENT_SPEED` | how fast the simulator moves shipments (*planned*) | — |

An agent that is off says so at `warn` on start; tracy's delivery and katcher are flushed on
`ApplicationStopping` (`ops/Observability.kt`).
