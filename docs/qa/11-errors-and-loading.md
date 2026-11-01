# Errors and loading — `TC-ERR`

What the shopper sees while a page is on its way, when it does not arrive, when the server refuses it, when it is
not there, and when a press fails. To make a page fail on purpose: DevTools «Offline» (no answer), a slow profile
(«Fast 4G», «Slow 4G») for placeholders, and on a local stack the database or the server stopped (an error of
the server's own: `503`).

### TC-ERR-01 — Every page kind has a placeholder of its own

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** DevTools «Slow 4G» once the bundle is loaded (the placeholder is drawn while a page's tree is on
  its way).
- **Steps:**
  1. From the home page, open in turn: the catalog's root `/c`, a category, `/deals`, a product, a search, the cart;
     as Maya checkout, an order, the account, the Saved list.
- **Expected:**
  - Step 1: each draws its own placeholder under the header (itself a placeholder: no name, no count) — never the
    header alone over nothing, and never the previous page's content; `/c` and `/deals` too. Checkout's marks no
    step. A placeholder's cards are as many across as the page's (at 1024 three beside a category's facets).
- **Decided in:** B-67, B-79, B-108, B-136
- **Coverage:** `StorefrontTest` «a page on its way draws its placeholders until the tree arrives»;
  `LoadingCardsTest` «no card placeholder is narrower than the page's own cards at any desktop width»;
  `StorefrontTest` «the catalog's root and the deals draw their placeholders on their way»;
  `CheckoutWiringTest` «the checkout before its tree marks no step»;
  golden `Home_Loading`; golden `Categories_Loading`; golden `Deals_Loading`; golden `Product_Loading`;
  golden `Checkout_Loading`; golden `Order_Loading`

### TC-ERR-02 — A cold first load shows the header's shape at once

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a fresh profile (no cache), DevTools «Fast 4G».
- **Steps:**
  1. Open `/c/mugs` by its address.
  2. Open `/checkout` the same way.
- **Expected:**
  - Step 1: within about a third of a second the header's shape (strip, logo's place, search field, shortcuts,
    category row) with the sweeping line under it; seconds later the store replaces it — never both at once, never
    a blank in between. The store's first frame already has its own faces — «Haul» in Bodoni Moda, the category row in
    Archivo, the strip in JetBrains Mono — and no word is redrawn in another face or moves to another line after it.
  - Step 2: checkout's own header shape (logo, three steps), not the storefront's.
- **Decided in:** B-80, B-90, B-150
- **Coverage:** `StaticFrameTest` «the page carries the frame the entry point removes»;
  `StaticFrameTest` «a cold checkout draws the checkout's header shape»;
  `StaticFrameTest` «the page fits the first round trip»;
  `StaticFrameTest` «the page preloads every bundled font the way the entry point reads it»;
  script `scripts/measure-first-load.sh`; script `scripts/first-frame-faces.py`;
  manual: the swap is only seen in a real browser on a throttled link

### TC-ERR-03 — A load within a page that fails keeps the page under a notice

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a category page drawn.
- **Steps:**
  1. Go offline; tick a facet — at 390 in the sheet «Filters» opens, which stays open.
  2. Go online; press «Retry».
- **Expected:**
  - Step 1: the page stays as it was, under «The page didn't update» with «Retry» — not the error page. At 390 the
    notice is drawn over the open sheet, its «Retry» where the sheet's «Show N items» is (B-145).
  - Step 2: the tick's results, in place; at 390 in the sheet, still open («1 applied»).
- **Decided in:** B-62, B-63, B-145
- **Coverage:** `KeptPageTest` «a load that fails within a screen keeps the page under a notice and Retry loads it»;
  `InPlaceAnswersTest` «a tick whose answer does not arrive keeps the page under a notice and Retry presses it again»;
  `InPlaceAnswersTest` «a tick in the filter sheet that does not arrive says so over the sheet and Retry applies it»;
  `SheetOverPageTest` «a notice over a sheet keeps the focus Tab gave it»

### TC-ERR-04 — An old or edited link loads the page it meant

- **Who:** guest
- **Width:** 1440
- **Preconditions:** none.
- **Steps:**
  1. Open `/c/electronics?sort=bogus&brand=Sonora`.
  2. Open the headphones at a `sku=` the product does not have.
  3. Open `/search?q=mug&page=x&from=3`.
  4. Open an address the server refuses without naming a parameter it has (a malformed request), such as
     `/c/electronics?brand=%E0%A4%A`, `/search?q=%zz`, `/c/electronics?%zz` or `/p/p-sonora-sh-718n?sku=%zz`.
- **Expected:**
  - Step 1: the category with `brand=Sonora` and without `sort` — no message, no Retry.
  - Step 2: the product at its default SKU.
  - Step 3: `page` dropped, then `from` — one parameter per refusal, the rest of the query as written.
  - Step 4: «This link is out of date», with «Open the page» (its path) or «Go to the home page». From
    `/search?q=%zz` the page is `/search` with nothing typed: «Start typing» and «Type what you are looking for in
    the field and press Enter.» — no claim that a query is still in it, nor that it needs two characters — the field
    empty, no Retry.
- **Decided in:** B-94, B-117, B-132, B-135
- **Coverage:** `RefusedPagesTest` «a parameter an old link got wrong is dropped and the page loads without it»;
  `RefusedPagesTest` «a product link whose sku is gone opens the product»;
  `RecoveryTest` «a parameter the server names is dropped and the rest of the query kept as written»;
  `RecoveryTest` «every value of a repeated parameter goes»;
  `RefusedPagesTest` «a link refused for no parameter it has is out of date and leads to its page»;
  `RefusedPagesTest` «a link whose query does not decode is out of date and leads to its page»;
  `RefusedPagesTest` «an out-of-date search link leads to a search page that asks for a query»;
  `RefusedPagesTest` «a search with nothing typed asks for a query without saying the minimum»;
  `WebBundleTest` «a storefront address whose query does not decode still answers the page, its tree refuses it»;
  `WebBundleTest` «a storefront address whose query does not decode ends with the page, no failure behind it»

### TC-ERR-05 — An error page tells no answer from an error of the store's

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** offline for the first step; a local stack with the database stopped for the second.
- **Steps:**
  1. Offline, open home, a search, the cart, checkout, the account, the Saved list.
  2. With the database stopped, open the same.
  3. Press «Retry» once the store is back.
- **Expected:**
  - Step 1: each says it could not reach Haul and to check the connection («We couldn't reach Haul …»); the cart
    and checkout also say nothing was lost.
  - Step 2: each says it was the store's side, within five seconds of the press (B-127) — also while an order being
    placed waits the outage out (B-139); the cart, checkout and search keep their «Try again in a moment».
  - Step 3: the page.
- **Decided in:** B-94, B-127, B-139
- **Coverage:** `RefusedPagesTest` «an account that could not be reached says so, not our side»;
  `RefusedPagesTest` «a saved list that could not be reached says so, not our side»;
  `RefusedPagesTest` «a saved list the server failed says it was our side»;
  `RefusedPagesTest` «a cart that could not be reached says so»;
  `RefusedPagesTest` «a checkout that could not be reached says so»;
  `RefusedPagesTest` «a search that could not be reached says so»;
  `StorefrontTest` «no answer at all says the server could not be reached»;
  `StorefrontTest` «a server error draws the error page and Retry loads the page again»;
  `ErrorAnswersTest` «a database that cannot be reached makes a screen 503 unavailable»;
  `DatabaseOutageTest` «a stopped database answers a page in seconds and readiness within its probe's timeout»;
  `ObservabilityTest` «a database outage answered 503 to every page files nothing and logs no stack per page»;
  `PlacementOutageTest` «a placement rides out a five second outage and the order is placed»;
  golden `Home_Error`; golden `Cart_Error`;
  manual: an error of the store's own cannot be caused on the stand; it needs a local stack with its database stopped

### TC-ERR-06 — A page that is not there is drawn under the viewer's own header

- **Who:** Maya; guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. As Maya, reload `/p/p-does-not-exist`, `/c/no-such-category` and `/account/orders/HL-99999` (or another
     customer's order).
  2. Read the header the moment the page appears, then a moment later.
  3. On the missing order, press «Go to your orders».
  4. As a guest with a line in the cart, reload `/p/p-does-not-exist`.
- **Expected:**
  - Step 1: «This product is no longer available», the category's and the order's not-found pages; another customer's order is
    answered exactly as a missing one.
  - Step 2: first «Account» with no count (the client's own customer header, its menu with «Sign out»), then her
    name, her count and the category words with their addresses — never «Sign in», never the guest's header.
  - Step 3: `/account/orders`.
  - Step 4: the guest's header with the cart's count.
- **Decided in:** B-18, B-67, B-105
- **Coverage:** `KnownHeaderTest` «a customer landing on a page that is not there sees her own header»;
  `KnownHeaderTest` «before the header answers a customer's header is hers, with no count»;
  `KnownHeaderTest` «a guest landing on a page that is not there gets the server's header»;
  `OrderRoutesTest` «another customer's order and a missing one get the same answer»;
  `ShellHeaderTest` «go to your orders on a missing order lands on the orders»;
  `ProductRoutesTest` «an unknown product is 404 product_not_found»;
  `CatalogRoutesTest` «an unknown category is 404 category_not_found»;
  golden `Product_NotFound`; golden `Order_NotFound`

### TC-ERR-07 — The header over a page with no tree still leads everywhere

- **Who:** guest; Sam
- **Width:** 1440, 390
- **Preconditions:** a page that did not load (offline), and a page still loading.
- **Steps:**
  1. Press «Deals», the cart, «Orders», «Saved», a category word, «Catalog» and an entry, «HAUL PLUS», the account,
     the logo — each from the failed page.
  2. As Sam, press «HAUL PLUS» over a page that is not there.
  3. On a phone, open the menu over the failed page.
  4. Sign out over a page with no tree.
- **Expected:**
  - Step 1: each goes where it would from any page; before any tree has been drawn a category word opens the
    catalog's root `/c`. After a tree, the last tree's links. The account is the one exception: while the header
    alone has not arrived (offline) its slot is a placeholder that presses nothing (B-105).
  - Step 2: the trial's dialog over the page; its answer — started, or refused as a member already — draws the
    page again and asks for its header anew: the pill is the membership's mark, to the account, and offers no
    second trial (B-146).
  - Step 3: the menu opens and its entries lead somewhere; its account row is the sign-in or the account.
  - Step 4: the guest's header, asked anew — not the customer's name left behind.
- **Decided in:** B-67, B-73, B-105, B-146
- **Coverage:** `ShellHeaderTest` «the header over a page that did not load leads somewhere»;
  `ShellHeaderTest` «the header over a page still loading leads somewhere»;
  `ShellHeaderTest` «after a tree the header of a failed page keeps the tree's links»;
  `ShellHeaderTest` «a non-member's haul plus over a page the shell draws presents the trial»;
  `ShellHeaderTest` «on a phone the menu over a page that did not load opens and leads somewhere»;
  `KnownHeaderTest` «a sign-out over a page with no tree draws the guest's header»;
  `KnownHeaderTest` «a page with no tree offers the server's search scopes»;
  `KnownHeaderTest` «a trial started over a page with no tree draws the member's header»;
  `KnownHeaderTest` «a trial refused over a page with no tree draws the member's header»

### TC-ERR-08 — A command refused says why in the server's words; one unanswered offers Retry where it is safe

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** her cart and Saved list; a second tab to make the server refuse (e.g. a line removed there).
- **Steps:**
  1. Change a quantity of a line the other tab removed.
  2. Offline, press «+» on a cart line; then «Retry» online.
  3. Offline, press a heart; press «Save for later»; press «Helpful».
  4. Apply `SUMMER5`.
- **Expected:**
  - Step 1: the line stays gone — the stepper changes a line and never adds one back (B-125) — the cart drawn again,
    and «This item is no longer in your cart» in a message at the error level (the alert mark).
  - Step 2: the page as it was, under «Couldn't reach the store» with «Retry», which stays until it is pressed or the
    page changes (B-136); Retry sends the press again.
  - Step 3: a heart and «Helpful» get «Retry» (sent twice is sent once); «Save for later» gets the message without
    «Retry» — its own button is the press again.
  - Step 4: the field says why; no message on top (a refusal the next tree draws is not said twice).
- **Decided in:** B-75, B-92, B-125, B-136
- **Coverage:** `CartRoutesTest` «a stale stepper does not bring a removed line back and a card's plus still adds»;
  `CommandAnswersTest` «a refusal draws the page again and then says why in the server's words»;
  `CommandAnswersTest` «a refusal the tree draws is the page drawn again and nothing more»;
  `CommandAnswersTest` «no answer says so with Retry which is the press given»;
  `CommandAnswersTest` «no answer without a press to send again has no button»;
  `CartWiringTest` «in the storefront no answer says so and Retry presses again and a refusal says why»;
  `CartWiringTest` «in the storefront no answer with Retry stays until the page changes»;
  `SavedWiringTest` «a refused heart says why and one with no answer offers Retry»;
  `SavedWiringTest` «save for later with no answer says so without Retry»;
  `ReviewWiringTest` «a vote with no answer offers Retry and a refused one redraws the page and says why»;
  golden `Shell_Message_NoAnswer`; golden `Shell_Message_Refused_Phone`

### TC-ERR-09 — A press after a page changed in place redraws what it answers

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** a page changed in place: a facet ticked, a page of results, a scoped search, back within the
  page.
- **Steps:**
  1. After each, press something answered by drawing the page again (a heart, «Helpful», a dialog's «Post»).
- **Expected:**
  - Step 1: the page drawn again with the answer at once — not the old page until a reload. A press whose answer
    lands after the shopper moved on leaves the page they moved to.
- **Decided in:** B-93
- **Coverage:** `RefreshInPlaceTest` «a refresh after a facet opened in place draws the tree it brings»;
  `RefreshInPlaceTest` «a refresh after a page of results opened in place draws the tree it brings»;
  `RefreshInPlaceTest` «a refresh after a scoped search opened in place draws the tree it brings»;
  `RefreshInPlaceTest` «a refresh after back within the screen draws the tree it brings»;
  `RefreshInPlaceTest` «a refresh that lands after the shopper moved on leaves the page they moved to»

### TC-ERR-10 — Nothing the storefront loads comes from another origin

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** a fresh profile; DevTools «Network» open, the cache disabled.
- **Steps:**
  1. Walk the home page, a category, the header's search with the suggest panel open (type `head`), the results,
     a product, the cart and checkout.
  2. Filter the requests by another domain than the store's.
- **Expected:**
  - Step 2: none — no font from `fonts.gstatic.com`, nothing else of anyone's. Sign-in opens shildik's page, which
    is the provider's by design and not a request of the storefront's; a signed-in page's token refresh (the
    provider's discovery and `oauth2/token`) goes to the store's own provider too, and counts as the store's.
- **Decided in:** B-128
- **Coverage:** `BundledGlyphsTest` «every character a string in the code holds is in a bundled face»;
  script `scripts/origin-check.py`; manual: text a shopper types that the bundled faces lack (Cyrillic outside
  the labels' JetBrains Mono, CJK, emoji) still fetches a face, and that is not in the code to hold
