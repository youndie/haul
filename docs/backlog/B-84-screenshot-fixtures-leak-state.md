---
id: B-84
title: "test: screenshot fixtures do not depend on their order"
status: wip
priority: P3
size: S
stage: stage-10-review
---

# B-84 — test: screenshot fixtures do not depend on their order

B-75 and B-72 saw `viddikDesignParity` read the Search_* artboards at 5–35 % on one run and 2–3 % on another
with no search change between them; the suspicion is state leaking between screenshot fixtures depending on
their order. `viddikVerify` is the gate and stays green, but a fixture whose pixels depend on what ran before it
can hide a regression or invent one.

- AC: the cause found and fixed (or the measurement explained), with a run in two different fixture orders
  giving the same numbers.
- Anchors: `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/ComponentFixtures.kt`.
