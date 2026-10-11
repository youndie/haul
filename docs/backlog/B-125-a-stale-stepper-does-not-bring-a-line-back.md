---
id: B-125
title: "fix: a stepper on a line removed elsewhere does not bring it back"
status: wip
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

## Findings (2026-10-12)

- **The wire shape: a flag in the address the cart's line carries.** `CartLine.url` is now
  `/api/v1/cart/lines/{skuId}?existing=true` (`CartPaths.existingLine`); the route hands the flag to
  `CartCommands.changeLine(lineOnly = true)`, which refuses a line the cart has not got with
  `404 line_not_found` before it reads the SKU. Every other `PUT` of a line — a card's «+» (`?answer=card`), «Add to
  cart» (`?answer=details`), «Buy now», the Saved list's «+», the empty cart's picks — goes to the bare address and
  still adds. Chosen because it is the smallest change that covers every control drawn on a cart line: the server
  writes the address and the client follows it (as `?answer=` already does, B-63), so the client, the contract's
  bodies and its methods are untouched, and «−», «+», the box and «Select all» are all held by the one address.
  Not taken: a field in `LineChange` (the client would set it at three presses, and a body the tree does not write
  is one the client can forget); a route or method of its own, `PATCH` (a new `CartCommand` in the client); a
  compare-and-set on the quantity the stepper saw (stronger — it would also refuse a quantity changed in another
  tab — but that is more than the case asks, and a stepper pressed twice quickly would refuse its own second
  press). Written into research D5's cart paragraph.
- **The refusal's words.** `line_not_found` said «No line for «p-002-08-0» in this cart» — B-92 noted it was not a
  shopper's sentence, and with the stepper it is no longer only a race of «OK» or «Save for later». It now says
  «This item is no longer in your cart» (`CartError.NO_LONGER_IN_CART`) for all three. The client needed nothing:
  a refused command is already the cart drawn again with the reason at the error level (B-92,
  `shell/CommandAnswers.kt`).
- **What it does not cover.** A product card's own stepper, where a card draws one, still adds on «+» by design; a
  stale page's «−» on a card is not a cart line and was not in the case.

## Acceptance

- [x] TC-ERR-08 step 1: the stale page's stepper on a removed line is `404 line_not_found` «This item is no longer
  in your cart», and the line stays gone; the client draws the cart again with the message at the error level.
- [x] A card's «+» still adds the same SKU.
- [x] A route test holds both: `CartRoutesTest` «a stale stepper does not bring a removed line back and a card's plus
  still adds».
