---
id: B-47
title: "client: a sign-in that fails with a browser error ends as not gone through"
status: wip
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
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`.
