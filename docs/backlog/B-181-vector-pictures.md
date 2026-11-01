---
id: B-181
title: "feat: pictures drawn as vectors, sharp at any size, with dark versions"
status: open
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
