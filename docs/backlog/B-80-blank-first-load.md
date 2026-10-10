---
id: B-80
title: "client: the first load shows something before the app starts"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-80 — client: the first load shows something before the app starts

Opening any address cold shows a blank page for a few seconds while the wasm bundle downloads and starts; the
skeletons appear only once the app runs. On a slow connection the shopper sees nothing at all.

- AC: `index.html` draws a static frame (the header's shape and a progress mark) that the app replaces; measured
  on the stand: time to first paint before and after.
- Anchors: `composeApp/src/wasmJsMain/resources/index.html`.
