---
id: B-12
title: "server + client: shildik sign-in in the browser, customer creation, cart merge, header states"
status: open
priority: P1
size: L
stage: stage-4-cart
blocked_by: [B-04, B-11]
---

# B-12 — server + client: shildik sign-in in the browser, customer creation, cart merge, header states

Checkout, saving, reviews and the account need a customer; the browser cannot use shildik's own client (research risk 2), so the flow is proven first.

Feature: `feature-identity` — its scenarios are this item's acceptance where it names them.

- Not covered: account settings.

- AC: feature-identity scenarios pass against a local shildik; a guest signs in and keeps the cart.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/server/identity/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/identity/`.
