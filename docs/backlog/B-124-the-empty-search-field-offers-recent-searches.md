---
id: B-124
title: "fix: pressing the empty search field offers a customer's recent searches"
status: wip
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-124 — fix: pressing the empty search field offers a customer's recent searches

Found by the catalogue's first run, TC-SRCH-10 ([run](../qa/runs/2026-10-11-v0.4.0.md)): Maya, after searching `mug`, `sony`, `duvet`, `mug`,
pressed the empty field — a caret, no panel, no request. «Recent · Clear» (in the right order) appeared only after
two letters, since `Storefront.kt` asks for the panel only for typed text (`if (!search.typed || typed.isEmpty())
panel = null`) and suggest refuses a query under two characters. No item decided the empty field either way.

- **Recall is for the empty field.** A recent list that shows only once two letters are typed is suggest filtered by
  history, not recall; the shopper who wants last night's query has to remember it to get it back. The empty field
  pressed by a customer asks for the panel with no query and the server answers the recent list alone; a guest's
  empty field asks nothing. Rejected: correcting the case to «two letters» — it would write the gap down as the
  design.

- AC: Maya presses the empty field and sees her last ten, newest first, with «Clear»; a row runs its search in the
  picker's scope; a guest's empty field sends no request. A client test and a route test hold it.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt`.
