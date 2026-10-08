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
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout
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

> The server's tree exists since B-14 (placement since B-16); the renderers, the screen's client
> states and its parity are B-15's, so nothing below is ticked and this document stays a draft until
> B-15 lands. The `Checkout_*` reference PNGs are not on `main` yet.

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | *planned* (B-15): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` — today `/checkout` is `PageKind.Other`: a pending header while loading, «This page didn’t load» on failure |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt` |
| The components on the wire | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/checkout` in the browser.
- **Shown when:** signed in; a guest is sent to sign-in. The server answers a guest `401
  unauthenticated` (`CheckoutRoutesTest.checkout needs a sign-in`); what the client does with that on
  this page is B-15's. `/checkout` is in `StorefrontPage`, so a reload answers the page.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

What the server's one tree does per state (B-14): **Content**, **PickupPoint**, **ParcelLocker**,
**Validation** and **PlaceError** are all `GET /ui/checkout`, drawn from the stored choices —
`CheckoutHeader`, a `CheckoutNotice` per notice, `PageTitle` «Checkout · N items»,
`DeliveryMethods`, then `CheckoutAddress` + `DeliverySlots` by courier or `PickupPoints` otherwise,
`PaymentMethods`, `CheckoutSummary`. **PointsApplied** has no tree yet: `CheckoutSummary.redeem` stays
empty until B-23. **Placing** is the client's (the button in progress while `POST /api/v1/orders`
runs, B-15).

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

- method / point / slot / payment → `PUT /api/v1/me/checkout`, answered `refresh`: the page is drawn again with a new quote
- «Save address» → `POST /api/v1/me/addresses`, answered `refresh`; a refused form comes back in the tree with an error under each field
- Place order → `POST /api/v1/orders` with a new `Idempotency-Key` per quote → `202` and `navigate` to `/orders/{id}` (screen-order, `Order_Placed`; the order page and its address are B-18's — see [endpoint-orders](../api/endpoint-orders.md)); `409 slot_unavailable` → the page drawn again as PlaceError
- the logo → `/` (`CheckoutHeader.home`)

## 6. Copy to check against the artboards (B-14's findings)

The tree's copy was chosen without the `Checkout_*` sources on disk; B-15 checks it: the section
titles («How to receive it», «Delivery address», «Delivery window», «Pickup points nearby», «Parcel
lockers nearby», «Payment»); a method's detail (the delivery fee or «Free»); the default window (the
first with room, 09:00–12:00 for Maya, where Content draws 15:00–18:00); the pickup list — two points
and one locker in research §6 against «3 nearby points» above; the address line «148 Wythe Avenue, Apt
4F» against «148 Wythe Avenue 4F» above; the form's fields, labels and error sentences; the header's
current step; the notices; «You'll earn 1,024 points» and «Your card is charged when the order ships»
under the button.
