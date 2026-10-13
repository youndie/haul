---
id: B-146
title: "fix: a Plus trial started over a page with no tree redraws the header"
status: done
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-identity
---

# B-146 — fix: a Plus trial started over a page with no tree redraws the header

Found outside the catalogue by the v0.6.0 run ([run](../qa/runs/2026-10-13-v0.6.0.md), walker D). Nora (not a member) on `/p/p-does-not-exist`: the pill
→ «Start trial» → `POST /api/v1/me/plus/trial 201`, then `GET /ui/p/p-does-not-exist 404` and no `GET /ui/header`:
the pill stays the pressable offer; pressing it again and «Start trial» answers `409` «You are a Haul Plus member
already». The next navigation draws the member's mark. On a page with a tree (home) the same trial redraws the header
(TC-ACC-08). B-130 made a session that ends ask the header anew; a membership that starts is the same kind of change.

- **A change of who the shopper is — a trial included — asks the header anew on every page**, with a tree or not.
- AC: the walk above ends with the member's mark in the header and no 409 path; a client test fails on today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/ShellHeaders.kt`.

## Findings

- **Why the pill stayed the offer.** The trial answers `sequence(close, refresh)`. Over a page with a tree the
  `refresh` loads the tree, whose header is drawn again for a member. Over a page with no tree the dialog is the
  shell's own (presented from `LocalShellHeader`), and its `refresh` reached `Storefront`'s own handler, which
  reloaded the page (`navigator.reload()`, a visit): the `404` came back, `AskForHeader` composed again — and
  returned at once, because `ShellHeaders.knows(signedIn = true)` was still true for the header asked on landing.
  The header is keyed by whether the shopper is signed in (B-105, B-130), and a membership changes no such thing.
  A `409 already_member` ends the same way: `refusal(…, then = sequence(close, refresh))`.
- **Decision: a `refresh` the shell follows asks for the header anew.** `ShellHeaders.redrawn()` marks the kept
  header as no longer current: `knows` answers false, so the reloaded page with no tree asks `GET /ui/header` once
  more (once per visit, as B-105's ask), while `of` keeps drawing it until the answer — no flash of the client's
  own «Account» header in between; an answer that does not arrive leaves it, and the next visit asks again.
  Called in `Storefront`'s `RefreshAction` branch, before the reload. This is B-130's mechanism generalised rather
  than a trial's special case: B-130 followed *who* is looking (`signedInChanges`); what the shopper *is* (a
  member) the client cannot follow — the token does not carry it, only the header does — so the server's
  `refresh`, the way every command says «what you drew is no longer so», is the change the shell follows. Rejected:
  a membership flag beside `SessionControls.signedIn` (nothing on the client knows it), and a signal from the trial
  dialog alone (it would leave any other `refresh` over the shell's header with the same stale header). Research
  §2, «Decided in B-105», has the paragraph.
- **Verified** by `KnownHeaderTest` «a trial started over a page with no tree draws the member's header» and «a
  trial refused over a page with no tree draws the member's header»: Maya on `/p/p-does-not-exist`, her
  non-member header asked; the pill, «Start trial» (answered `close, refresh`, or refused `409 already_member`);
  the page loaded again, `/ui/header` asked a second time and only then, the dialog gone; the pill now opens
  `/account`, no dialog, one trial sent. **Mutations**, each red then restored, `git status` clean: the
  `redrawn()` call removed from the refresh (today's code) → both; `knows` ignoring whether the header is current
  → both. The sign-out and sign-in over pages with no tree (`KnownHeaderTest`'s sign-out pair, `LapsedHeaderTest`,
  `ColdSignInHeaderTest`, `ShellHeaderTest`) stay green. QA TC-ERR-07 step 2 now expects the member's pill. Not
  walked in a browser against a local stack: the trial over the shell's header goes through the same
  `Storefront` handler in the test as in `App`.
