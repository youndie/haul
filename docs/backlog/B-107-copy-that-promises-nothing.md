---
id: B-107
title: "server: the store does not promise what it does not have"
status: open
priority: P3
size: S
stage: stage-11-qa
---

# B-107 — server: the store does not promise what it does not have

Found in the QA pass of 2026-10-10 (v0.3.0), code audit — words a visitor reads that nothing backs:
- «Get the app · Order tracking, price drop alerts and app-only deals» (`server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt:168-169`) — no app;
- «By placing the order you agree to the Terms of Sale» (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt:282`) — no page;
- the hero's eyebrow is fixed «Autumn mega sale · …» whatever the campaign (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt:121`);
  the empty cart's «… up to −70 % in the Autumn mega sale» is fixed (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt:240`);
- «Search 2.4 million products» (`Frame.kt:78`) over a catalogue of hundreds.

- AC: each line drawn from data or removed; tests where data drives it.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`.
