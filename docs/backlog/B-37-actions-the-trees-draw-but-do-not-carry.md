---
id: B-37
title: "server + client: actions the trees draw but do not carry"
status: wip
priority: P2
size: M
stage: stage-4-cart
blocked_by: [B-35]
---

# B-37 — server + client: actions the trees draw but do not carry

B-35 wired every action the contract gives; walking the screens showed controls that are drawn but
carry nothing to follow, so they do nothing when pressed: pagination and «Show 24 more», the sort,
«Clear all» on applied filters, the header's category row, «Catalog», «Deals» and «Cart», a card's
«+» (add to cart — the cart route exists since B-11), and «Clear» on recent searches (planned for
B-12, not built: `DELETE /api/v1/me/recent-searches`). «View all deals» points at `/deals`, which has
no screen.

For each: the server puts the action in the tree (a navigate to an address the shell maps, or a
command URL like the cart's), the client follows it. A control whose screen belongs to a later item
(the heart → Saved, B-20; «Orders» → B-18) gets no action here and is listed in that item instead.

- AC: each control above changes what the shopper sees, by a route test (the action is in the tree)
  and a client wiring test (pressing it follows the action); `/deals` either draws a screen or is
  no longer linked.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/`.
