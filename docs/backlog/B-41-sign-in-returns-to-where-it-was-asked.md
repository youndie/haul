---
id: B-41
title: "client: sign-in returns to where it was asked for"
status: done
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
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInTapTest.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/cart/CartWiringTest.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/StorefrontTest.kt`.

## Findings (2026-10-08)

- **The rule.** `SignInActions.next` reads the action's `next`, decoded once, and returns it only when
  `StorefrontPage.of` has a page for its path (the query is kept) and that page is not `/sign-in`
  itself — the same allow-list the server serves the page at (B-36). Everything else is refused and the
  shopper stays where they were: an absolute URL, `//host` and `/\host` (a browser reads both as
  another site), a scheme (`javascript:`), a path with no page (`/nowhere`, `/checkout/pay`,
  `/ui/checkout`, `/api/...`), a double-encoded `//`, a leading space, a query that does not decode.
  «Starts with a slash» — what `Navigator.open` checks — would have let `//host` through.
- **When it is followed.** Only after a sign-in that went through: a closed popup, a server without
  sign-in, or a merge that failed (`Identity.signIn` throws) opens nothing, and the screen is drawn
  again as before. A `next` that is the page already shown draws it again in place, since opening the
  address being shown does nothing and the header would keep «Sign in». Without a `next` (the header's
  «Sign in») nothing changed: the screen is drawn again.
- **The server's `next` is right as it is**: the guest cart's «Sign in to check out» is
  `/sign-in?next=%2Fcheckout` (`CartScreen.SIGN_IN`), and `/checkout` is a `StorefrontPage`. No server
  change.
- **Tests and where they ran** (the Linux build machine): `SignInTapTest` +3 (`next` opened after a
  sign-in; what a `next` may be; nineteen refused values, three actions without a `next` and a query
  that does not decode, with a positive control) and its failed-sign-in test now also checks that
  nothing opens, `CartWiringTest` +2 (in the storefront, the guest cart's «Sign in to
  check out» lands on `/checkout` — history `/cart`, `/checkout`, trees `/ui/cart`, `/ui/checkout`; a
  sign-in that does not go through stays on the cart), `StorefrontTest` +2 (a `next` that is the page
  shown redraws it; a `next` naming another site stays on the page). Mutation-checked, each against
  the three classes: `next` ignored (the checkout landing and the unit test fail), the allow-list
  replaced by «starts with `/`» (the refusal test and the other-site test fail), `/sign-in` allowed as a
  `next` (the refusal test fails), the sign-in's outcome ignored (both «does not go through» tests
  fail), the same-page branch removed (its test fails), the decode failure not caught (the refusal
  test fails on `%zz`); nothing else went red. The gate, rebased on B-22: `check :server:installDist` (server 188
  tests, client 102, `viddikVerify`, ktlint) and `:composeApp:wasmJsBrowserDistribution` on its own;
  `make check` and `make docs-against BASE=origin/main` on the Mac.
- **Not walked in a browser**: the popup needs a shildik realm, as in B-12; the wiring from the tree's
  action through `Shown` to the browser history is what `CartWiringTest` and `StorefrontTest` drive.
