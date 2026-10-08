---
id: feature-identity
title: Sign-in, guests and the guest cart
type: feature
status: active
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries: []
api:
  - endpoint-identity
tags: []
---

# Sign-in, guests and the guest cart

## 1. Overview

A shopper browses and fills a cart without an account; checkout, saving, reviews and the account need a sign-in through shildik (OIDC). Signing in keeps what the guest put in the cart.

No screen of its own: the header's account slot is the entry («Sign in» for a guest, the first name
for a customer), and `/sign-in` is an action the client handles rather than a page.

## 2. Business rules

* the client asks the server for a guest id on the first request that needs one, keeps it, and sends it as `X-Haul-Guest` with every request until sign-in; a request carries the bearer token or the guest id, never both;
* a cart request without a guest id the server issued is `401 unauthenticated`: the client creates a new guest rather than the server inventing one (research D5, «Decided in B-11»); a screen request with an unknown guest id is answered as for nobody (header count 0);
* a request with both a valid bearer and a guest id acts as the customer; the guest id is used only by the merge;
* on sign-in the guest cart is merged into the customer's: same `Sku` → quantities summed and capped at 10 and at stock (never below 1); the guest's other lines are appended after the customer's; the customer's promo code is kept, else the guest's comes along; the guest cart is then deleted, the guest id kept;
* the first authenticated request of an unknown `sub` creates the `Customer` from the token's `name` claim — or, without one, the e-mail's local part, or else the `sub`; a known customer keeps the name they were created with;
* a token must verify against the realm (signature, lifetime, issuer) and be issued to the storefront's client (`azp`); one that does not is `401`, also on the public routes — a lapsed sign-in is told, not shown as a guest;
* the client answers a `401` once: a customer's token is renewed with the refresh token (shildik's access token lives five minutes), or the customer is signed out back to the guest; a guest the server forgot is replaced; then the request is sent again;
* with sign-in off (no `HAUL_OIDC_ISSUER`/`HAUL_OIDC_CLIENT_ID`) every bearer is refused and `GET /api/v1/sign-in` is `503`;
* after a sign-in that went through, the client opens the action's `next` (`/sign-in?next=%2Fcheckout`) — decoded once, its query kept — **only** when it is a storefront address, a path `StorefrontPage` has a page for, and not `/sign-in` itself (`SignInActions.next`, B-41); an absolute URL, `//host` or `/\host`, a scheme (`javascript:`), a path with no page (`/nowhere`, `/ui/…`, `/api/…`) or a query that does not decode is ignored and the screen is drawn again; a `next` that is the page already shown draws it again in place; a sign-in that was cancelled or failed (a closed popup, a server without sign-in, a merge that failed) opens nothing;
* **a guest on a customer page** — `/checkout`, `/account`, `/account/orders`, an order, `/account/saved` opened directly — gets the tree's `401`, which the storefront draws as the sign-in prompt (`SignInPrompt`, B-44): the page's last header (or the shell's own), «Checkout needs a sign-in», «This order needs a sign-in» or «This page needs a sign-in», and **«Sign in to continue»**, whose press is the sign-in with `next` the page itself (`SignInActions.returningTo`, B-41's allow-list included); signed in → the page loads again in place (no new history entry); not gone through → `/`. A **press** starts the sign-in, not the page's arrival: a browser blocks a popup no click asked for (decided in B-44; no artboard draws this state, it reuses the not-found page's layout);
* no loop: a `401` on the load a sign-in from this page asked for draws the page's own error, whose Retry asks no further sign-in; a later lapse is a new load and asks again. A lapsed session (renewal refused, signed out to the guest) shows the prompt on a page load, and a refresh in place refused with `401` loads the page again, which shows it;
* **the popup is the storefront's** (B-46): it is opened blank under the name `haul-sign-in` from the press, before the provider's discovery, and the OIDC library is told to reuse it (`WebCodeAuthFlowFactory(windowTarget)`); the press watches it every 250 ms and ends as «did not go through» when it has been seen closed without the return page's answer on two polls in a row — within a second; a popup the browser blocks ends the same way before the provider is asked; a press while a sign-in is pending focuses that popup and opens no other; the popup is closed whenever its sign-in ends;
* whatever fails inside a sign-in — the settings request, the provider's token exchange, the merge, a browser error that is not an `Exception` — ends it as «did not go through» (`SignInActions.attempt` catches `Throwable`, a cancellation rethrown; B-47); `Identity.renewed` still catches `Exception` only, on purpose: a refresh whose fetch fails reaches the request as unreachable with the tokens kept, rather than signing a customer out over a network blip.

## 3. Flow

1. The header's account action is `/sign-in`; `SignInActions` claims it before navigation.
2. The client reads `GET /api/v1/sign-in` (public) → `SignInSettings`.
3. Authorization code with PKCE in a popup on shildik's page; the popup returns to
   `signed-in.html` beside the bundle, which posts its URL to the opener and closes. The token
   exchange runs in the page (end-user credential).
4. `POST /api/v1/me/cart/merge` with the bearer and `X-Haul-Guest` (customer tier) → `refresh`; the
   client opens the action's `next` when it is a storefront address, otherwise the screen is redrawn
   with the customer's header.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/` — the contract (`GuestDto`, `GUEST_HEADER`, `SignInSettings`) |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/identity/` — guests, customers, `Callers`, the sign-in check (`SignIn.kt`); the merge in `server/src/main/kotlin/io/github/youndie/haul/feature/cart/domain/CartCommands.kt`; the header per caller in `server/src/main/kotlin/io/github/youndie/haul/shell/Viewers.kt` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/` (`Identity`, `IdentityApi`, `SignInActions`, `PopupSignInFlow`), `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/feature/identity/` (`BrowserSessionStore`: guest id in `localStorage`, tokens in `sessionStorage`; `OidcSignInFlow`; `BrowserSignInPopup`), `composeApp/src/wasmJsMain/resources/signed-in.html`; the prompt `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt` |

## 5. Scenarios (BDD / test cases)

### Scenario: Guest cart survives sign-in
* **Given:** a guest with 1 × Stoneware Mug in the cart and a customer whose cart holds 1 × the same mug
* **When:** the guest signs in
* **Then:** the customer's cart holds 2 × the mug and the guest cart no longer exists.
* **Automated:** `IdentityRoutesTest.a guest cart survives sign-in` (`server/src/test/kotlin/io/github/youndie/haul/feature/identity/IdentityRoutesTest.kt`, against shildik in Testcontainers); through a realm a stand runs, over HTTP, `WholePathTest.a shopper browses buys receives and returns an order over HTTP` (`e2e/src/test/kotlin/io/github/youndie/haul/e2e/WholePathTest.kt`)

### Scenario: Account needs a sign-in
* **Given:** no bearer token
* **When:** the client opens `/ui/account`
* **Then:** the server returns `401` with `unauthenticated`.
* **Automated:** `IdentityRoutesTest.the account needs a sign-in`

### Scenario: A guest opening a customer page is asked to sign in
* **Given:** a guest who opens `/checkout`, or an order's address, directly
* **When:** they press «Sign in to continue» and the sign-in goes through
* **Then:** the page loads in place; a sign-in that does not go through lands on `/`; a page still refused after the sign-in draws its error and asks no more.
* **Automated:** `SignInPromptTest.a guest opening the checkout signs in and lands on the checkout`, `SignInPromptTest.a guest opening an order signs in and lands on the order`, `SignInPromptTest.a sign-in that does not go through lands on the home page`, `SignInPromptTest.a page still refused after a sign-in draws the error page and asks no more`; a lapsed session, `SignInPromptTest.a lapsed sign-in that cannot be renewed asks again and lands on the page`, `SignInPromptTest.a refresh refused after the sign-in lapsed asks again and draws the page` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInPromptTest.kt`)

### Scenario: A closed popup settles the sign-in
* **Given:** a sign-in pending in its popup
* **When:** the shopper closes the popup, or the browser blocks it, or presses again
* **Then:** a closed or blocked popup ends the sign-in as not gone through within a second; a second press opens no second popup.
* **Automated:** `PopupSignInFlowTest.a popup the shopper closes ends the sign-in as cancelled within a second`, `PopupSignInFlowTest.a blocked popup ends the sign-in as cancelled without starting the provider`, `PopupSignInFlowTest.a second press while a sign-in is pending focuses its popup and opens no other`; a browser error in the token exchange, `PopupSignInFlowTest.a token exchange that fails with a browser error ends the sign-in as cancelled` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/PopupSignInFlowTest.kt`). Walked in headless Chrome against a shildik realm (B-46): closed → `/` 529 ms after the close.

### Scenario: Sign-in returns to where it was asked
* **Given:** a guest on the cart
* **When:** they press «Sign in to check out» (`/sign-in?next=%2Fcheckout`) and the sign-in goes through
* **Then:** the storefront lands on `/checkout` (history `/cart`, `/checkout`); a sign-in that does not go through stays on the cart.
* **Automated:** `CartWiringTest.in the storefront a guest's Sign in to check out lands on the checkout`, `CartWiringTest.in the storefront a sign-in that does not go through stays on the cart` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/CartWiringTest.kt`)

### Scenario: A next that is not a storefront address is not followed
* **Given:** a sign-in action whose `next` names another site (`%2F%2Fevil.example%2Fcheckout`), a scheme or a path with no page
* **When:** the sign-in goes through
* **Then:** nothing is opened and the page shown is drawn again.
* **Automated:** `SignInTapTest.a next that is not a storefront address is refused` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInTapTest.kt`), `StorefrontTest.a sign-in whose next is another site stays on the page` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/StorefrontTest.kt`)

The server's rules are covered in `IdentityRoutesTest` (customer creation, a token with a guest id, the header on
every screen, a token that does not verify, the merge's errors, the settings route on and off), in
`server/src/test/kotlin/io/github/youndie/haul/feature/cart/CartMergeTest.kt` (caps and the promo
code) and, on the client, in
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/IdentityTest.kt` and
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInTapTest.kt`. The
browser popup itself was walked by hand in headless Chrome, not by a test (B-12's findings).

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
* A visible sign-out: no artboard draws one; `Identity.signOut` exists for a renewal that fails.
* `429 rate_limited` on guest creation: nothing limits it.

## 7. Quirks

* A guest id the server forgot (a reset database) is replaced only by a cart command: screens accept
  an unknown guest as nobody, so the header counts 0 until the shopper's first cart action.
* `503 unavailable` on `GET /api/v1/sign-in` means «sign-in is not configured», while the same code
  elsewhere means «a dependency is down» (B-12's findings).
* The desktop app's own browser pane opens the popup in the same tab, so the flow cannot finish
  there; a browser that opens popups as windows is fine.
* A cart merge that fails after the tokens are stored leaves the shopper signed in with no message;
  since B-41 that is a redraw of the page, not the `next` (B-41's findings; predates it) — since B-47
  a failed fetch there too.
* kotlin-multiplatform-oidc 0.18.4's web popup flow waits only for the return page's message, in a
  wait that ignores cancellation; the storefront's own popup handle is the workaround (B-46), and a
  report to the library is drafted in B-46's findings, not filed.
* The header's «Sign in» is inert on shell-drawn pages (not found, the sign-in prompt); the prompt's
  button is the way in there.
* The press reaches `window.open` after `GET /api/v1/sign-in` the first time, inside the browser's
  transient-activation window (about five seconds; Safari's is likely stricter); held past it, the
  popup is blocked and the sign-in ends as not gone through.
