---
id: B-115
title: "fix: a guest who opens a customer's page by its address keeps the cart"
status: open
priority: P1
size: S
stage: stage-12-qa-run
epic: feature-identity
---

# B-115 — fix: a guest who opens a customer's page by its address keeps the cart

Found by the catalogue's first run, TC-SIGN-06 ([run](../qa/runs/2026-10-11-v0.4.0.md)). On the stand
(v0.4.0) a guest with the WH-1000XM6 in the cart («Cart 1») opened `/checkout` by its address: `GET /ui/checkout`
answered `401 unauthenticated`, the client then made `POST /api/v1/guests` (201) and asked `/ui/checkout` again
(401). `localStorage` `haul.guest` went from `g-dc0d65…` to `g-1bb654…`, the header lost its count, and the
cart was gone — under a prompt saying «Whatever is in your cart comes with you». `/account/orders/HL-48302`
does the same. The cause is `Identity.send()`: every `401` goes to `recover()`, which with no tokens sets
`guestId = null`, so the retry creates a new guest. A guest's `401` on a page only a customer may open is the
expected answer, not a guest the server forgot.

- **Replace the guest only when the server says it does not know it.** The server already tells the two apart
  by code or can (`unauthenticated` for «no token», a distinct code for an unknown or malformed guest id); the
  client recovers on the second, and on the first leaves the session alone and draws the sign-in prompt the
  tree answers with. Rejected: never replacing a guest — a stand reseeded under a browser would leave it with
  an id nobody knows and an empty cart that cannot be filled.
- **The same path after a lapsed sign-in.** The customer walk of the same run saw a refused refresh
  (`400 invalid_grant`) end in `POST /api/v1/guests` and a new `haul.guest`, while «Sign out» keeps the guest. With
  the tokens dropped, the retry's `401` goes through `recover()` again; the fix covers it, and a test holds that a
  lapsed sign-in leaves the shopper the guest they were.
- Not covered: the merge itself (TC-SIGN-02), which already works with the guest that reaches it.

- AC: a guest with a line in the cart opens `/checkout` and `/account/orders/<id>` by address, sees the
  sign-in prompt, and comes back to the cart with the line and the same `haul.guest`; a guest id the server
  does not know is still replaced once. A test of `Identity` holds both, and fails with today's `recover()`.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/CheckoutRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt`.
