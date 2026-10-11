---
id: B-129
title: "fix: the small lines the v0.4.0 customer walk noticed"
status: open
priority: P3
size: S
stage: stage-12-qa-run
---

# B-129 — fix: the small lines the v0.4.0 customer walk noticed

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), on a local v0.4.0 stack, each small:

- A promo code outlives the order: `AUTUMN10` applied before placing HL-48304 stayed on the cart and was applied to
  the next two orders unasked. A code is for the order it was typed for; placing clears it.
- Seeded reviews from 2023 and 2025 read «Oct 21» on Oct 11 — a date with no year reads as the future. A date
  outside the current year carries its year.
- The overview draws «All orders» with an active order on screen though every other order is listed below.
- A cart with nothing ticked still says «You'll earn 0 points on this order».
- The return dialog's reason list stays open on Escape.
- A new question is listed last, not first.

- AC: each behaves as its line says; a test per line where it is the server's or the client's logic.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/cart/CartRouting.kt`.
