# Backlog: Haul, an open end-to-end marketplace storefront

> Role of this document: the product backlog. **One file per item in
> [`docs/backlog/`](docs/backlog/)** — `B-NN-<slug>.md`. What lives here is the index (generated)
> and everything that is not an item: the goal, the stages, and the decisions.
>
> New item: copy [`docs/templates/backlog-item.md`](docs/templates/backlog-item.md), take the next
> free `B-NN`, and run `make fix` after editing.

## Goal

A shopper on the public stand finds a product, buys it from two sellers in one order, watches it
arrive and returns part of it — and every step runs on the stack this project exists to show:
Ktor, kompot, petich, shildik, Compose Multiplatform in the browser. The order of work follows what
everything else compiles against: the skeleton and the two components every screen uses, then the
browse half, then the money half, then the account and the loyalty layer, then the stand.

Why it is built this way is in [research-architecture](docs/research/research-architecture.md);
read it before taking an item.

## Stages

A stage is a field on the item, not a directory.

| Stage id | Stage | What it is |
|---|---|---|
| `stage-1-skeleton` | Skeleton | the repository builds, the docs check passes, the canvas is complete, the theme and the first components render |
| `stage-2-browse` | Browse | home, category and product screens from the server on the seed, against the canvas |
| `stage-3-search` | Search | search and autocomplete |
| `stage-4-cart` | Cart and sign-in | guest cart, sign-in, merge, cart screen |
| `stage-5-order` | Checkout and orders | placement, the saga, simulators, the order screen |
| `stage-6-account` | Account | overview, history, Saved, price drops, returns |
| `stage-7-reviews` | Reviews | reviews and questions |
| `stage-8-loyalty` | Loyalty | Plus, points, Haul Pay, recommendations |
| `stage-9-ship` | Ship | the end-to-end suite, the chart and the stand, observability, first-load numbers |

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (12)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-18](docs/backlog/B-18-order-tree-and-renderers-reorder.md) `[~]` | server + client: Order tree and renderers, reorder | P1 | M | B-01, B-15, B-17 |
| [B-19](docs/backlog/B-19-account-overview-and-orders-history.md) `[ ]` | server + client: Account overview and orders history | P1 | M | B-01, B-18 |
| [B-26](docs/backlog/B-26-the-whole-path-over-http-against.md) `[ ]` | e2e: the whole path over HTTP against the composed stack | P1 | M | B-21, B-22 |
| [B-20](docs/backlog/B-20-saved-list-save-for-later-price.md) `[ ]` | server + client: Saved list, save for later, price drops | P2 | M | B-01, B-13, B-19 |
| [B-21](docs/backlog/B-21-returns-and-refunds.md) `[ ]` | server + client: returns and refunds | P2 | M | B-18 |
| [B-23](docs/backlog/B-23-haul-plus-trial-and-benefits-points.md) `[ ]` | server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings | P2 | M | B-15, B-17, B-19 |
| [B-24](docs/backlog/B-24-haul-pay-4-payments-two-weeks.md) `[ ]` | server + client: Haul Pay, 4 payments two weeks apart | P2 | M | B-15, B-16 |
| [B-25](docs/backlog/B-25-recommendations-from-views-in-the-home.md) `[ ]` | server: recommendations from views in the Home tree | P2 | M | B-07, B-19 |
| [B-27](docs/backlog/B-27-helm-chart-tracy-metrik-katcher-wiring.md) `[?]` | ops: Helm chart, tracy / metrik / katcher wiring, the public demo stand | P2 | M | B-03 |
| [B-31](docs/backlog/B-31-a-traffic-generator-that-walks-the.md) `[ ]` | synthetic shoppers: a traffic generator that walks the e2e path continuously on the stand | P2 | M | B-26, B-27 |
| [B-44](docs/backlog/B-44-a-guest-on-a-customer-page-is-sent-to-sign-in.md) `[ ]` | client: a guest on a customer page is sent to sign-in | P2 | S | B-41 |
| [B-29](docs/backlog/B-29-live-order-tracking-through-kompot-realtime.md) `[ ]` | live order tracking through kompot-realtime | P3 | M | B-18 |

## Closed (32)

**Skeleton**

- [B-01](docs/backlog/B-01-canvas-per-the-design-briefs-static.md) `[x]` - design: canvas per the design briefs — static artboards, every screen state, desktop and phone, the three disagreements resolved
- [B-02](docs/backlog/B-02-settings-plugin-modules-shared-server-composeapp.md) `[x]` - scaffold: settings plugin, modules `shared` / `server` / `composeApp` / `e2e`, CI, `CLAUDE.md`, docs check
- [B-03](docs/backlog/B-03-schema-migrations-seed-generator-and-sample.md) `[x]` - server: schema migrations, seed generator and sample-data fixtures, probes, the image with a zavarnik cache
- [B-04](docs/backlog/B-04-theme-registry-haulheader-and-productcard-renderers.md) `[x]` - contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers

**Browse**

- [B-05](docs/backlog/B-05-catalog-use-cases-and-the-home.md) `[x]` - server: catalog use cases (home, facets, product, delivery estimate) and the Home / Catalog / Product trees
- [B-06](docs/backlog/B-06-home-catalog-product-pngs.md) `[x]` - design refs: `Home_*`, `Catalog_*`, `Product_*` PNGs
- [B-07](docs/backlog/B-07-renderers-for-the-home-and-catalog.md) `[x]` - client: renderers for the Home and Catalog components; Loading / Error shells
- [B-08](docs/backlog/B-08-renderers-for-the-product-components.md) `[x]` - client: renderers for the Product components (without the dialogs)

**Search**

- [B-09](docs/backlog/B-09-search-suggest-recent-searches-suggest-latency.md) `[x]` - server: search, suggest, recent searches; suggest latency measured on the seed
- [B-10](docs/backlog/B-10-search-screen-and-searchsuggestpanel.md) `[x]` - design refs + client: Search screen and `SearchSuggestPanel`
- [B-32](docs/backlog/B-32-catch-all-answers-500-with-unavailable.md) `[x]` - server: the catch-all answers 500 with the `unavailable` code
- [B-33](docs/backlog/B-33-the-product-description-headline-is-never.md) `[x]` - server: the product description's headline is never sent
- [B-35](docs/backlog/B-35-the-app-loads-screens-and-navigates.md) `[x]` - client: the app loads screens from the server and navigates between them
- [B-36](docs/backlog/B-36-a-reloaded-or-shared-address-answers-404.md) `[x]` - server: a reloaded or shared storefront address answers 404
- [B-38](docs/backlog/B-38-re-render-the-search-references-with-grayscale.md) `[x]` - design refs: re-render the Search references with grayscale text

**Cart and sign-in**

- [B-11](docs/backlog/B-11-guests-cart-promo-codes-changed-lines.md) `[x]` - server: guests, cart, promo codes, changed lines, the Cart tree
- [B-12](docs/backlog/B-12-shildik-sign-in-in-the-browser.md) `[x]` - server + client: shildik sign-in in the browser, customer creation, cart merge, header states
- [B-13](docs/backlog/B-13-cart-renderers.md) `[x]` - design refs + client: Cart renderers
- [B-37](docs/backlog/B-37-actions-the-trees-draw-but-do-not-carry.md) `[x]` - server + client: actions the trees draw but do not carry

**Checkout and orders**

- [B-14](docs/backlog/B-14-checkout-tree-slots-with-capacity-pickup.md) `[x]` - server: checkout tree, slots with capacity, pickup points, quote, the address form
- [B-15](docs/backlog/B-15-checkout-renderers-and-the-address-form.md) `[x]` - design refs + client: Checkout renderers and the address form
- [B-16](docs/backlog/B-16-placement-with-an-idempotency-key-the.md) `[x]` - server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator
- [B-17](docs/backlog/B-17-fulfilment-simulator-capture-per-shipment-pickup.md) `[x]` - server: fulfilment simulator, capture per shipment, pickup codes
- [B-39](docs/backlog/B-39-placement-refuses-a-quote-checkout-holds.md) `[x]` - server: placement refuses a quote the checkout is holding
- [B-40](docs/backlog/B-40-saving-an-address-updates-it-in-place.md) `[x]` - server: saving the address being delivered to updates it in place
- [B-41](docs/backlog/B-41-sign-in-returns-to-where-it-was-asked.md) `[x]` - client: sign-in returns to where it was asked for
- [B-42](docs/backlog/B-42-a-refused-address-form-holds-pickup-orders.md) `[x]` - server: a refused address form does not hold a pickup or locker order

**Reviews**

- [B-22](docs/backlog/B-22-reviews-and-questions-the-two-dialog.md) `[x]` - server + client: reviews and questions, the two dialog routes and forms
- [B-43](docs/backlog/B-43-helpful-votes-on-reviews.md) `[x]` - server + client: «Helpful» votes on reviews

**Ship**

- [B-28](docs/backlog/B-28-first-load-size-and-time-of.md) `[x]` - measure: first-load size and time of the wasm bundle
- [B-30](docs/backlog/B-30-product-images-through-object-storage.md) `[x]` - product images through object storage
- [B-34](docs/backlog/B-34-the-stand-serves-the-bundle-uncompressed.md) `[x]` - ops: the stand serves the wasm bundle uncompressed

<!-- END INDEX -->

## Decisions worth not re-litigating

**A client item is accepted against the canvas, not against a description of it.** Every client
item's acceptance is `viddikDesignParity` within tolerance for the artboards it names; a number
above the tolerance comes with a reason, never with a loosened threshold.

**The feature, screen, endpoint and service documents are drafted in an open pull request.** They
describe intent until the code behind them exists, and intent does not live on `main`. An item that
lands a feature moves its documents from `draft` to `active` in the same change, after checking each
scenario against the real status codes and error strings.
