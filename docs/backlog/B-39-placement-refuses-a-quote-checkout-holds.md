---
id: B-39
title: "server: placement refuses a quote the checkout is holding"
status: open
priority: P1
size: S
stage: stage-5-order
blocked_by: [B-15, B-16]
---

# B-39 — server: placement refuses a quote the checkout is holding

Since B-15 the checkout holds «Place order» while a refused address form is shown
(`CheckoutState.placeable`), but `Placement` (B-16) only checks that the quote is complete. With a
refused form on screen the previous address still completes the quote, so a client that ignores the
held button — or a second tab — places the order to the address the shopper was trying to change.
Found by B-15.

- AC: `POST /api/v1/orders` answers a refusal (a typed code, e.g. `409 checkout_held` or the
  validation error the form carries — decide and document) when the checkout state is not placeable;
  a route test places with a refused form on record and asserts no order, no stock taken, no window.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`.
