---
id: B-137
title: "fix: every sentence the store writes uses ’, not a straight apostrophe"
status: wip
priority: P3
size: XS
stage: stage-12-qa-run
---

# B-137 — fix: every sentence the store writes uses ’, not a straight apostrophe

Found by B-136, which fixed «Couldn’t copy the link» from the v0.5.1 run
([run](../qa/runs/2026-10-12-v0.5.1.md)): two more of the store's own sentences use a straight apostrophe where the
store writes ’ — `ReorderAnswer` («isn't / aren't available now») and `OrderScreen.returnRule` («can't be sent
back»). They appear in tests, fixtures and the QA catalogue, so the change moves those too.

- **The store's own copy uses the typographic apostrophe**, as the canvas and the rest of the copy do; a check keeps a
  straight one from coming back in a string the store writes (server screens and client copy), shoppers' and sellers'
  words aside (seeded reviews and questions keep what their authors typed).
- Not covered: seeded reviews, questions and product names.

- AC: no string the store writes carries `'` inside a word; the two sentences above read with ’ on screen, in their
  fixtures and in the QA cases; a test fails if a straight apostrophe comes back in such a string.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/ReorderAnswer.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`.
