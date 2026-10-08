---
id: B-46
title: "client: a closed sign-in popup settles the sign-in"
status: done
priority: P2
size: S
stage: stage-4-cart
blocked_by: [B-44]
---

# B-46 — client: a closed sign-in popup settles the sign-in

B-41 redraws when the popup is closed and B-44 lands on `/` when a sign-in does not go through — both
assume the sign-in ends when the shopper closes the popup. From the OIDC library's symbols, its web popup
flow resumes only on the redirect page's message and does not watch `popup.closed`, and a popup the
browser blocks returns `null`: either way the sign-in would stay pending, a second press would open a
second popup, and neither «cancelled» path would ever run in a browser. Found by B-44, from the library's
code, not walked.

Decided as product owner: a closed or blocked popup ends the sign-in as «did not go through» within a
second, and a press while a sign-in is pending focuses the open popup rather than opening another.

- AC: walked in a browser against a shildik realm first — closing the popup, and a blocked popup —
  recording what the library does. If it hangs: the client watches the popup and settles the sign-in
  itself (a client test with a fake popup), and the finding is reported to the library with the user's
  consent. If it does not hang: B-41's and B-44's paths are confirmed and the item closes with that record.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/PopupSignInFlow.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/feature/identity/BrowserSignInPopup.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/feature/identity/OidcSignInFlow.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/identity/PopupSignInFlowTest.kt`.

## Findings (2026-10-08)

- **Walked, before the fix** (main at `1155b0d`, kotlin-multiplatform-oidc 0.18.4, Chrome 152 headless
  driven over the DevTools protocol, shildik-sqlite 0.4.1 with a `haul` realm, the server's
  `installDist` serving the production bundle; a guest at `/checkout`, B-44's «Sign in to continue»
  pressed with a real click):
  - **closed popup — hangs.** The popup reached shildik's page and was closed as a window; five
    seconds later the page was still `/checkout`, so B-44's «go home» never ran, and the next press
    opened a second popup.
  - **two presses — two popups**, both open.
  - **blocked popup — does not hang.** Made honestly by holding the provider's discovery for six
    seconds, past the click's user activation: `window.open` answered `null`, and the page went to `/`
    35 ms later. The item's premise was half right: `WebPopupFlow` throws `Could not open popup` from
    inside its `suspendCoroutine` block, which reaches the caller at once.
  - a normal sign-in went through in about 0.3 s after the form was posted.
- **The library's source** (`oidc-appsupport/src/webMain/.../appsupport/WebPopupFlow.kt` at tag 0.18.4,
  unchanged on `main`): the flow waits in `suspendCoroutine` (line 31) that only the return page's
  message resumes (lines 43–47); nothing looks at `popup.closed`, and `suspendCoroutine` does not give
  in to cancellation, so a `withTimeout` around it would hang too. A blocked popup throws from line 58
  and leaves the `message` listener of line 55 registered. `WebAuthenticationFlowResult.Cancelled`
  exists but the web flow never returns it. No published sources jar was in any Gradle cache; the
  source was read from the tagged tree.
- **The fix**, behind the existing `SignInFlow` seam: `PopupSignInFlow` (commonMain) opens the popup
  itself — blank, under the name `haul-sign-in` — and hands the provider's flow the same name
  (`WebCodeAuthFlowFactory(windowTarget = …)`), so the library navigates that window instead of opening
  its own and the storefront holds a handle to it (`BrowserSignInPopup`). The provider's flow runs
  detached from the press; the press polls the window every 250 ms and ends with
  `SignInPopupClosed` once it has seen it closed **without the return page's answer** twice in a row
  (the return page posts and closes in one breath, and the code exchange follows the close). A popup
  the browser refuses ends with `SignInPopupBlocked` before the provider is asked. A press while a
  sign-in is pending focuses its popup and throws `SignInPending`, which `SignInActions` answers with
  nothing (the first press answers); the window is closed when its sign-in ends any way.
- **Walked, after the fix** (same stand, branch bundle): a closed popup → `/` 529 ms after the close;
  two presses → one popup; a normal sign-in → signed in on `/checkout`, one window (the library reused
  `haul-sign-in`). Blocked: holding discovery no longer blocks anything — the popup now opens before
  discovery, inside the click's activation, which is a gain; holding `GET /api/v1/sign-in` (asked
  before the popup) for six seconds made `window.open` answer `null`, and the page went to `/` 30 ms
  later. Focus on the second press is not observable headless; the test covers it.
- **Tests and mutations**: `PopupSignInFlowTest`, five tests through `SignInActions` with a fake popup
  and a fake of the library's uncancellable wait — closed → `cancelled` within a second (then a new
  press opens a new popup); blocked → `cancelled`, provider never started; a second press → one
  popup, focused, nothing ended; answered then closed → signed in; a press gone → its popup closed.
  Each mutation turned exactly its test red (25 identity tests ran each time): no close check, the
  answer ignored, no single sign-in, `SignInPending` read as a failure, no close on the way out, a
  blocked popup falling through to the provider.
- **For the library's maintainers** (not filed; the owner decides). *Web popup sign-in never completes
  when the user closes the popup.* kotlin-multiplatform-oidc 0.18.4 (and `main`), `oidc-appsupport`
  wasmJs, any provider. Repro: `WebCodeAuthFlowFactory().createAuthFlow(client).getAccessToken()` from
  a click, close the popup — the call never returns, and cancelling its coroutine does not end it.
  Expected: the call ends — `WebAuthenticationFlowResult.Cancelled`, which `throwAuthenticationIfCancelled`
  already turns into an exception on the other platforms — within a short poll of `popup.closed`, and
  the wait is cancellable (`suspendCancellableCoroutine`, removing the `message` listener on every
  ending, including the blocked-popup throw). Workaround in use: a named `windowTarget` the app opens
  and watches itself.
