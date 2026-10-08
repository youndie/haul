---
id: B-04
title: "contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers"
status: open
priority: P1
size: M
stage: stage-1-skeleton
blocked_by: [B-02]
---

# B-04 — contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers

Every screen uses `HaulHeader` and `ProductCard`; doing them first fixes the theme roles, the bundled fonts and how a recorded server body becomes a screenshot fixture (research risk 3).

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: screen-specific components.

- AC: both components render in desktop screenshot tests from a recorded body; fonts embedded.
- Anchors (planned): `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/ui/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/theme/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/registry/`.
