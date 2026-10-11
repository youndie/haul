---
id: B-105
title: "client: a page opened directly knows who is signed in"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-105 — client: a page opened directly knows who is signed in

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk (phone). Reloading `/account/orders/HL-99999`, `/p/p-does-not-exist` or `/c/no-such-category` as
Maya draws the guest header («Sign in», no cart count); «Sign in» goes to the sign-in page, which sees she is signed in
and sends her back. B-67's `SHELL_HEADER` is the guest's; its category words all lead to `/c`.

- AC: before any tree, the shell's header reflects the session (account slot, a known cart count or none); category
  words lead to their category once the client knows them; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.

## Done (2026-10-11)

- **Decision: the server draws the header, the client asks for it.** `GET /ui/header` (public, `Frame.HEADER`,
  `server/.../feature/catalog/CatalogRouting.kt`) answers the header alone, exactly as every page draws it for
  whoever asks: the customer's name, the cart's count, the category words with their addresses, «HAUL PLUS» as
  the offer makes it. The shell asks for it when a page came with no tree — not there, refused, an error, the
  sign-in prompt — and no header has been drawn for who is looking (`AskForHeader`, `shell/ShellHeaders.kt`),
  once per visit and load; an answer that does not arrive leaves the header the client has. The sign-in page
  asks for none: it is a way through, a customer is sent on at once, and B-66 kept it free of server requests.
  Research §2, «Decided in B-105», has the rejected options (the header in the `404`'s body, the last header kept
  in browser storage, the name read off the token).
- **Before any answer, the client's header follows the session.** `SHELL_HEADER` stays the guest's;
  `CUSTOMER_SHELL_HEADER` is a customer's: the account slot reads «Account» and opens the account's menu
  («Account», «Sign out»), no count, «Orders» and «Saved» straight to them, «HAUL PLUS» to the account. Category
  words stay the catalog's root until the server's header names them.
- **Found on the way, the same header**: a header was kept whoever was looking. Signing out over a page with no
  tree drew the page again under the last tree's header, which still named the customer. `ShellHeaders` keeps
  each header — a tree's or the header alone — with whether the shopper was signed in when it was drawn
  (`SessionControls.signedIn`), and draws it only for that shopper; the page asks again.
- **Not changed**: placeholders and error pages still draw the header as a placeholder (B-67, the artboards'), so
  no golden moved. The header carried by an `update` (a card's «+») is still not what the shell keeps; the next
  tree is.
- **Tests**: `KnownHeaderTest` (5): the three addresses of the QA walk as Maya — her name, her count, «Electronics»
  to `/c/electronics`, no sign-in; before the header answers, the slot «Account» with «Sign out», «Go to your
  orders» straight to them; a guest on a missing product gets her count and the category's address; a sign-out
  over a missing product draws the guest's header asked anew, and the client's own guest header when that does
  not arrive. Server: `HomeRoutesTest` (the header alone is home's, «Electronics» to its category),
  `IdentityRoutesTest` (`/ui/header` among the screens whose header greets whoever is looking: nobody, a guest's
  count, Maya's). `SignInPromptTest` counts the page's loads and no longer counts the header asked beside them.
- **Mutations**, each red then restored, `git status` clean: the ask does nothing → all five; the header kept
  whoever looks → the sign-out test whose guest header does not arrive; the shell asked as if it knew whoever
  looks → both sign-out tests; no customer header → «before the header answers»; a tree's header tagged a guest's
  → both sign-out tests; the route gone → `HomeRoutesTest` and `IdentityRoutesTest`.

