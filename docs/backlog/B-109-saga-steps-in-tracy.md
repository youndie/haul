---
id: B-109
title: "server: an order's saga is visible in tracy"
status: open
priority: P2
size: S
stage: stage-11-qa
---

# B-109 — server: an order's saga is visible in tracy

Found in the QA pass of 2026-10-10 (v0.3.0). With the synthetic shoppers walking the stand (B-31), tracy shows the HTTP side — `PUT /api/v1/cart/lines`,
`POST /api/v1/me/cart/merge`, `POST /api/v1/orders` 202, the order page polled — but no span or log for the saga's steps
(reserve, charge, fulfil, refund): `search_spans` with `saga` finds nothing and `top_templates` for `haul-server` is
empty. B-31's AC («sagas … in tracy») needs them.

- AC: each saga step is a span (or a log line with the order id as an entity key) under the order, so `get_entity` on an
  order id shows placement → delivery → return in tracy; checked on the stand.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/`.
