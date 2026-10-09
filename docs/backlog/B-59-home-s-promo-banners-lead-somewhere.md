---
id: B-59
title: "server: home's promo banners lead somewhere"
status: done
priority: P3
size: S
stage: stage-2-browse
blocked_by: [B-58]
---

# B-59 — server: home's promo banners lead somewhere

Home's promo banners are drawn with no action, and no item gave them one (they were not on B-49's list). A
shopper presses a sale banner and nothing happens. Found by the PR #2 docs sync after B-53.

Decided as product owner: a banner for a campaign navigates to the deals page filtered to that campaign (or the
deals page when the campaign has no page of its own), a banner for Haul Plus opens the Plus offer like the
header's pill (B-49), and a banner whose campaign has ended is not drawn (B-58). The same holds for home's hero (it announces the
sale) and the empty cart's sale line («Today's deals end at midnight — up to −70 % …»): drawn only while their
campaign or a deal is live — found by B-58.

- AC: each seeded banner carries its action and the client follows it (route + wiring test per banner, as
  `DrawnActionsTest` does); an ended campaign's banner, hero and sale line are absent; home and cart goldens unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/HomeScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/cart/screen/CartScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/domain/Catalog.kt`.

## Done (2026-10-09)

Recorded in research, «Decided in B-59».

- **Each seeded banner opens the deals page** (`PromoBanner.action`, `/deals`, `HomeScreen.banner`); the client already
  followed a banner's action (`HomeViews.Banner`), so nothing changed there but its tests. **No campaign filter on
  `/deals`**: a campaign has SKUs only through `skus.campaign_slug`, and in the seed only the sale names any, so neither
  banner's campaign (Tech week, the free-delivery weekend) has a page of its own and the item's own fallback applies.
  The filter (`/deals?campaign=<slug>`) is for the first banner whose campaign sells SKUs.
- **The sale** is the first campaign by position (`liveSale`, `feature/catalog/domain/Catalog.kt`), the one the hero
  announces and the empty cart's line names. **The hero** is drawn while the sale is live for the viewer
  (`CampaignWindow.liveFor`: a Plus member from its early access, everybody from its start, nobody from its end); the
  campaign row is the hero's, so without the hero no banner is drawn either. **A banner** is drawn until its campaign
  ends (`Campaign.endedAt`) — not only while it is live: the canvas draws the free-delivery weekend on Oct 7, four days
  before it opens, and home's goldens are the canvas.
- **The empty cart's line** («Today's deals end at midnight — up to −70 % in the Autumn mega sale.») names today's deals
  and the sale, so it is drawn only while a deal is live **and** the sale is live for the cart's owner; otherwise the
  sentence is left out — `EmptyState.text` became optional on the wire (a default `null`, left out by
  `explicitNulls = false`; `EmptyStateView` draws no sentence) — and the title and «See today's deals» stay.
- **Tests** — server `DrawnActionsTest` (+1): home's two banners each carry `navigate /deals`, neither campaign names a
  SKU, and following each draws the deals page. `SaleAnnouncementsTest` (new, 5, on the store clock, against shildik
  for Maya): both banners at the canvas's «now» and a minute before Oct 13, none at Oct 13 while the hero stays; the
  hero a minute before the sale's end (Oct 15) and no row from it, the categories staying; on Oct 6 no row for a guest
  and the hero for Maya (Plus early access); the empty cart's line at the canvas's «now» and gone from the deals' end,
  title and `/deals` button staying; with the deals moved past the sale (a fresh database), at the sale's end no line
  while the cart's picks are drawn and no row while home's deals are. Client `DrawnActionsTest` (+2): pressing «Laptops
  from $399» and «Free delivery on everything» opens `/deals` (`/ui/deals` fetched).
- **Mutations** (each on the committed change, restored after, `git status` clean): a banner with no action — 1
  (`each of home's banners opens the deals page`); banners not dropped at their end — 1; the line without the
  live-deal check — 1; `liveSale` without the window (the sale never ends) — 3 (the hero's end, its opening, the
  line's live sale); the hero on everybody's prices instead of the viewer's — 1 (Maya on Oct 6); the line without the
  live-sale check — 1; the client's banner not following its action — the two client tests. No other test failed under
  any of them (server 371, client 175 per run).
- **Goldens**: `viddikVerify` green (136 cases), none re-recorded; no fixture body changed. The server's home and cart
  at the canvas clock are unchanged but for the banners' action (`CartFixturesTest` holds the cart bodies equal).

### Findings

- **No seeded banner is for Haul Plus**, so the decision's Plus clause (open the Plus offer like the header's pill) has
  nothing to apply to; a campaign carries nothing that would mark it as Plus. Not built — a marker (a column, V28) would
  be the item that seeds such a banner.
- **The row without banners**: from Oct 13 to the sale's end the row has the hero alone, and on a wide page the hero
  keeps its two thirds with an empty third beside it; no artboard draws that state.
- **Live banners without the hero are not drawn**: before the sale opens for a viewer (Oct 6 for everybody but Plus),
  Tech week is live but the row is the hero's. Unreachable on a seeded stand, whose sale opens on its seed day.
- **«See today's deals»** stays on the empty cart when no deal is live; it still opens `/deals`, whose «On sale» lists
  what remains. Its label is the canvas's.
