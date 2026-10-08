---
id: B-49
title: "server + client: the last drawn controls without actions"
status: open
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-37]
---

# B-49 — server + client: the last drawn controls without actions

B-37 gave most drawn controls their action and listed the rest as «owned by nobody»: home's «All N
categories», the brand facet's «Show N more», the filter sheet's «×», a recent search's own row, the header
strip's «Help» and «HAUL PLUS» pill, and the footer's links. A shopper presses each and nothing happens.

Decided as product owner: «All N categories» → the catalog root; «Show N more» expands the facet in place
(a `navigate` with the facet expanded, keeping the filters); the sheet's «×» closes it; a recent search's
row runs that search (the tree carries the `/search?q=` action, the client builds nothing); «HAUL PLUS»
opens the Plus offer (B-23's trial when it exists, else `/account`); «Help», «Sell on HAUL», the language
and the footer's links stay inert and are drawn as text, not as links, until a page exists for them.

- AC: each listed control either carries an action followed by the client (route + wiring test per
  control, as `DrawnActionsTest` does) or is drawn as plain text; goldens unchanged unless a control's look
  changes, and then re-recorded and explained.
- Anchors (planned): `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/DrawnActionsTest.kt`.
