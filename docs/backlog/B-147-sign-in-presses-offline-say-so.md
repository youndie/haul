---
id: B-147
title: "fix: a guest's sign-in presses offline say the store could not be reached"
status: done
priority: P3
size: XS
stage: stage-12-qa-run
epic: feature-identity
---

# B-147 — fix: a guest's sign-in presses offline say the store could not be reached

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walkers B and D independently). Offline, a guest's «Sign in»,
«Orders», «Saved», «HAUL PLUS» and the heart send `GET /api/v1/sign-in` → `ERR_INTERNET_DISCONNECTED`, then ask the
current page's tree again (also failing): no message, no Retry, nothing moves — on a loaded page and on a failed one.
Every other press offline says «Couldn’t reach the store».

- **A press that cannot reach the store says so**, as every other press does (B-92's message), with Retry where the
  press can be repeated.
- AC: offline, each of the presses above shows «Couldn’t reach the store»; online again, the press goes on as today; a
  client test.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`.

## Findings

- **Where it was swallowed.** `SignInActions.attempt` took every failure of a sign-in as one the shopper chose
  (B-47's `Throwable` catch): a closed popup, a server without sign-in and a request that never got an answer were
  one outcome, `cancelled`, which draws the page again. Offline that draw is another request with no answer, and
  it shows nothing (a screen's refresh drops a tree that does not arrive), so the press ended in silence — on a
  loaded page, on a page that had failed (`navigator.reload`), and on a customer's page's prompt, whose `cancelled`
  went home, offline too. The request itself (`IdentityApi.settings`, `GET /api/v1/sign-in`) could not be told from
  the rest: its failure arrived as whatever the fetch threw.
- **The decision.** No answer at all to that request is its own failure, `SignInUnreachable`, thrown by
  `IdentityApi.settings` around the fetch alone, as `HaulTransport.screen` does for a page (`ScreenFailed.Unreachable`).
  An answer that refuses — a `5xx`, a proxy's page — stays what it was (`IdentityFailure`, a sign-in that did not go
  through): the item is about the store not being reached, and a refusal is the store reached. `SignInActions` gives
  it its own outcome, `unreachable`, with the same press to send again; the shell's `Signing` answers it for every
  way in at once — the header's own presses, a tree's (the server's «Sign in», «Orders», «Saved», «HAUL PLUS», the
  heart, «Track an order», «Sign in to check out», «Helpful» for a guest), the prompt, the sign-in page — with B-92's
  message, `noAnswer`, over the page the press was on, and nothing drawn again. «Retry» is always offered: a sign-in
  press changes nothing in the store until the provider answers, so sending it twice cannot do it twice; B-136 keeps
  it until it is pressed or the page changes. Online, «Retry» is the press as it was: the popup opens from the
  message's own click, as it did from the first.
- **The sign-in page (B-145's finding, fixed here).** `Storefront` returned from the sign-in page's branch before
  `MessageHost` was drawn, so nothing raised there was seen — and its presses can be offline. `MessageHost` is drawn
  in that branch too. «Sign in on this page» offline (`Identity.signInHere` asks the same settings) said «The
  sign-in didn't go through», which is not what happened: it now leaves the page as it was under the same message,
  whose «Retry» tries to leave again.
- **Not changed.** The popup still opens for an instant before the request fails and is closed by the flow
  (`PopupSignInFlow` opens it first so the browser counts the click, B-66). Settings are kept once the store has
  answered them (`Identity.settings`): a guest who opened a sign-in online and pressed again offline goes straight
  to the provider's window, which shows the browser's own offline page — not what the run saw, and the provider's
  page is not the store's to word. The cart merge after a sign-in that went through offline mid-way is not touched.
- **Tests.** `OfflineSignInTest` runs the storefront over the real `Identity` and `PopupSignInFlow` against a mock
  engine that goes offline (it throws, as a fetch does): the header's «Sign in», «Orders», «Saved» on a page that
  failed offline, «HAUL PLUS», a card's heart, the prompt's «Sign in to continue», the sign-in page's «Sign in» and
  «Sign in on this page» — each shows «Couldn't reach the store» with «Retry», the page is not asked for again, and
  where the press goes somewhere «Retry» online goes there (the cart redrawn signed in, the orders, the Saved list,
  the checkout, the provider). `IdentityTest` «a store that does not answer where to sign in could not be reached,
  and nothing changes»; `SignInTapTest` «a sign-in the store did not answer is told as unreachable, and sent again
  goes on». The catalogue's TC-SIGN-09 holds the walk. Mutations, after the commit, each red then restored: the
  wrap in `IdentityApi.settings` removed (9 red: every new storefront test and the identity one — today's
  behaviour); the outcome removed from `SignInActions` (8 red); `MessageHost` not drawn on the sign-in page (its two
  tests red); «Sign in on this page» taking the request's failure as a failed sign-in (its test red); «Retry»
  sending nothing (the four tests that press it online red).
