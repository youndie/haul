---
id: B-172
title: "fix: the store's promises agree with its rules and with each other"
status: done
priority: P2
size: S
stage: stage-15-showcase
---

# B-172 — fix: the store's promises agree with its rules and with each other

The shopper (2026-11-01) found the store contradicting itself: «Free delivery over $35» in one place, a banner
«Free delivery on everything» in another, and Haul Plus sold for $4.99 a month for free delivery; a count of
«1.2M» items on sale next to a catalogue of 832; a banner «Laptops from $399» that opens the generic deals. A demo
that is checked by its visitors' eyes cannot be caught out by its own copy.

- Every promise the seed and the trees make is read against the rules (D7 delivery from $35, Plus's benefits) and
  either made true or reworded; counts shown are the catalogue's; every banner leads to what it names.

- AC: a test over the home tree finds no delivery promise that the cart's rules contradict; the banners' targets
  show what their titles say; changed goldens re-recorded and looked at.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/seed/SampleCatalog.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`.

## Done (2026-11-01)

Recorded in research, «Decided in B-172». Before → after, at the canvas's «now» (the stand shows the same figures on
its own day):

| Where | Before | After |
|---|---|---|
| Hero | «Up to −70%», «1.2 million items marked down across 32 categories» | «Up to −49%», «832 items marked down across 32 categories» — named in the copy, counted from what is on sale for the viewer (`SaleFigures`) |
| Empty cart | «… up to −70% in the Autumn mega sale.» | «… up to −49% in the Autumn mega sale.» |
| Tech week | «Laptops from $399» → `/deals` | «From $399» under «Tech week · Computers» → `/c/computers?price_min=399&sort=price-asc`, first card $399.99 (`campaigns.link`) |
| Free-delivery weekend | «Free delivery on everything» over «600K items from local sellers» → `/deals` | gone; Haul Plus's banner: «With Haul Plus», «Free delivery on everything», «Start a free month» → the trial (sign-in for a guest); a member's «Included in your membership», nothing to press |
| Plus block and dialog | «Next-day delivery with no minimum», «Next-day courier · Whatever the order total» | removed; the offer lists «No minimum basket» in its place |

- **Schema**: V34 (`campaign_promises`) adds `campaigns.link` and rewrites the seed's own rows (matched by slug and old
  copy): the sale's figures named, Tech week's copy and link, the weekend deleted. V34, not V33, which is B-164's.
  The seeder writes a link after the rows, only where a campaign has one, so the migration tests that seed a database
  migrated to before V34 seed the campaigns as they were (`campaignsBeforeV34`, test-side).
- **Tests**: server `PromisesAgreeTest` (new, 4) — every «free delivery» on a guest's and Maya's home, Sam's account,
  the deals, the headphones' page and an empty cart is the $35 rule or Plus's, `Totals` agreeing; every Plus benefit
  maps to a checked rule; the hero's figures equal the deals page's count, card by card over every page, its
  categories and its deepest badge, and the empty cart's line the same; every banner opens a page with what it names
  (cheapest first, $399 ≤ first < $400), a banner it cannot read fails it. `CampaignPromisesMigrationTest` (new, 1).
  Changed: `DrawnActionsTest` (server: «each of home's banners opens what it names»; client: Tech week's opens its
  address, Plus's presents the dialog), `SaleAnnouncementsTest` (Plus's banner stays after Tech week ends; −49 %),
  `PhotoRoutesTest`'s message, `LeafNameMigrationTest` and `RealNames.old` (old campaigns).
- **Mutations** (each on the committed change, `PromisesAgreeTest` run, the file restored, `git status` clean): the
  seed stating «−70%» and «1.2 million» again — 1 (figures); Tech week without its link — 1 (banners); its link at
  `price_min=300` — 1 (banners); Plus's banner eyebrow «This weekend» — 2 (delivery, banners); «Next-day delivery with
  no minimum» back in the offer — 1 (benefits).
- **Goldens**: re-recorded with `--force` in a copy outside the replica; changed and looked at: `Home_Content`,
  `Home_Content_1024`, `Home_Content_Phone`, `Home_Guest`, `Home_Guest_Phone`, `Home_PlusTrialDialog`,
  `Home_PlusTrialDialog_Phone`, `Cart_Empty`, `Cart_Empty_Phone`, `Search_Recent`, `Search_Recent_Phone`,
  `Search_Results`, `Search_Autocomplete` (the home under the search panel), and after merging B-163 `Hood_Panel` (the home
  behind the panel). None has an active design reference
  (`design/`), so parity is unchanged.
- **QA**: TC-HOME-01 (figures, banners), TC-HOME-07 (benefits), TC-HOME-08 (Plus's banner has no picture), new
  TC-HOME-09 (every promise is a rule), TC-CART empty cart (−49 %).

### Findings

- **The home got slower.** Counting the figures from every listed product took the home from 5.8 to 35 ms median in
  the server's tests (60 requests, after a warm-up, on the WSL box); read from SKU prices alone (`priced`) it is about
  twice a category page's (17–22 ms on a loaded box, the category page 9.5–11 ms). The deals page itself is 26–40 ms
  for the same reason — it lists every product — and was not touched.
- **Tech week's title** «Computers from $399» broke mid-word («Computer / s from») in the desktop banner, whose title
  is held to 240 px beside its picture; the eyebrow names the category instead.
- **The fixture bodies keep the canvas's banner styling** (Tech week in Acid with «Shop now», the second banner Hot),
  which the server does not send; only their words follow the server here. Pre-existing, left as it is.
- **The header's «Free delivery over $35»** is shown to a member too; true, and the shell draws it before it knows who
  is signed in, so it is kept.

