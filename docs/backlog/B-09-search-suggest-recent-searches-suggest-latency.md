---
id: B-09
title: "server: search, suggest, recent searches; suggest latency measured on the seed"
status: open
priority: P1
size: M
stage: stage-3-search
blocked_by: [B-05]
---

# B-09 — server: search, suggest, recent searches; suggest latency measured on the seed

Search is a header field on every screen; its suggest latency on PostgreSQL full text is research open question 1.

Feature: `feature-search` — its scenarios are this item's acceptance where it names them.

- Not covered: ranking beyond full-text relevance and popularity.

- AC: feature-search scenarios pass; the latency number is in the research document.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/search/`.
