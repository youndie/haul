---
id: B-114
title: "docs: a QA test-case catalogue the stand is walked against"
status: open
priority: P1
size: M
stage: stage-11-qa
---

# B-114 — docs: a QA test-case catalogue the stand is walked against

Two QA passes (2026-10-10 and the review before it) walked the stand by eye and by code audit; each found dozens of
problems, but there is nothing to walk the next release against, so coverage depends on who walks and what they
remember. The feature documents that would hold scenarios never landed (the draft layers PR is stale).

- **Decision:** a catalogue in `docs/qa/` — one file per area (header and navigation, home, catalog and filters,
  search, product and reviews, cart and promo, sign-in, checkout, order and returns, account and Saved, errors and
  loading, widths, stand-only: shoppers and observability). Each case has an id (`TC-<area>-NN`), who (guest or
  customer), width (1440, 1024, 390), preconditions, steps, the expected result, and **how it is covered**: the
  automated test that holds it (class and test name) or `manual`. Expected results come from what the product
  decided (research «Decided in B-…», item findings), not from what the code happens to do.
- A run of the catalogue is recorded as `docs/qa/runs/<date>-<release>.md`: case, result (pass / fail / blocked / not
  run), evidence; a failure becomes an item.
- AC: the catalogue covers every screen and every decision recorded in research §2 and the B-62…B-113 findings; a
  check in `make check` (or a small script it runs) refuses a case without an id, steps, an expected result or a
  coverage line, and a duplicate id; the manual cases are the ones a test cannot hold.
- Anchors: `docs/research/research-architecture.md`, `docs/backlog/`.
