---
id: B-12
title: "server + client: shildik sign-in in the browser, customer creation, cart merge, header states"
status: done
priority: P1
size: L
stage: stage-4-cart
blocked_by: [B-04, B-11]
---

# B-12 — server + client: shildik sign-in in the browser, customer creation, cart merge, header states

Checkout, saving, reviews and the account need a customer; the browser cannot use shildik's own client (research risk 2), so the flow is proven first.

Feature: `feature-identity` — its scenarios are this item's acceptance where it names them.

- Not covered: account settings.

- AC: feature-identity scenarios pass against a local shildik; a guest signs in and keeps the cart.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/identity/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/`.

## Done (2026-10-08)

- **The flow** (research D5, «Decided in B-12»; risk 2 settled): the browser signs in with
  kotlin-multiplatform-oidc 0.18.4 — authorization code with PKCE, shildik's page in a popup that
  returns to `signed-in.html` beside the bundle — the client shildik's app contour was accepted
  against; the server verifies with shildik's `oidc-auth-server` 0.4.1.23 (JWKS, lifetime, `iss`) plus
  `azp` = the storefront's client. Configuration: `HAUL_OIDC_ISSUER` and `HAUL_OIDC_CLIENT_ID`, both
  or neither; neither is sign-in off.
- **Server** (`server/.../feature/identity/`): `Callers` tells a request's caller — a verified token
  (the customer, created from its `name` claim on first sight, `customers` in `V8__customers.sql`),
  else a guest id the server issued, else nobody; `shell/Viewers.kt` turns that into every screen's
  header (first name, cart count, and `HaulHeader.account`: `/sign-in` for a guest, `/account` for a
  customer). Tiers at the mount in `HaulModule.kt`: the public routes take an optional bearer, the
  customer tier a required one; a `401` from Ktor's challenge is rewritten into `ErrorBody`
  `unauthenticated`. The cart routes accept a customer as well as a guest (a token wins).
- **Routes**: `GET /api/v1/sign-in` (public; `SignInSettings`, `503 unavailable` with sign-in off);
  `POST /api/v1/me/cart/merge` (customer; header `X-Haul-Guest`; `refresh`; `401 unauthenticated`,
  `404 guest_not_found` for a guest never issued or none named); `GET /ui/account` (customer; the
  frame and «Hi, <name>» until B-19 builds the Account tree).
- **Wire** (`shared/`): `SignInSettings` (`feature/identity/`), `HaulHeader.account`,
  `ErrorCode.GuestNotFound`.
- **The merge** (`CartCommands.merge`, one transaction in `ExposedCartRepository.merge`): the same
  SKU summed, capped at ten and at the stock; the guest's other lines appended; the customer's code
  kept, else the guest's; the guest's cart deleted, the guest kept.
- **Seed**: Maya (`maya`, Plus) and Sam (`sam`), and Maya's cart — the headphones, the duvet cover
  set, the mug set — part of the seed digest.
- **Client** (`composeApp/.../feature/identity/`): `Identity` (guest created once and kept; bearer or
  guest id on every request, never both; sign-in then merge; a `401` answered once — token renewed,
  or signed out; a forgotten guest replaced), `IdentityApi`, `SignInActions` (the seam a shell calls
  before its own navigation: `/sign-in` signs in and redraws), `BrowserSessionStore` (guest id in
  `localStorage`, tokens in `sessionStorage`), `OidcSignInFlow`; the header's account slot is
  tappable and hands `HaulHeader.account` to the action handler. `shell/Storefront.kt` is a stand-in
  host (the home page only) so sign-in can be reached in the browser; B-35's shell replaces it.
- Scenarios of feature-identity, automated in `server/src/test/.../feature/identity/IdentityRoutesTest.kt`
  against shildik's published image (`ghcr.io/youndie/shildik-sqlite:0.4.1`, Testcontainers,
  `testing/ShildikHarness.kt` — each test signs a person in through the code flow with PKCE over HTTP):
  «Guest cart survives sign-in» (`a guest cart survives sign-in`), «Account needs a sign-in» (`the
  account needs a sign-in`). The rules: customer creation (`the first request of a new person creates
  the customer from the token's name`), a token and a guest id together (`a token and a guest id
  together are the customer`); the header states on every screen (`every screen's header greets
  whoever is looking and counts their cart`); the merge's caps in `CartMergeTest`; Maya's seeded cart
  in `CustomerCartTest`; the client's session in `IdentityTest` and `SignInTapTest`. Mutations seen
  failing: no `azp` check, no merge caps, no recovery from a `401`.
- Where it ran: the server suite (102 tests after the rebase over B-30; PostgreSQL and shildik in
  Testcontainers), `check :server:installDist :composeApp:wasmJsBrowserDistribution` with
  `viddikVerify` (52 screenshots, 0 failing, no golden re-recorded) and `scripts/image-check.sh` (551 of
  551 classes from the cache) on the Linux build machine; `make check` on the Mac.

## Findings (2026-10-08)

- **The client's host is a stand-in.** The app had no screen loader; to reach sign-in in the browser
  `shell/Storefront.kt` loads the home page only. B-35 (the app shell) replaces it and keeps the
  identity seam: `Identity.send` around every request and `SignInActions.handle` before its own
  navigation. `App` now takes `Trees` and `Identity`, which the shell will reshape.
- **`/ui/account` is a placeholder** — the frame and «Hi, <name>» — so that «Account needs a sign-in»
  has a route and the header's shortcut a destination; the Account tree is B-19's.
- **Not built here:** `DELETE /api/v1/me/recent-searches` (endpoint-search drafts it as B-12's; the
  search panel's `clearAction` stays empty, `SearchRouting.kt` says so); `POST /api/v1/me/addresses`
  (endpoint-identity; the address form is B-14/B-15's); `429 rate_limited` on guest creation; a
  visible sign-out (no artboard draws one; `Identity.signOut` exists for a refresh that fails).
- **Fixed on the way, a wire defect: no guest's screen decoded in the client.** The server leaves a
  `null` out of the wire (`explicitNulls = false`) and `HaulHeader.customerName` had no default, so
  the client refused every header without a name with `MissingFieldException` — the home page drew
  «Home didn't load» for every guest. The server's own tests decode with the server's Json, which
  forgives the omission, and the guest fixture spelled the `null` out. `customerName` now defaults to
  `null`, and the guest fixture is the wire's shape (`SignInTapTest` decodes it). No other nullable
  wire field lacked a default.
- **The browser half was walked by hand, not by a test** (research, risk 2): a headless Chrome for
  Testing over the DevTools protocol, the served bundle, shildik 0.4.1 and a fresh database; a guest
  with two mugs signed in as Maya and the header read «Maya» and 5 (Maya's three seeded items and the
  merged mugs). The desktop app's own browser pane opens a popup in the same tab, so the popup flow
  cannot finish there; any browser that opens popups as windows is fine.
- **A guest id the server forgot is replaced only by a cart command**: screens accept an unknown guest
  as nobody (a `200` with an empty count), and a cart command's `401` makes the client create a new
  guest and retry. So after a database reset the header counts 0 until the shopper's first cart action.
- **At 800 px the wide header's search field is squeezed**: «All categories» wraps a letter per line
  (seen in the browser check). The canvas draws 1440 and 390 only; widths between are B-35's or a
  design question, not this item's.
- **No artboard draws the header on its own or a sign-in moment.** The canvas's header states are the
  ones inside Home_Guest / Home_Content (and their phone forms), which the B-04 header goldens and the
  Home goldens already hold; the header's pixels did not change (the account slot's tap draws no
  indication), so no golden was re-recorded.
- **The chart sets no realm**, so the stand runs with sign-in off until its shildik exists: the two
  variables, a public client registering `<origin>/signed-in.html`, and the realm importing Maya and
  Sam as `maya` and `sam`.
- **shildik's access token lives five minutes**; the client renews it with the refresh token on the
  first `401`, so a quiet tab costs one extra request, not a sign-in.
- **`Unavailable` (503) now also means «sign-in is not configured»** on `GET /api/v1/sign-in`; B-32
  gave the code «a dependency is down». Close enough for a deployment condition; a separate code
  would be cleaner if the client ever has to tell them apart.
