---
id: B-58
title: "server: campaigns and deals end"
status: done
priority: P2
size: S
stage: stage-4-cart
blocked_by: [B-57]
---

# B-58 — server: campaigns and deals end

Nothing reads `deals.ends_at` or `campaigns.ends_at`: the seeded October deals are still drawn on the stand, and
a campaign's prices never end. Every seeded window is dated around the canvas's day, so ending them as they
stand would remove every markdown from the stand at once. Found by B-53.

Decided as product owner: a deal's card and price end at its `ends_at`, and a campaign's prices end at its
`ends_at` (the SKU returns to its regular price). The seed dates its campaign and deal windows relative to the
day it seeds, so a stand keeps a live sale; tests keep the canvas's fixed clock and the canvas's dates.

- AC: route tests on the store clock before and after a deal's and a campaign's end; a stand seeded today shows
  the canvas's markdowns; the goldens unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/CampaignPricing.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/seed/CatalogSeed.kt`, `server/src/main/kotlin/io/github/youndie/haul/seed/Seeder.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/Cards.kt`.

## Done (2026-10-09)

Recorded in research, «Decided in B-58».

- **A campaign ends** (`CampaignWindow.liveFor`): from its `ends_at` the SKU is at its regular price with nothing struck
  through, for everybody — early access opens a sale sooner, never keeps it longer. A deal that outlives its campaign
  beats the regular price. The deal half was B-57's (`Deal.liveAt`, `deals()` returns live deals); checked, unchanged.
- **No deals, no section**: «Deals of the day» on home and on `/deals`, and the empty cart's «Picked for you», are left
  out — header, countdown and grid — while no deal is live.
- **The countdown reads the soonest live deal's `ends_at`** in the store's zone (`dealsOfTheDay`); a seeded deal of the
  day ends at the store's midnight (D7), so every body at the canvas clock is unchanged; `DeliveryCalendar.midnight()`
  is gone.
- **The seed dates its sale from a day** (`CatalogSeed.generate(day)`): the canvas's windows moved by whole New York days,
  on local midnights across a change of clocks; nothing else moves. Tests seed `CatalogSeed.CANVAS_DAY` (the canvas's
  rows exactly); `main` seeds the store's day (`CatalogSeed.dayOf(clock.now())`).
- **A seeded stand is re-dated at start, no migration** (`Seeder.redateSale`, called by `main` only under
  `HAUL_SEED`): the sample sale — campaigns by slug, deals by id and SKU — moves to the store's day, to exactly what a
  fresh seed of that day writes, once every sample deal has ended by that day's start; never under a live deal, at most
  once a day, under the seeding lock. A V28 would have re-dated once, at deploy, and on every database, seeded or not;
  no migration was taken.
- **Tests** — `CampaignEndTest` (route, shildik, a fresh seeded database each): the headphones' card and product page
  $349 under $449 in the sale's last minute, $449 with nothing struck through at its end for Sam and for Maya (Plus);
  Sam's headphones and mugs put in at $349 + $24 become «Price changed: now $449», the quote drawn before the end is
  `409 cart_changed`, and once accepted the cart and checkout are $473 and placement authorises 47,300.
  `DealsOfTheDayTest`: at the deals' end home, `/deals` and a guest's empty cart draw no deals section (controls: they
  do at the canvas clock; home's categories, `/deals`' «On sale» and the empty message stay); a deal ending at 21:00 is
  counted down to 21:00 on home and `/deals`, and at 21:00 its card is gone and the countdown runs to midnight.
  `SaleCalendarTest`: the canvas's day seeds the canvas's rows, another day differs only in the windows; a winter day's
  windows sit on New York's midnight (−05:00); a stand seeded on Jan 14, 2026 drawn that evening has the canvas's six
  deal cards (prices, struck-through prices, badges), the same «N items on sale», the headphones at $349 under $449,
  the hero «Autumn mega sale · Jan 14 — 21» and the countdown to that midnight; the re-date moves an ended sale to
  exactly a fresh seed's windows and the home page draws six deals again, does not move a live one or move twice a
  day, and leaves a deal under a sample id on another SKU and a store's own deal alone. `CampaignPricingTest`: the
  campaign's end for both price lists, a deal after the campaign. Changed: `DealPriceTest` reads an absent deals
  section as no cards; every `CatalogSeed.generate()` in the tests names `CANVAS_DAY`. The e2e (`WholePathTest`) now
  checks the image's home page draws live deals, each over the price it beats.
- **Mutations** (each on the committed change, restored after, `git status` clean): the campaign window without its end
  — 4 (`CampaignEndTest` ×2, `CampaignPricingTest` ×2); the seed not moving the windows — 5 (all of
  `SaleCalendarTest`); moving them by fixed-offset days instead of store days — 3; the re-date without the «all ended»
  check — 2; the re-date matching deals by id only — 1; the countdown to the latest end instead of the soonest — 1; a
  deals section with no deal — 1; the empty cart's picks with no deal — 1; the image seeding the canvas's day — the
  e2e, «the stand's home page draws no deal of the day».
- **Goldens and bodies**: `viddikVerify` green, no client file changed; the server-built bodies compared with the
  client's fixtures unchanged at the canvas clock (the countdown string is the same `2025-10-08T00:00-04:00`).

### Findings

- **Home's campaign banners are still drawn after their campaign ends** — B-59 owns it («a banner whose campaign has
  ended is not drawn»); so is the hero, which no item mentions: past the sale's end on a stand that does not restart,
  the hero announces a sale whose prices are gone. B-59 is the place to decide the hero too.
- **The empty cart's copy** «Today's deals end at midnight — up to −70 % in the Autumn mega sale.» is the canvas's and
  stays when no deal is live or the sale has ended.
- **Promo codes keep the canvas's windows**: `AUTUMN10` runs to Oct 15, 2025, so on a stand's wall clock it answers
  `promo_expired`. No item asks for it to move with the sale.
- **A stand that runs for days without a restart** still loses its deals at midnight and its sale on the eighth day; the
  next start brings them back. A daily re-date in-process was not built (no item asks for a worker).
