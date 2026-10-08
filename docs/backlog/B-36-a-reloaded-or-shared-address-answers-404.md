---
id: B-36
title: "server: a reloaded or shared storefront address answers 404"
status: open
priority: P1
size: S
stage: stage-3-search
blocked_by: [B-35]
---

# B-36 — server: a reloaded or shared storefront address answers 404

Since B-35 the app navigates by pushing browser history (`/c/…`, `/p/…`, `/search?q=…`), but the
server serves the bundle with no fallback to `index.html` (B-27, on purpose: an unknown `/ui/...` must
stay a 404). So any address the shopper reloads, pastes or was sent answers 404 — only in-page
navigation works. For a store that is a defect: a shared product link is the commonest way in.

Serve the page for the client's own addresses — exactly the shapes `shell/Navigation.kt` maps
(`/`, `/c/{slug}`, `/p/{id}`, `/search`, `/cart`, `/account`, `/sign-in`), as an allow-list, not a
catch-all — with the page's `no-cache`, while `/ui/...`, `/api/...`, `/images/...` and unknown paths
keep their 404. The client then draws its own not-found for a product or category that does not
exist, as it already does in-page.

- AC: a request for `/p/<existing id>` and `/c/<slug>` answers the page (200, `text/html`, `no-cache`);
  `/ui/nowhere`, `/api/nowhere` and `/nowhere` stay 404; a reload in headless Chrome on a product
  address draws the product.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`.
