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
  - S3-compatible object storage (product photos, optional)
publishes:
  - container image with a zavarnik AOT cache
---

# Haul server

> Describes the server as it stands after B-25: catalog with photos and the deals page, search,
> identity (guests, shildik sign-in, customers), the cart, checkout, placement through the order saga
> and the payment simulator, the fulfilment simulator with capture per shipment (B-17), the order page
> and reorder (B-18), the account and its history (B-19), the Saved list (B-20), returns and refunds
> (B-21), reviews, questions and helpful votes (B-22, B-43), Haul Plus and the points ledger (B-23),
> «Picked for you» (B-25), the probes, the precompressed web bundle with its storefront addresses, the
> agents. Still planned: Haul Pay's instalments (B-24).

## 1. Responsibility

Owns all data: catalog, carts, guests, customers, orders and their shipments, the saga's state,
reviews, questions, saved lists, memberships and points. Builds every screen as a kompot tree, takes
every command, runs the order saga, and simulates the outside world — the card processor and the
fulfilment of each seller's shipment. Today it holds the catalog, search, guests and customers,
carts, checkouts and addresses, orders with their own copy of the address, their shipments and each
shipment's history, the saga's state, the payment simulator's ledger with its captures and refunds,
returns, reviews, questions and helpful votes, saved lists, memberships, the points ledger and product
views (`server/src/main/kotlin/io/github/youndie/haul/feature/`). Product photos live in object storage, which the
server reads and serves from its own origin.

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
    kompot action (refresh the screen, navigate, show a route over it) or an error — the cart's,
    checkout's, the merge's and «Clear» on recent searches answer `refresh` (`respondKompotAction`);
    so do the Saved list's, and a helpful vote (`200`); placement answers `202` with `navigate` to the
    order, reorder `navigate` to the cart; a review, a question, a return and the Plus trial answer
    `201` with kompot's `sequence` of `close` and `refresh` (the dialog goes, the page is drawn again);
  - JSON is `haulWireJson` (`shared/src/commonMain/kotlin/io/github/youndie/haul/HaulWire.kt`):
    discriminator `type`, `explicitNulls = false`, unknown keys ignored. Prices and dates travel as
    the strings the screen shows, formatted by the server; the deals' countdown is an ISO-8601
    instant;
  - an error is `{ "code": "<error_code>", "message": "…", "field": "…"?, "fields": [...]? }`
    (`ErrorBody`) with `code` from the closed `ErrorCode` enum — `fields` lists every field at
    fault of a refused form (`FieldError`, `field_required` / `field_invalid`, B-14) — and every one leaves through `respondError`, so its
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
  | public | none, or an optional bearer — one that does not verify is `401`, not ignored; `X-Haul-Guest` for the header's cart count | catalog (photos included), deals, search, `POST /api/v1/guests`, `GET /api/v1/sign-in` |
  | public, cart owner | a customer's bearer or `X-Haul-Guest: <guest id>` the server issued (a token wins), otherwise `401 unauthenticated` | the cart |
  | customer | `Authorization: Bearer <shildik access token>`: signature against the realm's JWKS, lifetime and issuer checked by shildik's `oidc-auth-server` (`configureAuth`), and `azp` must be the storefront's client (`installSignIn` in `feature/identity/SignIn.kt`) | the merge, «Clear» on recent searches, the account, its history and the Saved list, checkout and placement, the order page, reorder and returns, writing a review or a question, a helpful vote, the Plus trial |
  | infra | none, not in the public schema | `/healthz`, `/readyz`, `/version` ([endpoint-ops](../api/endpoint-ops.md)) |

  The tiers are decided at the mount in `HaulModule.kt` (`authenticate(JWT_AUTH_OIDC, optional =
  true)` and `authenticate(JWT_AUTH_OIDC)`); Ktor's empty `401` challenge is rewritten into an
  `ErrorBody` `unauthenticated`. A route that needs a customer also resolves one itself
  (`Callers`), so a guest is `401` there whatever the mount. With sign-in off every bearer is
  refused. There are no roles inside a tier; a Plus membership changes prices and benefits, not
  access.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server/src/main/kotlin/io/github/youndie/haul/Application.kt` | entry point: configuration, migrations, the seed when asked, the CIO engine |
| `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt` | the application as `main` and the tests assemble it: the agents, DI, error answers, every route, the web bundle |
| `server/src/main/kotlin/io/github/youndie/haul/ServerConfig.kt` | every environment variable (section 7) |
| `server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt` | the catch-all |
| `server/src/main/kotlin/io/github/youndie/haul/ops/` | the probes (`Probes.kt`) and metrik, tracy, katcher (`Observability.kt`) |
| `server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt` | the bundle at `/`: precompressed variants, cache headers, the page at the storefront's addresses |
| `server/src/main/kotlin/io/github/youndie/haul/feature/` | `catalog/` (with photos and `/ui/deals`), `search/`, `identity/`, `cart/`, `checkout/`, `order/`, `payment/`, `fulfilment/`, `returns/`, `reviews/`, `account/`, `saved/`, `membership/`, `recommendations/` — each with its routing, use cases, storage and screens |
| `server/src/main/kotlin/io/github/youndie/haul/shell/` | `Frame` (the header and footer every screen but checkout sits in) and `Viewers` (who is looking: first name, cart count, what the cart holds) |
| `server/src/main/resources/db/migration/` | migrations (Flyway), V1 to V20 with no V13 or V16; Flyway runs with `validateOnMigrate` and without `outOfOrder`, so a number below one already merged refuses the start of a migrated database — a new migration takes a number above the highest merged |
| `server/src/main/kotlin/io/github/youndie/haul/seed/` | the generated catalog and the sample-data fixtures, promo codes included |
| `server/build.gradle.kts` | the `application` plugin, zavarnik, and the browser bundle copied into the distribution |
| `server/src/main/kotlin/io/github/youndie/haul/feature/order/` | placement and the order saga, the sweeper's engine (`saga/SagaEngine.kt`) |
| `server/src/main/kotlin/io/github/youndie/haul/feature/payment/` | the payment simulator |
| `server/src/main/kotlin/io/github/youndie/haul/feature/fulfilment/` | the fulfilment simulator (`domain/FulfilmentSimulator.kt`), its pace (`domain/Fulfilment.kt`), pickup codes, `OrderTracking`, the runner (`FulfilmentRunner.kt`), B-17 |
| `server/src/main/kotlin/io/github/youndie/haul/feature/returns/` | the return (`domain/RequestReturn.kt`) and its simulator (`domain/ReturnSimulator.kt`), run with the fulfilment pass, B-21 |
| `server/src/main/kotlin/io/github/youndie/haul/feature/membership/` | the Plus trial and standing, the points ledger (`domain/Points.kt`), the Plus block and dialog, B-23 |

## 3. How it is built

* **One feature, one package**, each with its use cases, its repository, its routes and the tree
  builders of the screens it owns (`catalog/` builds Home, Deals, Catalog and Product; `search/`
  Search; `cart/` the Cart; `checkout/` the Checkout; `order/` the Order; `account/` the overview and
  the history, `saved/` the Saved list inside it; `reviews/` the product page's reviews and questions
  tabs and their dialogs; `membership/` the Plus block and the trial dialog; `recommendations/` «Picked
  for you»). A feature another one reads is reached through a port its consumer declares (the
  account's `Loyalty` and `SavedLists`, bound to `membership/` and `saved/`).
* **Who is calling** is `Callers` (`feature/identity/Callers.kt`): a verified token is the customer
  (created from its `name` claim on first sight), else a guest id the server issued, else nobody.
* **The order is a petich saga** (`feature/order/saga/OrderSaga.kt`, B-16) — reserve stock → reserve
  the window → redeem points (B-23) → open the order (with its own copy of the address, B-40) →
  authorise payment → confirm, then the announcement that clears the bought lines — that compensates in reverse when a member refuses or fails; placement's
  `Idempotency-Key` is petich-idempotency's. It runs inside the placement request. A
  `SuspendedPetichSweeper`, started with the application in its own scope, carries on a saga left
  `PROCESSING` or `COMPENSATING` for 60 s by a process that died. Capture is per shipment, taken by
  the fulfilment simulator just before a shipment moves to `in_transit` — «your card is charged when
  the order ships» (B-17, feature-orders); the points a shipment earns are credited just before it
  moves to `delivered` or `picked_up` (B-23).
* **The outside world is in-process**: a payment simulator (`feature/payment/`, a ledger; every way
  to pay approves except the test card ending `0002`; pay on delivery holds nothing; a capture takes a
  part of an authorisation, keyed, never more than it) and a fulfilment simulator (`feature/fulfilment/`,
  B-17): one pass, `advance()`, moves every shipment through the steps that came due on the saga's
  clock at the store's pace times `HAUL_FULFILMENT_SPEED`. `FulfilmentRunner` runs the pass in the
  application's scope beside the saga's sweeper, every tenth of the shortest step (between 1 s and 1
  min); the tests get `FulfilmentSettings.MANUAL` and call the pass themselves on a clock they hold.
  The return simulator (`feature/returns/domain/ReturnSimulator.kt`, B-21) runs beside the pass: a
  return is collected a day after it is asked and refunded a day later, the refund (keyed
  `refund:<order>`) and the points' settlement before the move to `refunded`.
* **Two clocks are injected.** `StoreClock`, so the seed and the fixtures can run at the canvas's
  «now», 2025-10-07 19:47:23 America/New_York; and the saga's `PetichClock` (`sagaClock` in
  `haulModule`), a wall clock apart from it, because what it stamps is compared across processes; the
  fulfilment simulator reads the saga's clock too.
  `Application.kt` reads the wall clock for both, and only tracy's record timestamps read it
  elsewhere (`ops/Observability.kt`).
* **Product photos** (`feature/catalog/domain/Photos.kt`, `data/S3PhotoStore.kt`, B-30): a
  `PhotoStore` port over the JDK's `HttpClient` with its own SigV4 (no AWS SDK); keys under
  `products/<key>`; served at `GET /images/{key...}` from the server's origin, so the bucket stays
  private. No store configured, no photos, and every tile is the placeholder. On a seeded start the
  sample products of research §6 get a drawn photo each (`seed/SeedPhotos.kt`, idempotent); the
  generated catalog keeps its placeholder tiles.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Database | PostgreSQL | everything the server owns, the saga's state included |
| Module | [haul-shared](haul-shared.md) | the contract |
| Module | [haul-web](haul-web.md) | the browser bundle it serves at `/` |
| External | shildik | the issuer whose tokens the customer tier accepts; its discovery and JWKS are read from `HAUL_OIDC_ISSUER` |
| External | S3-compatible object storage | product photos (`HAUL_S3_*`); optional |
| External | metrik, tracy, katcher | route metrics, traces and shipped logs, crash reports — each optional (section 7) |

## 5. Infrastructure and deploy

* **Image:** `docker/Dockerfile`, built one way by `scripts/image-build.sh` (`installDist` and `docker
  build`), which `scripts/image-check.sh` and `scripts/e2e.sh` both call — a JVM image with a zavarnik AOT cache trained at build time
  against a PostgreSQL of its own; it sets `HAUL_WEB_DIR=/opt/app/web` and runs as uid 1000.
  `scripts/image-check.sh` checks the image, its cache, the page (`200`), the module's
  `application/wasm` type, its compression and cache headers, the page at a storefront address and
  a `404` on an unknown path. The image's throw-away stage installs `brotli` and writes a `.br`
  (quality 11) and a `.gz` (`-9 -n`) beside each file of the bundle (B-34).
* **The web bundle:** the distribution carries the wasm bundle in its `web` directory, without
  source maps (`server/build.gradle.kts`), and `WebBundle.kt` serves `HAUL_WEB_DIR` at `/` with
  `staticFiles`, beside the API. Every API route is more specific, so a screen route always wins.
  It picks the precompressed variant the browser accepts (brotli, then gzip) with the original's
  `Content-Type` and `Vary: Accept-Encoding`; the two content-hashed `.wasm` files are
  `public, max-age=31536000, immutable`, `index.html`, `composeApp.js` and the fonts `no-cache`;
  `.map` is `404`. The storefront's own addresses (`StorefrontPage` in `haul-shared`: `/`, `/c/…`,
  `/p/{id}`, `/search`, `/cart`, `/checkout`, `/account`, `/account/orders`, `/account/orders/{id}`,
  `/account/saved`, `/sign-in`, `/deals`) answer `index.html`
  as `/` does (B-36); there is no catch-all fallback, so an unknown path, `/ui/...` or `/api/...`
  stays a `404` (`WebBundleTest`, `StorefrontPageTest`). The `no-cache` files carry no ETag and
  nothing answers `304` (B-34's findings).
* **Chart:** `charts/haul/` (B-27) — the server Deployment (one replica, rolled with a surge of one
  and none unavailable; start-up
  and liveness on `/healthz`, readiness on `/readyz`; an init container that waits for the
  database; no service links), its Service, PostgreSQL in the release (Deployment, a PVC kept on
  uninstall, Service), one Secret, Traefik IngressRoutes. Rendering refuses a missing image tag,
  password or host, and half an agent; `scripts/chart-check.sh` makes each refusal fire.
  `charts/haul/values-stand.yaml` holds the public stand's choices; the stand is not deployed
  (`.github/workflows/stand.yaml` runs only by hand, B-27 waits for the owner). The chart passes no
  `HAUL_OIDC_*`, no `HAUL_S3_*` and no `HAUL_FULFILMENT_SPEED`, so a release runs with sign-in off,
  placeholder tiles and the store's own pace.
* **CI** (`.github/workflows/check.yaml`): the documentation gate, then the `gradle`, `image`, `chart`
  and `e2e` jobs; `e2e` runs `scripts/e2e.sh`, the whole path against the image built in the job, and
  is a required check (B-26). On a pull request that touches only documentation (`docs/`,
  `backlog.md`, `README.md`, `CLAUDE.md`) the code jobs report success with their steps skipped
  (`scripts/code-changed.sh`); the default branch runs everything on every push.
* **Health:** `GET /healthz`, `GET /readyz`; **Version:** `GET /version` ([endpoint-ops](../api/endpoint-ops.md)).

## 6. Local setup

```bash
# PostgreSQL of your own; the variables of section 7
HAUL_DB_URL=jdbc:postgresql://127.0.0.1:5432/haul HAUL_DB_USER=haul HAUL_DB_PASSWORD=haul \
  HAUL_SEED=true ./gradlew :server:run
```

Without `HAUL_WEB_DIR` the server answers the API and serves no page; without `HAUL_OIDC_*` sign-in
is off; without `HAUL_S3_ENDPOINT` there are no photos. There is no compose file: the tests run
shildik (`ghcr.io/youndie/shildik-sqlite:0.4.1`) and SeaweedFS in Testcontainers
(`server/src/test/kotlin/io/github/youndie/haul/testing/ShildikHarness.kt`,
`server/src/test/kotlin/io/github/youndie/haul/testing/SeaweedHarness.kt`), and the whole stack for the
end-to-end path — PostgreSQL 18, shildik and the server's image on the host's network, at
`HAUL_FULFILMENT_SPEED=86400` — is started by the test itself
(`e2e/src/test/kotlin/io/github/youndie/haul/e2e/ComposedStack.kt`), run by `scripts/e2e.sh [tag]`; it
needs Docker on Linux (B-26). Without `-Phaul.e2e.image`, `:e2e:test` is skipped, so `./gradlew check`
does not need the image. The seed adds Maya (Plus) and Sam as customers with ids `maya` and `sam`,
Maya's cart and address, three pickup points and two lockers (B-15), the sample product's reviews,
histogram and questions (B-22), Maya's membership and 2,480 points (B-23), her 48 saved products
(`seed/SampleSaved.kt`, B-20) and five product views (`seed/SampleViews.kt`, B-25); a realm for a local
shildik imports the customers by those ids. The orders of research §6 are not seeded (feature-orders).

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
| `HAUL_OIDC_ISSUER`, `HAUL_OIDC_CLIENT_ID` | the shildik realm's issuer (`{base}/realms/{realm}`) and the storefront's public client: sign-in on when both are set, off when neither; one without the other, or an issuer that is not a realm's, refuses the start | no |
| `HAUL_S3_ENDPOINT` | object storage for product photos; unset, no photos | no |
| `HAUL_S3_BUCKET`, `HAUL_S3_ACCESS_KEY`, `HAUL_S3_SECRET_KEY` | required once `HAUL_S3_ENDPOINT` is set; missing refuses the start | with the endpoint |
| `HAUL_S3_REGION` | the SigV4 region; a default when unset | no |
| `HAUL_FULFILMENT_SPEED` | how many times quicker than the store's pace the fulfilment and return simulators move shipments and returns: `1` by default (a courier order in about two days), `1440` a day a minute, `86400` a day a second (the e2e); anything but a positive number refuses the start | no |

An agent that is off says so at `warn` on start; tracy's delivery and katcher are flushed on
`ApplicationStopping` (`ops/Observability.kt`).
