---
id: B-107
title: "server: the store does not promise what it does not have"
status: done
priority: P3
size: S
stage: stage-11-qa
---

# B-107 — server: the store does not promise what it does not have

Found in the QA pass of 2026-10-10 (v0.3.0), code audit — words a visitor reads that nothing backs:
- «Get the app · Order tracking, price drop alerts and app-only deals» (`server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt:168-169`) — no app;
- «By placing the order you agree to the Terms of Sale» (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt:282`) — no page;
- the hero's eyebrow is fixed «Autumn mega sale · …» whatever the campaign (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt:121`);
  the empty cart's «… up to −70 % in the Autumn mega sale» is fixed (`server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt:240`);
- «Search 2.4 million products» (`Frame.kt:78`) over a catalogue of hundreds.

- AC: each line drawn from data or removed; tests where data drives it.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`.

## Findings

- **Done (2026-10-11).** Each line is drawn from data or left out:
  - the hero's eyebrow and the empty cart's line are the sale's campaign row — `campaigns.name` (V29, a campaign written
    before it named after its slug, which is how the seed names its own) and its title
    (`HomeScreen.hero`, `CartScreen.saleLine`); `SaleAnnouncementsTest` renames and retitles the sale and reads both,
    `CampaignNameMigrationTest` holds V29 and the seed together;
  - the search field reads «Search products» (`Frame.SEARCH_PLACEHOLDER`, the shell's own header too): the header is
    drawn before a page has read the catalogue and the shell's has none to count, so the number is dropped, not counted;
  - the footer's «Get the app» block is gone — `HaulFooter.appTitle`/`appText` left the wire; its place at 1440 stays
    empty so the footer keeps the canvas's frame;
  - checkout's note is «Your card is charged when the order ships.» for a card and nothing otherwise.
- The empty cart's line now writes «−70%» as the campaign's title does, not the canvas's «−70 %».
- **Left as found:** the seed's campaign subtitles promise counts the catalogue does not have either — «1.2 million items
  marked down across 32 categories» (the hero), «600K items from local sellers» (a banner;
  `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`). They are data, which the AC asks for, not
  copy in the code, so this item does not rewrite them.
- 111 goldens moved, each looked at against its predecessor by a pixel diff: the header's placeholder on every page and
  shell header that draws it; the footer at 1440 and 1024 on Home; checkout's note (one line shorter); the empty cart's
  line (one line at 1440, the page below moving up). The re-recorded files also carry the display face's sub-tolerance
  drift already on `main` (a few edge pixels in «$512.00», «$349», «86»), which the old goldens were accepting.
