---
id: B-125
title: "fix: a stepper on a line removed elsewhere does not bring it back"
status: open
priority: P2
size: S
stage: stage-12-qa-run
---

# B-125 — fix: a stepper on a line removed elsewhere does not bring it back

Found by the catalogue's first run, TC-ERR-08 step 1 ([run](../qa/runs/2026-10-11-v0.4.0.md)): with Maya's cart drawn in one tab, the TV line was
deleted in another (`DELETE /api/v1/cart/lines`); «+» on it in the stale page sent
`PUT /api/v1/cart/lines/p-002-08-0 {"quantity":2}` → 200, and the removed line came back at quantity 2, ticked.
The case expects the cart drawn again with the server's refusal in words. The line `PUT` is an upsert, which is
also what a card's «+» uses to add.

- **Changing a quantity and adding are two requests.** A stepper says which quantity it saw (or asks for a change
  of an existing line); a line that is not there answers a refusal the cart draws at the error level, and the cart
  is drawn again from the server. A card's «+» keeps adding. Rejected: refusing every `PUT` of a missing line —
  that is how «+» adds.

- AC: the walk above ends with the line gone and the refusal in words; a card's «+» still adds; a route test holds
  both.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt`.
