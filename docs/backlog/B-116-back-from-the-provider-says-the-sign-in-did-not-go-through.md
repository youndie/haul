---
id: B-116
title: "fix: back from the provider's page says the sign-in did not go through"
status: done
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-identity
---

# B-116 — fix: back from the provider's page says the sign-in did not go through

Found by the catalogue's first run, TC-SIGN-04 step 3 ([run](../qa/runs/2026-10-11-v0.4.0.md)). At 1440 a
guest pressed «Orders» (the popup was blocked), then «Sign in on this page»; the tab went to
`haul-id.kotlin.website/…/authorize`, and the browser's back landed on `/sign-in?next=%2Fcart` showing the
plain «Sign in to HAUL». The page has the right state — «The sign-in didn't go through» (`SignInPage.kt`,
`Failed`) — but reaches it only when the provider redirects back with an error; `sessionStorage` still holds
`haul.sign-in.request` and `haul.sign-in.next` after the back, so the page can know a sign-in on this page was
started and never finished.

- **A pending in-tab sign-in with no answer from the provider is a sign-in that did not go through.** The page
  that finds the pending request and no `code`/`error` in its address says so and offers another try, then
  forgets the request. Rejected: leaving the plain page — the shopper pressed sign-in, went somewhere and came
  back to the same button with no word about what happened.
- Not covered: a provider refusal (the case's other half), which already lands on `Failed`.

- AC: Orders → «Sign in on this page» → browser back shows «The sign-in didn't go through» with a way to try
  again; pressing it starts a fresh sign-in; a plain visit to `/sign-in` with nothing pending is unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPage.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInHere.kt`.

## Findings

- **The answer never travels in `/sign-in`'s address.** The provider returns to `signed-in.html`, which keeps
  the address beside the request (`haul.sign-in.answer`) and goes on to a bare `/sign-in`. So «no `code`/`error`
  in its address» is, in this storefront, a request (`haul.sign-in.request`) with no answer beside it:
  `BrowserSignInHere.forgetUnanswered` reads exactly that, forgets the request and its `next`, and
  `SignInPage` starts in `Failed` when it says there was one. A plain `/sign-in` with nothing pending still
  starts in `Asking`.
- **Back arrives two ways, and both are real.** In headless Chrome 149 (CDP, popups made to fail by
  `window.open = () => null`, a stub provider on another origin) the back off the provider's page restored
  the storefront from the back-forward cache three times in three (a marker on `window` survived, `pageshow`
  fired with `persisted: true`): no load, no Compose start, the page that left shown again in «The browser
  blocked the window». Its sign-in was still suspended in `leave`, which now waits for that `pageshow`,
  forgets the request and throws `SignInNotAnswered`, and the page turns to «The sign-in didn't go through».
  When the back came later, or the page had run JavaScript while cached, Chrome reported
  `backForwardCacheNotUsed: JavaScriptExecution` and loaded the page anew — the first way. The app's browser
  pane never restores from the cache (`notRestoredReasons: masked`), which is why the run saw a new load.
- **Walked in a browser:** the app's pane and headless Chrome, against the built bundle with a stub sign-in
  API and a stub provider that never answers (and refuses on request): blocked → «Sign in on this page» →
  provider → back shows «The sign-in didn't go through» with the request gone from session storage; a reload
  then asks as before; its «Sign in on this page» leaves with a new `state`; a refusal still lands on the
  same page.
- Not changed: a request left by a shopper who went from the provider's page somewhere other than back (typed
  another store address) waits until the tab's next `/sign-in`, which then says the sign-in did not go
  through. And a request forgotten on the way back is gone if the shopper then goes forward to the provider's
  page and signs in there: `signed-in.html` finds nothing waiting and stays on its blank page.
