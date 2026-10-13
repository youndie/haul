---
id: B-146
title: "fix: a Plus trial started over a page with no tree redraws the header"
status: open
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
