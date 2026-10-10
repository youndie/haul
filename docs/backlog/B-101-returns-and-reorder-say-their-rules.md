---
id: B-101
title: "server + client: returns and reorder say what they did and what is left"
status: open
priority: P2
size: S
stage: stage-11-qa
---

# B-101 — server + client: returns and reorder say what they did and what is left

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk:
- after one partial return, «Reorder» and «Return items» vanish for good although the page says «Returns are open until
  Nov 9»; «one return per order» is in a KDoc, never shown;
- the history row then says «Returned» at the original total although one line went back;
- «Reorder» drops the SKUs it could not add (`server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt:101`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt:13, 41-46`);
  an all-unavailable reorder lands on an empty cart with no word.

- AC: the rule is shown where the button was (or a second return is allowed — the owner's call, recorded); the row says
  «Partly returned» with what was paid; reorder names what it could not add; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt`.
