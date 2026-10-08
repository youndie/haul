---
id: screen-cart
title: Cart
type: client_screen
platform: [web]
status: draft
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
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/` (planned, B-13) |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` (the cart's pages planned, B-13) |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt` |
| The components on the wire | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CartComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/cart` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

Since B-11 the server answers Content, Empty, PromoApplied, PromoError, ItemChanged and Guest as one
tree — each is what the stored cart is, not a builder of its own — and its tests hold them
(`CartRoutesTest`, `ChangedLinesTest` in `server/src/test/kotlin/io/github/youndie/haul/feature/cart/`).
The boxes are ticked when the client draws them (B-13). Content's «You'll earn 1,024 points» is
Maya's as a Plus customer; over HTTP the cart is a guest's until B-12, and a guest's is Guest.

- [ ] **Loading:** title, placeholder groups and summary
- [ ] **Content:** 3 items in 2 seller groups, all selected, summary $652.00 / −$140.00 / Free / $512, empty promo field, «You'll earn 1,024 points»
- [ ] **Empty:** «Your cart is empty», link to deals, «Picked for you» row
- [ ] **PromoApplied:** Content with `AUTUMN10` applied: a promo line in the summary, the code with Remove
- [ ] **PromoError:** Content with `SUMMER5` in the field and «This code has expired»
- [ ] **ItemChanged:** the mug line marked «Price changed: now $26» (or «Out of stock»), unselected, «OK»
- [ ] **Guest:** Content without the points line; button «Sign in to check out»
- [ ] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1041 / 1220; Content 1179 / 1668; Empty 1216 / 1722; PromoApplied 1179 / 1705; PromoError 1179 / 1688; ItemChanged 1263 / 1776; Guest 1179 / 1607; Error 900 / 617.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |

## 5. Navigation (summary)

- − / + → quantity
- Remove
- Save for later
- Select all / Delete selected
- Apply
- Checkout → screen-checkout (guest → sign-in)
