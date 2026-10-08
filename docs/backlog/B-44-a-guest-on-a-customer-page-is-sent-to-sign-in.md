---
id: B-44
title: "client: a guest on a customer page is sent to sign-in"
status: done
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
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInPromptTest.kt`.

## Findings (2026-10-08)

- **A press starts the sign-in, not the page's arrival — a deviation from «starts sign-in».** The
  sign-in is a popup (B-12), and a browser blocks a popup no click asked for: `window.open` without a
  user activation answers `null`. kotlin-multiplatform-oidc 0.18.4's `WebPopupFlow` then waits for a
  message from a popup that never opened, so a sign-in started on arrival would leave the page hanging
  with nothing on it. So a `401` on a storefront page draws `SignInPrompt`: the page's own header (or
  the shell's), «This page / Checkout / This order needs a sign-in» and **«Sign in to continue»**,
  whose press is the sign-in a tree's `/sign-in?next=` starts — `SignInActions`, with `next` the page
  itself (`SignInActions.returningTo`), B-41's allow-list included. No artboard draws this state; it
  reuses the not-found page's layout.
- **After the press.** A sign-in that went through loads the page again in place (no new history
  entry); one that did not — `Identity.signIn` threw — opens `/` (`SignInActions`' new `cancelled`,
  which defaults to the old redraw, so the header's and the cart's sign-in behave as before).
- **No loop.** The shell counts the page's loads; a `401` on the load a sign-in from this page asked
  for is drawn as the page's error (Checkout_Error for the checkout), and its Retry asks for no
  sign-in either. A later lapse on the same page asks again, since that is a new load.
- **A lapsed sign-in.** `Identity.send` already renews a customer's token once and signs the customer
  out when it cannot; the guest's retry of a customer page is `401` too, and that is what reaches the
  shell. A page load (a reload, a link) shows the prompt; a refresh in place — kompot's `refresh`, a
  command's answer — used to fail quietly and leave the signed-out shopper on a stale customer page,
  and now loads the page again, which shows the prompt.
- **Not walked in a browser**: the popup needs a shildik realm, as in B-12 and B-41. Two things only
  a browser would show, recorded rather than fixed here: from the library's symbols its popup flow
  resumes on the redirect page's message alone, so a popup the shopper *closes* may leave the sign-in
  pending rather than failing (then «does not go through → home» is what the tests drive, not what a
  browser does); and the press reaches `window.open` only after `GET /api/v1/sign-in` (first time) and
  the provider's discovery, inside the browser's transient-activation window (about five seconds in
  Chrome and Firefox), as the header's «Sign in» always has.
- **Tests and where they ran** (the Linux build machine): `SignInPromptTest`, six storefront tests
  driving the whole `Storefront` — a guest at `/checkout` and at an order (B-18's address) is asked
  and lands on the page; a sign-in that does not go through lands on `/`; a page still refused after a
  sign-in draws Checkout_Error and its Retry asks no more; and over the real `Identity`, `ktorTransport`
  and a mock server, a lapsed sign-in the provider will not renew asks again on a load and on a refresh
  in place, and lands on the page. Mutation-checked: the prompt off (all six red), the loop guard off
  (the no-loop test), no reload after the sign-in (four), a cancel that redraws instead of going home
  (in the prompt, and in `SignInActions`: the home test), a refresh `401` ignored (the refresh test),
  the order's wording dropped (the order test); `CartWiringTest`, `SignInTapTest` and `StorefrontTest`
  stayed green under every mutation. The gate, rebased on B-18: `:composeApp:wasmJsBrowserDistribution`
  on its own, then `check :server:installDist` (server 207 tests, client 118, `viddikVerify` 106,
  ktlint); `make check` and `make docs-against BASE=origin/main` on the Mac.
