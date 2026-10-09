---
id: B-65
title: "server: every node id appears once on a page"
status: wip
priority: P2
size: S
stage: stage-9-ship
---

# B-65 — server: every node id appears once on a page

kompot requires a node's `id` to be unique within one screen's tree (SPEC §4.2): the update channel, the
override store and now `update` (B-63) address nodes by it, and with a duplicate the target is undefined.
The deals page breaks that: its root column is `deals` (`Frame.page("deals", …)`) and so is the «Deals of
the day» grid on its first page. B-63 worked around it by looking for the section through its header
(`DealsScreen.TODAY`). Nothing checks the rule, so the next duplicate would also go unnoticed.

- **Fix the deals page** so each id appears once. Prefer renaming the page's root, which nothing addresses,
  over the grid that tests, fixtures and the e2e look up on home and deals alike; record what was renamed.
- **Guard the rule:** one server test draws every page the storefront serves — home, categories, a
  catalog page with filters, search, deals pages 1 and 2, a product, the cart empty and with lines, the
  account pages, an order — as a guest and as a signed-in customer where the page differs, plus every
  `update` answer B-63 sends, and fails on any id that appears twice, naming the page and the id.
- AC: the guard red on main (the deals page) and green after; no other duplicate, or each found one fixed
  and listed; goldens and wire fixtures unchanged unless an id they carry changed; the e2e green.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/testing/HaulTestApp.kt`.
