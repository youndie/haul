---
id: B-116
title: "fix: back from the provider's page says the sign-in did not go through"
status: wip
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
