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
  - haul:shared ErrorCode
parent_feature: feature-cart
---

# API: Cart

> The six routes exist since B-11 and are described as the code has them. There are no route
> classes in `shared`: the paths are the server's strings (`CartPaths` in `CartRouting.kt`), handed
> to the client inside the tree — `CartLine.url` and `acknowledgeUrl`, `CartSelection.linesUrl`,
> `PromoField.url` — and the contract is the command bodies, the components and `ErrorCode`.

The public tier here means a guest id the server issued, sent as `X-Haul-Guest` (`GUEST_HEADER` in
`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt`; the id comes from
`POST /api/v1/guests`, [endpoint-identity](endpoint-identity.md)). A request with no such header, or
with an id the server never issued, is `401 unauthenticated` on every route below. A customer's
bearer token is not read yet: it joins the guest id with sign-in (B-12).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/cart` | haul-server | public (`X-Haul-Guest`) | yes | request: —; answers tree: Cart — Content, Empty, PromoApplied, PromoError, ItemChanged and Guest are all this one tree |
| `PUT` `/api/v1/cart/lines/{skuId}` | haul-server | public (`X-Haul-Guest`) | yes | request: `LineChange` — adds the SKU, or changes its line's quantity or selection; answers action: `refresh` |
| `DELETE` `/api/v1/cart/lines` | haul-server | public (`X-Haul-Guest`) | yes | request: `LinesRemoval` — «Remove» and «Delete selected»; answers action: `refresh` |
| `POST` `/api/v1/cart/lines/{skuId}/acknowledge` | haul-server | public (`X-Haul-Guest`) | yes | request: —; «OK» on a changed line; answers action: `refresh` |
| `PUT` `/api/v1/cart/promo` | haul-server | public (`X-Haul-Guest`) | yes | request: `PromoEntry`; answers action: `refresh` |
| `DELETE` `/api/v1/cart/promo` | haul-server | public (`X-Haul-Guest`) | yes | request: —; answers action: `refresh` |

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

## Request and response bodies

The command bodies are in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt`
(`LineChange`, `LinesRemoval`, `PromoEntry`), the components in
`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt`; not copied here. What the
server does with them:

* **`LineChange`** — a SKU not yet in the cart is added with its `quantity` (1 when absent), selected
  unless `selected` says otherwise; a line already there changes only the fields given. At least one
  of the two is required.
* **`LinesRemoval`** — the SKUs whose lines go; at least one. A SKU with no line is ignored.
* **`PromoEntry`** — the code as typed; the server trims it and ignores its case. The code already
  applied, sent again, changes nothing and is not a refusal.
* **The Cart tree** (`screen/CartScreen.kt`) — inside the frame (`HaulHeader` with the cart's unit
  count): `PageTitle`, `CartSelection`, one `CartGroup` per seller in the order the lines were added,
  each with its latest delivery day, `PromoField`, `OrderSummary`. An empty cart is `EmptyState`
  followed by `SectionHeader` «Picked for you» and a `ProductGrid` of the day's deals. A refused
  code is drawn in the `PromoField` (the code as typed and the reason) until the next command.

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
(changed lines and acknowledging).
