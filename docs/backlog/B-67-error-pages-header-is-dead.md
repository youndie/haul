---
id: B-67
title: "client: the header on loading, error and not-found pages leads somewhere"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-67 — client: the header on loading, error and not-found pages leads somewhere

Loading placeholders and error pages (`ErrorShell`, `SearchError`, `CartError`, `AccountError`, `SavedError`) draw
`SHELL_HEADER`, whose actions are all empty (`shell/Shell.kt:55-75`): on a 5xx or unreachable page only the logo and
the search work. Catalog, Deals, the category words, Orders, Saved, Sign in, Cart and HAUL PLUS do nothing. Landing
straight on a 404 or the sign-in prompt gives the same dead header (`Storefront.kt:286, :291`).
`NotFoundShell` and `SignInPrompt` draw the header without `onAccount` (`Shell.kt:405`, `SignInPrompt.kt:57`,
`ProductNotFound.kt:16`). «Go to your orders» on a missing order is a no-op with that header
(`feature/order/OrderNotFound.kt:23`).

- AC: on each of those pages every header control the shopper sees works, or is not drawn; a test per page kind.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderNotFound.kt`.
