---
id: B-173
title: "feat: «The stack in numbers» — start-up with and without the AOT cache, latency, memory, measured"
status: open
priority: P2
size: M
stage: stage-15-showcase
---

# B-173 — feat: «The stack in numbers» — start-up with and without the AOT cache, latency, memory, measured

The image ships a zavarnik AOT cache and the research says why (D-section on the image), but the gain was never
measured on this server (research: «not measured»), and there is no number anywhere for latency or memory under
load. The architect's review (2026-11-01): a showcase a developer trusts is one that shows its numbers.

- **Measure, then publish.** Following `research-method:measurement-study`: start-up of the image with the cache and
  without, interleaved, N runs; p50/p99 of the main routes and RSS under the synthetic shoppers at a fixed rate on the
  WSL box; raw logs kept. The results go into the research and a page of the stand («The stack in numbers», from
  About), with the date, the commit and how to reproduce.

- AC: the study's verdicts written (green, grey or red per question) with raw logs in the repository; the page shows
  the numbers and links the method; nothing on it is unmeasured.
- Anchors: `docker/Dockerfile`, `docs/research/research-architecture.md`, `scripts/`.
