---
id: B-41
title: "client: sign-in returns to where it was asked for"
status: open
priority: P2
size: S
stage: stage-5-order
blocked_by: [B-12, B-35]
---

# B-41 — client: sign-in returns to where it was asked for

The guest cart's «Sign in to check out» carries `/sign-in?next=%2Fcheckout`, but the app ignores `next`:
after the popup signs the shopper in, the cart is shown again and checkout has to be found by hand.
Found while syncing the drafts after B-13…B-16.

- AC: after sign-in from an action carrying `next`, the app navigates to that address (only a storefront
  address from `StorefrontPage` — never an arbitrary URL); a client test covers the cart's «Sign in to
  check out» landing on `/checkout`.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`.
