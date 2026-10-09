---
id: B-57
title: "server: a deal's price is what the cart charges"
status: done
priority: P1
size: S
stage: stage-4-cart
blocked_by: [B-53]
---

# B-57 — server: a deal's price is what the cart charges

Deal cards (`dealCards`) draw `deals.price_cents`, but the card's «+» puts the SKU in the cart, which charges
`skus.price_cents`. The Sony deal happens to match ($349); the five generated deals are drawn at 70 % of the
price the cart then charges in full — the storefront advertises a price it does not honour. Found by B-53.

Decided as product owner: a deal is a price of its SKU for its window. While a deal is live its price is what
every surface draws and charges — the deal card, the product page, the cart, the quote, placement — through
the same pricing rule B-53 made (`CampaignPricing`); outside its window the SKU sells at its campaign or
regular price. A deal and a campaign on one SKU: the lower price wins.

- AC: route tests for a generated deal through card → cart → checkout → placement, the amount authorised
  equal to the card's price; after the deal's window the card is gone and the cart charges the regular price;
  the Sony deal unchanged; goldens unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/CampaignPricing.kt`.

## Done (2026-10-09)

- **«Live»** (recorded in research, «Decided in B-57»): a deal is live from `deals.starts_at` up to, not including,
  `deals.ends_at` on the store's clock (`Deal.liveAt`). V1 gave a deal only its end, so with the columns as they were a
  deal would have been live from whenever it was written — the Sony deal would then sell at $349 to a guest on the
  Plus early-access day, which B-53's tests refuse. `V27__deal_windows.sql` adds `starts_at`, backfilled for a deal
  written before it as a deal of the day (research D7: its end minus a day), `NOT NULL`, with `starts_at < ends_at`
  checked; the seed opens the canvas's deals at the canvas's day's midnight (`CatalogSeed.DEALS_START`, the sale's
  start) and keeps their end. No seed date moved (B-58's).
- **One pricing path.** `CampaignPricing.priced` takes the SKU's deals: a live deal below the price the SKU would
  otherwise sell at — the campaign's while the campaign is open for the viewer, the regular one before — is the SKU's
  price, and the price it beats is struck through; a deal at or above it changes nothing, so a deal and a campaign
  sell at the lower. `ExposedCatalogRepository` reads the deals of the SKUs it prices beside their campaign, and
  `deals()` returns only the deals live on its clock. `dealCards` draws the deal's SKU as the catalog prices it, so
  the card, «+», the product page, search, the Saved list's drops, the cart, the quote and placement read one price.
- **The struck-through price** is what the canvas drew: the generated deals at 70 % under the SKU's (sale) price, the
  Sony deal at the sale's $349 under the regular $449, −22 %.
- **Tests** — `DealPriceTest` (route, shildik, a fresh seeded database each, Sam as the buyer): a generated deal outside
  the sale ($303.09 under $432.99) from the home page's card through the product page with that SKU, «+», the cart
  line, the checkout's total, to an authorisation of 30,309; at `DEALS_END` the card is gone, the product page is
  $432.99 with nothing struck through, the line is «Price changed: now $432.99», and placement authorises 43,299; the
  Sony deal stays $349 under $449 −22 % and is charged 34,900, and the day after it the sale still sells the headphones
  at $349; the Sony deal moved to $399 leaves $349 under $449, moved to $299 makes it $299 under $349 on the card, the
  page, the cart and the charge (29,900). `CampaignPricingTest`: the window's four edges, the lower of deal and
  campaign (below, equal, above), a deal before the campaign opens, two deals and another SKU's.
  `DealWindowMigrationTest`: a deal written at V26 opens where the seed opens it. Changed: `DrawnActionsTest`'s
  on-sale oracle prices the seed's deals (one generated deal outside the sale is now on sale: 832 items, was 831);
  `CartFixturesTest`'s shape check for the canvas's picks (below).
- **Mutations** (each on the committed change, restored after, `git status` clean): the card on `deals.price_cents`
  and the cart back on `skus.price_cents` — 4 failures (card → placement «the product page and the card disagree»,
  after the window, deal + campaign, the on-sale count); `liveAt` without the end — 3 (after the window, Sony the day
  after, the window's edges); without the start — 5 (the window's edges, four of `PlusEarlyAccessTest`: the Sony deal
  would sell on the early-access day); a deal that wins even when it is not lower — 8 (the tie leaves the Sony card
  at $349 with nothing struck through, the cart fixtures, the early-access charge…); V27's backfill two days back — 1 (the
  migration test).
- **Goldens and bodies**: `viddikVerify` green (136), no client file changed. The server-built bodies compared with
  the client's are unchanged at the canvas clock, with one investigated difference: the empty cart's picks are the
  live deals' products, and since every one of them is now under its old price none is drawn without `oldPrice` and
  `badge`, the shape of the canvas's six picks (which the body keeps, the canvas drawing products the seed does not
  have). `cart_empty.json` is not regenerated: the check now also accepts the server's card shape without those two
  keys, which `card` writes or omits together.
- **Where it ran**: the WSL box — `:composeApp:wasmJsBrowserDistribution`, then `check :server:installDist` (server 72
  classes, 353 tests; desktop 173; `viddikVerify` 136; the wasm browser tests skipped there for want of a browser),
  `scripts/image-check.sh` (`haul/server:b57`, port 18157: ready, 1045 of 1045 classes from the AOT cache) and
  `scripts/e2e.sh` against that image (`WholePathTest`).

### Findings

- **The stand loses its deals with this change, until B-58.** The stand runs the wall clock; V27 opens its seeded deals
  on 2025-10-07 and they end on 2025-10-08, so the home page's and the deals page's «Deals of the day» and the empty
  cart's «Picked for you» draw empty grids, and the five generated SKUs go back to their price. The canvas clock is
  inside the window (19:47 on Oct 7), so tests and goldens are untouched. **For B-58**: the seed only runs on an empty
  catalogue (`Seeder.seedIfEmpty`), so dating the seed relative to the day it seeds re-dates nothing on a stand that
  is already seeded — that needs its own step (a migration or a start-up re-date) — and an empty «Deals of the day»
  section is drawn as a header over nothing, which feature-browse does not describe.
- **Deals now count as Saved price drops** — a deal is the SKU's price wherever it is read; research's Saved rule said
  the opposite and is corrected. Maya's seeded Saved list leaves the deals' products out, so no fixture moved.

