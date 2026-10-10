---
id: B-81
title: "server + client: a card's «+» says the item was added"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-81 — server + client: a card's «+» says the item was added

B-75 gave «Add to cart» on the product page a message («Added to your cart», «View cart») and left a card's
«+» silent: the card stays under the finger and the header's count answers.

- **Decided as product owner (2026-10-10):** the «+» answers with the same message. Its answer becomes
  `sequence[update, show_message]`, the way B-75 made the buy box's.
- AC: on every page that draws cards (home, catalog, search, deals, «Picked for you»), «+» updates the header's
  count and the card, and shows «Added to your cart» with «View cart»; a «+» with nothing to add shows nothing;
  server and client tests; the e2e reads the message.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/LineAnswers.kt`.
