---
id: B-111
title: "test: a test database is seeded as fast as before B-99"
status: done
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

## Findings

- **The switch.** `Seeder.seedIfEmpty(database, catalog, generatedReviews = true)`: `false` leaves out
  `SeedReviews.fill` and nothing else — the catalogue's own rows and counts go in as they are, no histogram. Only
  test code passes it: `seededFreshDatabase(generatedReviews = false)` by default (a test's own database), and the
  seeder tests of loyalty, the sale calendar and photos. Full fill stays in the shared `SeededDatabase` (seeded
  once; every fixture and the browse routes read it), `SeedTest`, `SeedReviewsTest`, the review fixtures
  (`product_reviews_mine.json`) and the two review-route cases that list past the seed's own rows (`ownDatabase(
  generatedReviews = true)`).
- **Why a stand cannot end up without its rows.** `main` calls `seedIfEmpty` without the argument, so the stand's
  and the e2e image's starts fill; and the fill runs on every start that fills, already-seeded or not (B-99), so a
  database seeded without it gets every row on its next such start — `SeedReviewsTest`'s «a database seeded without
  the generated rows gets them on the next start that fills» compares it, by `SeedDigest`, with a database seeded in
  full. The e2e now reads the headphones' tabs off the image (`out of 5 · 2,341 reviews`, ten listed and «Show
  more», the same on questions), so an image that stopped filling is red there.
- **The flake was not the load, it was leaked pools.** haul#121's `SQLException: Could not create new connection`
  is `FATAL: sorry, too many clients already`: some twenty tests left a Hikari pool open, four connections each for
  the rest of the suite, against the container's hundred, and whichever test came after the hundredth waited out
  testcontainers' two-minute retry and failed — B-99's two more pools tipped the suite over. This branch's first CI
  run, with the fill off and the pools still leaking, failed exactly so (run 38136522701: `SeedTest` and two
  `UniqueIdsTest` cases, two minutes each). This item closed them too, then dropped that commit for B-100's, which
  closes the same pools and has `PostgresHarness` refuse a ninth open database; the new `SeedReviewsTest` case
  closes both of its databases.
- **Times.** `:server:test` on CI, from the `> Task :server:test` lines of the `gradle` job's log (main pushes):
  before B-99 225, 245, 243, 285, 301, 327 s (runs 38123088374 … 38131746260; `gradle` 6.7–9.0 min); B-99's merge
  c2a50567 821 s (`gradle` 17.3 min), its PR runs 488–827 s, haul#121's push 839 s with two failures; this branch
  280 s (run 38138510648, with this item's own pool fix; `gradle` 7.2 min). On the build box: 446 tests, 310 s summed
  over the suites.
- **Mutation.** The seeder ignoring `generatedReviews` → «…gets them on the next start that fills» red (60,847
  generated reviews written by the tests' seed); a start that skips the fill on an already-seeded database → the
  same test red on the digest (and B-99's own); `main` passing `generatedReviews = false` → the e2e red at «the
  headphones' reviews and questions are as many as they count» (`expected: <10> but was: <2>`).
- **Found on the way, not fixed:** on the build box an image rebuilt after a change that keeps the server's jar the
  same size is not rebuilt. `installDist` writes the jar with a fixed timestamp (1970-01-02), and BuildKit's context
  transfer, which compares size and modification time, kept the jar it had: the first try of the e2e mutation above
  (the default flipped, `iconst_1` → `iconst_0`) ran against the unmutated image and passed. CI builds on a fresh
  runner and is not affected; a check on the box that must see such a change needs `docker build --no-cache` or a
  pruned builder.
- **Checks.** Build box: `:composeApp:wasmJsBrowserDistribution`; `check :server:installDist` (server 446 tests,
  client, `viddikVerify`, ktlint) green; `scripts/e2e.sh` against an image from the branch green. CI on the branch:
  see the pull request for the three consecutive runs.
