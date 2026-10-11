# Home — `TC-HOME`

The home page: the sale's hero and banners, «Deals of the day» and its countdown, the popular row that takes
their place, «Picked for you», the Plus block. A card's «+» is walked here once for every page that draws cards.

### TC-HOME-01 — A guest's home announces the sale and leads to its pages

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a store where the sale is live (on the stand, every day: the sale is re-dated to today).
- **Steps:**
  1. Read the hero's eyebrow and the banners.
  2. Press each banner.
  3. Press «View all deals» and «All N categories».
  4. Look for «Picked for you».
- **Expected:**
  - Step 1: the hero's eyebrow is the sale campaign's name and dates («Autumn mega sale · Oct 7 — 14» on the
    canvas; on the stand today's week). A banner is drawn until its campaign ends.
  - Step 2: every banner opens `/deals`.
  - Step 3: `/deals`; the catalog's root `/c`, every top-level category as a tile.
  - Step 4: no «Picked for you» for a guest.
- **Decided in:** B-25, B-37, B-49, B-59, B-107
- **Coverage:** `HomeRoutesTest` «a guest sees the campaign the deals and the Plus offer»;
  `server:DrawnActionsTest` «each of home's banners opens the deals page»;
  `server:DrawnActionsTest` «all categories on the home page opens the catalog root»;
  `server:DrawnActionsTest` «the home page's deal links go to the deals page»;
  `SaleAnnouncementsTest` «the hero and the empty cart name the sale the campaign row is»;
  `RecommendationsRoutesTest` «a guest's home has no picked block»; golden `Home_Guest`

### TC-HOME-02 — The deals of the day count down to the soonest end

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a live deal (the stand's seeded deals end at the store's midnight, New York).
- **Steps:**
  1. Read the countdown beside «Deals of the day».
  2. Watch it for ten seconds.
- **Expected:**
  - Step 1: hours, minutes and seconds to the soonest live deal's end — on the stand the next store midnight;
    more than a day is counted in hours.
  - Step 2: it ticks every second, every countdown on the page in step.
- **Decided in:** B-58
- **Coverage:** `DealsOfTheDayTest` «the countdown runs to the soonest live deal's end»;
  `CountdownTest` «the canvas reads 04 12 37 at its now»; `CountdownTest` «more than a day left is counted in hours»;
  `StorefrontTest` «the countdown ticks with the one clock»

### TC-HOME-03 — A running stand keeps its deals across midnight

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a stand that has run past a store midnight (New York) since its last start.
- **Steps:**
  1. Open the home page a few minutes after the store's midnight.
  2. Open `/deals`.
- **Expected:**
  - Step 1: «Deals of the day» with six deals counted down to the next midnight; the hero's dates moved by a day.
    In the seconds between the midnight and the next look (at most an hour) the popular row stands in (TC-HOME-04).
  - Step 2: the same deals first, then «On sale».
- **Decided in:** B-58, B-70
- **Coverage:** `SaleCalendarTest` «a running stand moves its sale at each midnight»;
  `SaleCalendarTest` «the running look moves an ended sale by itself»;
  manual: only a stand that runs through a real midnight shows the look running on its own clock

### TC-HOME-04 — With no live deal, a row of popular products stands in, and no product is drawn twice

- **Who:** guest; Sam
- **Width:** 1440
- **Preconditions:** no live deal — a local stack seeded on another day than the clock's, or the seconds after a
  store midnight on the stand.
- **Steps:**
  1. As a guest, open the home page.
  2. As Sam (no views), open the home page.
- **Expected:**
  - Step 1: no «Deals of the day» header or countdown; «Popular on Haul» in its place — the six most reviewed
    products in stock.
  - Step 2: «Popular on Haul», and «Picked for you · Popular right now» under it with six products, none of them in
    the row above.
- **Decided in:** B-58, B-65, B-70, B-96
- **Coverage:** `DealsOfTheDayTest` «with no live deal no deals section is drawn»;
  `DealsOfTheDayTest` «with no live deal home draws the popular row»;
  `PickedForYouTest` «a product the page already draws is never picked»;
  `UniqueIdsTest` «with no live deal every page a customer opens draws each id once»

### TC-HOME-05 — «Picked for you» follows the customer's views

- **Who:** Maya; Sam
- **Width:** 1440
- **Preconditions:** Maya's seeded views (three headphones, her cart's duvet cover set and mugs); Sam with fewer
  than three views.
- **Steps:**
  1. As Maya, open the home page.
  2. As Sam, open the home page; then open three products of one category and come back.
- **Expected:**
  - Step 1: «Based on your recent views»: two headphones, two duvet covers, two mugs — none of the products she
    viewed, none she bought.
  - Step 2: first «Popular right now»; after the three views, «Based on your recent views», at most two of a
    category, none of the three viewed.
- **Decided in:** B-25
- **Coverage:** `RecommendationsRoutesTest` «the seeded Maya is picked two headphones two duvet covers and two mugs»;
  `RecommendationsRoutesTest` «fewer than three products viewed is the popular row»;
  `RecommendationsRoutesTest` «a customer who viewed three headphones is picked at most two and none of them»

### TC-HOME-06 — A card's «+» adds, says so, and leaves the page where it was

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the home page with deals live; the same on a category, a search and `/deals`.
- **Steps:**
  1. Press a deal card's «+».
  2. Press «View cart» in the message.
  3. Back on home, press «+» on a card and wait.
  4. Press «+» on a card over the Plus block's dark background (if one sits there at that width).
- **Expected:**
  - Step 1: the header's count grows by one, the card stays under the finger (no page loads, no scroll), and a
    message at the bottom says «Added to your cart» with «View cart».
  - Step 2: `/cart`, the message gone.
  - Step 3: the message goes away by itself after about six seconds.
  - Step 4: the message has a white ring and a shadow and stands out over the dark block.
- **Decided in:** B-63, B-75, B-81, B-108
- **Coverage:** `LineAnswersTest` «a deal's plus on the home page answers the header and the deal»;
  `LineAnswersTest` «a card's plus says the item was added on every page that draws cards»;
  `InPlaceAnswersTest` «a card's plus updates the header's count without a page and says the item was added»;
  `BuyBoxWiringTest` «a message goes away on its own»; golden `Shell_Message_OverPlus`

### TC-HOME-07 — The Plus block offers a trial to a non-member and shows a member what Plus saved

- **Who:** guest; Sam; Maya
- **Width:** 1440, 390
- **Preconditions:** the home page.
- **Steps:**
  1. Read the Plus block as each of the three.
  2. As a guest, press «Try 30 days free».
- **Expected:**
  - Step 1: a guest and Sam see «Try 30 days free»; Maya sees no upsell, but what Plus saved her this year ($186 on
    the seed, plus the fees waived on her orders since) and when it renews.
  - Step 2: the sign-in.
- **Decided in:** B-23; B-123 (the $186 is the seeding day's year's, so a stand seeded after 2025 shows it too)
- **Coverage:** `MembershipRoutesTest` «the trial is offered to a non-member and the savings shown to a member»;
  `HomeRoutesTest` «a guest sees the campaign the deals and the Plus offer»; `SaleCalendarTest` «a stand seeded on
  another day shows Maya's carried Plus savings»; golden `Home_Content`
