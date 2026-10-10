---
id: B-74
title: "client + server: the account page's tiles and old orders open"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-74 — client + server: the account page's tiles and old orders open

On the account pages: a delivered or picked-up order in the history or the overview cannot be opened — only
«Reorder» is pressable (`account/screen/AccountScreen.kt:495-497`, `AccountViews.kt:736-753, :851-866`). The
«Points», «Price drops» and member «Haul Plus» tiles have no action (`AccountScreen.kt:355-400`); «Price drops»
could open `/account/saved?filter=price-dropped`. The Saved empty state's big heart looks pressable
(`SavedViews.kt:88-110`).

- AC: every order row opens its order page; each tile opens what it summarises or reads as text; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/AccountViews.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SavedViews.kt`.
