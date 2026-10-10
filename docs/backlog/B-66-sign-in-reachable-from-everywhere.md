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
