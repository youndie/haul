---
id: B-161
title: "feat: every page has its own title, a description and a link preview"
status: wip
priority: P1
size: S
stage: stage-15-showcase
---

# B-161 — feat: every page has its own title, a description and a link preview

Every tab of the stand reads «Haul», whatever page it shows; the page has no meta description and no Open Graph
tags, so a link to it pasted into a chat or a post previews as a bare address, and a search engine has nothing to
show. There is no `robots.txt`. For a showcase, the link someone shares is the first thing anyone sees.

- **The title is the screen's; the preview is the stand's.** Each tree already names its page (the header's title,
  the product's name); the client sets `document.title` from it («Sony WH-CH720N · Haul», «Deals · Haul»). The page
  the server serves carries a fixed description and Open Graph / Twitter card tags with one image of the store (a
  golden of the home is enough) that say what the demo is, and `robots.txt` allows crawling.
- Rejected for now: rendering each page's HTML on the server for crawlers (the architect's snapshot idea) — the
  canvas is not HTML, and a fixed preview carries the message; it can be its own item if the numbers ask for it.

- AC: the tab's title follows navigation; a link to the stand previews with a title, a line and the image (checked
  with an OG validator or `curl` of the tags); `GET /robots.txt` answers 200; the image check still passes.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`, `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/`.

## Findings

- **The trees did not name their pages reliably**, so the server writes the title (research D9, «Decided in B-161»):
  a product names itself in its details, a category in its `PageTitle`, the cart in a title with its count, checkout in
  `CheckoutBody.title`, and the home and the account's overview nowhere. Reading them would have been a client table
  of node types plus copy the client invents. Instead `HaulHeader.tabTitle` and `CheckoutHeader.tabTitle`, written by
  `Frame.page`'s required `title`, so a new page without one does not compile. The header alone and an `update`'s
  header leave it out (`explicitNulls = false`), which is why the fixture bodies of whole pages gained the field and
  the header-alone test now compares the home's header without it.
- **The product's tab is its listing name** (B-45), «Sony WH-1000XM6 Wireless Noise Cancelling Headphones · Haul»: the
  product's `title` lacks the brand («WH-1000XM6 Wireless…»).
- **A placeholder titles the tab «Haul»** once a page has named it, so the tab never names a page that is not on the
  screen; a cold load keeps `index.html`'s `<title>` (the home's) until the first tree, so a fresh tab does not flash
  «Haul».
- **`robots.txt` is a route, not a bundle file**: answered with or without the bundle, and trained into the AOT cache
  with the rest of the workload (`server/build.gradle.kts`) — `scripts/image-check.sh` refuses a class of ours loaded
  outside the cache, and the check now fetches `/robots.txt`.
- **The picture**: `composeApp/src/wasmJsMain/resources/preview.png`, the top 1440 × 756 of the `Home_Guest` golden
  scaled to 1200 × 630 with `sips` on the Mac (neither box has ImageMagick or PIL), 240 KB. No golden moved. It is a
  copy, not a link to the golden: when the home's look changes, the preview stays until someone renews it.
- **B-160** adds an `/about` page in parallel: whichever merges second gives that page a `title` at its `Frame.page`
  call (the compiler will ask) and a line in `TabTitlesTest`.
