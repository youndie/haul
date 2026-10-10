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
| `stage-10-review` | Review | what a walk through the live stand found: controls that do nothing, behaviour that is wrong or surprising, placeholders |

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (7)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-31](docs/backlog/B-31-a-traffic-generator-that-walks-the.md) `[ ]` | synthetic shoppers: a traffic generator that walks the e2e path continuously on the stand | P2 | M | B-26, B-27 |
| [B-67](docs/backlog/B-67-error-pages-header-is-dead.md) `[ ]` | client: the header on loading, error and not-found pages leads somewhere | P2 | S | - |
| [B-71](docs/backlog/B-71-product-page-dead-links.md) `[ ]` | client + server: what looks pressable on the product page does something | P2 | M | - |
| [B-72](docs/backlog/B-72-header-and-footer-dead-words.md) `[ ]` | client + server: the header strip and the footer do not pretend to be links | P2 | S | - |
| [B-78](docs/backlog/B-78-placeholders-on-the-stand.md) `[ ]` | server + client: the stand shows pictures, not placeholder labels | P2 | M | - |
| [B-80](docs/backlog/B-80-blank-first-load.md) `[ ]` | client: the first load shows something before the app starts | P2 | S | - |
| [B-79](docs/backlog/B-79-checkout-step-indicator-and-links.md) `[ ]` | client + server: checkout's step indicator and summary tell the truth | P3 | S | - |

## Closed (73)

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
- [B-52](docs/backlog/B-52-bought-this-month-on-the-product-page.md) `[x]` - server: «bought this month» on the product page
- [B-59](docs/backlog/B-59-home-s-promo-banners-lead-somewhere.md) `[x]` - server: home's promo banners lead somewhere

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
- [B-45](docs/backlog/B-45-a-product-s-listing-name.md) `[x]` - server: a product's listing name, as cards, cart and orders write it
- [B-46](docs/backlog/B-46-a-closed-sign-in-popup-settles.md) `[x]` - client: a closed sign-in popup settles the sign-in
- [B-47](docs/backlog/B-47-a-sign-in-that-fails-with-a-js-error.md) `[x]` - client: a sign-in that fails with a browser error ends as not gone through
- [B-48](docs/backlog/B-48-the-product-page-adds-to-cart-and-buys-now.md) `[x]` - server + client: the product page adds to cart and buys now
- [B-49](docs/backlog/B-49-the-last-drawn-controls-without-actions.md) `[x]` - server + client: the last drawn controls without actions
- [B-54](docs/backlog/B-54-the-filter-sheet-stays-open-while-filtering.md) `[x]` - client: the filter sheet stays open while filtering
- [B-57](docs/backlog/B-57-a-deal-s-price-is-what-the-cart-charges.md) `[x]` - server: a deal's price is what the cart charges
- [B-58](docs/backlog/B-58-campaigns-and-deals-end.md) `[x]` - server: campaigns and deals end
- [B-61](docs/backlog/B-61-the-sale-s-promo-code-moves-with-it.md) `[x]` - server: the sale's promo code moves with the sale

**Checkout and orders**

- [B-14](docs/backlog/B-14-checkout-tree-slots-with-capacity-pickup.md) `[x]` - server: checkout tree, slots with capacity, pickup points, quote, the address form
- [B-15](docs/backlog/B-15-checkout-renderers-and-the-address-form.md) `[x]` - design refs + client: Checkout renderers and the address form
- [B-16](docs/backlog/B-16-placement-with-an-idempotency-key-the.md) `[x]` - server: placement with an idempotency key; the petich saga — reserve, authorise, confirm, compensate; the payment simulator
- [B-17](docs/backlog/B-17-fulfilment-simulator-capture-per-shipment-pickup.md) `[x]` - server: fulfilment simulator, capture per shipment, pickup codes
- [B-18](docs/backlog/B-18-order-tree-and-renderers-reorder.md) `[x]` - server + client: Order tree and renderers, reorder
- [B-39](docs/backlog/B-39-placement-refuses-a-quote-checkout-holds.md) `[x]` - server: placement refuses a quote the checkout is holding
- [B-40](docs/backlog/B-40-saving-an-address-updates-it-in-place.md) `[x]` - server: saving the address being delivered to updates it in place
- [B-41](docs/backlog/B-41-sign-in-returns-to-where-it-was-asked.md) `[x]` - client: sign-in returns to where it was asked for
- [B-42](docs/backlog/B-42-a-refused-address-form-holds-pickup-orders.md) `[x]` - server: a refused address form does not hold a pickup or locker order
- [B-44](docs/backlog/B-44-a-guest-on-a-customer-page-is-sent-to-sign-in.md) `[x]` - client: a guest on a customer page is sent to sign-in

**Account**

- [B-19](docs/backlog/B-19-account-overview-and-orders-history.md) `[x]` - server + client: Account overview and orders history
- [B-20](docs/backlog/B-20-saved-list-save-for-later-price.md) `[x]` - server + client: Saved list, save for later, price drops
- [B-21](docs/backlog/B-21-returns-and-refunds.md) `[x]` - server + client: returns and refunds
- [B-50](docs/backlog/B-50-the-return-dialog-names-the-points-share.md) `[x]` - server + client: the return dialog names the points share of a refund
- [B-51](docs/backlog/B-51-dialog-commands-are-not-review-commands.md) `[x]` - client: dialog commands are not review commands
- [B-55](docs/backlog/B-55-the-order-page-names-the-card-refund.md) `[x]` - server: the order page names what the card gets back

**Reviews**

- [B-22](docs/backlog/B-22-reviews-and-questions-the-two-dialog.md) `[x]` - server + client: reviews and questions, the two dialog routes and forms
- [B-43](docs/backlog/B-43-helpful-votes-on-reviews.md) `[x]` - server + client: «Helpful» votes on reviews
- [B-60](docs/backlog/B-60-dialogs-write-the-listing-name.md) `[x]` - server + shared: dialogs write the listing name, one groupedCount

**Loyalty**

- [B-23](docs/backlog/B-23-haul-plus-trial-and-benefits-points.md) `[x]` - server + client: Haul Plus trial and benefits, points ledger, redemption at checkout, delivery savings
- [B-24](docs/backlog/B-24-haul-pay-4-payments-two-weeks.md) `[x]` - server + client: Haul Pay, 4 payments two weeks apart
- [B-25](docs/backlog/B-25-recommendations-from-views-in-the-home.md) `[x]` - server: recommendations from views in the Home tree
- [B-53](docs/backlog/B-53-plus-members-see-campaign-prices-early.md) `[x]` - server: Plus members see campaign prices early

**Ship**

- [B-26](docs/backlog/B-26-the-whole-path-over-http-against.md) `[x]` - e2e: the whole path over HTTP against the composed stack
- [B-27](docs/backlog/B-27-helm-chart-tracy-metrik-katcher-wiring.md) `[x]` - ops: Helm chart, tracy / metrik / katcher wiring, the public demo stand
- [B-28](docs/backlog/B-28-first-load-size-and-time-of.md) `[x]` - measure: first-load size and time of the wasm bundle
- [B-29](docs/backlog/B-29-live-order-tracking-through-kompot-realtime.md) `[x]` - live order tracking through kompot-realtime
- [B-30](docs/backlog/B-30-product-images-through-object-storage.md) `[x]` - product images through object storage
- [B-34](docs/backlog/B-34-the-stand-serves-the-bundle-uncompressed.md) `[x]` - ops: the stand serves the wasm bundle uncompressed
- [B-56](docs/backlog/B-56-placement-moves-the-order-page-live.md) `[x]` - server: placement moves the order page live
- [B-62](docs/backlog/B-62-the-page-keeps-its-tree.md) `[x]` - client: a filter or a sort redraws the results, not the page
- [B-63](docs/backlog/B-63-answers-replace-what-changed.md) `[x]` - server + client: a filter answers with the parts that changed
- [B-64](docs/backlog/B-64-drop-the-override-workaround.md) `[x]` - client: back after a filter draws the address's page with kompot's own reset
- [B-65](docs/backlog/B-65-every-id-once-per-page.md) `[x]` - server: every node id appears once on a page

**Review**

- [B-66](docs/backlog/B-66-sign-in-reachable-from-everywhere.md) `[x]` - client: sign-in and sign-out work from every page and every way in
- [B-68](docs/backlog/B-68-first-filter-reloads-the-page.md) `[x]` - server + client: the first filter on a category does not reload the page
- [B-69](docs/backlog/B-69-price-facet-does-nothing.md) `[x]` - client + server: the price filter can be used
- [B-70](docs/backlog/B-70-deals-vanish-at-midnight.md) `[x]` - server: a running stand keeps its deals of the day
- [B-73](docs/backlog/B-73-phone-cannot-reach-catalog-and-orders.md) `[x]` - client: on a phone every part of the store is reachable
- [B-74](docs/backlog/B-74-account-tiles-lead-nowhere.md) `[x]` - client + server: the account page's tiles and old orders open
- [B-75](docs/backlog/B-75-feedback-after-a-press.md) `[x]` - client: a press shows that it worked
- [B-76](docs/backlog/B-76-promo-apply-sends-nothing.md) `[x]` - client: «Apply» on the cart's promo code is applied
- [B-77](docs/backlog/B-77-pages-beyond-four-unreachable.md) `[x]` - client + server: every page of results can be reached

<!-- END INDEX -->

## Decisions worth not re-litigating

**A client item is accepted against the canvas, not against a description of it.** Every client
item's acceptance is `viddikDesignParity` within tolerance for the artboards it names; a number
above the tolerance comes with a reason, never with a loosened threshold.

**The feature, screen, endpoint and service documents are drafted in an open pull request.** They
describe intent until the code behind them exists, and intent does not live on `main`. An item that
lands a feature moves its documents from `draft` to `active` in the same change, after checking each
scenario against the real status codes and error strings.
