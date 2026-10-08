---
id: B-51
title: "client: dialog commands are not review commands"
status: done
priority: P3
size: S
stage: stage-6-account
blocked_by: [B-21]
---

# B-51 — client: dialog commands are not review commands

B-22 built the dialogs' command seam for reviews and questions (`ReviewCommands`, `LocalReviewCommands`); B-21
sent the return through it as `ReviewCommand.Return`, and B-23 added `LocalHaulCommands` for the Plus trial
beside it. A return is not a review, and two seams carry the same kind of thing. Found by B-21 and B-23.

Decided: one seam for the commands a server-built dialog sends — named for dialogs, not for reviews — that
the review, question, return and Plus trial dialogs all use; behaviour unchanged.

- AC: one seam, the four dialogs on it, every existing wiring test green unchanged in meaning; no golden moves.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/shell/TreeCommandsTest.kt`.

## Done (2026-10-09)

- **The seam** is `TreeCommands` in `shell/TreeCommands.kt`: `TreeCommand` (`Review`, `Ask`, `Return`,
  `StartTrial`, `Vote` — each with the `url` the tree fixed and its body from the contract), `TreeCommands.send`,
  `CommandRefused`, `CommandOutcome` with `run` (a dialog's: answer, refusal or no answer to draw), `vote`
  («Helpful»'s: the action to follow), `LocalTreeCommands`, `ktorTreeCommands`. It sits in `shell` because three
  features import it (`product`, `order`, `home`) and the storefront provides it (research D3a); a sealed
  command needs every kind in its own package, so the kinds live there too.
- **Named for what the tree fixes, not for dialogs.** The decision above says «named for dialogs»; «Helpful»
  (B-43) is a button on the page, not a dialog, yet it is the same kind of command — a URL the tree fixed, a
  contract body, a kompot action answered — sent the same way through the same headers. Split off, it would
  be the second seam this item removes; so it stays, and the name covers it.
- **What moved**: `ReviewCommand` → `TreeCommand` (`Post` → `Review`, which no longer reads as an HTTP method
  beside four other `POST`s), `ReviewCommands` → `TreeCommands`, `ReviewRefused` → `CommandRefused`,
  `ReviewOutcome` → `CommandOutcome`, `LocalReviewCommands` → `LocalTreeCommands`, `ktorReviewCommands` →
  `ktorTreeCommands`; `App`'s and `Storefront`'s `reviewCommands` → `treeCommands`. The Plus trial's «Start
  trial» left `LocalHaulCommands` (deleted) for `TreeCommand.StartTrial`: a refusal is `CommandRefused` now
  instead of a non-2xx `HaulResponse`, followed the same way (close, then refresh). `HaulCommands` keeps
  «Clear» on recent searches only, a request the tree names by method and path (B-37).
- **The wire is unchanged.** No `shared` type is named for reviews and used by another dialog (`ReviewEntry`,
  `QuestionEntry`, `HelpfulVote` are the review feature's own, `ReturnEntry` the returns'). The requests are
  the same bytes: the four with a body `POST`/`PUT` it as JSON exactly as `ktorReviewCommands` did; the trial
  `POST`s with no body and no content type, as `ktorCommands` did with `body = null`.
- **Tests**: `ReviewWiringTest`, `ReturnWiringTest`, `PlusTrialWiringTest` and `TreeCommandsTest` (was
  `ReviewCommandsTest`) — the review, question, vote and return assertions renamed only. `PlusTrialWiringTest`'s
  fake is the new seam's, so it records `TreeCommand.StartTrial(TRIAL)` where it recorded `"POST $TRIAL"`, and
  its refusal is thrown `CommandRefused(409, already_member)` where it was a `409` response; the `POST` itself
  moved to `TreeCommandsTest`, which gained `a return and a trial are posted where their dialogs say` (method,
  URL, the return's JSON body, the trial's absent body and content type, the bearer token). No golden moved.
- **Mutations**, each restored: the trial routed as `PUT` in `ktorTreeCommands` and its refusal no longer
  caught as one in `startTrial` → exactly two failures, `TreeCommandsTest` (the new test) and
  `PlusTrialWiringTest.a refused trial closes the dialog and draws the page again`; `LocalTreeCommands` not
  provided by `Storefront` → 11 failures across the review, question, vote, return and trial wiring tests,
  and `DrawnActionsTest` («Clear», on `HaulCommands`) still green.
- **Where it ran**: the Linux build machine (WSL), rebased onto `05a5d9e`: `:composeApp:wasmJsBrowserDistribution`;
  `check :server:installDist` — `:composeApp:desktopTest` 153 tests, `:server:test` 288, `viddikVerify` green,
  ktlint green.

## Findings (2026-10-09)

- **The dialogs' chrome still lives in `feature/product`.** `ReturnDialog` imports `DialogFrame`, `Buttons`,
  `FormProblems`, `settle` and `without` from `feature/product/ReviewDialogs.kt` — the same smell as the seam,
  one layer up. Not moved here: the item is the command seam, and moving the views touches the goldens' code.
- **Two command seams remain, by kind.** `TreeCommands` (typed body, a kompot action answered) and
  `HaulCommands` (method and path, «Clear» only, its answer not followed). `CartCommands` and
  `CheckoutCommands` stay their features' own: they batch, carry an idempotency key or a quote.
