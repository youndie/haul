---
id: B-83
title: "client: every press that reads a field sees what was typed"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-83 — client: every press that reads a field sees what was typed

B-76 found that on the web a press can be handled before the typed text reaches the focused field, and added
`awaitTypedInput` (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/TypedInput.kt`) for the promo code. The same race is open on the header's search button,
the review and question dialogs' «Post»/«Send», and checkout's address form (B-76 findings). B-69 also lost
keystrokes when focus moved to another field before a throttled frame applied them.

- AC: each of those presses sends what was typed just before it (a client test per press, red before);
  moving focus right after typing keeps the typed text, or the finding is recorded with its cause.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/TypedInput.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt`.
