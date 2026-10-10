---
id: B-03
title: "server: schema migrations, seed generator and sample-data fixtures, probes, the image with a zavarnik cache"
status: done
priority: P1
size: M
stage: stage-1-skeleton
epic: feature-browse
blocked_by: [B-02]
---

# B-03 — server: schema migrations, seed generator and sample-data fixtures, probes, the image with a zavarnik cache

Every server item needs a schema, a deterministic seed with the sample data of research §6, and an image that starts fast; the zavarnik cache has to train against a database (research risk 4).

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: per-feature tables beyond what the catalog needs — each feature adds its own migration.

- AC: a fresh database is seeded deterministically (same hash twice); the image starts with its cache accepted.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/db/`, `server/src/main/resources/db/migration/`, `server/src/main/kotlin/io/github/youndie/haul/seed/`.

## Done (2026-10-08)

- `V1__catalog.sql` and the Exposed tables (`feature/catalog/data/CatalogTables.kt`), held together
  by `SchemaTest`; Flyway on start-up.
- The seed: `seed/CatalogSeed.kt` (one fixed-seed `Random`, no clock — every instant from the
  canvas's «now») and `seed/SampleCatalog.kt` (research §6's catalog rows); 1,920 products in
  224 categories, 40 sellers, 3 campaigns, 6 deals. `Seeder` runs on start when `HAUL_SEED=true`,
  under an advisory lock, into an empty catalog only.
- AC «same hash twice»: `SeedTest.a fresh database is seeded to the same hash twice` — two fresh
  databases, one `SeedDigest` (SHA-256 over every row as PostgreSQL prints it, in UTC).
- Probes: `/healthz` touches nothing, `/readyz` asks the database, `/version` names `HAUL_COMMIT`
  (`ops/Probes.kt`, `ProbesTest`).
- AC «the image starts with its cache accepted»: `scripts/image-check.sh`, run by the new `image`
  job — 43 of 43 of the server's classes from the cache under `-XX:AOTMode=on`. How the training
  gets a database is in research risk 4.
- Findings for the drafted documents (open pull request): the server reads `HAUL_DB_USER`,
  `HAUL_DB_PASSWORD`, `HAUL_DB_POOL_SIZE` and `HAUL_COMMIT` besides `HAUL_DB_URL` and `HAUL_SEED`;
  `/version` answers `{"commit": …}`.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/`,
  `server/src/main/resources/db/migration/V1__catalog.sql`, `docker/Dockerfile`,
  `scripts/image-check.sh`.
