---
id: endpoint-cart
title: Cart
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared LineChange
  - haul:shared LinesRemoval
  - haul:shared PromoEntry
  - haul:shared CartLine
  - haul:shared CartBody
  - haul:shared LineCommand
  - haul:shared ErrorCode
parent_feature: feature-cart
---

# API: Cart

> The six routes exist since B-11 and are described as the code has them; B-12 let a customer in,
> B-13 gave the tree its canvas shape (`CartBody`) and the client its commands, B-37 put the same
> line command on every product card, B-48 on the product page's «Add to cart» and «Buy now».
> «Save for later» on a line, `POST /api/v1/cart/lines/{skuId}/save-for-later`, is the Saved list's
> route ([endpoint-saved](endpoint-saved.md)); an order's «Reorder» goes through the same rules
> ([endpoint-orders](endpoint-orders.md)). There are no route classes in `shared`: the paths are the
> server's strings (`CartPaths` in `CartRouting.kt`), handed to the client inside the tree —
> `CartLine.url` and `acknowledgeUrl`, `CartSelection.linesUrl`, `PromoField.url`, `ProductCard.add`,
> `ProductDetails.add` and `.buy` — and the contract is the command bodies, the components and `ErrorCode`.

The public tier here means a cart owner: a customer by a verified shildik bearer, or a guest id the
server issued, sent as `X-Haul-Guest` (`GUEST_HEADER` in
`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt`; the id comes from
`POST /api/v1/guests`, [endpoint-identity](endpoint-identity.md)). A token wins over a guest id. A
request with neither, or with an id the server never issued, is `401 unauthenticated` on every route
below; so is a token that does not verify.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/cart` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: —; answers tree: Cart — Content, Empty, PromoApplied, PromoError, ItemChanged and Guest are all this one tree |
| `PUT` `/api/v1/cart/lines/{skuId}` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: `LineChange` — adds the SKU, or changes its line's quantity or selection; a card's «+» sends it too (`ProductCard.add`), and the product page's «Add to cart» and «Buy now» (`ProductDetails.add`, `.buy`); answers action: `refresh` — a `LineCommand` with a `next` («Buy now») is followed by the client to that address once the change was accepted |
| `DELETE` `/api/v1/cart/lines` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: `LinesRemoval` — «Remove» and «Delete selected»; answers action: `refresh` |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: —; «OK» on a changed line; answers action: `refresh` |
| `PUT` `/api/v1/cart/promo` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: `PromoEntry`; answers action: `refresh` |
| `DELETE` `/api/v1/cart/promo` | haul-server | public (customer bearer or `X-Haul-Guest`) | yes | request: —; answers action: `refresh` |

The guest cart's merge into the customer's on sign-in, `POST /api/v1/me/cart/merge`, is in
[endpoint-identity](endpoint-identity.md).

Every command answers kompot's `RefreshAction` (`respondKompotAction`), which redraws the cart and
with it the header's count. Conventions for every group — trees versus actions, the error body,
`404` for «not yours» — are in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/cart` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt` |
| `PUT` `/api/v1/cart/lines/{skuId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `CartCommands.changeLine` in `server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/CartCommands.kt` |
| `DELETE` `/api/v1/cart/lines` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `CartCommands.removeLines` |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `CartCommands.acknowledge` |
| `PUT` `/api/v1/cart/promo` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `CartCommands.applyPromo` |
| `DELETE` `/api/v1/cart/promo` | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt` → `CartCommands.removePromo` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |
| client | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` — each press a `CartCommand` (`ChangeLine`, `RemoveLines`, `Acknowledge`, `ApplyPromo`, `RemovePromo`) sent to the URL the tree carries through `Identity.send` |

## Request and response bodies

The command bodies are in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt`
(`LineChange`, `LinesRemoval`, `PromoEntry`, and `LineCommand` — a URL and a `LineChange` — which a
card's «+» carries), the components in
`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt`; not copied here. What the
server does with them:

* **`LineChange`** — a SKU not yet in the cart is added with its `quantity` (1 when absent), selected
  unless `selected` says otherwise; a line already there changes only the fields given. At least one
  of the two is required.
* **`LinesRemoval`** — the SKUs whose lines go; at least one. A SKU with no line is ignored.
* **`PromoEntry`** — the code as typed; the server trims it and ignores its case. The code already
  applied, sent again, changes nothing and is not a refusal.
* **The Cart tree** (`screen/CartScreen.kt`, B-13) — inside the frame (`HaulHeader` with the cart's
  unit count): `PageTitle` «Cart» with the count in a pill (`badge`), then one `CartBody` (the
  canvas lays the lines and the summary side by side, which a column of sections cannot): the
  `CartSelection`, one `CartGroup` per seller in the order the lines were added, each with «Courier ·
  <latest delivery day>», and the `OrderSummary` with the `PromoField` inside it. A line carries
  `changeDetail` («It was $24 when you added it») when changed, `each` above one unit, and «Save for
  later» — `saveUrl` for a customer, `saveAction` (a `navigate` to `/sign-in`) for a guest. The summary's
  rows are «Items (N)» of the counted units, «Discount», «Promo · AUTUMN10» when the code took
  something off, «Delivery»; `SummaryRow.saving` marks the savings; the total is a price tag («$512»);
  «You'll earn **N points** on this order» (`pointsAccent`) for a customer only. «Checkout» goes to
  `/checkout`, a guest's «Sign in to check out» to `/sign-in?next=%2Fcheckout`. An empty cart is
  `EmptyState` «Your cart is empty» with a filled «See today’s deals» (`/deals`), then `SectionHeader`
  «Picked for you» / «From today’s deals» and a `ProductGrid` of the day's deals, each card with its
  «+». A refused code is drawn in the `PromoField` (the code as typed and the reason) until the next
  command.

## Quirks

* A code applied while it was valid and expired since stops counting in the totals, but the cart's
  `PromoField` still shows it applied and nothing on the cart tells the shopper (`Totals.of` drops a
  code that is not `activeAt` now; the tree draws `cart.promoCode` as it is). Checkout's quote does
  not count it either and draws a notice saying so (B-14, `CheckoutQuoteTest`).
* The discount folds the promo in: Cart_PromoApplied draws «Discount −$140.00» beside «Promo ·
  AUTUMN10 −$50.00», while the server sends «Discount −$190.00» (B-11's tested rule, kept by B-13;
  feature-cart). Unresolved between the canvas and the rule.
* No command takes an `Idempotency-Key`: each is idempotent as written — set a quantity, delete
  lines, acknowledge, apply the code already applied.

## Errors

Every refusal is an `ErrorBody`; a body that does not parse as the command's JSON is
`400 validation_failed` with field `body`.

| Route | Status and `code` |
|---|---|
| `GET` `/ui/cart` | `401` unauthenticated |
| `PUT` `/api/v1/cart/lines/{skuId}` | `400` validation_failed (field `quantity`: outside 1…10, or neither field sent; field `body`), `401` unauthenticated, `404` sku_not_found, `409` out_of_stock (field `quantity`: adding a SKU whose stock is 0 or setting its quantity, or a quantity above the stock) |
| `DELETE` `/api/v1/cart/lines` | `400` validation_failed (field `skuIds`: an empty list; field `body`), `401` unauthenticated |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | `401` unauthenticated, `404` line_not_found (no such line in this cart), `404` sku_not_found (the line's SKU is no longer in the catalog) |
| `PUT` `/api/v1/cart/promo` | `400` validation_failed (field `code`: empty; field `body`), `401` unauthenticated, `404` promo_not_found, `409` promo_already_applied (another code is applied), `422` promo_expired, `422` promo_not_applicable (nothing selected counts, or the code's window has not opened) — the four promo refusals with field `code` |
| `DELETE` `/api/v1/cart/promo` | `401` unauthenticated |

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartRoutesTest.kt` (the routes,
against PostgreSQL), `server/src/test/kotlin/io/github/youndie/haul/feature/cart/ChangedLinesTest.kt`
(changed lines and acknowledging), `server/src/test/kotlin/io/github/youndie/haul/feature/cart/CustomerCartTest.kt`
(a customer's cart), `server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartFixturesTest.kt`
(the client's six cart bodies are exactly the trees the server builds),
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/CartCommandsTest.kt` (each
command's method, URL and body).
