---
id: B-166
title: "fix: sellers and brands in the sample data are invented, not real companies'"
status: done
priority: P2
size: M
stage: stage-15-showcase
---

# B-166 — fix: sellers and brands in the sample data are invented, not real companies'

The sample catalogue sells real products under real brands from sellers named after them — «Sony Official Store»,
«Bose Store», Sony WH-CH720N — next to invented reviews and prices (`seed/SampleCatalog.kt`). On a public stand
without a demo label that reads as those companies' stores, and with one it is still their trademarks on made-up
reviews. The product review (2026-11-01) flagged it as a risk worth removing before the stand is shown anywhere.

- **Invented brands and sellers, kept recognisable as kinds of things:** «Sonora WH-720 wireless headphones» from
  «Sonora Audio», and so on across the seed; the categories, prices and pictures stay. Tests and QA cases that name
  the old ones follow.
- Rejected: keeping the brands under the demo label (B-160) — the label answers «is this a store», not «may we use
  their names on invented reviews».

- AC: no real company's name in the seed, the trees or the goldens (a grep for the list removed); goldens re-recorded
  with `--force` and each changed one looked at; the QA cases updated.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`, `server/src/test/kotlin/io/github/youndie/haul/`,
  `docs/qa/`.

## Findings (2026-11-01)

- **What was renamed, old → new.** Sellers: Sony Official Store → Sonora Audio (`s-sonora-audio`), Bose Store →
  Bellwave Store (`s-bellwave-store`), Marshall Store → Mallard Store (`s-mallard-store`). Brands: Sony → Sonora,
  Bose → Bellwave, Marshall → Mallard. Products (ids follow): WH-1000XM6 → SH-718N (`p-sonora-sh-718n`, the hero),
  WH-CH720N → SH-C388N, WH-XB910N → SH-X527B, ULT Wear → Pulse 208T, WF-1000XM5 → SE-188, MDR-7506 → Monitor 630R,
  INZONE H9 → Arena 519, QuietComfort Ultra → Hush Ultra 165A, QuietComfort … Sandstone → Hush H597C … Sandstone,
  QuietComfort Earbuds II → Hush Buds 938A, QuietComfort 45 → Hush 120K, SoundLink Around-Ear Wireless II → Roam 580P
  Around-Ear Wireless, Major V → Mode 146. The codec LDAC → LC3 (the hero's connectivity and its codecs line). The
  canvas copy of the category's brand facet (client bodies only): JBL → Juno, Apple → Aspen, Sennheiser → Halvorsen.
  The canvas's reviews ids `r-sony-*`, `q-sony-*` → `r-sonora-*`, `q-sonora-*`. The seller's avatar letter stays
  (S, B, M).
- **Ids renamed, not kept**: an id is the product's address (`/p/p-sony-wh-1000xm6`), so keeping it kept the brand in
  every link. **The invented model numbers are chosen**, not decorative: `SeedArt` picks a picture's variant from the
  id's `String.hashCode()` (3), `SeedReviews` the copy of a product's generated reviews from it (997 salts), and lists
  break ties by the id. Each new id has its old id's hash modulo 2,991 and sorts where the old one did among every
  other id (Kotlin's order and PostgreSQL's collation both), so the pictures, the 2,339 generated reviews' copy and
  every list's order are what they were — the goldens changed only in words. `InventedBrandsTest` holds all three.
- **A stand's database follows (V31)**: the stand keeps its database across deploys and the seeder inserts into an
  empty catalogue only, so V31 renames a catalogue seeded before it — products, sellers, SKUs, and what names them:
  deals, carts, order lines (their copied names too), shipments, stock reservations and restocks, reviews with their
  generated ids and votes, questions, rating counts, Saved, product views, and the order sagas' and outbox's JSON. The
  keys that point at the renamed rows are `ON UPDATE RESTRICT`, so V31 drops and re-adds them as Exposed declares them
  (SchemaTest green). A photo the seed stored under the old id's folder is let go; the seed stores it again at the
  next start. `InventedBrandsMigrationTest`: a catalogue seeded the old way and migrated has a fresh seed's hash, and
  a stand's order, saga, outbox and vote follow. `ListingNameMigrationTest` and `BoughtBaseMigrationTest` seed the
  ids V23 and V25 knew and now read them renamed by V31.
- **Old links break**: `/p/p-sony-wh-1000xm6` and the other old addresses answer «not found» after the deploy, and an
  old replica still running against the migrated database during a rolling deploy can refuse a cart line or an
  order of an old SKU while both run. A demo's links; no redirect was added.
- **Left as they were, on purpose**: the canvas references (`snapshots/design/`, historical); the migrations before V31
  and the done backlog items and QA runs, which record what was (V23 and V25 still name `p-sony-wh-1000xm6` — editing
  an applied migration changes its checksum); the research's measured query list («sony» in the suggest-latency run
  of 2026-10-08) and its «WH-1000XM / 6» observation. The generated catalogue's brands (Northwind, Pace, Ridge, Lumen,
  …), seller words (Hudson, Bedford, …) and «Brooklyn Home Co.» are dictionary words and place names, not companies'
  brands, and stay; so do the canvas copy's «Ridge Trail GTX» (a generic suffix) and «Bluetooth»/«USB-C» (standards).
- **README**: its screenshots (`docs/images/*.png`) still show Sony and Bose; B-165 owns the README.
- **Goldens**: `viddikRecord --force` in a copy outside the mutagen replica on Linux changed 68; each was looked at
  (old and new crops of every changed band): 65 change only in the renamed words and what they reflow (the hero's
  title is a line shorter on the phone and at 1024, so the page below it moves up; a card's rating moves up a pixel
  under a one-line title), plus `DisplayFace_Characters`, whose specimen line read «WH-1000XM6 — …». `Search_Results`
  and `Search_Autocomplete` re-rendered five anti-aliased pixels of a chip, nothing renamed, and were left as they
  were.

## Where the checks ran (2026-11-01)

On the WSL box (Ubuntu 24.04), after merging `origin/main` (#200, #201): `check :server:installDist
:composeApp:wasmJsBrowserDistribution` green (1,074 tests: server and client suites, `viddikVerify`, ktlint);
`scripts/image-check.sh` green (`/p/p-sonora-sh-718n` served, preview tags 7 of 7, 1,215 of 1,215 classes from the
cache); `scripts/e2e.sh` against that image green (WholePathTest, the shoppers); `viddikDesignParity` 47/47 within
tolerance, so no reference retired. The new tests fail on a mutation: «Sony Official Store» put back in
`SampleCatalog` fails both greps and the migration's digest test, and V31 without its saga/outbox loop fails the
stand's-orders test. Not run: `scripts/shoppers-check.sh` and `scripts/chart-check.sh` (nothing they read changed).
`make check` green on the box (anchors 82 found, 0 not found); `make docs-against BASE=origin/main` clean on the Mac
(the replica has no git).
