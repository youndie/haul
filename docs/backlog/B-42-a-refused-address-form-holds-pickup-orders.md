---
id: B-42
title: "server: a refused address form does not hold a pickup or locker order"
status: open
priority: P2
size: S
stage: stage-5-order
blocked_by: [B-39]
---

# B-42 — server: a refused address form does not hold a pickup or locker order

A refused address form is kept as the checkout's draft (`StoredCheckout.draft`) when the method
changes to a pickup point or a parcel locker, and `CheckoutState.placeable` counts the draft's
problems whatever the method — while the screen draws the address form only for courier. A shopper
who switches to pickup after a refused form sees «Fill in the street address…» with no form to fill,
and since B-39 placement refuses the same way (`409 checkout_held`). Found by B-39, from the code.

Decided as product owner: the draft's problems hold «Place order» only for courier; the draft itself
is kept, so switching back to courier shows what the shopper was typing.

- AC: with a refused form on record, choosing a pickup point makes the checkout placeable and
  `POST /api/v1/orders` places to that point; switching back to courier shows the draft and holds
  the button again. Route tests for both.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/Quote.kt`.
