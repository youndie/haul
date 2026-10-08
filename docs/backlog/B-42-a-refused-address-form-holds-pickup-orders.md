---
id: B-42
title: "server: a refused address form does not hold a pickup or locker order"
status: done
priority: P2
size: S
stage: stage-5-order
blocked_by: [B-39]
---

# B-42 — server: a refused address form does not hold a pickup or locker order

A refused address form is kept as the checkout's draft (`StoredCheckout.draft`) when the method
changes to a pickup point or a parcel locker, and `CheckoutState.placeable` counts the draft's
problems whatever the method — while the screen draws the address form only for courier. A shopper
who switches to pickup after a refused form sees «Fill in the street address…» with no form to fill,
and since B-39 placement refuses the same way (`409 checkout_held`). Found by B-39, from the code.

Decided as product owner: the draft's problems hold «Place order» only for courier; the draft itself
is kept, so switching back to courier shows what the shopper was typing.

- AC: with a refused form on record, choosing a pickup point makes the checkout placeable and
  `POST /api/v1/orders` places to that point; switching back to courier shows the draft and holds
  the button again. Route tests for both.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/Quote.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt`, `server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`.

## Done (2026-10-08)

- **The rule**: `CheckoutState.holdingProblems`
  (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/Quote.kt`) is the refused form's
  problems while the courier is the method and nothing otherwise; `placeable` counts it instead of
  `draftProblems`. Placement follows `placeable` (B-39), so it needed no change of its own; the KDoc of
  `OrderError.CheckoutHeld` and `Placement` now say «by courier».
- **The draft is kept**, as decided: `CheckoutCommands.choose` still carries `StoredCheckout.draft` through a
  pickup point or a locker, and the form still draws `draftProblems`. Switching back to the courier draws what
  the shopper was typing with its errors and holds «Place order» again. No migration: nothing stored changed.
- **The hint**: `CheckoutScreen.hint` reads `holdingProblems` too, so a held pickup or locker quote (one with no
  point) would say «Pick a place to collect the order», not «Fill in the ZIP». On the seed a pickup or locker
  quote always has a point, so that branch is unreachable over HTTP and no test covers it; with a refused form
  and a point the summary has no hint at all, which the tests assert.
- **Locker** goes through the same `method != Courier` branch; it has a test of its own because it is chosen by
  the method (the `choice.method` path of `choose`) while the pickup test chooses a point (`choice.pointId`).
- **Tests** (`server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRoutesTest.kt`): `a refused
  address form does not hold an order to a pickup point` — the pickup tree is placeable with no hint and no
  form, and `POST /api/v1/orders` places to 96 N 6th St with no address and no window taken; `a refused address
  form does not hold an order to a parcel locker` — the same for the nearest locker; `switching back to the
  courier after a pickup draws the refused form and holds placement again` — the form holds «1 Kent Avenue» and
  «112ll» with its ZIP error, the hint is «Fill in the ZIP», placement is `409 checkout_held` and takes nothing.
- **Mutations**: `placeable` counting `draftProblems` again (the defect) fails all three new tests — the third at
  its pickup precondition. `choose` forgetting the draft off the courier (the rejected alternative) fails only
  the switch-back test («the refused form was forgotten»). `holdingProblems` always empty fails the switch-back
  test and B-39's `a refused address form holds placement and the same key places once the hold is lifted`.
