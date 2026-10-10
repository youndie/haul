---
id: B-66
title: "client: sign-in and sign-out work from every page and every way in"
status: wip
priority: P1
size: M
stage: stage-10-review
---

# B-66 — client: sign-in and sign-out work from every page and every way in

Found walking the stand on 2026-10-10. As a guest, «Sign in», «Orders», «Saved» and a card's heart did nothing visible:
each press fetched the current page again (`GET /ui/search?q=sony` three times in a row) and the page stayed as it was.
Sign-in runs in a popup (`PopupSignInFlow`); when the browser blocks the popup nothing tells the shopper, and there is
no fallback to a redirect.

The code audit adds:
- `/sign-in` and `/sign-in?next=…` have no `/ui` tree. They work only when the press goes through `SignInActions`.
  The shell's own header (`panelActions`, `Storefront.kt:516-517`) sends them to `navigator.open`, which loads
  `/ui/sign-in` and draws «This page isn't here». So do a reload of `/sign-in`, a shared link to it, and back or
  forward onto it.
- A non-member customer's HAUL PLUS (`PresentAction`) is ignored on those pages.
- There is no sign-out control anywhere: `Identity.signOut()` (`feature/identity/Identity.kt:68`) is never called
  from the UI.

- AC: from a guest's every entry point (header account, Orders, Saved, heart, «Sign in to check out», a reload of
  `/sign-in?next=…`, back onto it) the shopper either reaches the sign-in or sees why not (popup blocked → a message
  and a way that works); after signing in they land on `next`; a signed-in customer can sign out from the header
  or the account page; client tests for each.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/SignInActions.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/PopupSignInFlow.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.

## Findings (2026-10-10)

- **Why the stand's presses did nothing**: walked again in the desktop app's browser pane against the item's image and
  a local shildik 0.4.1, the pane blocks every popup. `PopupSignInFlow` threw `SignInPopupBlocked`, `SignInActions`
  answered it as `cancelled`, and `cancelled` on a tree is a redraw — the `GET /ui/search?q=sony` the walk saw three
  times. Nothing on the server was wrong.
- **`/sign-in` is the client's own page** (`shell/SignInPage.kt`, `PageKind.SignIn`), never a tree: a reload, a link,
  back or forward draw «Sign in to HAUL»; its press opens the popup and, once through, gives its history entry to
  `next` (`Navigator.redirect`); a signed-in customer arriving there is sent on at once. No server route was added:
  `StorefrontPage.SignIn` already served the page's HTML, and a tree would have had nothing to say the client does not.
- **A blocked popup is told, with a way that works**: `SignInActions` has a `blocked` outcome; the shell opens the
  sign-in page returning to where the press was («The browser blocked the window»), which offers «Sign in on this
  page» — the same code flow with PKCE in the tab itself (`BrowserSignInHere`, `signed-in.html` hands the answer on
  to `/sign-in` when no window opened it) — and «Try the sign-in window again». Walked end to end in the pane: the
  header's «Orders» → blocked page → shildik's form (a test shopper created on the local realm) → «Signing you in» →
  `/account/orders` as that shopper; then «Sign out» → home as a guest; a reload of `/sign-in?next=%2Faccount%2Fsaved`
  and back onto it from the provider's page drew the page.
- **The popup now opens before the server's settings are asked for** (`SignInFlow.signIn` takes them as a
  `suspend () -> SignInSettings`): the first press of a page load used to wait for `GET /api/v1/sign-in` first, which
  a stricter browser counts as outside the click.
- **One path for every press** (`Signing`): a tree's actions, the header the shell draws over a loading, error or
  not-found page (`panelActions`, which only followed links), the 401 prompt and the sign-in page. A guest's «Orders»
  and «Saved» now carry their `next` (`Frame.SIGN_IN_TO_ORDERS`, `SIGN_IN_TO_SAVED`; the cart's guest fixture follows
  the wire), so the sign-in lands on them; the account shortcut, the heart and «Save for later» keep a bare `/sign-in`
  and draw the page again, signed in.
- **Sign-out**: a customer's account slot opens a menu on both widths, «Account» and «Sign out» (`SIGN_OUT_ACTION`,
  the client's own `/sign-out`); `Identity.signOut()` is finally called. A customer's page is left for home, any other
  drawn again for the guest. shildik kept no session to sign back in from: the next sign-in asked for the password.
- **Left out**: a non-member's «HAUL PLUS» (`PresentAction`) on a page the shell draws itself is still ignored — the
  shell's `presented` state lives inside a screen; that header's other dead ends are B-67's.
- **Tests**: `SignInEverywhereTest` (nine, the storefront with a fake transport and session: reload, back and forward,
  a customer arriving, blocked → the page → «Sign in on this page», a tab sign-in coming back and one failing, the
  shell's own header, sign-out from a customer's page and from another), `PopupSignInFlowTest` (blocked is told as
  blocked and asks nothing; the popup opens before the settings and closes when they fail), `IdentityTest` (a tab
  sign-in keeps the tokens, merges, answers its next; one that failed changes nothing), `SignInTapTest` (blocked,
  sign-out). Server: `DrawnActionsTest`, `SavedRoutesTest`, `CartFixturesTest` on the guest's new `next`s.
- **Mutations**, each red then restored: the shell's header back to plain navigation; the sign-in page's branch off;
  the blocked catch removed; settings asked before the popup; «Sign out» left out of the menu; the tab sign-in not
  merged; `redirect` pushing instead of replacing; the customer's arrival not sent on; the guest's `next`s removed
  from `Frame`.
