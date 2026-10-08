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
  later, the window named on its own day; the card's line by how much has been captured; the courier's address
  the order's own copy (`NewOrder.address`, B-40), so editing the saved address later does not move a past order. Another customer's order
  and a missing one are both `404 order_not_found`. **Reorder** (`feature/order/domain/Reorder.kt`, `POST
  /api/v1/me/orders/{id}/reorder`) sets each of the order's SKUs in the cart through `CartCommands.changeLine`,
  selected, at the order's quantity within ten and the stock — a line already holding as many is only selected, so
  a second press adds nothing — leaves out what is gone or out of stock, and answers `navigate` to the cart.
  «Write a review» on a delivered line is B-22's review dialog (`ReviewTabs.writeReview`, kompot's `present`).
  «Orders» in the header goes to `/account` (a guest's to sign-in) until B-19 gives the history an address.
- **The client** (`composeApp/.../feature/order/`): `OrderBodyView` and its renderer (Reorder is a `CartCommand`, its
  `navigate` followed, a refusal drawn again); the shell's `OrderLoading`, `OrderNotFound` under the header last
  drawn («Go to your orders» follows its `orders`), and «This order didn’t load» with Retry; «Orders» followed in the
  header. `BalancedText` no longer narrows a title below its widest word (a phone title was broken inside
  «tomorrow,»).
- **Parity** (`viddikDesignParity --component "Order*"`, references rendered on Linux with grayscale text, tolerance
  untouched): fifteen of sixteen within 5 % — the 1440 artboards 1.42–2.45 %, the phones 0.42–4.12 % — and
  `Order_Placed_Phone` at 5.11 % (36,152 px against the 35,373 allowed). Of its red, 3,082 px are the Sony line's
  name (data, below) and 10,457 px the title, which the browser breaks «order #HL- / 48302 / is placed» and the
  client «order / #HL-48302 / is placed»: Chrome measures both lines as the client does (290.5 and 284.7 px) and
  still chooses the wider break, so its `text-wrap: balance` is not the narrowest width that keeps the line count,
  which is what `BalancedText` finds; a scoring breaker (least squared slack) chose a third break and was dropped.
  The page lays out on fractional line boxes (`CssColumn` in `OrderViews.kt`: the crumbs' 16.8 px, the lead's 23.8,
  the facts' 21.75 and 20.3, the pickup code's 64.8 summed unrounded and rounded only where a block is placed), which
  took the phones from 5.55–7.22 % to this. Goldens recorded for `Order*` only.

- **Tests written**: `OrderRoutesTest` (the page placement lands on; another customer's order and a missing one
  answer alike, `401` without a token, Sam cannot reorder Maya's; a delivered line's review is the dialog and reorder
  twice is once; a declined order's page),
  `OrderFixturesTest` (the five client bodies are the server's trees), `StorefrontPageTest`, `WebBundleTest`,
  `DrawnActionsTest` (server: a guest's «Orders» is sign-in), an address edited after placement leaving the page as
  it was (mutation: the page reading the saved row — failed, restored), `KoinGraphTest`; in the client `OrderWiringTest`
  (Reorder sends the tree's reorder and follows the cart; a refusal redraws; «Write a review» presents the dialog;
  «Back to cart»; «Return items» sends nothing), `StorefrontTest` (an order not there leads to the orders; one that did not load retries),
  `DrawnActionsTest` («Orders»), `AddressTest`, `CartCommandsTest`, and sixteen `Order_*` goldens.
- **Mutations**, each seen failing and restored: reorder adding to the line instead of setting it (the twice-is-once
  test), the header without `orders` (both `DrawnActionsTest`s, the page test, the fixtures), the page's address back
  to `/orders/{id}` (placement's and the order routes' tests), `StorefrontPage` without the order (its test and
  `WebBundleTest`), the renderer not sending Reorder (both wiring tests), the shell without Order_NotFound
  (`StorefrontTest`), `BalancedText` without its lower bound (two phone goldens). Not run: the owner check in
  `Reorder` removed — the session's safety check refused that edit; the «not yours» test covers it by its assertions
  (`404` for Sam, Sam's cart empty).
- **Where it ran**: the Linux build machine (WSL), on the branch rebased onto `df3a1d2` (B-40):
  `:composeApp:wasmJsBrowserDistribution` alone, then `./gradlew check :server:installDist`, one worker in a 5 GB
  scope, green (`:server:test` 202 tests, `:composeApp:desktopTest` 108 and `viddikVerify` 106, 0 failed; PostgreSQL
  and shildik in containers); `viddikDesignParity` as above. `make check` and `make docs-against BASE=origin/main` on
  the Mac. No migration, so `scripts/image-check.sh` was not run; the chart is unchanged.

## Findings (2026-10-08)

- **The listing name again** (B-13's): the canvas writes «Sony WH-1000XM6 …» on the order; the order line keeps the
  title as bought, which is the seed's «WH-1000XM6 …». The bodies are the server's, so the Sony line differs.
- **Unavailable lines are not reported.** endpoint-orders' draft and feature-orders say reorder «reports the ones that
  are not» available; `navigate` carries no message and no artboard draws one. They are left out of the cart, and the
  answer is the cart as it is. A person decides whether the cart should say so (and where).
- **«Return items»** is drawn and does nothing (B-21).
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
- **`Order_Placed_Phone` stays over the tolerance** (5.11 %), for a person to decide: without the listing name's 3,082
  px it reads 4.68 %. What else is left is the title's break (above), JetBrains Mono's crumbs and meta line drawn 2 px
  lower than Chrome draws them (1,761 px; the catalog's crumbs share the style), and glyph rasterisation.
- **`text-wrap: balance` is not reproduced by `BalancedText`** in general: Chrome keeps a wider break than the
  narrowest that holds the line count (measured on the Placed title in Chrome itself). Every other balanced title on
  the canvas happens to agree; a fix belongs to `BalancedText` and every screen that uses it, not to this item.
