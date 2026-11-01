---
id: B-181
title: "feat: pictures drawn as vectors, sharp at any size, with dark versions"
status: done
priority: P2
size: M
stage: stage-15-showcase
---

# B-181 — feat: pictures drawn as vectors, sharp at any size, with dark versions

The store's pictures are SVG (B-78), yet they look a little soft (owner, 2026-11-01): Coil rasterises each once at
the box's size (B-149) and the bitmap is what is drawn — at the screen's density, after a zoom, in an animation. Drawn
as vectors, they would be sharp; and with B-180 they need dark versions, as a light tile on a dark page glares.

- Find why they are soft (density, the size asked for, scaling in motion) and draw the pictures as vectors — the SVG
  rendered at draw time, or a vector the client builds from it — so a picture is sharp at every size and density, on
  the web and on the desktop, without costing the grid's scrolling; the numbers before and after in the item.
- Dark versions of the pictures (the seed's art, the category tiles, the campaign banners): served beside the light
  ones and chosen by the theme, or derived by a rule the research states; a picture uploaded by a seller with no dark
  version still sits well on a dark page.

- AC: side by side at 1× and 2× density, a product page's picture before and after (sharper, the same art); the
  grid scrolls as smoothly as before (measured); every seed picture has a dark version shown in dark (with B-180);
  the goldens that change are looked at one by one.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductPhoto.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/seed/SeedPhotos.kt`.

## Findings (2026-11-01)

- **Why soft.** Coil rasterised each SVG at its box rounded up to 64 px (B-149) and the page drew that bitmap scaled
  down with a bilinear filter — 512 → 497 px on the product page, 256 → 236 on a card, at 1× and 2× alike — then scaled
  up 6 % by a hovered card's lean and 4 % by a photo's arrival (graphics layers scale recorded pixels), and kept the
  larger bitmap after a zoom out. Density was not it: the canvas is at `devicePixelRatio` and the boxes were asked for
  in device pixels. Measured in headless Chrome against Chrome's own rasterisation of the same SVG: the product
  picture had 0.876 / 0.900 / 0.899 of its edge energy at 1× / 1.5× / 2×; after, 0.995 / 0.997 / 1.043 (research, D9,
  «Measured in B-181»; a card +13 % at rest, +15 % hovered).
- **Vectors, kept as rasters per size.** An SVG is parsed once by Skia's SVG renderer and recorded as a picture
  (`ui/VectorPicture.kt`, `VectorPictureDecoder` in place of `coil-svg`), rasterised at draw time at exactly the device
  pixels it covers and kept while that size holds. The first build replayed the vectors every frame: as sharp, but a
  wheel-scrolled grid of 60 cards at 2× in software GL had ≈200 of ≈380 frames over 20 ms (before: 3–7 of ≈430). With
  the kept raster: 1–2 of ≈430 at 2×, 0–1 at 1×, main-thread time within the before's spread (1,002–1,113 ms vs
  1,052–1,150 at 2×). The hover growth of card, category, hero and banner pictures and a photo's arrival are drawn in
  the draw phase (`growsOnHover`, `clipDrawn`), so the raster is made at the grown size; the banner's tilt draws the
  vectors. A browser with a GPU was not measured.
- **Dark versions**, decided in the research (D8, «Decided in B-181»): served beside the light picture at `….dark.svg`
  (`PictureVersions`, `:shared`), drawn by `SeedArt` over the tone's dark counterpart with Paper hatching and marks;
  the name's hash covers both drawings; the sample products' stored photos are stored with their dark versions; a
  stored SVG without one answers with itself; a raster photo is matted on the dark page (88 %, rounded, 90 % opacity).
  Tones the dark page keeps (Cobalt, Acid) keep their picture. All 67 pictures the goldens draw have a dark version
  (`DarkArtTest` ties the server's dark tones to the client's scheme). Found on the way: a review photo's `#E9E6E0`
  had no dark counterpart (B-180's tone scan reads only names with «tone»); it is `#2A2824` in both tables now.
- **Goldens** (re-recorded on Linux with `--force`, each looked at): 12 dark ones now show the dark pictures —
  `Search_Autocomplete_Dark` 22.98 %, `Catalog_Content_Dark` 20.82 %, `Home_Content_Phone_Dark` 15.36 %,
  `Home_PlusTrialDialog_Dark` 12.37 %, `Home_Content_Dark` 11.89 %, `Product_Description_Dark` 10.02 %,
  `Cart_Content_Dark` 2.53 %, `Order_InTransit_Phone_Dark` 2.08 %, `Product_ReviewDialog_Phone_Dark` 1.02 %,
  `Account_Content_Dark` 0.93 %, `Checkout_PointsApplied_Dark` 0.60 %, `Order_ReadyForPickup_Dark` 0.44 % of pixels;
  a new one, `ProductCard_Photo_Dark` (a raster photo matted). 92 light goldens moved by under a pixel where a picture
  is now a raster placed on whole pixels: the wide product pages 0.9–2.8 % of pixels (0.05–0.17 % by more than 16
  levels — the same art, about half a pixel higher), every other ≤ 0.1 %, none by more than 16 levels. The fixtures
  already drew the SVGs as vectors, so the light goldens are not visibly sharper; the browser is where it shows.
- **Ran** on the WSL box: `./gradlew check :server:installDist :composeApp:wasmJsBrowserDistribution` (server 604
  tests, desktop 588, `viddikVerify`), `scripts/image-check.sh`, `scripts/e2e.sh`, `make check`; mutation: making the
  client ignore the dark scheme failed both dark `PhotoFallbackTest` cases, drawing the server's dark versions light
  failed three `SeedArtTest`/`SeedPhotosTest` cases.

