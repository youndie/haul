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

> Describes the module as it is after B-11. The components of screens not built yet (checkout,
> orders, account, saved) arrive with their items and are not listed here.

## 1. Responsibility

The one place the server and the client agree: the Haul components on the wire (`HaulHeader`,
`ProductCard`, `CartLine`, … — each `@Serializable`, `@KompotComponentMarker`, wire type
`haul_<snake_case>`), the request bodies of commands, the guest header, the closed `ErrorCode` enum
with the `ErrorBody` every refusal carries, and `haulWireJson`, the JSON both sides use.

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
| frame | `HaulHeader`, `ProductCard` (`HaulComponents.kt`) | — |
| browse, home | `CampaignHero`, `PromoBanner`, `CampaignRow`, `SectionHeader`, `CategoryGrid`, `ProductGrid`, `PlusBlock`, `HaulFooter`, `Breadcrumbs`, `PageTitle`, `FilterChips`, `FacetPanel`, `AppliedFilters`, `FilteredResults`, `HaulPagination`, `EmptyState` (`BrowseComponents.kt`) | — |
| product | `ProductDetails`, `ProductTabs`, `ProductDescription`, `SpecificationList`, `ProductReviews`, `ProductQuestions` (`ProductComponents.kt`) | — |
| search | `SearchSuggestPanel`, `SearchNoResults` (`SearchComponents.kt`) | — |
| cart | `CartLine`, `CartGroup`, `CartSelection`, `PromoField`, `OrderSummary` — wire types `haul_cart_line`, `haul_cart_group`, `haul_cart_selection`, `haul_promo_field`, `haul_order_summary` (`CartComponents.kt`, B-11) | `LineChange`, `LinesRemoval`, `PromoEntry` (`feature/cart/CartCommands.kt`) |
| identity | — | `GuestDto`, `GUEST_HEADER` = `X-Haul-Guest` (`feature/identity/Guests.kt`) |
| every refusal | — | `ErrorCode`, `ErrorBody` (`ErrorCode.kt`) |

Each cart component carries the URL its commands go to — `CartLine.url` and `acknowledgeUrl`,
`CartSelection.linesUrl`, `PromoField.url` — and the method and body are written on the component
([endpoint-cart](../api/endpoint-cart.md)). The cart's components have no renderers yet (B-13).

## 2a. Code anchors

| File | What is there |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` | the Haul components |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` | the cart's command bodies |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Guests.kt` | `GuestDto` and the guest header |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` | every error code and the error body |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/HaulWire.kt` | `haulWireJson`: discriminator `type`, `explicitNulls = false`, `ignoreUnknownKeys`, kompot's modules plus the generated Haul one |
| `shared/build.gradle.kts` | targets: jvm, wasmJs; kompot's registry processor (KSP) |

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Library | kompot-core, kompot-standard (`api`) | the tree and the standard containers the trees are laid out with |
| Library | kompot-registry-annotations, kompot-registry-processor (KSP) | the polymorphic registration of every `@KompotComponentMarker` class |
| Library | kotlinx.serialization | the wire |
