---
id: B-33
title: "server: the product description's headline is never sent"
status: done
priority: P2
size: S
stage: stage-3-search
blocked_by: []
---

# B-33 — server: the product description's headline is never sent

`ProductDescription.title` and `.accent` exist in the contract and the client draws them («Silence,
*tuned to you*» in `Product_Description`), but `ProductScreen.kt` sets neither: the parity fixture
carries the canvas's copy, so B-08 passed while the running app shows the description without its
headline. The catalogue has nowhere to keep one — it needs a per-product headline in the seed and the
schema, sent with its accent.

- AC: `GET` of a product's description tab carries the headline the seed gives it; a route test
  asserts it, and the description fixture body matches what the server sends for the sample headphones.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/seed/`, `server/src/main/resources/db/migration/V6__product_headline.sql`, `server/src/test/kotlin/io/github/youndie/haul/feature/catalog/ProductRoutesTest.kt`.

## Findings

**2026-10-08.**

- **Schema.** `V6__product_headline.sql` adds `products.headline` (`NOT NULL`) and
  `products.headline_accent` (nullable), with `products_headline_accent_in_headline` checking that an
  accent occurs in its headline — the client finds the accent in the headline to draw it. A catalogue
  seeded before V6 is not reseeded (the seeder inserts into an empty catalogue only), so its rows take
  their title as the headline and no accent; only a fresh seed carries the headlines below. Exposed
  1.4's `MigrationUtils` diffs check constraints by name, so `ProductsTable` declares the same check,
  or `SchemaTest` asks to drop it.
- **Seed.** The headphones (`p-sony-wh-1000xm6`) carry `Product_Description`'s «Silence, tuned to
  you» / «to you» (also in research §6); the duvet cover, the mug and the twelve category headphones
  have headlines of their own. A generated product's headline is keyed by the adjective the seeded
  `Random` already drew for it, so it costs no draw and no value seeded before B-33 moved. `SeedDigest`
  has no stored expectation — `SeedTest` compares two fresh seeds and reads `SELECT *`, so the new
  columns are in the hash with nothing to update.
- **The fixture against the server.** The route test compares the server's `haul_product_description`
  with the one in `composeApp/src/desktopTest/resources/bodies/product_description.json` (passed to the
  test as `haul.clientBodies`, declared a test input). `id`, `title`, `accent` and `modifiers` are
  equal; `text` and `facts` are not, and are set aside: the fixture's text is the canvas's two
  paragraphs, the server sends the seed's one sentence, and the fixture's facts are «40 h / Battery on
  one charge» and the like, while the server sends the first four specifications («Battery / Up to 40
  hours, 3 min charge = 3 h»). The same shape of gap as this item — copy the fixture carries and the
  server does not — left as a finding, not widened into here.
- **Mutation.** Dropping `title`/`accent` from `ProductScreen` fails the test (`expected <Silence,
  tuned to you> but was <null>`); changing the fixture's accent to «tuned to you» fails the fixture
  comparison while the literal assertions still pass.
- **Flyway and the gap.** V4 (B-11) and V5 (B-30) are on parallel branches. `Databases.migrate` sets
  neither `outOfOrder` (default `false`) nor `ignoreMigrationPatterns` (default `*:future`), with
  `validateOnMigrate(true)`. A gap is not an error: every test database is fresh and migrated V1–V3 and
  V6, green. What fails is a database that was migrated to V6 *before* V4/V5 existed and then meets
  them: they resolve below the applied version, read as ignored, and validation stops the server on
  start. The test databases, `scripts/image-check.sh`, `scripts/suggest-latency.sh` and the image's
  training database are all throw-away, so CI is unaffected. The one database that would keep V6 is
  the stand's: B-27's chart runs PostgreSQL on a PersistentVolumeClaim, and `stand.yaml` has not run
  yet (no runs on 2026-10-08). If the stand is installed from a `main` that has V6 but not V4 and V5,
  the next deploy after they merge crash-loops on validation until the volume is dropped or
  `outOfOrder` is set. Merging B-11 and B-30 before the first stand deploy, or B-33 after them, avoids
  it. The failing case is read off Flyway's defaults, not run.

