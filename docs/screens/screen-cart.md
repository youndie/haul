---
id: screen-cart
title: Cart
type: client_screen
platform: [web]
status: active
entry:
  web: "/cart"
parent_feature: feature-cart
calls_api:
  - endpoint-cart
  - endpoint-saved
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Cart)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Cart_Loading
    Content: Cart_Content
    Empty: Cart_Empty
    PromoApplied: Cart_PromoApplied
    PromoError: Cart_PromoError
    ItemChanged: Cart_ItemChanged
    Guest: Cart_Guest
    Error: Cart_Error
---

# Screen: Cart

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartViews.kt` (`CartBodyRenderer` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRenderers.kt`); the commands in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`CartLoading`, `CartError`); `/cart` is `PageKind.Cart` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt` |
| The components on the wire | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` (rendered with grayscale text, B-13's findings) |
| Parity fixtures, one per artboard | `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/CartFixtures.kt`; the six server states are `composeApp/src/desktopTest/resources/bodies/cart_*.json`, held equal to the server's trees by `CartFixturesTest` |

## 0. Entry point and visibility

- **Entry point:** `/cart` in the browser.
- **Shown when:** always; guests included. Reached from the header's cart button (`HaulHeader.cart`, B-37) or the address; a reload answers the page (`StorefrontPage.Cart`).

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

The server answers Content, Empty, PromoApplied, PromoError, ItemChanged and Guest as one tree —
each is what the stored cart is, not a builder of its own — and its tests hold them (`CartRoutesTest`,
`ChangedLinesTest`, `CustomerCartTest` in `server/src/test/kotlin/io/github/youndie/haul/feature/cart/`).
B-13 drew all eight: `viddikDesignParity` 16/16 (desktop and phone) within the default tolerance.
Content's «You'll earn 1,024 points» is Maya's as a Plus customer; a guest's cart is Guest.

- [x] **Loading:** title, placeholder groups and summary
- [x] **Content:** 3 items in 2 seller groups, all selected, summary $652.00 / −$140.00 / Free / $512, empty promo field, «You'll earn 1,024 points»
- [x] **Empty:** «Your cart is empty», link to deals, «Picked for you» row
- [x] **PromoApplied:** Content with `AUTUMN10` applied: a promo line in the summary, the code with Remove. The server's Discount reads −$190.00 (the promo inside it), the canvas −$140.00 — see feature-cart, Quirks
- [x] **PromoError:** Content with `SUMMER5` in the field and «This code has expired»
- [x] **ItemChanged:** the mug line marked «Price changed: now $26» (or «Out of stock»), unselected, «OK»
- [x] **Guest:** Content without the points line; button «Sign in to check out»
- [x] **Error:** header, message, Retry («Nothing in it was lost», `CartError`)

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1041 / 1220; Content 1179 / 1668; Empty 1216 / 1722; PromoApplied 1179 / 1705; PromoError 1179 / 1688; ItemChanged 1263 / 1776; Guest 1179 / 1607; Error 900 / 617.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |

## 5. Navigation (summary)

Each press is a `CartCommand` sent to the URL the tree carries; the answer, `refresh`, makes the
shell fetch `/ui/cart` again in place (`LocalScreenRefresh`) — a refusal too.

- − / + → `PUT /api/v1/cart/lines/{skuId}` with the new quantity (− at one and + at the limit send nothing)
- a line's box → `PUT` with `selected`; «Select all» → one `ChangeLine` per line, one redraw
- Remove / Delete selected → `DELETE /api/v1/cart/lines`
- «OK» on a changed line → `POST /api/v1/cart/lines/{skuId}/acknowledge`
- Apply / Remove on the code → `PUT` / `DELETE /api/v1/cart/promo` (Apply with nothing typed sends nothing)
- a line's title → screen-product
- Save for later → `POST /api/v1/cart/lines/{skuId}/save-for-later` (`CartLine.saveUrl`), then the cart drawn again without the line; a guest's → `/sign-in` (B-20)
- a pick's heart on the empty cart → `PUT` / `DELETE /api/v1/me/saved/{productId}` ([endpoint-saved](../api/endpoint-saved.md))
- Checkout → screen-checkout (`/checkout`); a guest's «Sign in to check out» → sign-in, then `/checkout` (the `?next=%2Fcheckout` it carries, B-41); a sign-in that does not go through draws the cart again
- the empty cart's «See today’s deals» → [screen-deals](screen-deals.md)

The client's wiring is `CartWiringTest` and `CartCommandsTest` in
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/`; «Save for later» is
`SavedWiringTest` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/saved/SavedWiringTest.kt`).
