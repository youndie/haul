---
id: B-74
title: "client + server: the account page's tiles and old orders open"
status: done
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

## Findings

- **Every history row opens its order's page.** `HistoryRow.action` is now the order's page on every row, delivered
  and picked-up ones included, and the client presses the whole row (`WideRow`, `PhoneRow`); «Reorder» stays its own
  press and sends only the reorder. «Track» and «Details» are labels of the row's press now, not separate links.
- **Tiles.** «Price drops» carries `AccountTile.action`, `navigate` to `/account/saved?filter=price-dropped`, also
  when the count is 0 (the filtered list then says nothing got cheaper yet). «Points» and a member's «Haul Plus» have
  no page that explains them — the only description of the membership is the home page's Plus block, which has no
  address of its own — so, per the item, they stay text with no action. The trial's offer keeps its button, which
  presents the trial dialog; the tile around it is not pressed.
- **The Saved empty state's heart** is drawn like a card's saved heart, so it is now pressed: it follows the empty
  state's own action («Browse deals», `/deals`), to the products whose hearts fill the list. No pixels changed: the
  canvas draws no pressed or hovered state, so the goldens stay as they were.
