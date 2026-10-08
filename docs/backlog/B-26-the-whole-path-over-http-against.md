---
id: B-26
title: "e2e: the whole path over HTTP against the composed stack"
status: done
priority: P1
size: M
stage: stage-9-ship
blocked_by: [B-21, B-22]
---

# B-26 — e2e: the whole path over HTTP against the composed stack

One run that browses, buys, receives and returns is the end-to-end proof the name of the project promises.

- Not covered: load.

- AC: one run browses, buys, receives and returns, green in CI.
- Anchors: `e2e/src/test/kotlin/io/github/youndie/haul/e2e/`, `scripts/e2e.sh`, `scripts/image-build.sh`.

## Done (2026-10-08)

- **The stack** (`e2e/src/test/kotlin/io/github/youndie/haul/e2e/ComposedStack.kt`): started from the test with
  Testcontainers, as the server's suite starts PostgreSQL and shildik — no compose file to keep beside the code
  that depends on it («Decided in B-26»). PostgreSQL 18, shildik 0.4.1 (the release `ShildikHarness` signs in
  against) and the server's own image, named by `-Phaul.e2e.image`. The server gets what a stand gives it
  (`ServerConfig`): the database, `HAUL_SEED=true`, `HAUL_OIDC_ISSUER` / `HAUL_OIDC_CLIENT_ID`, and
  `HAUL_FULFILMENT_SPEED=86400` — a day a second, so delivery and the refund each take a few seconds.
- **The server runs on the host's network.** The issuer is one URL for three parties: shildik writes it into
  every token, the server reads the keys from its discovery, the test signs in against it. On a bridge network
  the server would know shildik by a name the test cannot resolve, and the test by a mapped port the server
  cannot reach; on the host's network `127.0.0.1` is the same place for all three. It needs Docker on Linux,
  which CI's runners and the build box are.
- **The image is built one way** (`scripts/image-build.sh`): `scripts/image-check.sh` and `scripts/e2e.sh` both
  call it, so the image the path runs on is the one the image job checks. Without `-Phaul.e2e.image`, `:e2e:test`
  is skipped, so `check` stays what it was where there is no image.
- **The token** comes the storefront's way: the realm, its public client `haul-web` and a shopper are created
  through shildik's management API in the test's setup, and the shopper signs in by authorization code with PKCE
  through the client and scope the server names (`GET /api/v1/sign-in`), as `ShildikHarness` does. No password
  grant and no client of the test's own.
- **The path** (`WholePathTest`), following only what the trees hand it after `/` — every deeplink is read with
  `StorefrontPage`, every command goes to the component's `url` with the contract's body: a guest; the home's
  first category tile, the category's first card that can be added, its product page (same product, same price,
  in stock); the card's «+» (`refresh`, one line at the card's price, the header counts it); a guest's «Checkout»
  goes to sign-in; the token, the merge (`refresh`), the line now the customer's by the token alone and the header
  greeting her; the customer's «Checkout»; courier, the address form, the last window with room and the card —
  each chosen from the options the tree offers and drawn back as chosen; placement (`202`, `navigate` to the
  order; the same `Idempotency-Key` answers the same order); the order's page until it has arrived (its total the
  checkout's, every shipment delivered, «Return items» presenting the form); the return of the line (`201`,
  `close` and `refresh`, the refund what the line cost); the page until the refund (the «Refunded» row, the total
  less the refund); the header's «Orders» to the history, where the row leads to the order and reads Returned at
  the order's total.
- **Runs**: the whole path takes about 8 s on the build box, 15 s with the containers' start; with the store's
  own pace (mutation: `FULFILMENT_SPEED = 1`) it fails at «the fast clock delivers the order» after its two
  minutes, naming where the order was. In CI the `e2e` job (`check.yaml`) takes about four minutes, most of it
  the distribution and the image's training; a change that touches only documentation skips its steps and
  still reports success, as the other code jobs do.

## Findings (2026-10-08)

- **No server defect stood in the path.** Two answers differ from what a reader of the drafts would expect,
  and the test follows the code: a return answers `201` (B-21's findings say so; the drafts do not yet), and a
  guest's «Checkout» goes to `/sign-in` with its query, so an address is read with `StorefrontPage` after its
  query is cut.
- **The product page's «Add to cart» carries nothing** — screen-product's draft records it as a *target*, from
  B-37's findings — so the path adds through the category card's «+», which does. A shopper on the product page
  cannot buy from it yet; that is the storefront's gap, not this test's.
- **Docker on Linux only**: the server's container on the host's network is what makes one issuer reachable by
  all three parties. Docker Desktop has host networking only behind a setting, so on a Mac the e2e runs on the
  build box or in CI, as the native builds of other projects do.
- PR #2's drafts that this changes: **haul-server** §6 (still no compose file: the whole stack is started by
  `e2e/src/test/kotlin/io/github/youndie/haul/e2e/ComposedStack.kt`, run by `scripts/e2e.sh [tag]`; the image is
  built by `scripts/image-build.sh`, which `scripts/image-check.sh` now calls); **feature-orders** «An order
  reaches delivered on the fast clock» (`**Automated:**` also `WholePathTest.a shopper browses buys receives and
  returns an order over HTTP`, the image by itself at speed 86400), and a scenario for a return refunded when
  the parcel is back, which the drafts lack and this test walks; **feature-identity** «Guest cart survives
  sign-in» (the e2e as well, through the realm a stand runs); **feature-checkout** «Place by courier» (the
  navigate goes to `/account/orders/HL-48302`, not `/orders/HL-48302`).
