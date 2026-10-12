---
id: B-86
title: "client + server: review photos open and the histogram filters"
status: wip
priority: P3
size: M
stage: stage-10-review
---

# B-86 — client + server: review photos open and the histogram filters

B-71 left two things on the Reviews tab without an action because nothing existed for them to do: a review's
photo tiles (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt` ~375) and the rating histogram's bars (~314). The owner
deferred the decision on 2026-10-10.

- **Decided (owner, 2026-10-12): both, as proposed.** A review's photo opens a viewer over the page (the photos of
  that review, one at a time, closed by «×», Escape or a press outside); a histogram bar filters the reviews by its
  stars with a `load` (pressed again, or «All», the filter goes). The rating shown is computed from the same data as
  the histogram — today the headphones' bars 78/14/4/2/2 average 4.64 under a «4.8» (the v0.4.0…v0.5.1 runs).
- AC: a photo opens its viewer and closes as above, named for assistive tech (B-131's rule); a bar filters, the
  address carries the filter, «All» clears it; the rating and the histogram agree for every product; tests per control.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.
