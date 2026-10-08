---
id: B-44
title: "client: a guest on a customer page is sent to sign-in"
status: wip
priority: P2
size: S
stage: stage-5-order
blocked_by: [B-41]
---

# B-44 — client: a guest on a customer page is sent to sign-in

A guest who opens a customer-tier page directly — `/checkout`, `/account`, an order page — gets the
tree's `401`, and the shell draws «… didn't load» with Retry, which never helps. Found by the docs sync
after B-15…B-42.

Decided as product owner: a `401` on a storefront page is not an error page — the client starts sign-in
with `next` set to that page (B-41's rule: only a `StorefrontPage` address), and a sign-in that does not
go through leaves the shopper on the home page's guest view rather than a dead error.

- AC: opening `/checkout` as a guest starts sign-in and, once signed in, lands on `/checkout`; a cancelled
  sign-in lands on `/`; a `401` on a page refresh while signed in (an expired session) does the same.
  Client tests drive the whole `Storefront`.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
