---
id: B-130
title: "fix: a lapsed sign-in draws the guest's header, not the customer's"
status: open
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-identity
---

# B-130 — fix: a lapsed sign-in draws the guest's header, not the customer's

Found by the catalogue's run on v0.5.0, TC-SIGN-08 step 2 ([run](../qa/runs/2026-10-12-v0.5.0.md)), on a local stack at 1440. Maya on
`/account/orders`; the next parts request answered 401 and the realm's token endpoint `400 invalid_grant`; she
pressed «Delivered». The body was taken down to «This page needs a sign‑in · Sign in to continue», the tokens left
`sessionStorage`, `haul.guest` stayed the one before and no `POST /api/v1/guests` went out — B-115's half holds.
But no `GET /ui/header` followed: the header still read «Maya» with her menu («Account», «Sign out») and «Cart 3»,
while the tab was a guest — its cart opened «Your cart is empty». Requests seen: `GET /ui/parts/account/orders?
status=delivered 401` → `GET /api/v1/sign-in` → token `400` → the parts again `401` → `GET /ui/account/orders
401`, nothing for the header. Whether v0.4.0 drew the guest's header here was not recorded.

- **A session that ends asks the header anew, however it ended.** Sign-out already does (TC-ERR-07 step 4); a
  renewal the provider refused is the same change of who is looking. Rejected: blanking the header — the guest has a
  header of their own (cart, sign-in).

- AC: the walk above ends with the guest's header (no name, «Sign in», the guest's cart count) asked once, the
  sign-in prompt in the body; signing in from it brings Maya's header back. A client test holds it and fails on
  today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
