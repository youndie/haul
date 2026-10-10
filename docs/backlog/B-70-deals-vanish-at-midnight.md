---
id: B-70
title: "server: a running stand keeps its deals of the day"
status: done
priority: P1
size: M
stage: stage-10-review
---

# B-70 — server: a running stand keeps its deals of the day

On 2026-10-10 the stand (started 2026-10-09) had no «Deals of the day» on home or `/deals`: the seeded deals end at
the store's midnight and are re-dated only at start-up (B-58 recorded it: «a stand running for days without a
restart loses its deals at midnight»). With them gone a guest's home page shows no product at all — hero, banners,
categories, Plus, footer. The sale itself ends on the eighth day the same way.

- AC: on a stand that has run past midnight (and past the sale's end) home and `/deals` draw deals of the day, with
  a countdown to the next midnight; the sample sale keeps moving the way the start-up re-date moves it; a test with
  a moving store clock across two midnights; a guest's home always has at least one row of products.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/Seeder.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/DealsScreen.kt`.

## Done (2026-10-10)

Recorded in research, «Decided in B-70» (the B-58 bullet that said a running stand loses its deals now points there).

- **The start-up re-date runs at each store midnight** (`SaleRedater`, `seed/SaleRedater.kt`): each look is
  `Seeder.redateSale` with a fresh seed of the store's day at that moment, unchanged — the seed's own rows only (campaigns
  by slug, deals by id and SKU, `AUTUMN10` by code and terms), under the seeding lock, never under a live deal, at most
  once a day. So the sale moves exactly as a start on that day moves it: the deals open at the midnight and are counted
  down to the next, the hero's week and the banners move with them, and the sale never reaches its eighth day. It looks
  at each store midnight and at least hourly (`SaleRedater.LOOK`), never sooner than a second apart, so a process that
  slept past a midnight catches up at the next look. `main` starts it only under `HAUL_SEED`
  (`haulModule(saleLook = …)`); tests start nothing unless they ask. No migration, no new table.
- **A popular row while no deal is live** (`HomeScreen.popular`): «Popular on Haul» (`popular-title`, `popular`) takes
  the deals' place — the six most reviewed products in stock, at the viewer's prices — so a guest's home has a row of
  products in the seconds between a midnight and the look, on a store never seeded, or once a store's own deals end.
  Never drawn beside live deals: at the canvas clock every body is unchanged. Decided as a default (the item asks for
  «at least one row of products», not which): a measure that needs no window is the only one that is there whatever
  the calendar; «On sale» would be empty once the sale ends.
- **Tests** — `SaleCalendarTest`: `a running stand moves its sale at each midnight` (a moving store clock across Oct 8
  and Oct 9: just past each midnight no deals and the popular row; the look moves the sale to exactly a fresh seed's
  windows of the day, once; home and `/deals` draw six deals counted down to the next midnight under «Autumn mega sale ·
  Oct 8 — 15» / «Oct 9 — 16»; then on Oct 20, past the sale's own end with no look between, no hero and the popular
  row, and the next look brings «Oct 20 — 27» and six deals to Oct 21); `the running look moves an ended sale by itself`
  (the loop, started in a scope, moves the sale with nobody calling it); `the next look is at the store's midnight`
  (the hour cap, 23:30 → 30 min also when «now» comes in UTC, half a second to midnight → one second).
  `DealsOfTheDayTest`: `with no live deal home draws the popular row` (not beside live deals; at the deals' end the six
  most reviewed products in stock, in order).
- **Mutations** (each on the committed change, restored after, `git status` clean): the loop not calling the re-date —
  1 (the loop test); no popular row — 2 (the popular-row test, the moving-clock test: «a guest's home has no row of
  products before the look»); the popular row least-reviewed first — 1; no one-second floor — 1; the midnight read in
  «now»'s zone instead of the store's — 1 («midnight read in UTC»).
- **Unchanged**: the canvas's bodies and fixtures (deals are live at the canvas clock), no client file, no golden.
