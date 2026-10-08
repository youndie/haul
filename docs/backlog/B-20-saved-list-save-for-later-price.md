---
id: B-20
title: "server + client: Saved list, save for later, price drops"
status: open
priority: P2
size: M
stage: stage-6-account
blocked_by: [B-01, B-13, B-19]
---

# B-20 — server + client: Saved list, save for later, price drops

The Saved list holds hearted products and lines saved for later, and marks price drops.

Feature: `feature-account` — its scenarios are this item's acceptance where it names them.

- Not covered: price-drop notifications outside the app.

- AC: parity for every `Saved_*`; the price-drop scenario passes.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/saved/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/`.

- **From B-37:** the heart on a product card and the header's «Saved» shortcut are drawn and carry no
  action; they get one here — the heart a command fixed in the tree like the card's «+»
  (`ProductCard.add`, a `LineCommand`), «Saved» a `navigate` in `HaulHeader`.
