---
id: B-47
title: "client: a sign-in that fails with a browser error ends as not gone through"
status: done
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-46]
---

# B-47 — client: a sign-in that fails with a browser error ends as not gone through

`SignInActions.attempt` catches `Exception`. On wasm a JavaScript error — a fetch that fails during the token
exchange — reaches Kotlin as a `Throwable` that is not an `Exception`, so it escapes the launched press: the
shopper stays on the prompt with nothing happening, and B-44's «cancelled → `/`» never runs.
`Storefront.answered` already catches `Throwable` for this reason. Found by B-46, from the code.

- AC: a sign-in that fails with a non-`Exception` `Throwable` ends as «did not go through» (`cancelled`), and
  a `CancellationException` is still rethrown; a client test with a flow failing that way, mutation-checked.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/PopupSignInFlowTest.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/SignInTapTest.kt`.

## Findings (2026-10-09)

- **The fix.** `SignInActions.attempt` catches `Throwable` after `CancellationException` (rethrown) and
  `SignInPending`, as `Storefront.answered` and `HaulTransport.tree` do: whatever fails inside the sign-in —
  the settings request, the provider's token exchange, the cart merge — ends as `cancelled`.
- **Tests and mutations** (`:composeApp:desktopTest`, the identity tests, 27 each run, on the Linux build
  machine). `PopupSignInFlowTest.a token exchange that fails with a browser error ends the sign-in as
  cancelled`: the fake of the provider's flow fails with a `Throwable` that is no `Exception` (the desktop
  has no JavaScript error, so a subclass of `Throwable` stands in for one); the press ends as `cancelled`
  within a second, opens nothing, closes its popup, and the next press opens a popup of its own.
  `SignInTapTest.a sign-in whose press is cancelled is rethrown and ends nothing`: the wider catch still
  lets a `CancellationException` through. Catching `Exception` again turned only the first red («the
  browser's error escaped the press», `cancelled` 0); dropping the cancellation rethrow turned only the
  second red. Not walked in a browser: making a fetch fail mid-exchange on purpose needs a stand, and the
  catch is the same shape as the two already walked (B-35, B-46).
- **Other catches looked at.** `PopupSignInFlow` catches nothing: its `try`/`finally` lets the provider's
  failure through to `SignInActions`, which is now where every sign-in failure ends. `SignInPrompt`'s press
  goes through `SignInActions.handle`. `Identity.renewed` still catches `Exception`, on purpose not changed:
  it is no press but the answer to a `401`, and a refresh whose fetch fails in the browser escapes to the
  request — a screen drawn as unreachable (`HaulTransport.tree`), a command that got no answer
  (`Storefront.answered`) — with the customer's tokens kept; catching it would sign a customer out over a
  network blip. A merge that fails after the tokens are stored ends as `cancelled` while the shopper is
  signed in — already a quirk of feature-identity for a refused merge, now for a failed fetch too.
