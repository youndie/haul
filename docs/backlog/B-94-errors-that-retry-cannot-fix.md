---
id: B-94
title: "client + server: an error page says what happened and offers what helps"
status: wip
priority: P1
size: S
stage: stage-11-qa
---

# B-94 — client + server: an error page says what happened and offers what helps

Found in the QA pass of 2026-10-10 (v0.3.0), code audit. Three answers are drawn as «try again» with a Retry that can never succeed:
- `/checkout` with nothing selected (back after placing an order, a reload, an empty merged cart) gets `409 cart_empty`
  → «Checkout didn't load · Your cart is unchanged. Try again» (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt:188`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:724-726`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:757-766`). It should take the shopper to the cart.
- a one-character search gets `400 query_too_short` → «Search didn't respond … Try again in a moment»
  (`server/src/main/kotlin/io/github/youndie/haul/feature/search/domain/Search.kt:36`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:158-166`).
- any `400` from an old or edited link (bad `page`, `sort`, `from`, `shown`, a `sku` no longer on the product — a
  shared link included) → «Something went wrong on our side» (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:736-739`).
The Account and Saved error pages always say «on our side», even when the network was unreachable (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:955, 1018`).

- AC: checkout with an empty cart lands on the cart; a too-short query explains itself in the search; a bad
  parameter falls back to the page without it (or says the link is stale); every error page tells «unreachable»
  from «our side»; tests per case.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`.
