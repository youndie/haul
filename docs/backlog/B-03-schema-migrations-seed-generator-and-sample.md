---
id: B-03
title: "server: schema migrations, seed generator and sample-data fixtures, probes, the image with a zavarnik cache"
status: open
priority: P1
size: M
stage: stage-1-skeleton
blocked_by: [B-02]
---

# B-03 — server: schema migrations, seed generator and sample-data fixtures, probes, the image with a zavarnik cache

Every server item needs a schema, a deterministic seed with the sample data of research §6, and an image that starts fast; the zavarnik cache has to train against a database (research risk 4).

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: per-feature tables beyond what the catalog needs — each feature adds its own migration.

- AC: a fresh database is seeded deterministically (same hash twice); the image starts with its cache accepted.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/db/`, `server/src/main/resources/db/migration/`, `server/src/main/kotlin/io/github/youndie/haul/seed/`.
