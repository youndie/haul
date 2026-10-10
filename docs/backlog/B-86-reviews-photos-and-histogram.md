---
id: B-86
title: "client + server: review photos open and the histogram filters"
status: open
priority: P3
size: M
stage: stage-10-review
---

# B-86 — client + server: review photos open and the histogram filters

B-71 left two things on the Reviews tab without an action because nothing existed for them to do: a review's
photo tiles (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt` ~375) and the rating histogram's bars (~314). The owner
deferred the decision on 2026-10-10.

- Proposal: a photo opens a viewer over the page; a bar filters the reviews by its stars with a `load`.
- AC: per the owner's decision; tests per control.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.
