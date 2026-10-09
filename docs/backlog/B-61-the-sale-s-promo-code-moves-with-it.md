---
id: B-61
title: "server: the sale's promo code moves with the sale"
status: done
priority: P3
size: S
stage: stage-4-cart
blocked_by: [B-58]
---

# B-61 — server: the sale's promo code moves with the sale

Since B-58 the seed dates the Autumn sale from the day it seeds and re-dates an ended sample sale at start, but the
sale's promo code `AUTUMN10` keeps the canvas's 2025 window: on a stand's wall clock it answers `promo_expired`,
while the sale it belongs to is live. Found by B-58.

Decided: the seed's promo codes that belong to the sample sale are dated with it — moved by the same whole store
days in `CatalogSeed.generate(day)` and by `Seeder.redateSale` — and nothing else's codes move.

- AC: a stand seeded on another day accepts `AUTUMN10` during its sale and refuses it after (`promo_expired`); the
  re-date moves the code with the sale; tests on the canvas clock unchanged.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/seed/Seeder.kt`.

## Done (2026-10-09)

Recorded in research, «Decided in B-58» (the bullet that said promo codes are not moved now says how the sale's
code is).

- **How a code belongs to the sale**: the seed says so — `SeedPromoCode.campaignSlug` (`seed/SamplePromoCodes.kt`):
  `AUTUMN10` names `autumn-mega-sale`, `SUMMER5` names nothing. Not a `promo_codes.campaign_slug` column (V28): nothing
  but the seed would read it — a code's validity is its own window, checkout never asks which sale a code is — and a
  column would not tell the seed's code from a store's own code of the same sale, which the decision says never moves.
  No migration.
- **The seed** (`CatalogSeed.generate(day)`): a code that names a campaign is moved by the same whole store days as the
  campaigns and the deals (`on(day)`), so on the canvas's day every row is the canvas's and on a stand `AUTUMN10`'s
  window is exactly the Autumn mega sale's; `SUMMER5` keeps 2025-08-01…09-01, expired everywhere.
- **The re-date** (`Seeder.redateSale`): under the same condition and in the same transaction as the campaigns and the
  deals, the sale's codes are written to the fresh seed's window, matched by code **and** terms (percent off, cap) —
  the deal's id-and-SKU rule — so a store's own code, `SUMMER5`, and `AUTUMN10` under a store's own terms are not
  written.
- **Tests** — all in `SaleCalendarTest`: the canvas's day seeds `SamplePromoCodes.all` unchanged and another day
  differs only in the windows, `AUTUMN10` moved back onto the canvas's, `SUMMER5` the same; on Jan 14, 2026 the code's
  window is the sale's; the one code that names a campaign names a seeded one; a database seeded on Jan 14, 2026 takes
  `AUTUMN10` over HTTP that evening and a minute before the sale's last midnight, and answers `422 promo_expired` from
  that midnight; the re-date moves `AUTUMN10` with the sale (and `SUMMER5` stays) to exactly a fresh seed's windows,
  not under a live deal, and leaves a store's own `OWN10` of the sale's window and a re-run `SUMMER5` alone; a store's
  15 % `AUTUMN10` stays while the rest of the sale moves.
- **Mutations** (each on the committed change, restored after, `git status` clean): `generate` not moving codes — 3
  (the windows, the winter day, and the HTTP test, which answered `promo_expired` at the stand's evening: the bug);
  the re-date not writing codes — 2; the re-date matching by code only — 1 (the store's terms); the re-date writing
  every seeded code — 1 (the re-run `SUMMER5`).
- **Unchanged at the canvas clock**: every fixture and body (`CartRoutesTest`, `CheckoutQuoteTest`,
  `CartFixturesTest`), `SeedDigest`'s rows, `viddikVerify`; no client file changed.
- **Where it ran** (the Linux box): `check :server:installDist` — `:server:test` 368 tests, `:composeApp:desktopTest`
  173, `viddikVerify`, ktlint, all green; `:composeApp:wasmJsBrowserDistribution`; `scripts/e2e.sh` against the
  branch's image — `WholePathTest` green. No migration, so `scripts/image-check.sh` was not needed.
