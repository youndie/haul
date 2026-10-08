---
id: screen-checkout
title: Checkout
type: client_screen
platform: [web]
status: draft
entry:
  web: "/checkout"
parent_feature: feature-checkout
calls_api:
  - endpoint-checkout
  - endpoint-identity
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/checkout
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Checkout)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Checkout_Loading
    Content: Checkout_Content
    PointsApplied: Checkout_PointsApplied
    PickupPoint: Checkout_PickupPoint
    ParcelLocker: Checkout_ParcelLocker
    Validation: Checkout_Validation
    Placing: Checkout_Placing
    PlaceError: Checkout_PlaceError
    Error: Checkout_Error
---

# Screen: Checkout

The checkout page has its own minimal header (logo, steps «Delivery · Payment · Review»,
«Secure checkout») instead of `Header`.

## 0a. Code anchors

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/checkout/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/server/checkout/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/checkout` in the browser.
- **Shown when:** signed in; a guest is sent to sign-in.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** the minimal header, placeholder sections and summary
- [ ] **Content:** courier, 148 Wythe Avenue 4F, Wed 8 / 15:00–18:00, card ···· 4821, «Use 2,480 points (−$24.80)» off, summary $512, «Place order · $512.00»
- [ ] **PointsApplied:** Content with the points toggle on: a «Points −$24.80» line, total $487.20, «Place order · $487.20», Haul Pay «4 payments of $121.80»
- [ ] **PickupPoint:** «Pickup point» chosen: list of 3 nearby points with distance and hours, 214 Bedford Ave selected; no slot section
- [ ] **ParcelLocker:** «Parcel locker» chosen: list of lockers; «Pay on delivery» absent
- [ ] **Validation:** courier with empty street and ZIP: field errors, button disabled
- [ ] **Placing:** Content with the button in progress, inputs disabled
- [ ] **PlaceError:** Content with a banner «That delivery window just filled up — pick another», the slot cleared
- [ ] **Error:** the minimal header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1467 / 2204; Content 1564 / 2362; PointsApplied 1564 / 2390; PickupPoint 1267 / 2005; ParcelLocker 1193 / 1802; Validation 1608 / 2438; Placing 1564 / 2362; PlaceError 1678 / 2509; Error 804 / 529.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-checkout | [endpoint-checkout](../api/endpoint-checkout.md) |
| endpoint-identity | [endpoint-identity](../api/endpoint-identity.md) |

## 5. Navigation (summary)

- method / point / slot / payment → re-quote totals
- Place order → screen-order (`Order_Placed`)
