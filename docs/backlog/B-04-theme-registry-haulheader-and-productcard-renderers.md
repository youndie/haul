---
id: B-04
title: "contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers"
status: done
priority: P1
size: M
stage: stage-1-skeleton
epic: feature-browse
blocked_by: [B-02]
---

# B-04 — contract + client: theme (colour roles, three bundled fonts), registry, `HaulHeader` and `ProductCard` renderers

Every screen uses `HaulHeader` and `ProductCard`; doing them first fixes the theme roles, the bundled fonts and how a recorded server body becomes a screenshot fixture (research risk 3).

Feature: `feature-browse` — its scenarios are this item's acceptance where it names them.

- Not covered: screen-specific components.

- AC: both components render in desktop screenshot tests from a recorded body; fonts embedded.
- Anchors (planned): `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/`.

## Done (2026-10-08)

- Contract: `HaulHeader` and `ProductCard` (`shared/.../haul/ui/HaulComponents.kt`), wire types
  `haul_header` and `haul_product_card`, registered by kompot's processor.
- Theme: the canvas's colour roles (`composeApp/.../haul/theme/HaulColors.kt`), the three fonts as
  Compose resources with their OFL licences, Bodoni Moda per optical size (`HaulFontResources.kt`).
- Renderers: `HaulHeaderView` at 1440 and at 390, `ProductCardView` at both widths, from the
  artboards' exact values; `haulRegistry()` and `haulJson`.
- AC «both components render in desktop screenshot tests from a recorded body»: six fixtures in
  `composeApp/src/desktopTest/.../ComponentFixtures.kt` decode JSON bodies (`resources/bodies`)
  through the app's registry; goldens recorded on Linux and verified there by `check`. The bodies
  are written by hand to the wire shape for now; the server records them once it builds the trees
  (B-05).
- AC «fonts embedded»: the fixtures read the app's own font files; the bundle carries all three.
- Found: macOS and Linux differ by 0.06–0.10 % on these fonts — research, risk 1.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/`,
  `composeApp/src/desktopTest/snapshots/`.
