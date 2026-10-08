---
id: haul-shared
title: Haul shared contract
type: service
module: shared
tech_stack: [Kotlin Multiplatform, kotlinx.serialization, kompot-core]
owner: unassigned
depends_on: []
publishes:
  - nothing — a module of this build
---

# Haul shared contract

> Describes the module as it is after B-37: browse, product, search, cart, checkout, sign-in,
> placement, and the storefront's address list. The components of screens not built yet (orders,
> account, saved, reviews' answers, membership) arrive with their items and are not listed here.

## 1. Responsibility

The one place the server and the client agree: the Haul components on the wire (`HaulHeader`,
`ProductCard`, `CartLine`, … — each `@Serializable`, `@KompotComponentMarker`, wire type
`haul_<snake_case>`), the request bodies of commands, the guest and idempotency headers, the browser's
sign-in settings, the closed `ErrorCode` enum with the `ErrorBody` every refusal carries,
`haulWireJson`, the JSON both sides use, and `StorefrontPage`, the one list of the storefront's
addresses that the server serves the page at and the client reads its page kinds from (B-36).

There are **no route classes**: paths are the server's strings, handed to the client inside the
tree (a component's `url`, a kompot action), and the client follows them rather than building any
(CLAUDE.md, «Modules»). There are no money or time types either: prices and dates travel as the
strings the screen shows («$349», «Delivery tomorrow»), formatted by the server; the one instant on
the wire, `SectionHeader.countdownEndsAt`, is an ISO-8601 string.

Deliberately does **not**: hold logic, defaults that a screen depends on, or anything only one side
reads.

## 2. What is in it

| Feature | Components (`ui/`) | Command bodies and other types |
|---|---|---|
| frame | `HaulHeader` (with `account`, `catalog` — a list of `Link` —, `deals`, `cart`, and `customerName` defaulting to `null`), `Link`, `ProductCard` (with `image` and `add`) (`HaulComponents.kt`) | — |
| browse, home, deals | `CampaignHero`, `PromoBanner`, `CampaignRow`, `SectionHeader`, `CategoryGrid`, `ProductGrid`, `PlusBlock`, `HaulFooter`, `Breadcrumbs`, `PageTitle` (with `badge`), `FilterChips`, `FacetPanel`, `AppliedFilters` (with `clearAction`, `sorts`), `FilteredResults`, `HaulPagination` (with `moreAction`, `links`), `EmptyState` (with `primary`) (`BrowseComponents.kt`) | — |
| product | `ProductDetails` (with `photo`), `ProductTabs`, `ProductDescription`, `SpecificationList`, `ProductReviews`, `ProductQuestions` (`ProductComponents.kt`) | — |
| search | `SearchSuggestPanel` (with `clearUrl`, which replaced `clearAction`), `SearchNoResults` (`SearchComponents.kt`) | — |
| cart | `CartBody`, `CartLine` (with `changeDetail`), `CartGroup`, `CartSelection`, `PromoField` (with `terms`), `SummaryRow` (with `saving`, which replaced `detail`), `OrderSummary` (with `title`, `promo`, `pointsAccent`) — wire types `haul_cart_body`, `haul_cart_line`, `haul_cart_group`, `haul_cart_selection`, `haul_promo_field`, `haul_order_summary` (`CartComponents.kt`, B-11, B-13) | `LineChange`, `LinesRemoval`, `PromoEntry`, `LineCommand` (a URL and a `LineChange`: a card's «+», B-37) (`feature/cart/CartCommands.kt`) |
| checkout | `CheckoutHeader`, `CheckoutNotice`, `DeliveryMethods`, `CheckoutAddress`, `DeliverySlots`, `PickupPoints`, `PaymentMethods`, `CheckoutSummary` — wire types `haul_checkout_header`, `haul_checkout_notice`, `haul_delivery_methods`, `haul_checkout_address`, `haul_delivery_slots`, `haul_pickup_points`, `haul_payment_methods`, `haul_checkout_summary` — and their parts (`MethodOption`, `AddressOption`, `FormField`, `SlotDay`, `SlotOption`, `PickupPointOption`, `PaymentOption`, `SummaryItem`, `PointsToggle`) (`CheckoutComponents.kt`, B-14) | `DeliveryMethod`, `CheckoutChoice`, `AddressEntry`, `PlaceOrderRequest`, `IDEMPOTENCY_KEY_HEADER` = `Idempotency-Key` (`feature/checkout/CheckoutCommands.kt`, B-14, B-16) |
| identity | — | `GuestDto`, `GUEST_HEADER` = `X-Haul-Guest` (`feature/identity/Guests.kt`); `SignInSettings` (`feature/identity/SignInSettings.kt`, B-12) |
| addresses | — | `StorefrontPage` (`StorefrontPage.kt`, B-36): `/`, `/c/{path...}`, `/p/{productId}`, `/search`, `/cart`, `/deals`, `/checkout`, `/account`, `/sign-in`, matched exactly by `StorefrontPage.of` |
| every refusal | — | `ErrorCode`, `ErrorBody` (with `fields`), `FieldError` (`ErrorCode.kt`) |

Each command-carrying component holds the URL its commands go to — `CartLine.url` and
`acknowledgeUrl`, `CartSelection.linesUrl`, `PromoField.url`, `ProductCard.add`,
`SearchSuggestPanel.clearUrl`, the checkout components' `url` (and `CheckoutAddress.choiceUrl`),
`CheckoutSummary.placeUrl` — and the method and body are written on the component
([endpoint-cart](../api/endpoint-cart.md), [endpoint-checkout](../api/endpoint-checkout.md)). The
checkout's components have no renderers yet (B-15).

**`ErrorCode`**, all of them: `validation_failed`, `category_not_found`, `product_not_found`,
`unavailable`, `internal`, `query_too_short`, `unauthenticated`, `guest_not_found` (B-12),
`sku_not_found`, `out_of_stock`, `line_not_found`, `promo_not_found`, `promo_already_applied`,
`promo_expired`, `promo_not_applicable`, `cart_empty`, `slot_unavailable`, `slot_not_found`,
`pickup_point_not_found`, `address_not_found`, `payment_method_not_allowed`, `field_required`,
`field_invalid` (B-14; the last two only inside `ErrorBody.fields`), `idempotency_key_missing`,
`idempotency_key_reused`, `cart_changed` (B-16). Their statuses are the server's (`status` in
`server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`).

## 2a. Code anchors

| File | What is there |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` | the Haul components |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` | the cart's command bodies and `LineCommand` |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` | checkout's command bodies, placement's request and its header |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt` | `GuestDto` and the guest header |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInSettings.kt` | what the browser reads before it signs in |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/StorefrontPage.kt` | the storefront's addresses |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` | every error code and the error body |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/HaulWire.kt` | `haulWireJson`: discriminator `type`, `explicitNulls = false`, `ignoreUnknownKeys`, kompot's modules plus the generated Haul one |
| `shared/build.gradle.kts` | targets: jvm, wasmJs; kompot's registry processor (KSP) |

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Library | kompot-core, kompot-standard (`api`) | the tree and the standard containers the trees are laid out with |
| Library | kompot-registry-annotations, kompot-registry-processor (KSP) | the polymorphic registration of every `@KompotComponentMarker` class |
| Library | kotlinx.serialization | the wire |
