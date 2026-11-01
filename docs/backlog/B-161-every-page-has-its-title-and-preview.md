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
