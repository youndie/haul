---
id: B-111
title: "test: a test database is seeded as fast as before B-99"
status: wip
priority: P1
size: S
stage: stage-11-qa
---

# B-111 — test: a test database is seeded as fast as before B-99

B-99 made the seed write the rest of every product's review and question counts — 60,849 reviews and 6,799
questions — so the stand shows what its counts say. A fresh seed went from 0.6–1.4 s to 2.2–2.7 s, and every server
test that seeds its own database pays it: `gradle` on main went from about 10 to 17 minutes after B-99, and on
haul#121 the run took 20 minutes and failed with `SQLException: Could not create new connection` from
testcontainers in two `UniqueIdsTest` cases and a timing failure in `LiveOrderTest` (a rerun was needed).

- **Decision:** tests seed only the rows they read. The bulk review/question fill runs on the stand and in the e2e
  image (where the counts are shown), and in the tests that hold it (`SeedReviewsTest`, the reviews' paging); every
  other test database gets the seed without the fill. Record how the switch is made and why it cannot leave a stand
  without its rows.
- AC: `:server:test` on CI back near its pre-B-99 time (record before/after from the run logs); the review tests
  still see the full counts; the e2e and the stand's start still fill; no flake in three consecutive CI runs.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SeedReviews.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/testing/PostgresHarness.kt`.
