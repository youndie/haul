---
id: B-18
title: "server + client: Order tree and renderers, reorder"
status: done
priority: P1
size: M
stage: stage-5-order
blocked_by: [B-01, B-15, B-17]
---

# B-18 — server + client: Order tree and renderers, reorder

The order page every placement lands on, and the source of reorder.

Feature: `feature-orders` — its scenarios are this item's acceptance where it names them.

- Not covered: the return dialog (B-21).

- AC: parity for `Order_*` except ReturnDialog and Returned.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/`.

- **From B-37:** the header's «Orders» shortcut is drawn and carries no action; it gets one with the
  orders it leads to (here or B-19's history) — a `navigate` in `HaulHeader`, followed by the client
  like «Deals» and the cart (`Frame.kt`).

## Done (2026-10-08)

- **The address** is `/account/orders/{id}`, its tree `GET /ui/account/orders/{id}` (`StorefrontPage.Order`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt`): the canvas's crumbs are «Account /
  Orders / #HL-48302», screen-order's entry is that address, and the orders' history (B-19) is the account's
  `/account/orders`, which the page then sits under. Placement's `navigate` lands there (it said `/orders/{id}`,
  which nothing served); a reload of it is the page (B-36). Recorded in research D4, «Decided in B-18».
- **The contract** (`shared/.../ui/OrderComponents.kt`): one `OrderBody` — crumbs, the meta line, the title with its
  accent and lead, `OrderSteps`, a cancelled order's `OrderNotice`, the `PickupCode`, the `OrderShipment`s with their
  `OrderItem`s, and `OrderTotals` with its facts and its ways on — and `ErrorCode.OrderNotFound`
  (`order_not_found`, `404`). `HaulHeader.orders` is where «Orders» goes.
- **The server** (`feature/order/screen/OrderScreen.kt`): one builder over `OrderTracking.track` (B-17) for Placed,
  InTransit, ReadyForPickup, Delivered and Cancelled — the copy the canvas's; each shipment's day the courier's for
  the order's placing time plus its seller's dispatch days (a point a day later), or the chosen window when that is
  later, the window named on its own day; the card's line by how much has been captured. Another customer's order
  and a missing one are both `404 order_not_found`. **Reorder** (`feature/order/domain/Reorder.kt`, `POST
  /api/v1/me/orders/{id}/reorder`) sets each of the order's SKUs in the cart through `CartCommands.changeLine`,
  selected, at the order's quantity within ten and the stock — a line already holding as many is only selected, so
  a second press adds nothing — leaves out what is gone or out of stock, and answers `navigate` to the cart.
  «Orders» in the header goes to `/account` (a guest's to sign-in) until B-19 gives the history an address.
- **The client** (`composeApp/.../feature/order/`): `OrderBodyView` and its renderer (Reorder is a `CartCommand`, its
  `navigate` followed, a refusal drawn again); the shell's `OrderLoading`, `OrderNotFound` under the header last
  drawn («Go to your orders» follows its `orders`), and «This order didn’t load» with Retry; «Orders» followed in the
  header. `BalancedText` no longer narrows a title below its widest word (a phone title was broken inside
  «tomorrow,»), and `HaulType.normal` is the checkout's `line-height: normal` text, now shared.
- **Parity** (`viddikDesignParity --component "Order*"`, references rendered on Linux with grayscale text, tolerance
  untouched): all eight 1440 artboards within 5 % (0.50–3.34 %); the phones read 0.42–3.18 % for Loading, NotFound and
  Error and 5.55–7.22 % for the five order states, over by glyph rasterisation, a 1 px drift from fractional line
  boxes, and the data differences below. Goldens recorded for `Order*` only.

- **Tests written**: `OrderRoutesTest` (the page placement lands on; another customer's order and a missing one
  answer alike, `401` without a token, Sam cannot reorder Maya's; reorder twice is once; a declined order's page),
  `OrderFixturesTest` (the five client bodies are the server's trees), `StorefrontPageTest`, `WebBundleTest`,
  `DrawnActionsTest` (server: a guest's «Orders» is sign-in), `KoinGraphTest`; in the client `OrderWiringTest`
  (Reorder sends the tree's reorder and follows the cart; a refusal redraws; «Back to cart»; «Return items» sends
  nothing), `StorefrontTest` (an order not there leads to the orders; one that did not load retries),
  `DrawnActionsTest` («Orders»), `AddressTest`, `CartCommandsTest`, and sixteen `Order_*` goldens.
- **Mutations**, each seen failing and restored: reorder adding to the line instead of setting it (the twice-is-once
  test), the header without `orders` (both `DrawnActionsTest`s, the page test, the fixtures), the page's address back
  to `/orders/{id}` (placement's and the order routes' tests), `StorefrontPage` without the order (its test and
  `WebBundleTest`), the renderer not sending Reorder (both wiring tests), the shell without Order_NotFound
  (`StorefrontTest`), `BalancedText` without its lower bound (two phone goldens). Not run: the owner check in
  `Reorder` removed — the session's safety check refused that edit; the «not yours» test covers it by its assertions
  (`404` for Sam, Sam's cart empty).
- **Where it ran**: the Linux build machine (WSL), on the branch rebased onto `e2782e5`: `./gradlew check
  :server:installDist` green (`:server:test` 177 tests, `:composeApp` 231 tests including `viddikVerify`, 0 failed;
  PostgreSQL and shildik in containers), `:composeApp:wasmJsBrowserDistribution` alone green, `viddikDesignParity` as
  above. `make check` and `make docs-against BASE=origin/main` on the Mac. No migration, so `scripts/image-check.sh`
  was not run; the chart is unchanged.

## Findings (2026-10-08)

- **The listing name again** (B-13's): the canvas writes «Sony WH-1000XM6 …» on the order; the order line keeps the
  title as bought, which is the seed's «WH-1000XM6 …». The bodies are the server's, so the Sony line differs.
- **Unavailable lines are not reported.** endpoint-orders' draft and feature-orders say reorder «reports the ones that
  are not» available; `navigate` carries no message and no artboard draws one. They are left out of the cart, and the
  answer is the cart as it is. A person decides whether the cart should say so (and where).
- **«Write a review»** opens the product page until B-22 has a review route; **«Return items»** is drawn and does
  nothing (B-21).
- **«Go to your orders»** follows the last header's `orders`: on an order page reloaded at a missing number the
  client has drawn no header yet, so the button (and the header, the shell's own) is Product_NotFound's case, inert.
- **A guest at an order's address** gets the shell's error page (`401`, as `/account` does today), not sign-in, which
  screen-order asks for.
- **The points fact** is drawn only while nothing has moved, as the canvas draws it on Placed alone.
- **The research §6 orders** (#HL-48211, #HL-47960, #HL-46102, #HL-48303) are not seeded; `OrderFixturesTest` writes
  them as orders (the yoga mat, the sweater and the serum with their sellers, which the seed does not sell) and draws
  them through the route's builder. #HL-48302 goes through placement itself.
- **The canvas's `canvas.json`** lists the Order page's artboards under `pages` but not in the top-level `artboards`
  index the reference script reads sizes from; the sizes were copied from `pages` into the (ignored) `.canvas` copy.
- **Test isolation**: a route test that fails inside `haulTest` after a `FulfilmentWorld` can leave the next test's
  cart repository on a closed pool (`HikariPool-n has been closed`); seen only under a mutation.
