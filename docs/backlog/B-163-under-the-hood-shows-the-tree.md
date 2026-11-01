---
id: B-163
title: "feat: «Under the hood» shows the screen's kompot tree, what it cost and what updated"
status: wip
priority: P1
size: M
stage: stage-15-showcase
blocked_by: [B-160]
---

# B-163 — feat: «Under the hood» shows the screen's kompot tree, what it cost and what updated

Every page of the store is a kompot tree the server sends and the browser draws with Compose (research D1), and
nothing on the page shows it: from outside it looks like any web shop. The product and the designer reviews
(2026-11-01) both put a «show the machinery» layer at the top of their lists — it is the one thing a developer
would come to a showcase for.

- **A toggle in the demo strip (B-160) opens a side panel** over the page: the tree of the screen on view as an
  outline (component types and ids, expandable to their fields), the request that brought it (path, status, size,
  server time, from the response headers), and — while the panel is open — a flash on the parts of the page a
  partial update (`update` / `load`, B-63) replaced, with the update listed in the panel. A link from a component to
  its renderer and its server tree builder in the repository.
- The panel is a client feature that reads what the client already has (the tree it decoded, the responses it
  made); the server adds only a `Server-Timing` header. It is off by default and costs nothing when closed.
- Rejected: a separate «developer page» (nobody finds it), raw JSON only (an outline reads; a JSON dump is a
  scroll).

- AC: on the home, a product and the cart, the panel shows the screen's tree and its request; adding to the cart
  with the panel open flashes the badge and the card's part and lists the update; closing the panel leaves the page
  pixel-identical to before; screenshots of the panel recorded.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/App.kt`.
