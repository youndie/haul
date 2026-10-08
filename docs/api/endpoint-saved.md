---
id: endpoint-saved
title: Saved list
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared SaveCommand
  - haul:shared SavedList
  - haul:shared ErrorCode
parent_feature: feature-account
---

# API: Saved list

> The Saved list, the heart and «Save for later» exist since B-20 and are described as the code has
> them. There are no route classes in `shared`: the paths are the server's strings (`SavedPaths` in
> `server/src/main/kotlin/io/github/youndie/haul/feature/saved/SavedRouting.kt`), handed to the client
> inside the trees — `SaveCommand.url` on every card's and the product page's heart, `CartLine.saveUrl`
> on a cart line — and the contract is `SaveCommand`
> (`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SaveCommand.kt`), the list's
> components (`shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SavedComponents.kt`) and `ErrorCode`.

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `GET` `/ui/account/saved` | haul-server | customer (shildik bearer) | yes | request: query `filter` (`price-dropped`; none or an unknown one is all) and `page` (from 1); answers tree: Account with Saved selected — the list, 24 to a page, newest first |
| `PUT` `/api/v1/me/saved/{productId}` | haul-server | customer (shildik bearer) | yes | request: —; keeps the product at its cheapest in-stock price, once; answers kompot's `refresh` |
| `DELETE` `/api/v1/me/saved/{productId}` | haul-server | customer (shildik bearer) | yes | request: —; lets the product go (twice changes nothing); answers kompot's `refresh` |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | haul-server | customer (shildik bearer) | yes | request: —; saves the line's product and takes the line out of the cart; answers kompot's `refresh` |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET` `/ui/account/saved` | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/SavedRouting.kt` → `AccountScreen` (`AccountPage.Saved`) → `server/src/main/kotlin/io/github/youndie/haul/feature/saved/screen/SavedScreen.kt` |
| `PUT` `/api/v1/me/saved/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/SavedRouting.kt` → `SavedCommands.save` (`server/src/main/kotlin/io/github/youndie/haul/feature/saved/domain/Saved.kt`) |
| `DELETE` `/api/v1/me/saved/{productId}` | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/SavedRouting.kt` → `SavedCommands.remove` |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/SavedRouting.kt` → `SavedCommands.saveForLater` |
| storage | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/data/ExposedSavedItems.kt`, `server/src/main/resources/db/migration/V18__saved.sql` (`saved_items`, keyed by customer and product) |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SaveCommand.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SavedComponents.kt` (`SavedList`, `SavedStep`) |

## Request and response bodies

Not copied here; what the server does with them:

* **The heart** — `SaveCommand(url, save)` fixed in the tree like the card's «+»: a saved product's
  heart carries `DELETE`, any other a customer's `PUT`, a guest's heart no command but
  `ProductCard.heartAction`, a `navigate` to `/sign-in`. The same on the product page
  (`ProductDetails.heartCommand`, `.heartAction`) and for a cart line's «Save for later»
  (`CartLine.saveUrl`, `.saveAction`).
* **A save** is written at the cheapest in-stock SKU's price (the cheapest SKU when none is in stock)
  and the store's date; an existing row is left as it was. A product has dropped when its cheapest
  in-stock SKU is below that price; the card carries the difference (`ProductCard.drop`).
* **The list** — `AccountBody.saved`, a `SavedList`: the filter's chips (the history's `HistoryFilter`),
  the cards, the page numbers, or the empty state with its three `SavedStep`s, or the sentence for a
  filter that matches nothing; `AccountBody.count` is the pill beside the title.

## Errors

| Route | Status and `code` |
|---|---|
| `GET` `/ui/account/saved` | `400` validation_failed — a `page` that is not a number ≥ 1; `401` unauthenticated (`SavedRoutesTest.the list and its commands need a sign-in`) |
| `PUT` `/api/v1/me/saved/{productId}` | `401` unauthenticated; `404` product_not_found (`SavedRoutesTest.an unknown product cannot be saved`) |
| `DELETE` `/api/v1/me/saved/{productId}` | `401` unauthenticated |
| `POST` `/api/v1/cart/lines/{skuId}/save-for-later` | `401` unauthenticated; `404` line_not_found — a SKU the cart has no line of, a press after a finished move included |
