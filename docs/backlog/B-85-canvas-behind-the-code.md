---
id: B-85
title: "design: the canvas draws what the store now has"
status: wip
priority: P3
size: M
stage: stage-10-review
---

# B-85 — design: the canvas draws what the store now has

The client moved past the canvas in five places, each decided and recorded in research §2: the phone header's
menu button and menu (B-73), the strip «Delivering to … · Free delivery over $35» and the shorter footer (B-72),
the search sort and the no-results tips as text (B-77), the message frame and «N in your cart» (B-75); since B-133
also Order_InTransit's shipment line, which keeps tomorrow's date («TOMORROW, OCT 8 · 15:00 – 18:00»). Parity
reads four short phone artboards just over 5 % (Catalog_Empty_Phone 5.19, Product_NotFound_Phone 5.38,
Order_Placed_Phone 5.12, Account_NoOrders_Phone 5.15) and the search artboards far off; the tolerance is
unchanged.

- **Decided (owner, 2026-10-12): Claude updates the canvas** in Claude Design — the project in research §1.6
  (`https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59`) — to what research §2 decided, then re-renders
  the references; it needs write access to the canvas, and stops as a question if it has none.
- AC: references re-rendered from the updated canvas; parity back within tolerance for every artboard.
- Anchors: `composeApp/src/desktopTest/snapshots/design/`.
