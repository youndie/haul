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

> Describes the module as it is after B-55: browse, product with its reviews, questions, helpful votes
> and buy box, search, cart, checkout with points, sign-in, placement, the order page with Haul Pay's
> plan (B-24) and returns with their points back (B-50), the account, the Saved list, Haul Plus, the
> header's Plus pill and the catalog's root (B-49), and the storefront's address list. «Picked for you»
> (B-25), «bought this month» (B-52, `ProductDetails.bought` was already there), listing names (B-45),
> early campaign prices (B-53) and the live order page (B-29, B-56 — its frames are kompot-realtime's
> own `KompotScreenResponse` and `UpdateComponentMessage`) added nothing to it.

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
| frame | `HaulHeader` (with `account`, `catalog` — a list of `Link` —, `deals`, `cart`, `orders` (B-18), `saved` (B-20), `plus`, the «HAUL PLUS» pill's action (B-49), and `customerName` defaulting to `null`), `Link`, `ProductCard` (with `image`, `add`, and the heart: `saved`, `heartCommand`, `heartAction`, and `drop`, the Saved list's mark — B-20) (`HaulComponents.kt`) | `SaveCommand` (a URL and the state a press leaves: `PUT` keeps, `DELETE` lets go) (`feature/saved/SaveCommand.kt`, B-20) |
| browse, home, deals | `CampaignHero`, `PromoBanner`, `CampaignRow`, `SectionHeader`, `CategoryGrid`, `ProductGrid`, `PlusBlock`, `HaulFooter`, `Breadcrumbs`, `PageTitle` (with `badge`), `FilterChips`, `FacetPanel`, `AppliedFilters` (with `clearAction`, `sorts`), `FilteredResults`, `Facet` (with `moreAction`, «Show N more», B-49), `HaulPagination` (with `moreAction`, `links`), `EmptyState` (with `primary`) (`BrowseComponents.kt`) | — |
| product | `ProductDetails` (with `photo`; `add` and `buy`, the buy box's `LineCommand`s, B-48; `heartCommand`, `heartAction`, B-20), `ProductTabs`, `ProductDescription`, `SpecificationList`, `ProductReviews` and `ProductQuestions` (each with `action`: the dialog's `present` for a customer, sign-in for a guest), `ReviewForm` (`haul_review_form`) and `QuestionForm` (`haul_question_form`) with their `FormProduct`; a review's `helpfulCommand` (B-43) (`ProductComponents.kt`, B-22) | `ReviewEntry`, `QuestionEntry`, `ReviewRules`, `reviewProblems`, `questionProblems` — the one copy of the rules, which the server refuses by and the client checks before sending; `HelpfulVote` and `HelpfulCommand` (the vote a press leaves, B-43) (`feature/reviews/ReviewCommands.kt`, B-22) |
| search | `SearchSuggestPanel` (with `clearUrl`, which replaced `clearAction`, and `recent` as `List<Link>`, which was `List<String>`, B-49), `SearchNoResults` (`SearchComponents.kt`) | — |
| cart | `CartBody`, `CartLine` (with `changeDetail`; `saveUrl` and `saveAction`, «Save for later», B-20), `CartGroup`, `CartSelection`, `PromoField` (with `terms`), `SummaryRow` (with `saving`, which replaced `detail`), `OrderSummary` (with `title`, `promo`, `pointsAccent`) — wire types `haul_cart_body`, `haul_cart_line`, `haul_cart_group`, `haul_cart_selection`, `haul_promo_field`, `haul_order_summary` (`CartComponents.kt`, B-11, B-13) | `LineChange`, `LinesRemoval`, `PromoEntry`, `LineCommand` (a URL, a `LineChange` and an optional `next` the client follows once the change is accepted: a card's «+», B-37; «Add to cart» and «Buy now», B-48) (`feature/cart/CartCommands.kt`) |
| checkout | `CheckoutHeader` and `CheckoutBody` (the title, the notices, the sections in order, the summary — B-15), whose parts are the components `CheckoutNotice`, `DeliveryMethods`, `CheckoutAddress` (the inline form: `form`, `url`), `DeliverySlots` (with `notice`), `PickupPoints`, `PaymentMethods` (with the `points` toggle), `CheckoutSummary` (with `title`, `placeHint`, `placingLabel`) — wire types `haul_checkout_header`, `haul_checkout_body`, `haul_checkout_notice`, `haul_delivery_methods`, `haul_checkout_address`, `haul_delivery_slots`, `haul_pickup_points`, `haul_payment_methods`, `haul_checkout_summary` — and their parts (`MethodOption` with `price`, `FormField` with `placeholder`, `SlotDay` with `weekday`, `date`, `selected`, `SlotOption`, `PickupPointOption` with `detail`, `PaymentOption`, `SummaryItem`, `PointsToggle` with `detail` and a nullable `url`) (`CheckoutComponents.kt`, B-14, B-15) | `DeliveryMethod`, `CheckoutChoice` (with `usePoints`, B-23), `AddressEntry`, `PlaceOrderRequest`, `IDEMPOTENCY_KEY_HEADER` = `Idempotency-Key` (`feature/checkout/CheckoutCommands.kt`, B-14, B-16) |
| order and returns | `OrderBody` (`haul_order_body`) with `OrderSteps`, `OrderNotice`, `PickupCode`, `OrderShipment`, `OrderItem`, `OrderFact` (`OrderFactKind`: `card`, `place`, `points`), `OrderTotals` (with `reorderUrl`, `returnAction`, and `plan` — `PaymentPlan`, `PlanPayment`, `PlanPaymentState`, Haul Pay's schedule, B-24); `ReturnForm` (`haul_return_form`, with `pointsBack`, B-50) with `ReturnLine` (with `pointsBack`, B-50) and `ReturnReason` (`OrderComponents.kt`, B-18, B-21) | `ReturnEntry`, `returnProblems`, `exactDollars`, `groupedCount` — the return's body, its rules and the money and count formats both sides add a refund up in (`feature/returns/ReturnCommands.kt`, B-21, B-50) |
| account and saved | `AccountBody` (`haul_account_body`) with `AccountProfile`, `AccountMenuItem`, `AccountTile` (`AccountTileKind`: `points`, `plus`, `plus_offer`, `price_drops`), `ActiveOrders`, `ActiveOrder`, `AccountPickup`, `OrderHistory`, `HistoryFilter`, `HistoryRow` (`HistoryStatusKind`), `count` and `saved` (`AccountComponents.kt`, B-19, B-20); `SavedList`, `SavedStep` (`SavedComponents.kt`, B-20) | — |
| membership | `PlusTrialDialog` (`haul_plus_trial_dialog`) with `PlusBenefit` (`MembershipComponents.kt`, B-23) | — |
| identity | — | `GuestDto`, `GUEST_HEADER` = `X-Haul-Guest` (`feature/identity/Guests.kt`); `SignInSettings` (`feature/identity/SignInSettings.kt`, B-12) |
| addresses | — | `StorefrontPage` (`StorefrontPage.kt`, B-36): `/`, `/c` (`Categories`, B-49), `/c/{path...}`, `/p/{productId}`, `/search`, `/cart`, `/deals`, `/checkout`, `/account`, `/account/orders` (B-19), `/account/orders/{orderId}` (B-18), `/account/saved` (B-20), `/sign-in`, matched exactly by `StorefrontPage.of` |
| every refusal | — | `ErrorCode`, `ErrorBody` (with `fields`), `FieldError` (`ErrorCode.kt`) |

Each command-carrying component holds the URL its commands go to — `CartLine.url` and
`acknowledgeUrl`, `CartLine.saveUrl`, `CartSelection.linesUrl`, `PromoField.url`, `ProductCard.add`,
`ProductCard.heartCommand`, `ProductDetails.add` and `.buy`, `SearchSuggestPanel.clearUrl`, the
checkout components' `url` (`PointsToggle.url` included), `CheckoutSummary.placeUrl`, `ReviewForm.url`,
`QuestionForm.url`, `Review.helpfulCommand`, `OrderTotals.reorderUrl`, `ReturnForm.url`,
`PlusTrialDialog.url` — and the method and body are written on the component
([endpoint-cart](../api/endpoint-cart.md), [endpoint-checkout](../api/endpoint-checkout.md),
[endpoint-reviews](../api/endpoint-reviews.md), [endpoint-saved](../api/endpoint-saved.md),
[endpoint-orders](../api/endpoint-orders.md), [endpoint-membership](../api/endpoint-membership.md)).

**`ErrorCode`**, all of them: `validation_failed`, `category_not_found`, `product_not_found`,
`unavailable`, `internal`, `query_too_short`, `unauthenticated`, `guest_not_found` (B-12),
`sku_not_found`, `out_of_stock`, `line_not_found`, `promo_not_found`, `promo_already_applied`,
`promo_expired`, `promo_not_applicable`, `cart_empty`, `slot_unavailable`, `slot_not_found`,
`pickup_point_not_found`, `address_not_found`, `payment_method_not_allowed`, `field_required`,
`field_invalid` (B-14; the last two only inside `ErrorBody.fields`), `idempotency_key_missing`,
`idempotency_key_reused`, `cart_changed` (B-16), `checkout_held` (`409`, B-39: placement of a
checkout that holds «Place order» for a refused address form while the method is courier),
`review_exists` (`409`, B-22: one review per customer per product), `order_not_found` (`404`, B-18:
another customer's order too), `review_not_found` (`404`) and `own_review` (`409`, B-43),
`return_window_closed` (`422`), `not_delivered` (`422`) and `already_returned` (`409`, B-21),
`already_member` (`409`, B-23). Their statuses are the
server's (`status` in `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`).

## 2a. Code anchors

| File | What is there |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/` | the Haul components |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt` | the cart's command bodies and `LineCommand` |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommands.kt` | checkout's command bodies, placement's request and its header |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt` | the review's and the question's bodies and their rules, the helpful vote |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/returns/ReturnCommands.kt` | the return's body and its rules |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SaveCommand.kt` | the heart's command |
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
