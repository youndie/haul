---
id: B-127
title: "fix: a database outage reaches the shopper in seconds, not in a minute and a half"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-127 — fix: a database outage reaches the shopper in seconds, not in a minute and a half

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)): with PostgreSQL stopped, `/ui/home` answered `503` after
96.8 s and `/readyz` after 32 s — Hikari's 30 s connection timeout, paid once per query of the page. The storefront
showed the page's placeholder for about 100 s before «Something went wrong on our side» (TC-ERR-05's words are
right; the wait is decided nowhere).

- **A page whose database is gone fails fast.** A short connection timeout for request work (a few seconds) and
  one failure per request rather than one per query; readiness answers within its probe's timeout. Rejected:
  leaving the default — a shopper leaves long before 97 s.

- AC: with the database stopped, a page answers `503` within five seconds and `/readyz` within the probe's
  timeout; a test with an unreachable database holds the bound.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/db/Database.kt`.
