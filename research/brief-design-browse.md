# Design brief: Haul — feature-browse (Home and category catalog)

Source of every name and value here: `research/brief-technical.md` (§5 screens, §5a sample data). Canvas today: Claude Design project «E-commerce витрина», `Storefront.dc.html` — 7 screens, desktop only, one state each. This file lives on the brief branch and is deleted before it merges.

## The brief

> **Screens for Haul, `feature-browse` (Home and category catalog).** Draw them in the canvas's own system — the
> palette and type of `Storefront.dc.html` as Material 3 colour and type roles, fonts Bodoni Moda,
> Archivo and JetBrains Mono (technical brief §8) — as **static artboards**, one artboard per
> screen state, on a canvas page per screen.
>
> 1. **One artboard per state, named `<Screen>_<State>`**, letters, digits and underscores only.
>    The states are the ones in the technical brief's §5, listed below. No dark variants.
> 2. **Fixed size, the target's**: `1440×H` for desktop and `390×H` for phone, where `H` is the
>    full height of that state's page; set the artboard frame and the root element to the same
>    size, the root filling the frame with Paper `#F5F3EE`. Write each `H` next to the artboard
>    so it can go back into §5. The table names the desktop artboard; the phone artboard of the
>    same state is the same name plus `_Phone` (`Cart_Empty_Phone`), because the reference PNG
>    is named after the artboard and two sizes cannot share a file. A row already ending in
>    `_Phone` has no desktop artboard.
> 3. **Static**: plain markup with inline styles. No `{{ holes }}`, no `<sc-for>` / `<sc-if>`,
>    no `<dc-import>` (the current `Header` and `ProductCard` imports are written out in place),
>    no tweaks. Repeated items are written out, with the sample data below.
> 4. **The app's fonts**: Bodoni Moda, Archivo and JetBrains Mono via the Google Fonts `<link>`
>    the canvas already has, inside each artboard's `<helmet>`. Not `system-ui`.
> 5. **Exact values from the palette**: Cobalt `#2F2BFF`, Acid `#DFFF3A`, Hot `#FF3D2E`, Ink
>    `#0F0F0F`, Paper `#F5F3EE`. The greys in use (`#3A3833`, `#5E5B55`, `#6F6B64`, `#E2DED6`,
>    `#D8D5CE`) and any new value are named on the canvas as roles, not left as bare hex.
> 6. **Sample data**, the same on every artboard that shows it: the table below. «Now» is
>    **2025-10-07 19:47:23 America/New_York, a Tuesday**.
> 7. **No device chrome**: no browser frame, status bar or keyboard inside the artboard.
> 8. **One canvas page per screen**, artboards in a row in the order below, notes beside them
>    for anything the code has to know that the picture does not say.
> 9. **Hand over the files**: the `.dc.html` artboards with `canvas.json`, as a directory, or
>    the project link when it is the same Claude Design project this brief came from.

The reasons behind each line are in `design-to-compose`'s design-brief reference; keep the
constraint even when shortening the wording.

Every screen is assembled by the server from the components in the technical brief's §5b
(`ProductCard`, `CartLine`, `OrderProgress`, …) and drawn by one renderer each. So a piece that
appears on several screens is drawn the same everywhere — the same card in Home, Catalog, Search
and Saved — and a variation is a named variant of the component, noted on the canvas, never a
one-off on one artboard.

## Artboards

### Home (`Home_*`) — at 1440×H and 390×H

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Home_Loading` | header, placeholder blocks for hero, categories and two product rows | to draw |
| Content | `Home_Content` | Maya signed in: campaign «Autumn mega sale», «Tech week», «Free delivery» banners, 8 categories, 6 deals with the countdown, the Plus block **in its member form** («You saved $186 on delivery this year · renews Nov 2»), «Picked for you» 6 products, footer | drawn as `01 Home` — Plus block to redraw |
| Guest | `Home_Guest` | header «Sign in»; Plus block offers the trial; no «Picked for you» | to draw |
| PlusTrialDialog | `Home_PlusTrialDialog` | Sam signed in, dialog over Content: the benefits, «30 days free, then $4.99/month», Start trial / Not now | to draw |
| Error | `Home_Error` | header, message that the page could not load, Retry | to draw |

Against the current canvas:

- The Plus block on `01 Home` offers Maya the trial, but she is a member (`07 Account`). Decided: a member never sees the upsell. In `Home_Content` the block reads «You saved $186 on delivery this year · renews Nov 2»; the trial offer is `Home_Guest`'s.
- The deals countdown reads 04:12:37 at the fixed «now» — keep it.
- «Deals» in the category row is `#D9230F`, which is not Hot `#FF3D2E`: pick one, or name the second as its own role.

### Category (`Catalog_*`) — at 1440×H and 390×H

| State | Artboard | What is visible | Canvas |
|---|---|---|---|
| Loading | `Catalog_Loading` | breadcrumbs and title, placeholder facet column and 12 card placeholders | to draw |
| Content | `Catalog_Content` | Headphones, 12,408 items, facets with Sony + Bose + $80–$400 + Noise cancelling applied, chips, sort «Popular», 12 cards, «Show 24 more», pages 1 2 3 … 517 | drawn as `02 Catalog` |
| Empty | `Catalog_Empty` | same filters plus Marshall + Pink: «No items match these filters», Clear all, facets still visible | to draw |
| FiltersSheet | `Catalog_FiltersSheet_Phone` | **phone only**: the facet column as a full-height sheet with «Show 48 items» | to draw (phone) |
| Error | `Catalog_Error` | header, message, Retry | to draw |

Against the current canvas:

- `Catalog_FiltersSheet_Phone` exists at the phone width only; there is no desktop artboard for it.

## Sample data (technical brief §5a, verbatim)

The values every artboard, preview, scenario and screenshot fixture shows. Taken from the canvas
verbatim; values the canvas does not have are marked *new*. The seed database is a separate,
larger generated set; these are the fixtures.

| Entity | Values |
|---|---|
| Now | **2025-10-07 19:47:23 America/New_York (Tuesday)** — the canvas's weekdays (Wed 8, Thu 9) fit 2025, not 2026; the deals countdown 04:12:37 is the time to midnight |
| `Customer` | Maya Kowalski — Plus since 2023, renews 2025-11-02, points 2,480, delivery savings $186, 48 saved, 6 price drops; Sam Ortiz *new* — no membership, 0 points, one delivered order |
| `Address` | 148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211; header «Deliver to Brooklyn, NY 11211» |
| `PaymentMethod` | Card ···· 4821, expires 08/28 (approves); test card ···· 0002 *new* (declines); Haul Pay «4 payments of $128» on $512, «4 payments of $87.25» on $349 *new*; points «Use 2,480 points (−$24.80)» → total $487.20 *new* |
| `PickupPoint` | 214 Bedford Ave, 240 m, open until 21:00; *new*: 96 N 6th St, 650 m, until 22:00; locker «Wythe & N 7th», 24/7 |
| `Seller` | Sony Official Store — 4.9, 98 % positive, 6 yrs on Haul; Brooklyn Home Co. *new rating* — 4.8, 97 %, 3 yrs |
| `Product` (hero) | Sony WH-1000XM6 Wireless Noise Cancelling Headphones — $349, was $449, −22 %, Bestseller, 4.8, 2,341 reviews, 86 questions, 12K bought this month; colour Midnight Black (*new*: Silver, out of stock); bundles Headphones only / + Travel case / + 2-year care; specs: battery up to 40 h, 3 min charge = 3 h; adaptive noise cancelling, 12 microphones; Bluetooth 5.4 · LDAC · multipoint; 250 g; histogram 78/14/4/2/2 % |
| `Review` | Daniel R., Sep 28, verified, 5.0, «The quietest flight I've had»; Aisha K., Sep 21, verified, 4.0, «Great sound, tight at first», 48 found helpful |
| `Cart` (Maya) | Sony WH-1000XM6, Midnight Black · Headphones only, 1 × $349 (was $449), courier tomorrow; Linen Duvet Cover Set, Queen, Oat · 3 pieces, 1 × $139 (was $179), Brooklyn Home Co., Thu Oct 9; Stoneware Mug, 12 oz, Sage · Set of 2, 1 × $24, Brooklyn Home Co.; Items $652.00, Discount −$140.00, Delivery Free, Total $512 |
| `PromoCode` *new* | `AUTUMN10` — 10 % off items, up to $50, valid 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211, placed Oct 5, $512.00, in transit, arriving tomorrow 15:00–18:00; #HL-47960, Oct 3, $58.00, ready for pickup at 214 Bedford Ave until Oct 10, code 4821; history: Sep 24 #HL-46102 $103.00 delivered; Sep 11 #HL-45277 $299.00 delivered; Aug 30 #HL-44019 $87.50 returned; Aug 12 #HL-42860 $42.00 delivered; *new*: #HL-48302 — the order placed from the checkout fixture (Oct 7, $512.00); #HL-48303 — cancelled, card ···· 0002 |
| `Category` | Electronics, Home & Kitchen, Fashion, Beauty, Kids & Toys, Sports, Grocery, Pets (tiles); header row adds Auto, Books; Headphones: 12,408 items; brands Sony 1,204, Bose 611, JBL 1,980, Apple 842, Sennheiser 530, Marshall 204 |
| Search | «running shoes»: 14,870 results; Men's shoes 6,204, Women's shoes 5,880, Kids' shoes 1,412, Insoles 830, Socks 544; suggestions «running shoes women», «running shoes men wide», «trail running shoes», «running shoes waterproof»; Sports › Running shoes 8,214, Fashion › Sneakers 5,102; recent «wireless earbuds», «standing desk»; top products Pace Runner 3 Men's $89, Cloudline Daily Trainer Women's $112, Ridge Trail GTX $72 |
| Product lists | the four lists in the canvas script (`renderVals`): deals 6, recs 6, catalog 12, search 10 — titles, prices, old prices, badges, ratings, review counts, delivery days and tile tones, copied verbatim into the fixtures |
| Campaigns | «Autumn mega sale · Oct 7 — 14, Up to −70 %, 1.2 million items marked down across 32 categories»; «Tech week — Laptops from $399»; «This weekend — Free delivery on everything, 600K items from local sellers» |

### 5b. Components on the wire

Every screen is a kompot tree the server builds; the client renders it through a registry. The
standard set (`kompot-standard`: text, containers, lists, tabs, pagination) covers layout; what
the canvas draws as a recognisable piece is a Haul component, declared once in `shared` with
`@KompotComponentMarker` and rendered by `composeApp`. The names go into the code unchanged; the
wire type is the snake-case form with a `haul_` prefix.

| Component | Wire type | Where | Carries |
|---|---|---|---|
| `HaulHeader` | `haul_header` | every screen but Checkout | address line, signed-in first name or sign-in action, cart count, query |
| `CheckoutHeader` | `haul_checkout_header` | Checkout | current step |
| `HaulFooter` | `haul_footer` | Home | link columns (labels only in v1) |
| `CampaignHero`, `PromoBanner` | `haul_campaign_hero`, `haul_promo_banner` | Home | campaign copy, tone, target action |
| `CategoryTile` | `haul_category_tile` | Home | name, tone, label |
| `DealsRow` | `haul_deals_row` | Home | `endsAt` (the client counts down), product cards |
| `PlusBlock` | `haul_plus_block` | Home | member form (savings) or offer form (trial action) |
| `ProductCard` | `haul_product_card` | Home, Catalog, Search, Cart (empty), Saved | the canvas's `ProductCard` props plus product id, save state, price-drop mark |
| `Breadcrumbs` | `haul_breadcrumbs` | Catalog, Product | path |
| `FacetPanel`, `FilterChips`, `SortSelect` | `haul_facet_panel`, `haul_filter_chips`, `haul_sort_select` | Catalog, Search | facets with counts, applied filters, sort |
| `SearchSuggestPanel` | `haul_search_suggest` | Search | suggestions, categories, recent, top products |
| `ProductGallery`, `VariantPicker`, `PriceBlock` | `haul_product_gallery`, `haul_variant_picker`, `haul_price_block` | Product | photo tiles, options and the chosen `Sku`, price / old / discount / Haul Pay line |
| `DeliveryOptions`, `SellerCard` | `haul_delivery_options`, `haul_seller_card` | Product | estimates per method, cut-off; seller summary |
| `RatingSummary`, `ReviewItem`, `QuestionItem` | `haul_rating_summary`, `haul_review_item`, `haul_question_item` | Product | histogram; one review; one question |
| `CartGroup`, `CartLine`, `OrderSummary`, `PromoField` | `haul_cart_group`, `haul_cart_line`, `haul_order_summary`, `haul_promo_field` | Cart, Checkout | seller and delivery day; line; totals and points; promo state |
| `DeliveryMethodPicker`, `PickupPointList`, `SlotPicker`, `PaymentMethodPicker` | `haul_delivery_method_picker`, `haul_pickup_point_list`, `haul_slot_picker`, `haul_payment_method_picker` | Checkout | the choices and which is selected; the address is a `kompot-forms` form |
| `OrderProgress`, `OrderCard`, `PickupCode` | `haul_order_progress`, `haul_order_card`, `haul_pickup_code` | Order, Account | stepper; order row; code and hold date |
| `AccountMenu`, `AccountTile` | `haul_account_menu`, `haul_account_tile` | Account, Saved | menu with counts; points / Plus / price-drops tile |
| `EmptyState` | `haul_empty_state` | every Empty / NoResults / NotFound | title, text, action |

Dialogs (`Home_PlusTrialDialog`, `Product_ReviewDialog`, `Product_QuestionDialog`,
`Order_ReturnDialog`) are routes shown over the screen (`kompot-navigation`); the review, question,
return and address inputs are `kompot-forms` forms, validated on the client by the same rules the
server checks.
