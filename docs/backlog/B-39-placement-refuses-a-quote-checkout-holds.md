---
id: B-39
title: "server: placement refuses a quote the checkout is holding"
status: done
priority: P1
size: S
stage: stage-5-order
blocked_by: [B-15, B-16]
---

# B-39 — server: placement refuses a quote the checkout is holding

Since B-15 the checkout holds «Place order» while a refused address form is shown
(`CheckoutState.placeable`), but `Placement` (B-16) only checks that the quote is complete. With a
refused form on screen the previous address still completes the quote, so a client that ignores the
held button — or a second tab — places the order to the address the shopper was trying to change.
Found by B-15.

- AC: `POST /api/v1/orders` answers a refusal (a typed code, e.g. `409 checkout_held` or the
  validation error the form carries — decide and document) when the checkout state is not placeable;
  a route test places with a refused form on record and asserts no order, no stock taken, no window.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`.

## Done (2026-10-08)

- **Decision: `409 checkout_held`** (`ErrorCode.CheckoutHeld`,
  `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt`; `409` in `status` in
  `server/src/main/kotlin/io/github/youndie/haul/HaulModule.kt`), not the form's `400 validation_failed`. The
  placement request is well-formed — the conflict is with what the checkout holds, the class `cart_changed` and
  `slot_unavailable` are already in — and the same request under the same key places once the hold is lifted,
  which a `400` says it never will. `validation_failed` with the form's `FieldError`s would name fields
  (`street`, `zip`) that are not in `PlaceOrderRequest`, so a client would read its own body as malformed. A code
  of its own also tells «somebody placed past a held button» (a client defect, a second tab) apart in the logs.
  The body carries no `field`: nothing in the request is at fault.
- **Placement follows the button's rule**: `Placement.place` refuses when `CheckoutState.placeable` is false,
  after the window and fingerprint checks and before the key is claimed — `checkout_held` when the quote is
  complete (a refused form is on record), the existing `400 validation_failed` (field `quote`) when it is not.
  Whatever later changes `placeable` changes placement with it.
- **The key**: a held refusal claims nothing (no saga — asserted — and the key's guard is never reached), so
  the same request under the same key places once the shopper chooses a saved address again — the quote and its fingerprint are then what they
  were, and the client's key, remembered per fingerprint (`HaulRenderers.kt`), is the same one. A key whose
  saga exists is answered from that saga before checkout is read, so a retry of a placed order is the order, not
  `checkout_held`.
- **The client**: `CheckoutCommands.run` turns every `CheckoutRefused` into `refresh`, whatever the code (an
  unknown code too: the body is then `null` and the status still refuses), so `checkout_held` redraws the
  checkout with the held button and the form's errors. No client code path changed; no client test added.
- **Tests** (`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`): `a refused
  address form holds placement and the same key places once the hold is lifted` — the refused form leaves the
  fingerprint unchanged (asserted), placing is `409 checkout_held`, no order, no saga, stock and window
  untouched, nothing authorised; choosing the saved address then places under the same key, to it. `a retry of
  a placed key answers its order with a refused address form on record`. `Ledger.sagas()` added.

## Findings (2026-10-08)

- **A refused form holds a pickup order too** (by reading, not run): `CheckoutCommands.choose` keeps
  `StoredCheckout.draft` when the method changes to a pickup point or a locker, `CheckoutState.placeable` ignores
  the method, and the screen draws the address form only for the courier. A shopper who switches to pickup after
  a refused form sees «Fill in the street address…» with no form to fill, and placement now refuses the same way.
  The fix is B-15's side (clear the draft on a non-courier method, or count draft problems only for the courier);
  placement follows `placeable` and needs no change then.
- **An incomplete quote stays `400 validation_failed`** (field `quote`), the other half of `placeable`; it is
  documented that way (endpoint-checkout) and the client treats every refusal alike. Folding it into
  `checkout_held` is a one-line change if the owner wants one code for «the button is held».
- **Mutations**: placement checking `quote.complete` instead of `placeable` fails the first test (`202` to
  `HL-48302` with the refused form on record — the defect). Reading checkout before the saga lookup fails the
  second, but also «Same key twice»: after a placement the cart is empty, so the mutant answers `cart_empty`
  rather than `checkout_held`; the second test pins the decision more than it catches a mutant of its own.
- Docs to sync on PR #2's drafts: feature-checkout (the business rule and a scenario with its `**Automated:**`
  line), endpoint-checkout (the check order and the `POST /api/v1/orders` errors row), haul-shared (the
  `ErrorCode` list).
