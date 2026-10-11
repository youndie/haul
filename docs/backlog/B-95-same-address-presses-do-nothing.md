---
id: B-95
title: "client: a press to the page already shown refreshes it"
status: wip
priority: P1
size: S
stage: stage-11-qa
---

# B-95 — client: a press to the page already shown refreshes it

Found in the QA pass of 2026-10-10 (v0.3.0). `Navigator.open` ignores the address already shown (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt:146`), so:
- on an empty cart, «+» on a «Picked for you» card answers an `update` of the header and the card only; the page still
  says «Your cart is empty» with the count at 1, and the message's «View cart» and the header's Cart do nothing
  (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt:230`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt:58-65`);
- submitting the query already shown (Enter, the button, a suggestion, «All N results») does nothing and the
  suggestion panel stays open (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:193-195, 221-226`).

- AC: a press to the page already shown reloads it in place (B-62 keeps it on screen) and closes what it opened; on
  the empty cart the line appears after «+»; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.
