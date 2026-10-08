---
id: B-11
title: "server: guests, cart, promo codes, changed lines, the Cart tree"
status: done
priority: P1
size: M
stage: stage-4-cart
blocked_by: [B-05]
---

# B-11 — server: guests, cart, promo codes, changed lines, the Cart tree

The cart is where guests and customers meet, and where totals, promo codes and changed lines are computed once for checkout to reuse.

Feature: `feature-cart` — its scenarios are this item's acceptance where it names them.

- Not covered: save for later (B-20).

- AC: feature-cart scenarios pass.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/cart/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/`.

## Done (2026-10-08)

- Guests (`server/.../feature/identity/`, `shared/.../feature/identity/Guests.kt`): `POST /api/v1/guests`
  answers `201` with `GuestDto(id)`; the id travels in `X-Haul-Guest` (`GUEST_HEADER`). Every cart route resolves the
  owner from it and answers `401 unauthenticated` with none, or with an id the server never issued.
- Cart routes, public tier (`server/.../feature/cart/CartRouting.kt`, the paths in `CartPaths` there —
  the server's strings, handed to the client in the tree — and the bodies in
  `shared/.../feature/cart/CartCommands.kt`): `GET /ui/cart`; `PUT /api/v1/cart/lines/{skuId}`
  (`LineChange`: adds the SKU or changes quantity and selection), `DELETE /api/v1/cart/lines`
  (`LinesRemoval`), `POST /api/v1/cart/lines/{skuId}/acknowledge`, `PUT` and `DELETE /api/v1/cart/promo`
  (`PromoEntry`). Commands answer kompot's `refresh`; refusals are `ErrorBody` — `400 validation_failed`
  (quantity outside 1…10, an empty change or list, a body that does not parse), `404 sku_not_found`,
  `409 out_of_stock` (stock 0, or a quantity above the stock), `404 line_not_found`, `404 promo_not_found`,
  `409 promo_already_applied`, `422 promo_expired`, `422 promo_not_applicable` (nothing selected, or the
  code's window not open yet).
- Storage (`V4__cart.sql`): `guests`, `promo_codes` (seeded: `AUTUMN10`, `SUMMER5`, research §6),
  `carts` (exactly one owner, guest or customer; the applied code and the last refused one with its
  reason) and `cart_lines` (keyed by SKU; quantity, selection, the price and stock last seen, the
  order of adding). Rules in `domain/CartCommands.kt` and `domain/Totals.kt`; decisions in research D5.
- The Cart tree (`screen/CartScreen.kt`): `PageTitle`, `CartSelection`, one `CartGroup` per seller in the
  order the lines were added with its latest delivery day, `PromoField`, `OrderSummary`; the empty cart
  is `EmptyState` with a «Picked for you» grid of the day's deals. The states Content, Empty,
  PromoApplied, PromoError, ItemChanged and Guest are all this one tree. New wire components in
  `shared/.../ui/CartComponents.kt`: `haul_cart_line`, `haul_cart_group`, `haul_cart_selection`,
  `haul_promo_field`, `haul_order_summary` (no renderers: B-13).
- Where it ran: the server suite (47 tests, PostgreSQL in Testcontainers), `check
  :server:installDist :composeApp:wasmJsBrowserDistribution` and the image check on the Linux build
  machine; `make check` on the Mac.
- Scenarios of feature-cart, automated in `server/src/test/.../feature/cart/CartRoutesTest.kt` against
  PostgreSQL: «Totals as drawn» (`totals as drawn`, over a guest cart holding Maya's three lines),
  «Promo applies once» (`a promo applies once`), «Expired promo» (`an expired promo is 422 promo_expired
  and the field says so`). Also feature-product's «Out of stock» (`quantities stay within one to ten and
  the stock`). Changed lines in `ChangedLinesTest`, a customer's points in `CustomerCartTest` (below the
  routes: nothing names a customer over HTTP before B-12). Every new test was seen failing under a
  mutation of the rule it holds (three runs, twelve mutations).
- The image trains on guest creation and an empty cart (`server/build.gradle.kts`): zavarnik's workload
  has `GET` and `POST` only, so no line is put into the cart there. `scripts/image-check.sh` passed with
  V4 and the cart in it (486 of 486 classes from the cache).

## Findings (2026-10-08)

- **Fixed on the way, test only:** `KoinGraphTest` left its `koinApplication` open. Koin keeps a resolved
  `single` in the shared module object (`catalogModule`, …) until a Koin that loaded it closes, so the
  first test application after it served the graph test's repositories — over the suite's seeded
  database, not the one the test gave it. Nothing caught it while every route test used that one
  database; `ChangedLinesTest`, the first over a database of its own, failed whenever it ran after the
  graph test. The test now closes the graph; the comment that blamed Exposed's global `Database` was
  this.
- **The client's way to send a command is open (B-13).** The tree carries each command's URL, and the
  method and body are documented on the component (research D5); kompot's `perform` would need `POST`
  routes and a payload of form values fixed in the tree, while the promo code is typed by the shopper.
- **Rebased over B-10 mid-item:** `CLAUDE.md` now says paths are the server's strings, so the
  `CartRoutes` / `GuestRoutes` objects this branch first put in `:shared` moved to the server
  (`CartPaths`, `GUESTS`) and the components gained their URLs. PR #2's endpoint drafts still name
  `CartRoutes` and `GuestRoutes, AddressRoutes` as `contract_source`; there are none.
- **No `Idempotency-Key`.** kompot's protocol (SPEC §16.4–16.5) wants one on every state-changing
  submit. The cart's commands are idempotent as written — set a quantity, delete lines, acknowledge,
  apply the code already applied — so none is required; placement (B-16) is where it matters.
- **Not built here:** `429 rate_limited` on `POST /api/v1/guests` (endpoint-identity lists it; nothing
  limits guest creation yet); the header's cart count on screens other than the cart (every other route
  still builds `Viewer()`; header states are B-12); Maya's cart in the seed (its owner is a customer,
  B-12); «Save for later» (B-20; `CartLine.saveLabel` is drawn, no command exists); checkout taking the
  selected lines (B-14).
- **A code applied while valid and expired since** stops counting in the totals but the field still
  shows it applied; nothing tells the shopper. Small; worth a line in B-14's quote, which must refuse it.
- `carts.promo_error` stores the Kotlin name of the `ErrorCode` (`PromoExpired`), not its wire name; a
  rename of the enum entry would silently drop a remembered refusal.
- PR #2's drafts that this changes: endpoint-cart (`404 line_not_found` on acknowledge; `401
  unauthenticated` on every route; `409 out_of_stock` also for a quantity above the stock; the contract
  bodies are `LineChange`, `LinesRemoval`, `PromoEntry`; no route classes), endpoint-identity (`POST /api/v1/guests`
  is `201`; no `429` yet), feature-cart (the three scenarios automated; delivery and promo rules as in
  research D5), feature-product («Out of stock» automated), haul-shared (the five cart components).
