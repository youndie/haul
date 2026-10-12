---
id: B-132
title: "fix: an empty scoped panel, an out-of-date search link and the reviews link's address"
status: done
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

## Findings (2026-10-12)

- **The empty scoped panel** — the server matched `running` under the scope, found nothing, and still wrote «All
  0 results» with its search; the client drew the products' column whenever the panel had a query. Now a query with
  no match under the scope sends no «All N results» (an empty label, no action; `SearchScreen.suggest`), and the
  client draws the products' column only for a panel with products or a way to every result — the heading only over
  products, the button only with its action (`SearchSuggestPanelView`). Such a panel is drawn as recall is
  (B-124): its queries, categories and recent searches. No wire change: the fields were already optional.
- **«Open the page» on `/search?q=%zz`** — decided: the target stays `/search`, and the page with nothing typed
  says «Start typing» and «Type what you are looking for in the field and press Enter.» (`SearchTooShort`, empty
  query). Home was the other choice; the search page is where the field is, one press nearer, and the same words
  fix a bare `/search` opened any other way, which said «Your query is still in the field» over an empty field
  too. The out-of-date page itself is unchanged (B-117's «Open the page» to the path).
- **«2,341 reviews» on `?tab=reviews`** — the server wrote every address the product page makes for itself at the
  SKU drawn, so on an address naming none the press to the page shown rewrote it. Those links — the tabs, the
  rating, «All specifications», «Show more» — now name the SKU only when the page's address did
  (`ProductScreen.address` takes a `Sku?`); a variant picked names its SKU as before. The buy box «Add to cart»
  redraws carries the same: its command's address gains `no_sku=1` on a page without one (`LineAnswers.NO_SKU`),
  so the redrawn rating does not bring the SKU back. The client's product bodies were opened without a SKU, and now
  hold the links the server writes for that (`ReviewFixturesTest` compares the tabs and the lists). Rejected: the
  client comparing addresses with the drawn SKU filled in — it would learn the product page's parameters. Left as
  is: an address naming its parameters in another order (`?tab=reviews&sku=…`) is still rewritten to the server's
  order by the press.
- **Goldens**: none changed — no fixture draws a panel without products or the search page with nothing typed.
