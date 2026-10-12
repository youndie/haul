---
id: B-85
title: "design: the canvas draws what the store now has"
status: open
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

- **Needs the designer:** new or updated artboards for those states (the `design-to-compose` brief).
- AC: references re-rendered from the updated canvas; parity back within tolerance for every artboard.
- Anchors: `composeApp/src/desktopTest/snapshots/design/`.
