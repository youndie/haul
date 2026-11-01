---
id: B-169
title: "fix: on a phone the product's variant is chosen before its buy buttons"
status: done
priority: P2
size: S
stage: stage-15-showcase
---

# B-169 — fix: on a phone the product's variant is chosen before its buy buttons

On a phone's product page the colour and size choice sits below «Add to cart» and «Buy now» (designer's review,
2026-11-01): the shopper presses Add before they see there was a choice, and gets the default.

- The phone layout puts the variants between the price and the buttons, as the desktop column does.

- AC: on the phone fixture the variants are above the buttons; the desktop is unchanged; the phone product goldens
  re-recorded and looked at.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`.

## Findings (2026-11-01)

- The order is the client's: the server sends one `ProductDetails` with its `variants`, and `ProductDetailsView`
  stacked the phone's buy box (price, buttons, delivery, seller) above the choices — the canvas's
  Product_Description_Phone drew it so. The phone now draws the choices inside the buy box, between the price and
  «Add to cart»: the price, then what it is the price of, then the buttons, in one card. The alternative — the
  choices above the whole box, under the rating — puts them above the price rather than between it and the
  buttons, as the item asks, and leaves the price below the fold on a short phone.
- A chosen swatch's ring has a 2 px gap drawn in the colour under it; inside the white card the gap is the card's
  white, not the page's paper (`Variants(ground = …)`).
- 1024 (`LocalHaulMedium`) has the same order the phone had — the buy box under the title, the choices under the
  box — and is left unchanged, as the assignment says; the B-169 test does not pin it either way.
- The phone's product artboards (`Product_Description_Phone`, `Product_OutOfStock_Phone` and the tabs') were
  already retired (B-71, B-78), so no design reference moves; `Product_Error_Phone` draws no choices.
- The first `viddikRecord --force` (before main's B-172 was merged in) also rewrote `Search_Autocomplete` and
  `Search_Results`; this change cannot reach the search page, so they were not brought back. After merging main a
  second `--force` recording matched every golden byte for byte.
- `viddikDesignParity` (report-only): every phone product artboard is retired (no reference); the ones that remain
  are unchanged — `Product_Loading` 3.52 %, `Product_NotFound` 1.50 %, `Product_Error` 1.95 %, `Product_Error_Phone`
  4.86 %, all ok.

## Where the checks ran (2026-11-01)

- Tests and goldens on the WSL box (Ubuntu 24.04): `ProductPageLayoutTest` «on a phone the choices stand above
  the buy buttons» and «at 1440 the choices stay in the details' column beside the buy box»; mutation — the view at
  the commit before, the phone test fails («a choice at … 1449 stands below the buy buttons at 996»), restored.
- Goldens recorded with `viddikRecord --force` in a copy outside the mutagen replica; the eight phone product
  pages moved and were looked at; no 1440 or 1024 product golden moved.
- After merging main: `./gradlew check :server:installDist :composeApp:wasmJsBrowserDistribution` green on the WSL
  box (`desktopTest` every test in 71 classes, `viddikVerify`, `:server:test` with Testcontainers), `make check`
  green. `scripts/image-check.sh`, `scripts/e2e.sh` and the other image scripts were not run: the change is the
  client's layout alone, and CI runs them.
