---
id: B-92
title: "client: a command that fails or is refused tells the shopper"
status: wip
priority: P1
size: M
stage: stage-11-qa
---

# B-92 — client: a command that fails or is refused tells the shopper

Found in the QA pass of 2026-10-10 (v0.3.0). With the server stopped, «Place order» and «Reorder» failed (`ERR_CONNECTION_RESET`) and the UI showed
nothing; the button went back to normal. A refused «Place order» (cart or quote changed, payment not allowed, out of
stock) becomes a `refresh` that throws the reason away (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommandsClient.kt:82-87`). The
same holds across presses: a failed page `load` shows «The page didn't update» with Retry (B-62); a cart, heart,
checkout or vote command with no answer shows nothing (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt:113-118`,
`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt:129-138`); the review dialogs say «not sent»; the Plus trial dialog says nothing.

- **Decided as product owner:** one rule for every command — no answer means a message («Couldn't reach the store»
  with Retry for idempotent presses); a refusal shows its reason (the server's words) where the press was.
- AC: a test per command family (cart, saved, checkout choice/address/place, order reorder/return, reviews, Plus) for
  «no answer» and «refused»; the e2e unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommandsClient.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Messages.kt`.
