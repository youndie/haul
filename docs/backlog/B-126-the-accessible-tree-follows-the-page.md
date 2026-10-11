---
id: B-126
title: "fix: the accessible tree follows the page after a popup, and every button has a name"
status: open
priority: P2
size: M
stage: stage-12-qa-run
---

# B-126 — fix: the accessible tree follows the page after a popup, and every button has a name

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), in headless Chromium on a local v0.4.0 stack:

- After any popup opens and closes (the account menu, the sort menu, «Catalog», the phone menu) the browser's
  accessible mirror of the page (`cmp_a11y_root` in the shadow root) keeps only the popup's nodes («Account | Sign
  out»); navigating does not refresh it, only a reload does. A screen reader reads a menu that is gone.
- Buttons with empty names: the phone header's heart and account, the account's «Price drops» tile, checkout's
  summary tiles, the category row's words.

- **First find whose bug the mirror is** — Compose Multiplatform's wasm accessibility or the way the storefront
  hosts its popups — with a minimal page; a framework defect goes upstream as a report, the storefront's own part is
  fixed here. Names are the storefront's: each press gets a `contentDescription` or semantics.

- AC: open and close each popup, then navigate — the mirror holds the page's nodes; every button in the mirror has a
  name; where the defect is upstream, a minimal reproduction exists and the report is linked here (asked of the
  owner before it is filed).
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`.
