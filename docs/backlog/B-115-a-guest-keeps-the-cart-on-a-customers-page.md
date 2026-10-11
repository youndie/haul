---
id: B-115
title: "fix: a guest who opens a customer's page by its address keeps the cart"
status: done
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

## Findings (2026-10-12)

- **What the server said before.** Every refusal a guest could meet was `401 unauthenticated`: no
  `X-Haul-Guest` on a cart route, a guest id the server never issued on a cart route (`Callers.of` answers
  `Nobody` for both), and a known guest on a customer-tier route (`/ui/checkout`, `/ui/account/orders/<id>`:
  Ktor's challenge, rewritten into the `ErrorBody` in `HaulModule.kt`). The client could not tell a forgotten
  guest from a guest on a customer's page, so `Identity.recover()` replaced the guest on all of them.
- **The signal: the existing `guest_not_found`, with `401` on the cart routes.** A cart route naming a guest
  the server does not know — never issued, gone after a reseed, or not an id at all — answers `401
  guest_not_found` (`IdentityError.UnknownGuest`, `CartRouting.refusal()`); no guest at all stays `401
  unauthenticated`. No new code: `guest_not_found` already meant «a guest the server never issued». Its
  status stays `404` on the merge, where the token is good and only the named guest is missing, so the
  one code has two statuses and `respondError` takes the status as an override for this case. The customer
  tier is unchanged: any guest there is `401 unauthenticated`, now pinned for an order too
  (`OrderRoutesTest`) beside checkout's (`CheckoutRoutesTest` «checkout needs a sign-in»).
- **The client** (`Identity.send`): on a `401` a customer's token is renewed at most once; with no token, the
  guest is replaced at most once and only when the body says `guest_not_found`; any other `401` is returned
  as it came, guest and cart kept, and the shell draws the prompt as before (B-44). A lapsed sign-in falls
  back to the guest the shopper was; if the server forgot that guest meanwhile, the same request replaces
  it once. A sign-in whose merge answers `404 guest_not_found` goes through and drops the guest — without
  that, a guest the server forgot (now kept on a customer's page instead of replaced) would make the sign-in
  from that page fail on the merge. The `401` body is read for its code; Ktor keeps a non-streaming body, so
  the shell reads it again (asserted in the test).
- **Tests.** `IdentityTest`: «a guest on a customer's page stays the same guest», «a lapsed sign-in leaves the
  shopper the guest they were», «a lapsed sign-in over a forgotten guest gets a new one», «signing in over a
  forgotten guest keeps the tokens and drops the guest», «a guest is replaced once per request», and «a guest
  the server forgot is replaced» now answered with `guest_not_found`. `CartRoutesTest` «no guest is 401
  unauthenticated and an unknown one 401 guest_not_found» (a malformed id included). Mutations, each red
  then restored with `git status` clean: `Identity.kt` from `origin/main` (today's `recover()`) → four of the
  new `IdentityTest` cases red (the once-per-request bound holds on both); the cart's refusal always
  `Unauthenticated` → the cart test red; the `401` override dropped → the cart test red on `404 Not Found`.
- **Not walked in a browser.** The AC's walk — a guest with a line opens `/checkout` and an order by address,
  then the cart — is held by the `Identity` test and the routes' tests, and written into TC-SIGN-06 for the
  next run on the stand; this iteration had no browser against a local stack.
