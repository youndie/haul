---
id: B-132
title: "fix: an empty scoped panel, an out-of-date search link and the reviews link's address"
status: open
priority: P3
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-132 — fix: an empty scoped panel, an out-of-date search link and the reviews link's address

Found outside the catalogue by its v0.5.0 run on the stand ([run](../qa/runs/2026-10-12-v0.5.0.md)):

- **A scoped suggest panel with no products** draws an empty «TOP PRODUCTS» heading and «All 0 results»
  (`running` typed with the picker on «Appliances · Classics»), while still suggesting «running shoes». A part with
  nothing in it is not drawn, and «All 0 results» is not offered.
- **«Open the page» on a broken search link** (`/search?q=%zz`, B-117) opens `/search` with no query, which says
  «Keep typing… Your query is still in the field» over an empty field. The out-of-date search leads somewhere a
  shopper can act on — the search page with an empty field that says to type, or home.
- **«2,341 reviews» pressed on `?tab=reviews`** reloads in place and brings the tabs into view (B-121) but rewrites
  the address to `?sku=p-sony-wh-1000xm6-0&tab=reviews`; TC-PDP-03 step 5 expects the address shown.

- AC: each of the three behaves as its line says; a server or client test per line.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`.
