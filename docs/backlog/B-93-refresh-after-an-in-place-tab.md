---
id: B-93
title: "client: a press after switching a product tab redraws the page"
status: open
priority: P1
size: S
stage: stage-11-qa
---

# B-93 — client: a press after switching a product tab redraws the page

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk at 1366. On a product page: Description → Reviews (or Questions), then «Helpful», posting a
review or asking a question. The command answered 200/201 and the follow-up `GET /ui/p/…&tab=reviews` returned the new
tree («1 person found this helpful», «2,342 reviews», questions 87), but the screen kept the old tree until a reload.
On a freshly loaded page the same presses redraw.

Likely cause (read, not verified): in `Shown` (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` ~510-525) `refresh` is `remember(address)` and
writes into the `tree` state made by `remember(loaded)`; an in-place navigation changes the address before the new
`loaded` arrives, so the refresh writes into a state no longer drawn.

- AC: a client test (switch tab in place, then a `refresh`-answered press → the new tree is drawn), red before; the
  same for every in-place navigation (filters, pages, scoped search).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
