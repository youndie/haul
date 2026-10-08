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

> Built: the server's tree (B-14), placement (B-16), the renderers, the client's commands and parity
> for all eighteen `Checkout_*` artboards (B-15), placement held with the button (B-39, B-42). Still
> *target*, which keeps this document a draft: points (B-23 — PointsApplied and the toggle are drawn
> from the canvas's data, not from a balance) and the order page placement navigates to (B-18).

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt` (`CheckoutHeaderRenderer`, `CheckoutBodyRenderer` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRenderers.kt`) |
| The client's commands | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommandsClient.kt` (`CheckoutCommand`, `CheckoutCommands`, `ktorCheckoutCommands`) |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`CheckoutLoading`, `CheckoutError`); `/checkout` is `PageKind.Checkout` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt` |
| The components on the wire | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/CheckoutComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` (rendered with grayscale text) |
| Parity fixtures, one per artboard | `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/CheckoutFixtures.kt`, held equal to the server's trees by `server/src/test/kotlin/io/github/youndie/haul/feature/checkout/CheckoutFixturesTest.kt` |

## 0. Entry point and visibility

- **Entry point:** `/checkout` in the browser.
- **Shown when:** signed in. The way here is the cart's «Checkout», or a guest's «Sign in to check
  out», which returns to `/checkout` after sign-in (B-41). A guest who opens `/checkout` directly gets
  the server's `401 unauthenticated` (`CheckoutRoutesTest.checkout needs a sign-in`), which the client
  draws as Error («Checkout didn’t load»), not as a sign-in. `/checkout` is in `StorefrontPage`, so a
  reload answers the page.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

What the server's one tree does per state: **Content**, **PickupPoint**, **ParcelLocker**,
**Validation** and **PlaceError** are all `GET /ui/checkout`, drawn from the stored choices —
`CheckoutHeader`, then `CheckoutBody`: the title, a `CheckoutNotice` per notice, `DeliveryMethods`,
`CheckoutAddress` (the inline form) + `DeliverySlots` by courier or `PickupPoints` otherwise,
`PaymentMethods` (with the points toggle under it), `CheckoutSummary` — the sections beside the order
at 1440, under them on a phone. **PointsApplied** has no tree of its own: no points balance is stored
(B-23), so the server sends no toggle, and the fixtures carry the canvas's («Use 2,480 points
(−$24.80)», inert). **Placing** is the client's: Content with the button «Placing order…» while
`POST /api/v1/orders` runs, nothing sent meanwhile. **Loading** and **Error** are `CheckoutLoading` and
`CheckoutError` («Checkout didn’t load», «Your cart is unchanged»).

All eighteen artboards are within the default parity tolerance (B-15's findings carry the table).

- [x] **Loading:** the minimal header, placeholder sections and summary
- [x] **Content:** courier, 148 Wythe Avenue 4F, Wed 8 / 15:00–18:00, card ···· 4821, «Use 2,480 points (−$24.80)» off, summary $512, «Place order · $512.00»
- [x] **PointsApplied:** Content with the points toggle on: a «Points −$24.80» line, total $487.20, «Place order · $487.20», Haul Pay «4 payments of $121.80» — drawn from the canvas's data until B-23
- [x] **PickupPoint:** «Pickup point» chosen: list of 3 nearby points with distance and hours, 214 Bedford Ave selected; no slot section
- [x] **ParcelLocker:** «Parcel locker» chosen: list of lockers; «Pay on delivery» absent
- [x] **Validation:** courier with empty street and ZIP: field errors, button disabled with its hint («Fill in the street address and ZIP»). The hold is the server's: placement past it is refused `409 checkout_held` (B-39). It holds only while the method is courier — a refused form does not hold a pickup point or a locker, and switching back to courier draws the form with its errors and holds the button again (B-42)
- [x] **Placing:** Content with the button in progress, inputs disabled
- [x] **PlaceError:** Content with a banner «That delivery window just filled up — pick another», the slot cleared
- [x] **Error:** the minimal header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1467 / 2204; Content 1564 / 2362; PointsApplied 1564 / 2390; PickupPoint 1267 / 2005; ParcelLocker 1193 / 1802; Validation 1608 / 2438; Placing 1564 / 2362; PlaceError 1678 / 2509; Error 804 / 529.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-checkout | [endpoint-checkout](../api/endpoint-checkout.md) |
| endpoint-identity | [endpoint-identity](../api/endpoint-identity.md) |

## 5. Navigation (summary)

Every press is a `CheckoutCommand` sent through `Identity.send`; the answer — kompot's `refresh`, a
refusal included — fetches the tree again, and no answer leaves the page as it was.

- method / point / slot / payment → `Choose`: `PUT /api/v1/me/checkout`; the method already chosen, a full window and a held button send nothing
- the address form → `SaveAddress`: `POST /api/v1/me/addresses` when the shopper leaves the form (or presses Enter in a field), only when something changed; a refused form comes back in the tree with an error under each field
- Place order → `Place`: `POST /api/v1/orders` with `PlaceOrderRequest(quote)` and one `Idempotency-Key` per quote (remembered per fingerprint, so a retry is the same order) → `202` and `navigate` to `/orders/{id}` (screen-order, `Order_Placed`; the order page and its address are B-18's — see [endpoint-orders](../api/endpoint-orders.md)); a refusal (`409 slot_unavailable`, `cart_changed`, `checkout_held`) redraws the checkout — PlaceError for a window that filled
- the points toggle → nothing (B-23)
- the logo → `/` (`CheckoutHeader.home`)

## 6. Copy

The canvas answered B-14's copy questions and the server follows it (B-15): «How to receive» with
«Tomorrow, Oct 8» / «Thu, Oct 9 · 240 m away» / «Thu, Oct 9 · 24/7 access» and «Free»; «Address»
with «Street address», «Apt / suite», «City», «ZIP», «Door code» («Optional»), «Note for courier»;
«Delivery time» with day tiles and windows «15:00 – 18:00» («· Full» when full); «Payment» with «Card
or cash» for pay on delivery; «Your order» with «Delivery · Wed, Oct 8»; the terms and «Your card is
charged when the order ships» under the button; the hint saying why it is held. The test card ···· 0002
is not listed (it is still chosen by id). Two of B-14's choices were rules and stay: the default window
is the first with room (Content's 15:00–18:00 is the fixture's choice), and the windows start tomorrow
whatever the items' dispatch days. The step indicator stays on «Delivery»: the steps are labels in v1.
