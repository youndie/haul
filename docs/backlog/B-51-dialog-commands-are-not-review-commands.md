---
id: B-51
title: "client: dialog commands are not review commands"
status: wip
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
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewCommandsClient.kt`.
