---
id: B-121
title: "fix: the small presses and lines the v0.4.0 run noticed"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-121 — fix: the small presses and lines the v0.4.0 run noticed

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), each small and each seen
on the stand:

- «Checkout needs a sign-in» breaks at its hyphen at 1440 («sign-» / «in»): a non-breaking hyphen
  (`SignInPrompt.kt`).
- The header's «Sign in» on `/sign-in?next=…` drops the `next` (→ `/sign-in`).
- A too-short query rewrites the address: `a` + Enter over the `mug` results leaves `/search?q=a` with the mug
  results under the notice; a reload then shows «Keep typing».
- A suggest request fires after a submit (`GET /ui/search/suggest?q=classic` right after Enter opened the
  results).
- The rating link pressed while already on Reviews refetches the same address and does not scroll the tab row
  into view.
- The sort menu stays open after picking the order already shown.

- AC: each of the six behaves as its line says it should; a test per line where the line is the client's logic.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`.
