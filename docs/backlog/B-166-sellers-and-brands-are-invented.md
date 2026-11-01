---
id: B-166
title: "fix: sellers and brands in the sample data are invented, not real companies'"
status: wip
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
