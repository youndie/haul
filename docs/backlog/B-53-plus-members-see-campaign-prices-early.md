---
id: B-53
title: "server: Plus members see campaign prices early"
status: done
priority: P3
size: S
stage: stage-8-loyalty
blocked_by: [B-23]
---

# B-53 — server: Plus members see campaign prices early

The Plus trial dialog sells «Early access to sales», and the seed stores `campaigns.plus_early_access_at`,
but no price reads it: a member sees a campaign's prices when everyone does. Found by the PR #2 docs sync
after B-25.

Decided as product owner: from `plus_early_access_at` until the campaign's start, a member (trial included)
gets the campaign's prices everywhere a price is drawn and charged — cards, product page, cart, checkout — and
a non-member the regular ones; from the start, everyone. The quote's fingerprint already follows the price,
so a membership that ends mid-checkout redraws as `cart_changed`.

- AC: route tests for a member and a non-member inside the early window and after the start, through the
  cart to the checkout's total; the canvas's goldens unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/data/CatalogTables.kt`.

## Done (2026-10-09)

- **Decisions** (recorded in research D6, «Decided in B-53»): a SKU names its campaign (`skus.campaign_slug`,
  `V26__sku_campaigns.sql`, a foreign key and a check that a campaign's price is a markdown; V24 is left unused, since
  B-45's V25 merges first and Flyway refuses a lower version after it); its stored price is the campaign's and its old
  price the regular one. The seed puts every markdown into the Autumn mega sale
  (`CatalogSeed.inCampaigns`, after generation, so the random stream and every other row are as they were); a
  catalogue seeded before V26 keeps no links. Only the opening is read, not `ends_at` (below).
- **One pricing path.** `CampaignPricing.priced` (`feature/catalog/domain/CampaignPricing.kt`) is applied by
  `ExposedCatalogRepository` to every read that returns SKUs, at the store's clock; `listedIn`, `listed`,
  `listedBySkus` and `product` take a `PriceList` (`Public` / `Plus`) with no default, so every caller says whose
  prices it draws: screens from `Viewer.prices` (the signed-in customer's `plus`, a trial included), the cart, the
  quote and placement from `CartOwner.prices`, the Saved list and the account's drops from the customer. Reads that
  draw no price (reviews' existence check, the account's tones, the order page's options) say `Public` with a comment.
  A trial started mid-checkout changes the line's price, which the cart marks as changed (B-11's rule) and the quote's
  fingerprint answers with `409 cart_changed`.
- **Caching**: none. Every tree is built per request for its caller and the answers carry no `Cache-Control`; the
  route test asks the member's page first and then the guest's and the non-member's, and asserts no answer is marked
  `public`.
- **Tests** — `PlusEarlyAccessTest` (route, shildik, a fresh seeded database each, the store's clock moved inside the
  test): on Oct 6, noon, the product page and the card are $349 under $449 for Maya and $449 with nothing struck through
  for a guest and for Sam; Maya's seeded cart is $512 in the cart and at checkout and placement authorises 51,200
  cents; Sam's headphones and mugs are $473 through to an authorisation of 47,300; Sam's trial mid-checkout makes the
  headphones «Price changed: now $349», the page drawn before `409 cart_changed`, and, accepted, $373 to 37,300; at the
  sale's start a guest and Sam see $349 under $449 and Sam's line is checked out and charged at $373.
  `CampaignPricingTest`: the rule at its edges (one second before early access, the early instant, one second before
  the start, the start; a campaign without early access; a SKU in no campaign). Changed to the new signatures:
  `HaulTestApp.haulTest` (a `clock` parameter, the canvas's by default) and the tests that build the repository or
  read it directly.
- **Mutations** (each on the committed change, restored after, `git status` clean): the member's opening made the
  public one — 4 failures (`CampaignPricingTest` early window; the route tests for the member's pages, charge and
  trial); `priced` returning the stored SKU — 7 failures (three of the rule's edges, and every route test but the
  member's charge, which reads the stored price either way); `Viewer.prices` always `Plus` (the leak) — 2 failures (the early-window pages, «from the start»);
  `CartOwner.Customer.prices` always `Public` — 2 failures (the member's charge, the trial).
- **Where it ran**: the WSL box — `:composeApp:wasmJsBrowserDistribution`, then `check :server:installDist`, then
  `scripts/e2e.sh` against an image of this branch (`WholePathTest`), each green on the branch as first written and
  again after every rebase (onto B-50 and B-52, B-29, then B-45 and B-55 with the migration numbered V26; last:
  server 70 classes, 341 tests; desktop 173; `viddikVerify` 136; the wasm browser tests skipped there for want of a
  browser).
- **Goldens and fixtures**: the canvas's «now» (Oct 7, 19:47) is after the sale's start, so every seeded viewer's prices
  on it are the ones they were; `viddikVerify` green (136), the fixture tests that compare server-built bodies with the
  client's (`CartFixturesTest`, `CheckoutFixturesTest`, `SavedFixturesTest`, `OrderFixturesTest`…) unchanged.

### Findings

- **A second price path: deals.** `dealCards` (`feature/catalog/screen/Cards.kt`) draws `deals.price_cents`, but «+»
  puts the SKU into the cart, which charges `skus.price_cents` — the Sony deal is the same $349, the five generated
  deals are drawn at 70 % of the price the cart then charges in full. And `deals.ends_at` is read by nothing: the
  seed's deals of Oct 7 are drawn on the stand today, against feature-browse's «an ended deal disappears on the next
  load». Not this item's: worth an item of its own.
- **What a campaign's prices do after `ends_at`** is undecided; nothing read it before this item either. Every seeded
  campaign ended in October 2025, so ending them would take every markdown off the stand at once. A question for the
  owner if it matters.
