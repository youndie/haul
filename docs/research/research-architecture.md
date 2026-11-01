---
id: research-architecture
title: Haul — architecture research
type: research
status: active
date: 2026-10-08
---

# Research: the architecture of Haul

Haul is an open, end-to-end marketplace storefront in Kotlin: catalog, search, cart, checkout,
payment, the order's life after it is placed, and an account that shows it. Every demo of a Kotlin
stack stops at the catalog; this one goes through the seams a real shop has — several sellers in
one order, an order paid when it ships, a payment that fails after the stock was reserved — and
simulates everything outside the system (card processor, couriers, sellers) inside it, so the whole
path runs on one machine and on a public demo stand.

This document records **verified facts** (what was actually read in code and artefacts),
**decisions taken**, and **risks**. Anything unverified is marked as a hypothesis and says where it
will be checked. Nothing in this repository is built yet: every fact below was read in a dependency's
repository at the commit named, or on the design canvas, and every scenario elsewhere is *target*.

The product brief this was written from and the seven design briefs sent to the designer were removed from the tree before this branch
merged; they are at `youndie/haul@61a8dce!/research/brief-technical.md` and next to it.

---

## 1. Verified facts

### 1.1 The stack the sibling reference services are held at

Verified against shashki, the closest sibling (a reference service on the same stack), at
`youndie/shashki@b3da98f`.

| Fact | Where verified |
|---|---|
| The settings plugin `io.github.youndie.sborka.settings` 0.5.0.113 carries the shared version catalog (Kotlin, Compose Multiplatform, toolchain) | `youndie/shashki@b3da98f!/settings.gradle.kts:36` |
| petich 0.4.0.112, Exposed 1.4.0, PostgreSQL driver 42.7.13, shildik 0.3.0.16, kompot 0.40.0.210, viddik 0.4.0 | `youndie/shashki@b3da98f!/gradle/libs.versions.toml:41-104` |
| The server there is JVM-only: Ktor, Exposed, `petich-postgres` | `youndie/shashki@b3da98f!/gradle/libs.versions.toml:136` and its build files, no native target |

**Consequence 1.** Haul starts from the same settings plugin and catalog rather than choosing
versions of its own; the compiler, Compose Multiplatform and the toolchain move when the plugin
moves. Exact library versions are read again at scaffold (B-02), not copied from here — a sibling's
pins are a starting point, not a source.

### 1.2 kompot on the server and in the browser

Verified against `youndie/kompot@d35ae36`.

| Fact | Where verified |
|---|---|
| `kompot-ktor` is jvm-only | `youndie/kompot@d35ae36!/kompot-ktor/build.gradle.kts:14` |
| `kompot-core` targets jvm and wasmJs (plus android and ios) | `youndie/kompot@d35ae36!/kompot-core/build.gradle.kts:15-29` |
| `kompot-client` renders on wasmJs and on a `jvm("desktop")` target | `youndie/kompot@d35ae36!/kompot-client/build.gradle.kts:18-35` |
| `kompot-realtime-server` is jvm-only | `youndie/kompot@d35ae36!/kompot-realtime-server/build.gradle.kts:14` |
| A tree must be answered through `respondKompotComponent`: `call.respond` drops the `"type"` discriminator on the root | `youndie/kompot@d35ae36!/README.md:103-104` |
| No module publishes a Linux native target | every `build.gradle.kts` of `youndie/kompot@d35ae36` — none declares `linuxX64` |
| `update` writes its nodes into the screen's override store and hands its `deeplink` to the application, which keeps the history; `load` GETs an action and runs it down the chain, the last press winning (B-63) | `youndie/kompot@b0d3fb8!/kompot-client/src/commonMain/kotlin/io/github/youndie/kompot/Update.kt:22`, `Load.kt:50` |
| A tree that arrives drops the overrides even when it is equal to the one drawn — back to the address before a filter brings exactly the tree under the filter's `update`: each load `KompotScreenLoader` completes is an arrival, and a screen drawn without it is told by `arrival`, which Haul's own refresh passes; one store per screen (kompot 0.40.0.215, B-84; B-63 worked around the earlier «an equal tree is not news», B-64 dropped it) | `youndie/kompot@0895da8!/kompot-client/src/commonMain/kotlin/io/github/youndie/kompot/NodeOverrides.kt:23-29`, `ScreenLoader.kt:115`, `Components.kt:769` |

**Consequence 2.** A server built by kompot has to run on the JVM (D1). The browser client and
the screenshot tests share one renderer: `kompot-client` on wasmJs ships, the same on
`jvm("desktop")` draws the references the parity check compares against.

**Consequence 3.** Live order tracking through `kompot-realtime-server` is available on the JVM,
and B-29 built it on kompot's own pieces at the pinned version: the order's tree comes in
`KompotScreenResponse` naming its channel, `KompotUpdateBroadcaster` (its in-memory bus) hands each
move's `UpdateComponentMessage` to the pages on that channel, and `KompotRealtimeProvider` swaps the
node by its id in the browser — since kompot 0.40.0 through the screen's override store, which a newer
tree of the screen drops (B-62), so a refresh no longer has to start the listening over. kompot leaves the transport to the application; Haul's is kompot's
reference one, server-sent events at `GET /ui/updates?topic=` (kompot SPEC §16.6). **Decided in
B-29:** a channel is one customer's — delivered by the order and its customer, refused like the page
(`404 order_not_found`) to anybody else; every stream opens with the order as it is, so a move made
while the page loaded or reconnected is not lost, and the browser reconnects by itself (a second,
doubling to thirty) rather than reloading the page. A page that leaves ends its stream quietly: the broken pipe its next
write meets is a line at `debug`, not an unhandled error for katcher (B-110). The bus is in memory: one process serves the
pages and runs the simulated world, and a second replica would need kompot's Redis bus.

### 1.3 petich storage

Verified against `youndie/petich@7f711e6`.

| Fact | Where verified |
|---|---|
| `petich-postgres` — storage on Exposed over JDBC, jvm only | `youndie/petich@7f711e6!/README.md:57` |
| `petich-sqlx4k-postgres` — the same storage contracts over sqlx4k, jvm and linuxX64, brings no driver | `youndie/petich@7f711e6!/README.md:63` |
| `petich-idempotency` — refuses a key reused with a different request | `youndie/petich@7f711e6!/README.md:59` |

**Consequence 4.** On the JVM both stores exist, so the PostgreSQL access layer is a free choice
(D3). Order placement's `Idempotency-Key` is petich-idempotency's job, not hand-written.

### 1.4 shildik

Verified against `youndie/shildik@18488c4`.

| Fact | Where verified |
|---|---|
| `oidc-auth-server` (token checks on a Ktor server) targets jvm | `youndie/shildik@18488c4!/oidc-auth-server/build.gradle.kts:26` |
| `shared-oidc` (claim types) targets wasmJs too | `youndie/shildik@18488c4!/shared-oidc/build.gradle.kts:20` |
| `oidc-auth-client` targets jvm and native, not wasmJs | `youndie/shildik@18488c4!/oidc-auth-client/build.gradle.kts:16-18` |

**Consequence 5.** The server checks shildik tokens with shildik's own module; the browser cannot
use shildik's client and needs another way through the authorisation-code flow (risk 2).

### 1.5 zavarnik

Verified against `youndie/zavarnik@ba2d6e8`.

| Fact | Where verified |
|---|---|
| `0.1.0` is on the Gradle Plugin Portal as `io.github.youndie.zavarnik` | `youndie/zavarnik@ba2d6e8!/README.md:14` |
| It needs JDK 25 or newer as the toolchain (JEP 514) | `youndie/zavarnik@ba2d6e8!/README.md:36` |
| It trains the cache through the start script of the `application` plugin | `youndie/zavarnik@ba2d6e8!/README.md:40` |

**Consequence 6.** The server is an `application` with a JDK 25 toolchain, and the image ships a
trained AOT cache. Training runs the application, so it needs whatever the application needs to
start — here a database (risk 4).

### 1.6 The design canvas

Verified against the Claude Design project «E-commerce витрина»
(`https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59`), read 2026-10-08.

| Fact | Where verified |
|---|---|
| 125 static artboards, one per `<Screen>_<State>` at 1440 wide and `<Screen>_<State>_Phone` at 390 wide; every state of the nine screens has both, `Catalog_FiltersSheet_Phone` only the phone one | *canvas/canvas.json* in the project, `pages[].artboards[]` |
| Each artboard is a separate file *canvas/<Artboard>.dc.html* with no template logic, its root sized to the artboard | *canvas/Cart_Content.dc.html*, *canvas/Checkout_PointsApplied_Phone.dc.html* |
| Fonts: Bodoni Moda (headlines, prices), Archivo (UI), JetBrains Mono (labels), loaded from Google Fonts | *canvas/canvas.json*, `fonts`, `fontsUrl` |
| Colours are named Material 3 roles: primary Cobalt `#2F2BFF`, secondaryContainer Acid `#DFFF3A`, tertiaryContainer Hot `#FF3D2E`, error Sale red `#D9230F`, background Paper `#F5F3EE`, and the surface, outline and inverse roles | *canvas/canvas.json*, `roles` |
| «Now» on every artboard is 2025-10-07 19:47:23 America/New_York, a Tuesday | *canvas/canvas.json*, `now`; the canvas's weekdays (Wed 8, Thu 9) fit 2025, not 2026 |

**Consequence 7.** The theme is written from `roles`, not from the pictures; a colour on an artboard
that has no role is drift to raise with the designer, not a token to invent. The fixtures and the
seed's fixed clock use the canvas's «now», or every date on a reference differs from its render.

**Decided in B-85 (owner, 2026-10-13): the code is the reference; the canvas is a historical one.** Nothing here
can edit the Claude Design canvas, and it is not caught up with what §2 decided since it was drawn. What it was read
for stands — the roles, the fonts, the copy, the sizes, the «now» — but where the store has moved past an artboard
on purpose, the store is right and the artboard is history: no item re-renders a reference, and none is accepted
against an artboard the code has left.

- **An artboard leaves the parity comparison by its PNG moving to `snapshots/design/retired/`.** viddik reads a
  reference at `design/<stem>.png` and nowhere else (`ViddikEngine.designParity`, viddik 0.7.0), so a fixture whose
  reference moved reads `no ref`, as a page the canvas never drew does. `design/manifest.json` keeps all 125
  entries: a retired one points at `retired/<stem>.png` and carries a `retired` line naming the decisions that
  moved the store past it. The PNG stays in the tree, beside its reason, as what a reviewer compares a decision
  against. Rejected: re-rendering a canvas nothing here can edit; deleting the PNGs (the manifest would then name
  files nobody can open without the history); a list of exclusions viddik does not read; raising the tolerance.
- **Retired, 78.** Every artboard that draws a placeholder tile where the store draws the seed's pictures (D8,
  B-78; the 70 its pull request measured), on the search's pages with B-77's sort and tips as text and on the
  product page with B-71's gallery and seller card; the two product dialogs on a phone, whose page underneath is
  B-71's (`Product_ReviewDialog_Phone` also B-86's bars); and four short phone pages the header's departures take
  past 5 % — B-73's menu button, B-72's strip, B-107's «Search products», B-75's empty cart button
  (`Catalog_Empty_Phone`, `Product_NotFound_Phone`, `Account_NoOrders_Phone`, `Account_Error_Phone`). B-75's
  messages and «N in your cart» and B-133's shipment date had no artboard of their own past the line
  (`Shell_Message*` and `Product_InCart*` never had one; Order_InTransit went with the pictures). B-143's rows of
  the page's heights moved `Product_Loading_Phone` and `Cart_Loading` 5 px down their length (5.4 % and 5.9 %).
- **Kept, 47**, all within 5 %: the loading, error, empty and not-found states, the account's pages and the filter
  sheet. Their residual includes the header's departures, still under the line; a decided change that takes one
  of them over retires it in the same pull request, with its reason in the manifest, and a number over the line
  with no decision behind it is a defect.
- **The goldens are the regression gate** (`viddikVerify` in `check`, every fixture, retired or not): they hold what
  the code draws. Parity holds only that the artboards still describing the store are still drawn so. The fixtures
  keep their artboards' names and sizes.

---

## 2. Decisions

### D1. The server runs on the JVM *(deviation from the brief)*

Brief, second revision: the server on Kotlin/Native (linuxX64), PostgreSQL through sqlx4k or
mongkn, with petich, shildik and kompot.
Decision: the server on the JVM, with a zavarnik AOT cache for start-up.

Why:

- kompot publishes no native target at all (§1.2); a native server would first need linuxX64 added
  to twelve kompot modules, which the owner declined to take on for this product;
- every other library chosen has a JVM target (§1.3–§1.5), so nothing else forced native;
- the price: a larger image and memory than a native binary, and a cold start that zavarnik has to
  win back (risk 4).

### D2. Every screen is a kompot tree built by the server

Decision: the server answers `GET /ui/...` with a kompot tree for each screen; commands under
`/api/v1/...` answer a kompot action (refresh, update, navigate, show a route over the screen) or an
error; a press that only filters, sorts or pages a screen loads its parts from `GET /ui/parts/...`
(B-63, below).
The client renders the tree through a registry of Haul components (`HaulHeader`, `ProductCard`,
`CartLine`, `OrderProgress`, …) declared once in `shared`, and draws `Loading` and `Error` itself.

Why:

- it is the stack's showcase: the storefront is the case backend-driven UI is built for
  (campaigns, deals, the Plus block change without a client release);
- one renderer per component gives the canvas one drawing per piece, so parity is checked per
  component as well as per screen;
- rejected: JSON endpoints per screen and Compose layouts in the client — a second contract for the
  same screens, and the server would no longer own what the shopper sees.

**Decided in B-37, what a drawn control carries.** Every control the canvas draws either carries
something in its tree or belongs to a later item that will give it something; none is wired in the
client alone.

- A control that changes the page is a `navigate` to an address the shell maps to a screen route
  (`/c/…?page=2`, `?sort=price-asc`, `/deals`, `/cart`); the server builds every address, filters,
  sort and page included. A control that changes state is a command: the URL and the body sit in the
  tree, fixed by the server — a card's «+» is `ProductCard.add`, the `LineChange` with the line's
  *next* quantity (one more than the viewer's cart holds), so a press sent twice puts in one, and it
  is absent at ten, at the stock or out of stock. The client sends it and draws the screen again.
  **Decided in B-48:** a command that leads somewhere carries where in the same tree —
  `LineCommand.next`, followed in place of the answer once the server accepted the change, never on a
  refusal. The product page's «Add to cart» is `ProductDetails.add`, a card's «+» for the SKU shown;
  «Buy now» is `ProductDetails.buy`, the same line selected, then `/checkout` for a customer or
  `/sign-in?next=%2Fcheckout` for a guest — the address the cart's «Sign in to check out» uses, so a
  guest never reaches checkout through a page refused first (B-44's prompt is for addresses opened
  directly). Out of stock neither is offered. **Decided in B-100 (the owner's call):** «Buy now» checks out
  that product alone and never adds to its line — `LineChange(selected = true, alone = true)`: the line at
  the quantity the cart already holds, or one when it holds none, is the only one left selected, the cart's
  other lines stay in it unticked, and the price and stock the line shows become the ones the shopper saw
  (the product page showed them), so a line changed since it was added does not leave checkout empty. Of the
  two quantities the owner allowed — one, or what the cart holds — the cart's is taken: the buy box says
  «2 in your cart» right under the button, so checkout shows what the page said, and nothing the shopper
  chose is undone. The cost: the other lines are unticked when the shopper comes back to the cart. A
  guest's «Buy now» goes through sign-in's merge, which keeps the customer's own stored lines as they were
  ticked and sums a SKU both carts hold, so a guest whose account already has a ticked cart reaches checkout
  with more than the product (B-100's findings).
- A list of choices («Catalog»'s categories, the sort's orders) travels as `Link`s and opens as a
  menu in the client. No artboard draws a menu open; the menu is drawn from the theme's tokens.
- **«Show N more» appends** (owner's call in B-77). An `update` replaces a node by its id and has no
  «append», so the append is an address: the next page together with every page the grid already holds,
  `?page=<next>&from=<first>` — `/c/mugs?page=3&from=1` draws pages 1 to 3 in one grid, and «Show N more»
  under it goes to `page=4&from=1`. The press is the screen's `load` as before (B-63), answered with the
  grid of all those pages; the cards above are drawn again unchanged, the shell keeps the page and its
  scroll (B-62), and a reload or a shared link draws the same grid. A page number opens that page alone
  (no `from`); a filter, a sort or a category starts again from the first page. «N» is how many the next
  page holds — 24, or fewer on the last. `from` outside 1 to `page` is `400 validation_failed`, on the
  category, the search and the deals alike. The cost: each press sends every card the grid holds, so the
  answer grows by a page each time. **A grid holds at most eight pages** (B-90, `Browse.MAX_HELD_PAGES`):
  192 cards, «everyday», the seed's longest search (185), whole. A grid that holds eight has no «Show N more»
  — its page numbers go on — and `from` more than seven pages before `page` is `400 validation_failed`, so
  `?page=517&from=1` no longer asks for a listing's every card in one answer.
- **The page numbers are the current page with its neighbours** (B-77): the first and the last, the
  current one and one on either side, «…» for each run left out — «1 … 6 7 8 … 517» —, a run of one page
  drawn as its number, and the three pages at either end, so the first page still reads «1 2 3 … 517» as
  the canvas draws it. At most seven numbers, every page one press from the one before it
  (`server/.../feature/catalog/screen/Cards.kt`, `pageNumbers`). The Saved list draws the same numbers.
- **The search has the sort** (B-77) the route always parsed: `FilterChips.sortLabel` and `sorts` end the
  category chips' row — at its right edge at 1440, over it on a phone — the same orders and menu as a
  category's, each a `load` from the first page; a category chip keeps the sort. Since B-103 the search's
  own order, «Relevance», comes first and is its default: the order the database matched in
  (`ts_rank`, then the most reviewed), which «Popular» as the default had thrown away. Its address has no
  `sort` (`sort=relevance` is taken too), so «Popular» is now `sort=popular` on a search; a category page
  has no relevance and keeps «Popular» as its default. The chip chosen and the sort shown carry nothing,
  as on a category (B-102). The
  no-results tips («01 Check the spelling», …) are advice, drawn as text with their numbers, not on the
  white pills the queries to try use, which run a search when pressed. No artboard draws the sort and the
  canvas draws the tips as pills, so `Search_Results`, `Search_Autocomplete` and `Search_NoResults` (and
  their phone twins) depart from their artboards on purpose from B-77 on, and left the parity comparison in B-85
  (§1.6).
- **The suggest panel follows the keyboard** (B-77): with the panel's queries offered, ↓ and ↑ move the
  highlight over them — from none to the last and back to none above the first — and Enter opens the
  highlighted query; with none highlighted Enter opens the text as typed, as before. Typing starts over
  from none. The field owns it (`SearchInput` in `ui/HaulHeaderView.kt`); the shell offers the panel's
  queries and draws the highlight.
- **`/deals` is a screen** (owner's call in B-37): «Deals», «View all deals», «Shop the sale» and the
  empty cart's «See today's deals» all lead there, and the server already had what it needs — today's
  deals with their countdown on the first page, then every product whose shown price is under its old
  one, the deepest discount first, 24 a page. It is built from the components other screens draw; no
  artboard draws it, so it has no parity reference.

**Decided in B-49, the last of them.** Home's «All N categories» opens **the catalog's root, `/c`**
(`StorefrontPage.Categories`, tree `GET /ui/c`; owner's call), every top-level category as a tile in
the header's order — built from home's own tiles, no artboard, so no parity reference. The brand facet's
«Show N more» is `Facet.moreAction`, the same page with `expand=brand` — filters, sort and page kept, and
kept again on every address the page links to, so ticking a brand does not fold the list; any other
`expand` is `400 validation_failed`. A recent search's row is a `Link` to its `/search?q=`, so the client
builds no address from the text. The «HAUL PLUS» pill is `HaulHeader.plus`: B-23's `present` of the
trial's dialog for a customer who is not a member, sign-in for a guest; for a member, since B-108, nothing —
the membership's mark (see «Decided in B-108»).

- **The one control wired in the client alone is the filter sheet's** (and, since B-73, the phone
  header's menu, below). The sheet is the facets already in the tree, drawn over the page on a phone;
  «Filters» opens it and «×» closes it without asking the server, because there is nothing for the server to decide and no address to change. «Show N items»
  closes it too (B-54): each press inside the sheet already opened the page those results are on. The
  sheet is held by the shell above the page
  (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`), so
  it stays open over the pages its own presses open and is drawn from each one's tree; any other new
  page closes it. It stays there after B-62 kept the page across a filter: back and forward keep the
  page too, and only the shell sees whether an arrival came from the sheet. Since B-63 the sheet's presses
  are `load`s, so it stays open by itself and is drawn from the updated results; what the shell still
  decides is that a page *visited* — a link, back, forward — closes it, and that its facets follow nothing
  while a `load` of the screen is on its way.
- **A control with no page behind it is not drawn** (B-72; B-49 had drawn such words as plain text, and
  on the stand they still read as links). See «Decided in B-72» below.

**Decided in B-108, the same thing looks the same on every page.** The QA pass of 2026-10-10 listed what one
page drew one way and another page another; each line was fixed, or the difference kept and said why here:

- **The footer stays on home, the catalog's root and the deals** — kept. The canvas draws it on home alone, no
  artboard of another page has one, and every word it holds is in the header on every page: «Deals» in the
  category row, «HAUL PLUS» at its end, «Orders» among the shortcuts. Drawn on the other pages it would be a
  second way to the same three places, below content the artboards end without it.
- **The catalog's root and the deals have placeholders of their own** (`CategoriesLoading`, `DealsLoading`,
  `PageKind.Categories` and `Deals`): they drew the header alone while their trees were on their way.
- **A listing of one page draws no page number** (`PaginationView`): «1» alone was a control with nothing to
  choose. The tree still sends the node — a search narrowed in place by an `update` must keep the node it
  replaces — and the client draws nothing for it.
- **A page number opens its page at the top** — an amendment to B-62, whose kept page kept the scroll for
  every new address of a screen, so a page number pressed at the foot of the Saved list on a phone landed at the
  foot of the next page. The page is still kept while it loads (no placeholder); only the scroll moves, at the
  press. A filter, a sort and «Show N more» keep the scroll as before. The Saved list keeps only the page
  numbers, no «Show 24 more» (B-20, as the canvas draws `Saved_*`).
- **An active order's card opens its order wherever it is pressed**, as a history row does (B-74); «Details ›»
  says what the press does.
- **Every tile of a product carries its stored photo** (D8): a cart line, checkout's lines, an order's lines, the
  suggest panel's products — or, since B-78, its picture where no photo is stored.
- **A cancelled order says «Back to cart» once**, in its notice beside the reason; the summary's second one is
  gone (`OrderTotals.back` with it). The notice is the first thing a phone draws, and every other way on of an
  order page is the summary's only because no notice is there.
- **An order's summary draws the promo code's row**, as the cart's and checkout's do since B-100: an order keeps
  the sale and the code as one sum (`discount_cents`), so «Discount» is read off its lines (each line's price
  before its sale less what it cost) and «Promo · AUTUMN10» is the rest.
- **A member's «HAUL PLUS» is the membership's mark, with nothing to press** (amends B-49). It led a member to
  the account overview, where the membership is one tile of four, and on the overview it opened the page shown —
  a press that did nothing. No page of the store is the membership's own (B-74), so, as with the strip's words
  without a page (B-72), the pill is not a control for a member; the footer's «Haul Plus» is text for one too.
  Rejected: a dialog describing the membership (no artboard, and the home page's Plus block already says what
  it saved and when it renews). The client's own header before any tree (B-105) still points the pill at the
  account: it does not know whether the customer is a member, and the server's header replaces it.
- **A card's old price is never broken.** Beside the price when the two fit, under it when they do not
  (`ProductCardView`, `Prices`): a deal of the day with cents, six across at 1440, broke «$432.9» from «9».
- **A message over the page has a ring of the page's white and a shadow** (`MessageNotice`): Ink on the home
  page's Ink Plus block, «Added to your cart» did not stand out.

**Decided in B-143, a placeholder's rows are the page's heights.** The product page's crumbs placeholder was a
12 px bar where the crumbs' line is 16.8 px, so the photo came 5 px lower when the page arrived (B-104 had matched
its left edge and width only). A placeholder row standing for a line of the page's text is now that line's height
(`lineOf`, the height `Text` gives one line of a style), with the bar the artboard drew centred in it, and the
spaces around it are the page's own: the measures live beside the views that draw them (`crumbsStyle`,
`crumbsTop`, `titleStyle`, `kindsHeight`, `FILTERS_HEIGHT` in `CatalogViews.kt`, `detailsTop` in
`ProductDetailsView.kt`) and `shell/Shell.kt` reads them rather than copying the numbers.

- **Matched**, at 1440, 1280 and 390: the product's photo (top, left, width) — and at 1024, where the placeholder
  draws the page's two columns from the page's `photoWeight` and `COLUMNS_GAP` (B-153); a category's first card, opened with
  no filter applied — the crumbs, the title, the kinds (their pills 40 px, 37 on a phone, the page's, not the
  canvas's 44 and 40) and on a phone the applied filters' row, which stands there empty; the cart's lines, under a
  title row of 101 px rather than the bar's 96 (`ProductPageLayoutTest`, `LoadingPlaceTest`).
- **Not matched, because the page's content decides it**: an order's steps arrive about 150 px lower at 1440 —
  the title takes two lines there and a lead follows it, which the placeholder cannot know; a category's cards
  come lower under applied filters (a row of chips on a phone, a second line of them at 1024) and on a phone under
  a name long enough to put the count under the title («Headphones», 28 px).
- **Not measured**: the catalog's root and the deals (`TitleSkeleton`), which have no fixture of a page to
  compare with.

**Decided in B-72, the strip and the footer name only what has a page** (owner's call: wire what has a page,
draw the rest as text or leave it out). Words in a utility strip or a footer link column read as links
whatever their style, so the ones with no page are left out rather than drawn as text:

- **The strip** says where the store delivers and for how much: «DELIVERING TO BROOKLYN, NY 11211» in the
  strip's own type (the canvas's «Deliver to» with the place in the accent colour is a place picker's look, and
  there is no picker), «FREE DELIVERY OVER $35». «Sell on HAUL», «Help» and «EN · USD» are gone, and the
  phone's «HELP».
- **The footer** keeps «Deals» (`/deals`), «Haul Plus» (the header's pill: the trial's dialog, a guest's
  sign-in; a member's is text since B-108) and «Track an order» (the orders; a guest's sign-in returning to them) under
  «Shop» and «Help». «New arrivals», «Gift cards», «Returns», «Delivery», «Contact us» and the «Sell» and
  «Company» columns are left out; a word comes back with its page. On the wire each column's words gained
  their actions by label (`FooterColumn.linked`, a default-empty addition, as `HaulHeader.categories` follows
  `catalog`), and the footer is built per viewer (`Frame.footer`). The footer keeps the canvas's height —
  four column places at 1440, two rows of two on a phone, four lines to a column — so the wordmark is cut
  where it was; without that Home's phone artboards read 9–13 % against the canvas.
- **The search field's «All categories ▾» is a real scope.** The header carries every top-level category with
  its slug (`HaulHeader.scopes`, `SearchScope`) and the scope of the page's search (`HaulHeader.scope`). The
  picker is a menu kept in the client (`SearchInput.scope`): a choice changes no page, and the next search is
  `/search?q=…&category=<slug>` — `Address.search` stays the one address the client builds. The search's
  `category` now holds the category and every category under it (it matched a leaf's products only), and a
  top-level scope is drawn as a chosen chip after «All». The search's parts include the header, so «All» sets
  the picker back to «All categories», and a leaf's chip names the leaf there (B-119; until then it read «All
  categories» over the leaf's results): a search in a category below the top level adds that category to the
  header's `scopes`, named with its top-level one — «Party · Limited» — and offered right after it, so the picker
  reads it and the next search stays in it. A card's «+» keeps the scope in its answer's header (`&category=`,
  `LineAnswers.SCOPE`). Without scopes on the wire — the shell's header before
  any tree (B-67) — or without a field to type in, the picker opens nothing. Since B-105 a page that stays
  without a tree draws the header alone the shell asks for, scopes included, so the picker works there (B-90
  holds it in `KnownHeaderTest`); it is still drawn and opens nothing while the first page loads (the tree brings
  the scopes, and the pending header's account slot is a placeholder too) and when the header alone does not
  arrive. On the sign-in page landed on cold, which asks for nothing while it loads (B-66), the picker's press asks
  for the header alone and opens with its scopes once it answers (B-138).
- **A scope the query finds nothing in says so** (B-90). With the query's results all in other categories, the
  scope's chip read 0 over an empty grid. The page is now the title and the chips as before — the scope's
  chosen at 0, and since B-119 the title «0 results» with it — then an `EmptyState`, «Nothing for “mug” in Fashion», «N results in other categories.» and
  «Search all categories». It has no grid, no pages and no sort, so its chips `navigate` rather than `load`:
  the pages they open have the grid an `update` could not add (a `load` of its parts is answered with the
  whole page, as for a category gone).
- **What a search's page names is what its grid holds** (B-119). The title counts the chosen chip's results —
  «15 results» under one «Classics 15», where it counted every match, «600 results» — and «All»'s are every
  match. A chip's name is its category's, and where that repeats on the page — the seed's generic leaves,
  «Classics», «New in», «Signature» and «Limited», sit under every top-level category, so `classic` drew ten
  «Classics 15» — it carries its top-level category, «Party · Limited», then every level if that still repeats
  (`SearchScreen.names`, `nameOf`). Rejected: hiding the repeated chips — they hold different results. The picker
  names the chip's category (above).
- **The suggest panel is in the picker's scope too** (B-103; it ignored it until then). The client asks for
  `/ui/search/suggest?q=…&category=<slug>` (`Address.suggest`), and again when the picker changes; the panel's
  products, categories and counts and «All N results» are the matches under the scope, and every search it
  opens — a query, a recent search, «All N results» — stays in it. A category suggestion opens the query in that
  category (`/search?q=…&category=<leaf>`), the count it shows, where it used to open the whole category
  without the query. A scope the catalog does not have is `404`, as on the results, and the client shows no
  panel. A query that matches nothing under the scope — `running` under «Appliances · Classics» — still suggests
  its queries, but sends no products and no «All N results» (B-132): the client draws it without the products'
  column, as it draws recall, where it drew an empty «Top products» over «All 0 results».
- Rejected: plain text for the dead words (B-49's answer, which the stand walk showed did not read as text);
  a picker that runs the search as soon as a category is chosen (it would leave the page while the shopper
  may not have typed yet).

**Decided in B-160, the stand says it is a demo — in a strip of its own and an About page** (owner, 2026-11-01).
Four reviews of the stand found nothing on it said it was a demo or what it was built with.

- **The strip is not part of the header tree.** In `HaulHeader` it would have moved nearly every golden and every
  client body, and taken the canvas references past their tolerance. The server answers it alone, `GET /ui/demo`
  (`DemoNote`: the words, «About this demo» to `/about`, «Source on GitHub» to the repository); the storefront's root
  (`App` → `DemoStripped`, `shell/DemoStrip.kt`) asks for it a frame after the first page has drawn, so the first
  load waits for nothing more, and draws it at the top of every page's scroll, the pages the shell draws itself
  included. A close hides it for the session (`sessionStorage`; memory on the desktop) and it is not asked for again.
  `Storefront` drawn on its own — the shell's tests — and every golden have no strip.
- **A link off the store is kompot's `open_url`**, already in the wire's standard set: the shell opens it in a tab of
  its own (`LinkCopier.openUrl`), and the right button offers its whole address.
- **The About page, `/about`, is a server tree in the frame** (`GET /ui/about`: the header, `AboutBody`, the footer):
  what the stack is, what is real (the saga and its compensations, the simulators, the observability) and what is
  not (no money moves, no parcel ships, the products, reviews and sellers are invented), and the repository and its
  README. It is a `StorefrontPage`, so a reload of it is the page (B-36).
- **The footer is left as it is.** A word there would move every footer golden; the strip leads to the About page
  from every page. Rejected: a banner over the hero, a modal on arrival.

**Decided in B-164, «Live orders» shows every order's saga as it runs** (2026-11-01). The synthetic shoppers place,
ship and return orders all day, one in ten declined on purpose so the saga compensates (B-89), and nothing a visitor
could open showed it — only tracy did.

- **The page, `/live`, is a server tree in the frame** (`GET /ui/live`, `feature/live/`, a `StorefrontPage`): the last
  20 orders across the store, newest first, each a row of lanes — «The saga» (the members of `OrderSaga.kt` by
  `OrderSagaMembers`, in their order), one per seller's shipment (Packed, On its way with what its leaving charged,
  Delivered — or Ready, Collected), «The return» — and its trail. Its intro says the rows are the petich saga of every
  order and links to `OrderSaga.kt` on GitHub. Reached from the About page («Watch the live orders») and the demo strip
  («Live orders», between «About this demo» and «Source on GitHub»: it fits on the strip's one line at 1440 and its
  links' line at 390).
- **A row is read off the order's own rows, not off its trail in tracy.** `OrderTrail` (B-109) writes to tracy only;
  reading tracy from a public page would put another product's API in the request, and a second write of the trail
  into the database would put a write in every saga member's path, where B-139/B-140 bound the time a member may take.
  The saga runs inside the request and leaves the order `placed`, or `cancelled` with its reason; the shipments'
  history, their captures and the return are stamped as they happen. So a placed order ran every member; a declined
  card refused at `authorise-payment`, and the members before it that took something are undone, in reverse; a member
  with nothing to take (a pickup's window, no points, pay on delivery) is drawn as having nothing to do. The one thing
  a row cannot know is which member after the order opened failed in an order cancelled as `failed`: its saga lane says
  «failed» at the end and «unknown» before it, and its trail says the saga turned back — tracy has the member. The saga's
  steps carry the moment the order was placed: they run within the request, in milliseconds.
- **Anonymised by construction.** The feed reads its own projection (`ExposedFeed`, `FeedOrder`), which selects no
  customer, address, window, pickup point or code — not the orders' repository, which reads all of them.
  `LiveRoutesTest` reads every such value the orders hold off the database and finds none in the body.
- **Cheap whoever looks.** Five statements whatever the store holds — the last 20 by the index `orders_placed_at`
  (V33), then their items, shipments with their sellers and history, captures by the shipments' ids, returns — held
  by counting statements over two thousand copied orders (B-159's lesson). The live channel is the order page's
  transport (B-29): the page's tree names `live:orders`, the same `GET /ui/updates` streams it, the same broadcaster
  delivers it. A move draws the body once for every listener, at most once a second (`LiveFeed.PAUSE`), nothing when
  nobody listens; a listener that connects while nothing moved is sent the last drawing; at most 200 listen at once
  (`LiveFeed.CAPACITY`), past that the stream is `503 unavailable` and the client asks again later. The body is sent
  whole: a new order changes every row, and twenty rows are a few dozen kilobytes.
- **`GET /ui/updates` moved to the public tier**: one address serves both live pages, and the topic decides who may
  hear it — `live:orders` anybody, an order's topic the customer's, `401` without a sign-in and `404 order_not_found`
  for another's, as before (`respondOrderUpdates`).
- **A row opens in place**, not onto a page of its own: the trail is a dozen lines, the rows stay live while one is
  open, and no second address or channel is needed. Which rows are open is the only thing the client keeps (by the
  order's number), so a row stays open as the body arrives again. Rejected: an order's page for the public (it would be
  a second order page, with the customer's page's rules to keep apart), reading tracy, writing the trail twice.

**Decided in B-124, the empty field is recall.** A customer pressing the empty search field — or emptying it while
in it — is offered their recent searches at once: the last ten, newest first, «Clear», each row in the picker's
scope; a guest's empty field asks nothing. Until then the press drew a caret, and the recent list showed only once
two letters were typed — suggest filtered by history, not recall. The wire is the suggest route with an empty `q`
(`/ui/search/suggest?q=&category=…`, `SearchScreen.suggest`): the same `SearchSuggestPanel` with `query` empty,
the recent searches alone and no queries, categories, products or «All N results»; for a guest the same panel
with nothing in it, the route staying public. One character is still `400 query_too_short` — anything typed is a
query. The client draws that panel without the products' column (`SearchSuggestPanelView`), and a panel with no
query and nothing recent — a guest's, or one just cleared — is no panel (`HaulTransport.suggest`). The shell asks
at once rather than after the typing pause, keeps the panel when the focus leaves the field for a press on it, and
drops it once anything is typed (`Storefront`). Rejected: a route of its own (`/ui/search/recent`) — a second
address for the same panel, with the scope's `404` and «Clear»'s asking-again to repeat; `401` for a guest — the
route is public and the client does not ask for one anyway; dropping suggest's minimum to nothing — it would
match the whole catalog for an empty query. No artboard draws it: `Search_Recent` and `Search_Recent_Phone` are
the goldens, over the home page.

**Decided in B-135, the suggest panel is a suggestion, not a menu.** The field asks for no panel under the server's
minimum — `MIN_SEARCH_QUERY` in the contract, which the server's `MIN_QUERY` is — so one typed character sends nothing
rather than a `400 query_too_short` the browser logs; Enter on it still opens the «Keep typing» page. Escape closes the
panel and leaves the caret in the field, as the combobox pattern has it. A press outside the panel and the field closes
it and still reaches what it pressed: the scrim is drawn and reads no press (`SearchSuggestOverlay` watches presses
before the page does and consumes none). The menus of B-126 keep absorbing the press off them by decision — they are
choices the shopper opened; the panel opens by itself under typing, and a press past it is the shopper moving on.
Rejected: drawing the panel through `OverPage` — it would take the press-catching layer that is the opposite of what
the panel needs, and the field it hangs from is in the page.

**Decided in B-73, the phone header reaches what the 1440 one does.** The canvas's phone header has the
logo, the heart, the account, the cart, the search field and a category row cut at the edge — no
«Catalog», no «Orders», no «HAUL PLUS», and ten of the 32 top-level categories, the rest past the edge.
Where the canvas has no answer the conventional phone pattern is taken:

- **The category row scrolls sideways**; the canvas's cut at the edge reads as the sign that there is more.
  At rest it is pixel for pixel the row it was. The cut is the row's, not a word's (B-152): at 390 the canvas's row
  ends on «Fashion» with «Beauty» just past the edge, as the store's does at 375, 390 and 800, so at those widths
  no letter of the next word shows. Kept as drawn: whether a word is cut depends on the width and the categories'
  names, not on a rule; the menu, not the row, is the phone's way to every category; and a fade or a word pulled
  into view would move every phone golden for a hint no artboard draws.
- **A menu button left of the logo** opens a full-screen menu: the account («Sign in» for a guest),
  «Orders», «Saved», «Deals», «HAUL PLUS», then under «Catalog» every top-level category
  (`HaulHeader.catalog`). Each entry follows what the header already carries, so the server is unchanged.
  It is drawn in the filter sheet's frame (Catalog_FiltersSheet_Phone: title row, «×», a scrolling body)
  from the theme's tokens; no artboard draws it (goldens `HaulHeader_Menu_Phone`, `HaulHeader_Menu_Guest_Phone`).
  It is the second control wired in the client alone, beside the filter sheet, and held the same way: by the
  shell (`ui/HeaderMenu.kt`, `HeaderMenuState`), which closes it on any page visited; an entry closes it
  before it is followed, so the Plus trial's dialog is not drawn under it.
- **The cost against the canvas**: the button and the logo moved 44 px right are drawn on every phone page
  but checkout's, +0.12 to +0.77 % on each phone artboard. Three short ones that already sat near the line now
  read over 5 % (Catalog_Empty_Phone 4.54 → 5.06, Order_Placed_Phone 4.83 → 5.06, Product_NotFound_Phone
  4.53 → 5.21); the tolerance is unchanged. The canvas will not gain the menu (B-85, §1.6): Catalog_Empty_Phone,
  Product_NotFound_Phone and two account pages the header later took over left the parity comparison,
  Order_Placed_Phone with the pictures; every other phone artboard kept stays within the line with the button.
- Rejected: a menu inside the category row (a trailing «All» or a leading grid button) — it scrolls away
  with the row or hides its words, and «Orders» and the account do not belong under categories; the button
  right of the cart — it moves the heart, the account and the cart instead of the logo.

**Decided in B-82, the widths the canvas does not draw.** The canvas draws 1440 and 390; the client drew the
1440 arrangement from 768 up, squeezed — at 1024 the search field a sliver and «HAUL PLUS» one letter a line,
at 800 the cart's title one letter a line, at 1280 the product's «WH-1000XM6» broken before its last figure.
Three bands now, chosen by the page's width (`HaulThemeAtWidth`, `App.kt`):

- **Below 1024, the phone's arrangement** (`COMPACT_BELOW`, was 768): the menu button, the scrolling category
  row, one column, the phone's cart lines. Every phone layout already held any width from 375 up; at 800 it
  reads, with large cards (two across) and a photo as wide as the page. A tablet held upright gets a big
  phone, which is what the stores the canvas imitates give it.
- **From 1024 to 1279, the desktop rearranged** (`LocalHaulMedium`, `WIDE_FROM`): what 1440's arrangement
  squeezes moves rather than shrinks. The header's search field takes a row of its own under the logo's (the
  header is 261 px, not 185; the page's static frame draws it so). The cart's lines are the phone's beside the
  summary; the product's buy box goes under its title, beside the photo, and its facts under the copy;
  checkout's summary under its sections; the account's tiles one under another and its history rows the
  phone's; a search's kinds scroll sideways; a banner's title is 32 px and its picture is left out.
- **At every desktop width, what does not fit is fitted**: the header's category row draws the words that fit
  whole and keeps «HAUL PLUS» at its end («Catalog» lists every category); a grid takes as many cards of
  168 px as fit (four at 1024) up to the columns the tree asks for; the campaign's figure, the footer's
  wordmark and the product's title are set as large as their width holds (`FittedText`); the catalog's applied
  filters wrap, the sort at the first line's end; a search's category chips scroll sideways before the sort
  (B-142 — from 1280 up they were one line cut under it, and `classic`'s 111 left all but a dozen out of reach;
  wrapping them would push the grid a screen down), and the chosen one is scrolled into view when it arrives.
  At 1440 each of these draws what it drew, and no 1440 or 390 golden moved.
- No artboard draws 1024: `Home_Content_1024`, `Catalog_Content_1024`, `Product_Description_1024` and
  `Cart_Content_1024` are goldens without a reference, and `WidthsTest` reads every text of the header and of
  every page's tree at 800, 1024 and 1280 for a word cut at an edge or broken between two lines.
- Rejected: the phone below 1280 (a laptop at 1024 with a menu button and two cards across); the desktop down
  to 768 (at 800 the cart, the product page and the catalog's grid beside the filters need a third
  arrangement each); dropping the words under «Orders», «Saved» and the account and the cart's «Cart» to keep
  the 1024 header one row (about 200 px back for the field, and three bare icons the canvas draws nowhere on a
  desktop); scaling the whole page down.

**Decided in B-67, the header over a page the shell draws itself leads somewhere.** A placeholder, an error
page, a page that is not there, the sign-in prompt and the sign-in page carry no tree, so their header is the
client's. It used to be `SHELL_HEADER` with no actions at all: on a page that did not load only the logo and
the search worked, and landing straight on a 404 or the prompt gave the same dead header.

- **Once a tree has been drawn, its header is the one**, its links the server's: the shell keeps the last
  tree's header and draws every page of its own under it (`LocalShellHeader`, `shell/Shell.kt`) — a
  placeholder or an error as a placeholder (who is looking not shown, no cart count), a page that is not
  there as it is. The pixels are the artboards' as before; no golden changed.
- **Before any tree, the shell's header carries the addresses the server's `Frame` fixes for a guest**:
  «Deals» `/deals`, the cart `/cart`, «Orders» and «Saved» the sign-in returning to them (a customer
  arriving at `/sign-in` is sent straight on, B-66), the account and «HAUL PLUS» the sign-in. Which address
  a category has is the server's to say and no tree has said it yet, so each word of the category row and
  each entry of «Catalog» opens the catalog's root, `/c`, every top-level category, one press from the one
  meant. Rejected: copying the seed's slugs into the client (paths are the server's strings, and a store
  with other categories would get 404s); drawing the words as plain text or not at all (the placeholders'
  artboards draw the row, and the shopper cannot tell a dead word from a live one). Since B-105 that is a
  guest's header only, and only until the server answers the header alone (below).
- **A `present` of that header is drawn over the page**: the last tree's «HAUL PLUS» for a customer who is
  not a member (the trial's dialog, B-49), which the shell's handler used to drop because the screen's
  `presented` lives inside a screen that a page of the shell's own does not have. The dialog's answer
  `refresh` loads the page again; a visit takes the dialog away.
- **A sign-in from that header that returns to the page already shown loads it again** — a guest's «Saved»
  over the Saved list's error page — as a tree's does; opening it did nothing. Since B-95 any press to the
  page already shown does (below).
- **The phone header's menu opens over a placeholder too** (B-73 kept it shut there, a button drawn and
  dead): it lists what the header carries, and its account row is the sign-in or the account, while the slot
  itself stays a placeholder.

**Decided in B-105, a page that came with no tree knows who is looking.** Reloading a missing order, product
or category as a signed-in customer drew B-67's guest header: «Sign in» (which sent her to the sign-in page and
straight back), no count, every category word to `/c`. The header is the server's to draw, so the client asks
for it:

- **`GET /ui/header`** (public, `Frame.HEADER`) answers the header alone, as every page draws it for whoever
  asks — the customer's name, the cart's count, the category words with their addresses, «HAUL PLUS» as the
  offer makes it (`server/.../feature/catalog/CatalogRouting.kt`). The shell asks for it when a page came with no
  tree — not there, refused, an error, the sign-in prompt — and no header has been drawn for who is looking
  (`AskForHeader`, `shell/ShellHeaders.kt`): once per visit and load, and an answer that does not arrive leaves
  the header the client has. Placeholders and error pages still draw it as a placeholder (B-67), so their pixels
  are unchanged. The sign-in page asks for none while it loads: it is a way through, and a customer is sent on
  at once. So the sign-in page landed on cold draws the client's own header, whose ten category words each lead
  to the catalog's root (B-133). **Since B-138 a press asks instead** (owner, 2026-10-12): the phone header's
  menu button or the search picker asks for the header alone (`askForHeaderOnPress`, `LocalAskForHeader`), once
  per visit however often they are pressed; the menu opens at once with what the client has and lists the 32
  top-level categories with their addresses when the answer arrives, and the picker opens with the scopes once
  they have. An answer that does not arrive leaves the client's own header until the next visit. Rejected:
  copying the 32 into the client (D2: the categories are the server's), and asking on the page's load (B-66).
- **A header is kept for whom it was drawn** (`ShellHeaders`): a tree's or the header alone, tagged with
  whether the shopper was signed in (`SessionControls.signedIn`) when it was drawn. A sign-in or a sign-out over
  a page with no tree no longer leaves the other shopper's header (a guest's «Maya» after signing out over a
  404); the page asks again. **Since B-130 the shell follows who is looking as it changes**
  (`SessionControls.signedInChanges`, collected in `Storefront`) instead of reading it whenever something else
  draws the storefront again: a sign-in that ended by itself — the provider refused to renew it under a press,
  and `Identity.send` left the guest — changed it with no visit, and Maya's header stayed over a guest's tab.
  A session ends the same way however it ends; blanking the header was rejected (the guest has one of their own).
  **Since B-146 a `refresh` over such a page asks again too**: what the shopper is can change with no sign-in
  or sign-out — the Plus trial's answer (`close`, `refresh`) makes a member — and the header kept for the customer
  was still «known», so the pill stayed the offer and a second «Start trial» met `409 already_member`. A refresh
  the shell's own handler follows (`ShellHeaders.redrawn`) leaves the kept header drawn until the answer and asks
  `/ui/header` once more; a page with a tree needed nothing, its refresh draws its own header. Rejected: a
  membership flag beside `SessionControls.signedIn` (the client does not know the membership — the token does not
  carry it, the header does) and a signal from the trial's dialog alone (a refresh is how every command says the
  page changed; the shell's header is part of the page).
- **Until the server answers, the client's own header follows the session**: `SHELL_HEADER` for a guest,
  `CUSTOMER_SHELL_HEADER` for a customer — the account slot reads «Account» and opens the account's menu with
  «Sign out», no count (the client does not know it), «Orders» and «Saved» lead straight to them and «HAUL PLUS»
  to the account; the category words are still the catalog's root.
- Rejected: sending the header with the `404` (an error body is the contract's `ErrorBody`, and B-94 refused
  a `200` for a refusal); the client keeping the last header across reloads in the browser's storage (a count
  and a name that may be stale, and the categories still a guess on a first visit); reading the customer's name
  out of the access token (the client never reads the token, and the count would still be unknown).

**Decided in B-62, a screen is a path** (owner's call). `/c/mugs?brand=Ostra` and `/c/mugs` are one
screen, `/c/mugs` and `/p/…` two. A new address of the same path — a facet, a sort, a page of results, a
product's tab or SKU, back and forward between two of them — is loaded behind the page that is drawn
(kompot's `KompotScreenLoader(screenKey = …)`, 0.40.0.212): the page keeps its scroll and whatever its
nodes hold open, a thin line under the header says a load is on its way, and the new tree is drawn in
place, Compose redrawing only what changed (kompot keys `column` children by `id`). A load that does not
arrive keeps the page under a notice with Retry, not the error page. Another path draws its placeholder
and starts at the top, as before. A customer's page refused within its screen for a lapsed sign-in is the
exception: it is taken down and asks for the sign-in (B-44), so a guest is not left looking at it. The
server still answers each address with the whole tree; the presses that only filter answer with the
changed nodes since B-63. A kept page's address changes before its tree arrives, so the screen's `refresh`
reads the address and the tree drawn when it runs, not when it was made (B-93): one made with the address
wrote the new page into the tree before it, and the shopper kept the old one until a reload. **A press to the
address already shown is a visit too** (B-95): the header's Cart or a message's «View cart» on the cart, the
query shown submitted again by Enter, the button, a suggestion or «All N results». `Navigator.open` used to
drop it, so the press did nothing and the suggest panel stayed open; now it takes no history entry and loads
the page again behind itself like any new address of the screen, and closes what a visit closes — the panel,
the filter sheet, the phone menu, a dialog presented over the page. The product page's «2,341 reviews» on the reviews
tab is such a press: it loads the page again, and since B-121 asks for the tab row itself (`TabsInView`): the
same tab arriving moves nothing. Since B-132 it is the address shown, too: the links the product page writes for
itself — the tabs, the rating, «All specifications», «Show more», the buy box an «Add to cart» redraws — name the
SKU only when the page's address did (`ProductScreen.address`), so on `?tab=reviews` the press no longer rewrote
the address to `?sku=…-0&tab=reviews`. A variant picked names its SKU as before. Rejected: comparing addresses in
the client with the drawn SKU filled in — the client would learn the product page's parameters, which are the
server's.

**Decided in B-94, a page the server refused leads where it helps.** The shell drew every answer that was
not a tree as «didn’t load» with Retry, and Retry sends the same request: a checkout with nothing selected
(`409 cart_empty` — back after placing an order, a reload), a one-character search (`400 query_too_short`) and
an old or edited link (`400 validation_failed` naming a `page`, `sort`, `from`, `shown` or a `sku` no longer on
the product) all came back as the same error. The server's code and `field` already say what happened, so the
client reads them (`shell/Recovery.kt`): an empty checkout gives its entry to the cart; a search too short is
said as such under the header, the query in its field and no Retry (`SearchTooShort`, no artboard); a `400`
naming a query parameter the address has loads the page without it — one parameter per refusal, every value
of it, the rest of the query as written — and one naming none is «This link is out of date», which opens the
page's path or home. Over a page kept on the same screen (B-62) the notice says the same and drops its Retry. Its
address is given back too (B-121): a kept page's address changes before its tree arrives and stays when a load
fails, which Retry needs, but a search too short has no Retry and the address named a page a reload would draw as
«Keep typing» — the entry the search took is withdrawn (`Navigator.withdraw`, the browser's back, which arrives as
no visit).
Every error page tells no answer («We couldn’t reach Haul … check your connection») from an error of the
server's own; the Cart, Checkout and Search pages keep their artboards' copy for the latter. No server change:
the codes were already there. Rejected: answering a short query or an empty checkout with a tree of the
server's (a `200` for a request the endpoint refuses, and the code the contract names would go unread).

**Decided in B-117, a page's address is the page whatever its query.** A link with broken percent-encoding —
cut off mid-escape (`/c/electronics?brand=%E0%A4%A`) or with an escape that is none (`/search?q=%zz`) — answered
the page request itself with the API's `400` JSON, which the browser showed as the page: Ktor decodes the query as
it starts any routed call (`RoutingCall` reads the call's parameters whole), before the static route can look at
it. The server now answers a `GET` of a `StorefrontPage` address whose query does not decode with the page, ahead of
routing (`PageAtAnyQuery`, `WebBundle.kt`); `/ui/…` and `/api/…` still refuse it, `400 validation_failed` naming
no parameter. In the browser the tree request is never sent: Ktor's client decodes a URL's query as it builds the
request and throws, so the shell takes that as the same refusal (`Transport.kt`) and draws «This link is out of
date» with «Open the page»; a search's query that does not decode is no query rather than a throw from the
page's placeholders (`Address.query`). «Open the page» on a search link opens `/search` with nothing typed, which
the server refuses as too short; that page says «Start typing» rather than «Keep typing… Your query is still in
the field» over an empty field (B-132, `SearchTooShort`), and so does a bare `/search` opened any other way.
Rejected: sending the stale search home — the search page is where the field is, one press nearer. Rejected: decoding leniently on the server — it would open a broken link at
a guessed one.

**Decided in B-68, a category has one address** (owner's call): the slugs from its top-level category down
to it, `/c/electronics/audio/headphones` — a top-level category's is its slug. A screen being a path, two
forms of one category were two screens: links named the last slug alone while the route answered any path
by its last slug, so a page opened at the whole path loaded again on its first filter. Every link — the
header, tiles, breadcrumbs, a search's suggestions — and every address a category page names is the whole
path (`pathOf`, `feature/catalog/domain/Catalog.kt`); any other form whose last slug is a category — the
slug alone, a path that skips a level — answers `301` to it, query kept, at the page (`/c/…`), its tree
(`/ui/c/…`) and its parts (`/ui/parts/c/…`) alike (`feature/catalog/OneCategoryAddress.kt`). A tab drawn
before the change follows its old links into a redirect and draws the right page at the old address; its
first filter then loads the page once, at the whole path, and from there the addresses agree.

**Decided in B-63, a filter answers with the parts that changed** (owner's call; kompot 0.40.0.213, B-82).
A press that only filters, sorts or pages what a screen already shows carries kompot's `load` of the
screen's address under `/ui/parts`: one `GET`, answered with an `update` of the nodes the press changes and
the address they make (`deeplink`, `push`). The parts are cut from the page that address opens
(`server/.../shell/Parts.kt`), so they cannot drift from it; what is spared is the bytes and the redraw of
the frame, not the building. The whole page at every address is served as before, so back, a reload and a
shared link show what the shopper saw. An answer that cannot be partial — the category is gone, or the
page would gain or lose a section an `update` cannot add or take away — is `navigate` to the address.

| Endpoint (kind `load`, kompot SPEC §16.1) | Tier | Pressed from | The parts |
|---|---|---|---|
| `GET /ui/parts/c/{categoryPath}?…` (the whole path, B-68) | public | a kind, a facet, «Show N more», an applied chip, «Clear all», a sort, a page, the price range (B-69, below) | `title`, `kinds` (a leaf's kinds, or a category's subcategories, which `navigate`; B-102), `results` |
| `GET /ui/parts/search?q=…` | public | a category chip, a sort (B-77, the end of the chips' row), «Show N more», a page | `header` (its picker reads the scope, B-72), `title` (it counts the chip's results, B-119), `categories`, `grid`, `pagination` |
| `GET /ui/parts/deals?page=…` | public | a page or «Show N more» between grids that start past the first page; to or from a grid with the first page, whose «Deals of the day» the others lack, the press is still `navigate` — «Show N more» from the first page included, which the shell draws behind the kept page (B-62) | `grid`, `pagination` |
| `GET /ui/parts/account/orders?status=…` | customer (`401`) | a chip of the history | `account` |
| `GET /ui/parts/account/saved?…` | customer (`401`) | a filter or a page of the Saved list | `account` |
| `GET /ui/parts/p/{productId}?sku=…&tab=…&stars=…&shown=…` | public | «Show more reviews», «Show more questions» (B-71): the tab listing ten more, `shown` from 10 to 500; a bar of the rating histogram, the bar pressed again or «All» (B-86): the reviews of `stars` (1 to 5), or every review | `reviews` or `questions`, the tab's node |

**No press loads the page it is on** (B-102). What is already chosen carries no action — the selected kind or
history chip, the sort shown, the rating ticked («Any rating» with none) — and «Clear all» is sent, and drawn, only
while an applied chip is (`AppliedFilters.filterCount` is the number of chips, so the phone's «Filters N» counts
what it lists). «Clear all» keeps the kind: the kind is the row above the results, not a chip. A category with
subcategories offers them in that row, each to its page, and applies no `kind`; a leaf offers its products' kinds,
and a `kind` it has none of is not applied either — nothing filters a page without a chip or a selected pill
saying so.

A part is found by its id, so an id names one node of a page (kompot SPEC §4.2) — the order's live frame and the
override store rely on it too. `server/.../shell/UniqueIdsTest.kt` holds it over every page a guest and a customer
open and every `update` above, the nodes sent and the page after them — at the canvas's «now» and again once the
day's deals have ended, when home draws B-70's popular row (B-96); a page whose section already carries the
page's name has the root `<name>-page` (B-65: until then the account's parts were its whole page, root included).

The query is the page's own, refused the same way (`400 validation_failed`); the search's parts do not record
the search again — a `load` changes nothing. In the client (`shell/Storefront.kt`) an `update` goes into the
screen's override store and its address into the history without a load (`Navigator.record`); back and forward
to such an address are visits like any other and load its page, which replaces whatever the updates drew. The
line under the header is on while a `load` is on its way, and one whose answer does not arrive leaves the page
under B-62's notice, whose Retry presses it again; of two `load`s the last press wins (kompot's `withLoad`).

**Decided in B-69, the price range is the one address the client completes.** A bound is any whole number the
shopper types, so no list of `load`s in the tree could hold the ranges — a `load` per step of the slider would
have capped the fields to the steps and cost two lists of addresses per page. The price facet carries
`Facet.range` (`FacetRange`, `shared/.../ui/BrowseComponents.kt`): the bounds applied, the dollars at the
slider's right end, and `template` — the parts address of the page as it is from the first page, the category's
one address, every other filter and the sort written by the server, with `{min}` and `{max}` where the bounds go
(`CatalogUrl.priced`). The client puts two numbers into it and nothing else (`FacetRange.applying`): a bound
left empty, or a thumb taken to its end of the track, drops its parameter; bounds typed the wrong way round
are swapped. A bound is applied on Enter, the keyboard's «Done» or leaving the field — after
`awaitTypedInput` (B-76) — and on releasing a thumb, unless it is the range already applied. A field is left by Tab,
a press on another control, and a press on blank page space (B-118): a Compose field keeps the focus through a press
nothing takes, so a focused price field holds the page's `BlankPress` (`ui/BlankPress.kt`), and a press no control
takes outside the field's box takes the focus off it, after the keys typed before it have landed. One confirm waits
at a time: two leaves inside one wait (a press from «from» into «to», then Tab) both read the same bounds and sent
the same load twice, so the later replaces the waiting one. The applied range
is a chip («$80 – $400», «From $80», «Up to $400») whose action removes it. A thumb is grabbed anywhere on it:
the slider takes presses half a thumb past each end of its track (B-90), where a thumb at an end is drawn. The template is not a `load` on
the wire (no `type`), so nothing that walks a tree's `load`s (`PartsCalls.loads`) mistakes it for one.

**A card's «+» and the product page's «Add to cart» answer an `update` too**: the header (its count) and the
control pressed (its next quantity), instead of `refresh`. Which node is in the command's own address, written
by the tree (`?answer=card`, `?answer=details`, and the search the header shows, `&q=`;
`feature/catalog/screen/LineAnswers.kt`); a card is built the same from its product and SKU on every page, and
the client draws a grid's cards as nodes of the tree (`LocalCardNodes`), so the `update` of one card reaches it.
The cart's own lines, «Buy now», the Saved list's cards (whose «Price dropped» mark only that page draws), the
empty cart's «Picked for you» (whose line turns the page into a cart with it, B-95: an `update` of the card and the
count left «Your cart is empty» drawn over a count of one) and every other command still answer `refresh`.

**Decided in B-75, a press shows that it worked.** «Add to cart» answers `sequence[update, show_message]`: the
header and the buy box as above, then kompot's message «Added to your cart» with «View cart» (`LineAnswers.ADDED`),
and the buy box says what is in the cart (`ProductDetails.inCart`, «2 in your cart» leading to `/cart`). The client
draws `show_message` itself (`shell/Messages.kt`): kompot leaves its look to the client and no artboard draws one,
so it is the frame the shell already had over a page — B-62's notice, inverse surface, the Acid accent, the button
on the right — at the bottom, four seconds (six with a button), a new message replacing the last; a failure with a
button, «Couldn't reach the store» and «Retry», has no time since B-136: it stays until it is answered or the page
changes, where its press would land on a page that is gone (B-92's findings); kompot's Material
snackbar was not taken, the app has no Material theme. A card's «+» says the same since B-81 (the owner's
decision): its answer is the same `sequence` around the header and the card, on every page that answers it in
place — home, catalog, search, deals, a customer's «Picked for you». The Saved list's «+» and the empty cart's
picks, answered `refresh`, say nothing: the line drawn is the answer.
What has nothing to do looks it and presses nothing: a «+» the tree gives no change is greyed as the buy box's
buttons are out of stock, and takes the press instead of letting it open the product (nor says it added anything); «Add to cart» at the line's limit is greyed the same; a facet option another filter
leaves at 0, unticked, carries no action and is drawn faded (ticked, it keeps its action — that is how it is
unticked). The cart button keeps the count's place when the cart is empty, so the count appearing moves nothing
around it (the search field at 1440, the heart and the account on a phone); empty, the bag and «Cart» sit in the
middle of that width. The cost against the canvas: every header drawn with an empty cart — a guest's, the
placeholder's — has a cart button 34 px wider at 1440 and 32 px on a phone than the canvas's Home_Guest.

**Decided in B-92, a command that fails or is refused tells the shopper** (owner's call). One rule for every command
a press sends — the cart's, the Saved list's heart and «Save for later», the checkout's choices, address form and
«Place order», «Reorder» on an order or the account, «Helpful», the review, question and return dialogs and the Plus
trial: an answer is followed as before; a **refusal** draws the page again (`refresh`, the trial's `close` first)
and says why in the server's words — `ErrorBody.message` — in B-75's message over the page, at the `error` level
(the alert mark); **no answer** leaves the page as it was under «Couldn't reach the store», with «Retry» when sending
the press twice does what sending it once does: a `PUT` or a `DELETE`, and «Place order», whose key makes a retry
the same order (B-16). A `POST` that adds — «Reorder», «Save for later», «OK» on a changed line, the address form,
the dialogs — offers no «Retry»; its own button is the press again. Two exceptions keep a reason from being said
twice: a refusal the next tree already draws at the control — a promo code the server stores (Cart_PromoError), an
address form's errors under its fields (Checkout_Validation), a window that filled up at placement
(Checkout_PlaceError) — is the `refresh` alone; and a dialog with a place of its own for what went wrong (the
review, question and return dialogs) says it there, «Couldn't reach the store» in place of «That didn't go
through». The rule lives in `shell/CommandAnswers.kt` (`refusal`, `noAnswer`); «Retry» is `PressAgain`, the
client's own action — never on the wire — which the screen's handler runs. A `load` that does not arrive keeps
B-62's notice, which already had its «Retry».

**Decided in B-100, the cart's controls look what they do.** A control with nothing to do is greyed and presses
nothing, as the buy box's buttons out of stock are (B-75): checkout with nothing ticked (`HaulButton`'s
`enabled`, Paper's grey under a muted label), «Delete selected» with nothing ticked (muted instead of Cobalt), the
stepper's «−» at one and «+» at the line's limit; each is disabled for assistive technology too (`inert`).
«Save for later» answers `sequence[refresh, show_message]` — «Moved to your Saved list» with «View saved» — since
the line leaves the page it was pressed on; the Saved list is the one place a saved product is drawn (B-20), so the
cart has no section of its own for it. The summary's rows add up to the total: «Discount» is the sale alone, the
promo code's row («Promo · AUTUMN10») beside it instead of inside it, and a row that takes nothing off is not
drawn — no «Discount −$0.00» in the sale red, on the cart and at checkout, as the order's page already left it
out. The cost against the canvas: `Cart_PromoApplied` draws «Discount −$190.00» over the code's −$50.00, the cart
now «−$140.00»; and the stepper's «−» at one, black on every Cart artboard, is muted. A line's tile and title
open the product at the line's SKU (`/p/<product>?sku=<sku>`), not at the product's default.

**Decided in B-22, a route over the screen.** A dialog the canvas draws over a page («Write a review»,
«Ask a question») is kompot's `present` of a component the server built — the form, its labels and the
URL it posts to — carried by the control that opens it, so it has no address of its own and
`StorefrontPage` lists nothing for it; a reload shows the page without it. The shell draws the presented
component over the page with the scrim, and the same handler follows its actions: `close` takes it away,
and a `sequence` is each of its actions in turn — a dialog's command answers `close`, then `refresh`.
What is typed is the client's until it is sent; the client checks the rules the server will (they are
the contract's, `shared/.../feature/reviews/`), and a refusal is drawn in the dialog. For a guest the
same control is the way to sign in instead of a `present`.

**Decided in B-99, a product's counts are its rows: the seed stores them.** «2,341 reviews» and «86 questions»
stood over two and three rows, so «Show more» (B-71) could never be reached and the cards, the buy box and the
tabs counted what nobody could read. Of the item's two ways — count the stored rows, or store what is counted —
the second was taken: counting would have written «2 reviews» on the hero product of a marketplace and 0 on
nearly every card, and broken «Popular», «Bestseller» and every artboard's copy. `seed/SeedReviews.kt` writes the
rest of each count in SQL (`generate_series` over per-product targets, copy picked from fixed pools by the row's
number, no clock), the stars following the product's histogram — the canvas's 78 / 14 / 4 / 2 / 2 % for the
headphones — or the one its average makes; answered questions answered by the seed, as the canvas's are. The
generated catalogue's counts were scaled down to what that costs (0–23 reviews, 0–7 questions, drawn from the
same random numbers, so every other row is unchanged); the sample products keep their canvas counts, so the seed
holds 60,849 reviews and 6,799 questions, which add about a second and a half to a fresh seed (0.6–1.4 s before,
2.2–2.7 s after, on the build box). It runs on every seeding start
like the loyalty rows, so a stand seeded before it gets the rows and then every product's counts and histogram
recounted from them, a customer's own review counted once; a database that has them all reads one count and writes
nothing. Rows built in Kotlin and batch-inserted through Exposed were rejected unmeasured: the per-row round
trip is what `INSERT … SELECT` avoids, and the server's suite seeds about ninety databases.

**Decided in B-111, the server's tests seed only the rows they read.** B-99's fill ran on every seeding start,
the tests' included, and the suite seeds about ninety databases: `:server:test` on CI went from 225–327 s to
821 s (`gradle` from 7–9 to 17 minutes). `Seeder.seedIfEmpty` takes `generatedReviews`, `true` unless a caller
says otherwise; `seededFreshDatabase()` — a test's own database — passes `false` unless the test lists past the
seed's own reviews (the review routes' «yours first» and «counted» cases, the review fixtures), and such a database
keeps the catalogue's counts and histograms over the canvas's rows (the histograms since B-86, so a review written
there moves the rating as on the stand). The shared `SeededDatabase`, `SeedTest`
and `SeedReviewsTest` seed in full, and so does every start of the image: `main` never passes the switch, the
stand and the e2e run `main`, and a database seeded without the fill gets every row on the next start that fills
(`SeedReviewsTest`; the e2e reads the headphones' 2,341 and 86 off the image). CI's other failure of those days, `Could not create new connection` (haul#121),
was test pools left open against the container's hundred connections, closed by B-100 (`PostgresHarness` refuses
a ninth open database). With both, `:server:test` took 280 s on CI.

**Decided in B-99, what the review controls promise.** A customer who has reviewed a product is offered their
review, never a second «Write a review» the route refuses (`409 review_exists`): on the product page theirs leads
the list and the button's place reads «You reviewed this on Oct 7» (`ProductReviews.action` absent); on a
delivered order line the link is «Your review», the product's reviews tab. «Helpful» is drawn filled with a tick
while the viewer's vote is on it (`Review.helpfulVoted`), so the second press — which takes the vote back — is not
a surprise. The question dialog says what happens: the question is shown, «Not answered yet»; nothing answers it,
since there is no seller side.

**Decided in B-86, a product's rating is its histogram's average.** The rating was the seed's number, moved by
each new review from there, while the bars were counted from the rows: the headphones read «4.8» over the canvas's
78 / 14 / 4 / 2 / 2 %, which average 4.64. Now the rows are the one source: `rating_counts` counts them and the
rating is that histogram's average to a tenth, half up (`Ratings.average`), wherever either is written — a review
stored, the seed's catalogue, and the seed's recount of a database. One of the canvas's two numbers had to move, and
the bars moved: the seed's headphones are 1,966 / 281 / 47 / 24 / 23 reviews, 84 / 12 / 2 / 1 / 1 %, a 4.77, so the
«4.8» every card, page and tab draws stays, and only Product_Reviews and its phone twin draw other bars than the
canvas. The generated catalogue's ratings are their histograms' — a few reviews cannot make every drawn average,
and one review of a «4.5» is a five or a four — and every generated product has at least one review: a rating with
no stars under it agrees with nothing. A stand seeded before is turned into what a fresh seed writes on its next
start: the generated rows past a product's target go with their votes, the missing ones are written, each dated as
a fresh seed dates it, and every rating is recounted.

**Decided in B-86, the reviews tab's two pictures are controls.** A bar of the histogram lists the reviews of its
stars in place — kompot's `load` of the tab's address with `stars`, as «Show more» is — and «Showing 4-star reviews»
with «All» says so; the bar drawn pressed, or «All», lists every review again. A bar nobody gave has nothing to list
and is no press. The rating, the caption and the bars stay the product's whatever is listed; the viewer's own review
leads the list only among its stars. A review's photo opens the review's photos over the page, one at a time, «‹» and
«›» or the arrow keys between them, closed by «×», Escape or a press outside the photo — drawn in the storefront's
own layer (`SheetOverPage`), for the reason B-126 found below, and each press named. The seed has no shoppers'
photography: since B-149 each photo is a picture of the reviewed product over its placeholder's tone (D8), small on
its tile and large in the viewer.

### D3. PostgreSQL; the access layer is chosen at scaffold

Decision: one PostgreSQL database for the catalog, carts, orders and the saga's state. Exposed with
`petich-postgres` (as shashki) or sqlx4k with `petich-sqlx4k-postgres` — both exist on the JVM
(§1.3); B-02 picks one and records why here.

Why: the order rows and the saga's state live in one database; product specifications and facets,
which would be natural in documents, are `jsonb` and SQL aggregation. *Hypothesis:* facet counts on
the seed stay interactive (measured in B-05).

**Decided in B-02: Exposed over JDBC, with `petich-postgres`.** The two sibling reference services
run petich on exactly this pair, so the saga's storage is the combination already exercised by
petich's conformance corpus and by two consumers (§1.1, §1.3); sqlx4k on the JVM would make Haul the
first consumer of `petich-sqlx4k-postgres` off native, a risk with nothing bought by it. The price:
JDBC blocks a thread per query, which is what decided the engine below.

**The engine is CIO, for the same reason.** Handlers that block on JDBC need the slack of CIO's
dispatch through `Dispatchers.IO` (64 workers on the JVM); Netty's call group defaults to the
processor count, the worst default for blocking handlers. Recorded in `server/build.gradle.kts`.

### D3a. One root package, features under `feature/<name>/` *(decided in B-02)*

Every module uses `io.github.youndie.haul`; a feature has the same directory in each module it
touches (*feature/catalog* in `shared`, `server` and `composeApp`), and packages several features
import — `db`, `seed`, `di`, `ops`, `theme`, `registry`, `shell`, `ui` — sit at the root. A module-specific
root (`…haul.server`, `…haul.app`) was what the brief's planned paths used; it buys nothing and
breaks the one `grep` that finds a feature everywhere. The planned anchors were rewritten to match.

### D4. The order is a petich saga; the outside world is simulated

Decision: placement reserves stock and authorises payment in a saga that compensates (release
stock, void the authorisation) when a step fails. A payment simulator approves every card except the
test card ending `0002`; a fulfilment simulator moves each seller's shipment along on a configurable
clock and triggers the capture at `in_transit`.

Why: «your card is charged when the order ships» (the canvas) is exactly an authorise-then-capture
saga across shipments; simulating the outside world is what lets the whole path run on one machine
and on the stand.

**Decided in B-16, placement and the saga.**

- **petich 0.4.0.112** (§1.1's build, the sibling's), `petich-postgres` over the application's Exposed
  database and `petich-idempotency`; petich's three tables are written by hand in `V10__orders.sql`
  (petich ships no DDL) and held to petich's declarations by `SchemaTest`.
- **The members, in their order** (`feature/order/saga/OrderSaga.kt`): `reserve-stock` → `reserve-slot`
  → `open-order` → `authorise-payment` → `confirm`, then the announcement `clear-cart`. Stock and the
  window come first because a refusal there sends the shopper back to checkout and no order should
  exist for it; the order opens before the payment because a declined card is an order that exists,
  cancelled (§6, #HL-48303). A refusal or a failure undoes the members before it in reverse: void the
  authorisation, cancel the order and its shipments, release the window, release the stock. Clearing the
  bought lines is an announcement, not a step: the order is placed by then, and a cart that could not be
  cleared is no reason to take it back.
- **Everything is named by the order**: stock and the window are held by the order's id, the
  authorisation by the member's idempotency key, so a member re-run after a restart takes nothing twice
  and a compensation releases by that same name, whether or not its step landed (petich's member rules).
- **The saga runs inside the request.** Every member is in-process, so placement can say what happened:
  a window that filled or stock that ran out is a `409` the checkout is drawn again for; anything else
  answers `202` with kompot's `navigate` to `/account/orders/{id}` (the order's page, B-18) — placed, or
  cancelled because the card was declined. A saga whose process died is carried on by the application's sweeper once its row has been
  untouched for 60 s (`STUCK_AFTER`: twice petich's largest phase timeout, AUTHORIZATION's 30 s).
- **The saga rides out a short database outage; the rest of the request does not (B-139).** Since B-127 a
  request waits for a connection once, 2 s, and is then refused at once for 2 s (`FailFastDataSource`). Saga
  work — placement's pass of the saga, its compensations, the sweeper — runs under `ConnectionPatience`
  (`db/ConnectionPatience.kt`): it is not refused, and asks the pool again while its coroutine lives — petich
  cancels a member at its phase timeout, EXECUTION's 10 s for every member here, the card's authorisation
  included — and for at most `SAGA_CONNECTION_PATIENCE` (that same 10 s) per ask, the bound of the engine's
  writes between members. Placement's pass waits `PLACEMENT_PATIENCE` (20 s) in all, so a shopper whose
  database stays away hears in about that, not after every write's own wait. **The placement request is a
  command until its saga starts**: a database already away when «Place order» is pressed is answered `503` in
  seconds and spends nothing; one that goes away under the saga is waited for, because failing then
  compensates an order the shopper meant to place. The pool opens connections through `ReconnectingDriver`,
  which asks a refusing database again every 200 ms for up to 1 s, because Hikari's own retry sleeps up to 5 s
  between tries and found a database back after a 5 s outage only at 10 s — past the member's phase.
  **Saga work is also handed only a live connection (B-140)**: Hikari does not check a connection used in the
  last 500 ms, so an outage right after a busy moment handed the member the connections the shutdown had closed,
  Exposed's three tries failed on them in milliseconds and the order was compensated; under the patience each
  connection is asked `isValid` and a dead one is evicted (`FailFastDataSource`). Past about 7 s of outage
  Hikari's sleeps between tries reach 0.64 s and then 1.28 s, so the line between riding out and compensating
  is nearer 9 s than the phase's 10 s on the saga's clock: a database back 9.8 s after the stop began was undone.
  Measured (`PlacementOutageTest`, the outage counted from the stop being asked for to PostgreSQL accepting
  connections, right after a burst of pages): 5 s asked, back at about 6 s, placed, answered 6.7–6.9 s after the
  stop began on the WSL box and 5.2 s on a hosted runner; 12 s asked, undone, answered 13.6 s after; a page in
  either outage `503` in 2.0 s.
- **The key is the customer's own**: the saga's id is a hash of the customer and the `Idempotency-Key`,
  and a key is claimed (petich-idempotency) only by a placement that passed the quote checks — a stale
  quote does not spend it. The same key with the same quote answers the saga's outcome again; with a
  different quote it is `409 idempotency_key_reused`.
- **The simulator** (`feature/payment/`) approves every way to pay except the test card ···· 0002;
  pay on delivery is never sent to it, Haul Pay is authorised like a card, its whole total (its four
  payments are captured out of that, «Decided in B-24»). Its ledger is a table, one row per key, so a replay gets the first answer and a void is
  by the key. The saga's clock is the wall clock, read at the composition root apart from the store's
  «now», because its stamps are compared across processes.
- **Order numbers** come from a sequence starting at 48302, the checkout fixture's order (§6).

**Decided in B-17, fulfilment and the capture.**

- **The simulator is one pass** (`feature/fulfilment/domain/FulfilmentSimulator.kt`): it reads the
  shipments of `placed` orders still on their way and moves each through every step that has come due,
  each stamped at the moment it came due (`shipment_events`, `V11__fulfilment.sql`) — so a pass after a
  pause catches up and writes the history an unbroken run would. A shipment the seller has not seen yet
  is stamped `placed` by the first pass that finds it. The application runs the pass in its own scope
  (`FulfilmentRunner`); a test calls it on a clock it holds.
- **Its clock is the saga's** (`PetichClock`, the wall clock read in `Application.kt`): its stamps are
  compared across processes, which is the saga clock's reason for being, and the store's «now» is not it.
- **The pace is a decision** (`FulfilmentPace.STORE`): packed 4 h after the seller sees the order, on the
  road 20 h later plus a day per dispatch day (the most of the shipment's products'), delivered or ready
  to collect a day after that, collected two days later — inside the five days a point holds it. So Sonora
  ships a day before Brooklyn Home Co., as the canvas promises. `HAUL_FULFILMENT_SPEED` runs the same
  schedule that many times quicker (default 1; 1440 is a day a minute), polled every tenth of the
  shortest step, between 1 s and 1 min. The pace does not follow the window the shopper chose.
- **The capture is per shipment, at `in_transit`, before the move**: the shipment's share of the
  authorised total — in proportion to what each seller's lines cost at the price paid, every share but
  the last rounded down and the last taking the rest, so the parts add up to the total to the cent. The
  capture is named by the shipment id, so a pass re-run after a process died between the capture and the
  move is answered with what was taken. A refused capture holds the shipment `packed`; pay on delivery
  has nothing to capture; Haul Pay is not captured by the shipment but by its plan («Decided in B-24»).
- **The ledger** gains `payment_captures`, one row per key out of one authorisation; the authorisation
  is locked while a capture is taken, captures never add up to more than it holds, and an authorisation
  part of which was captured is no longer voided.
- **A pickup code** is four digits of an HMAC of the shipment id keyed by the order's saga id (a hash of
  the customer and their key, never on the wire): deterministic, so a re-run writes the same code, and
  not derivable from the shipment id the page shows. It is stored when the shipment becomes
  `ready_for_pickup` and shown only while it waits, only to the order's customer.
- **The order's progress** (`OrderTracking`, `OrderProgress`) is the saga's while `placing` or
  `cancelled`, then the least advanced shipment's — B-18 draws the order page from it.

**Decided in B-18, the order's page and reorder.**

- **The page's address is `/account/orders/{id}`**, its tree `GET /ui/account/orders/{id}`
  (`feature/order/OrderRouting.kt`, `StorefrontPage.Order`): the canvas's crumbs are «Account / Orders /
  #HL-48302», screen-order's entry is that address, and the orders' history (B-19) is the account's
  `/account/orders`, which the page then sits under. Placement's `navigate` lands there. `/orders/{id}`,
  which endpoint-orders' draft wrote, is no address.
- **One builder for every state** (`feature/order/screen/OrderScreen.kt`): Placed, InTransit,
  ReadyForPickup, Delivered and Cancelled are what `OrderTracking.track` says the order is. A shipment's
  day is the courier's for an order placed when it was (the day after, the seller's dispatch days, the
  23:30 cut-off; a point a day later) or the chosen window's when that is later; the window is named on
  the day it was chosen for. Tomorrow is said with its date whether the shipment is packed or on its way
  («Tomorrow, Oct 8 · 15:00 – 18:00»): the canvas's Order_InTransit drops the date, and a page watched from
  packed to in transit lost it mid-order (B-133). A line's quantity and price are held together by no-break
  spaces («1 × $349» never breaks inside, B-133). Another customer's order and a missing one are
  `404 order_not_found`, which the client draws as Order_NotFound under the header it last drew.
- **Reorder** (`POST /api/v1/me/orders/{id}/reorder`, `feature/order/domain/Reorder.kt`) puts each SKU
  of the order into the cart through the cart's own `changeLine`, selected, at the order's quantity — at
  most ten and the stock; a line already holding as many is only selected, so a second press adds
  nothing. A SKU gone or out of stock is left out. It answers `navigate` to the cart. Since B-101 the answer
  names what it could not put back (`ReorderAnswer`): a SKU left out («… isn’t available now») or put in with
  fewer units than the order had («…: only 1 of 2 went in») follows the `navigate` as B-75's message at the
  `error` level; when nothing went in, the message is the whole answer and the shopper stays on the order rather
  than landing on a cart with none of it. A `200` with a message, not a refusal: the press did what it could.
  «Reorder» is offered on every order that arrived, a returned one included — a return unbuys nothing.
- **«Orders» in the header** goes to the orders' history, `/account/orders` (B-19; until then `/account`),
  and a guest's to sign-in that lands on the orders once it has gone through (`/sign-in?next=…`, B-66).
  The page's «Orders» crumb goes there too.
- **The address drawn is the order's** (`NewOrder.address`, B-40's copy), never the saved address it came from,
  which the checkout edits in place.
- **«Write a review»** on a delivered line is the product page's own: B-22's review dialog, presented over the
  order (`ReviewTabs.writeReview`). «Return items» is B-21's return dialog (below).

**Decided in B-21, returns and refunds.**

- **One return per order, of whole lines** (a second is not stored: `returns` holds one row per order, and the
  refund, the points and the restock are keyed by the order), as the dialog's checkboxes take them: `POST
  /api/v1/me/orders/{id}/returns` with the ticked lines' positions and a reason (`feature/returns/`,
  `RequestReturn`), answered like a dialog's command — `201`, kompot's `close` then `refresh`. Refused, in this
  order: a form at fault (`400 validation_failed`, every field named), not the caller's order or none
  (`404 order_not_found`, as the order's page), a second return (`409 already_returned`), a line not arrived
  (`422 not_delivered`), a line past its window (`422 return_window_closed` — «Late return»; a `422` as an
  expired promo code is, the request is well-formed and its moment has passed).
- **The window is 30 store days from each line's own arrival** (`ReturnWindow`): delivered on Sep 26, returnable
  all of Oct 26. It is counted against the saga's clock, the one that stamped the arrival (`shipment_events`).
- **The refund comes when the parcel is back, not when it is asked for**: the canvas draws «Requested · Picked
  up · Refunded» and «$87.50 refunded Sep 19». The returns' simulator (`ReturnSimulator`, run with the
  fulfilment pass) collects a return a day after it is asked for and refunds it a day later
  (`FulfilmentPace.returnPickup`, `returnRefund`, sped up with the rest), the refund before the move, keyed
  `refund:<order>`. The ledger gains `payment_refunds`: a key refunded once answers what it gave, and the
  refunds of an order never add up to more than its captures. Pay on delivery is paid back by the courier,
  outside the processor.
- **The returned units go back to stock with the refund** (B-88), not at «picked up»: the refund is the moment the
  seller has the parcel back, and while the courier carries it the unit cannot be sold. They come back after the
  points and before the move, once by `return:<order>` (`StockReturns.restock`, the table `stock_restocks`, V28):
  a pass that died after the restock and runs again adds nothing. The order's `stock_reservations` stay what
  placement took — releasing them would put back the lines the shopper kept. Every reason restocks, «Arrived
  damaged» included: the simulated world writes nothing off. A return held by a refused refund puts nothing back
  until it is refunded.
- **What a line gives back** is its share of the items as paid — the order's items are at list price and its
  discount holds the sale and the code, so the code is shared over the lines in proportion to what each cost at
  the price paid; the delivery is kept. What a line cost is its total, the price times the quantity, which is what
  an order line keeps (`OrderLine.totalCents`, column `price_cents`): a reader that multiplies it by the quantity
  again counts it twice, and the price of one is `unitCents` (B-91). The points taken back are the order's in proportion to the refund (the canvas's 87 × 2 =
  174); there is no points ledger yet (B-23), so they are the return's own number, drawn once refunded.
- **The order's progress** is `returning` while its return is requested or picked up and `returned` once it is
  refunded (`OrderProgress.of`, reading `Order.returnStatus`): the order page draws the return's three steps,
  the lines kept in their shipments and the returned ones in a card of their own, «Paid» net of the refund.
- **The rule is said where «Return items» was** (B-101): once a return of some of the lines is in, the summary
  reads «One return per order: what you kept can’t be sent back.» (`OrderTotals.returnNote`) under «Reorder»; a
  whole return has nothing left to ask about and says nothing. Allowing a second return was the alternative the
  QA pass offered; the owner recorded no call, and the store holds one return per order everywhere (the table,
  the refund, points and restock keys, the order's progress), so the rule is shown rather than changed.

### D5. Sign-in through shildik; guests can browse and fill a cart

Decision: a guest has a server-issued id (`X-Haul-Guest`) and a cart; checkout, saving, reviews and
the account need a shildik token; signing in merges the guest cart.

**Decided in B-11, the guest and the cart.**

- A guest id is `g-` and a random UUID from `POST /api/v1/guests` (`201`), stored in `guests`; it is
  the guest's only credential, so it is unguessable. A cart route with no `X-Haul-Guest` answers `401
  unauthenticated`, and one naming a guest the server never issued (or a reseed forgot) `401
  guest_not_found`: the client creates a new guest rather than the server inventing one — on that code
  alone (B-115). The merge's `guest_not_found` stays `404`; the code is shared, the status is not.
- The cart's commands keep the methods and JSON bodies of endpoint-cart (`PUT`/`DELETE` with
  `LineChange`, `LinesRemoval`, `PromoEntry` from `shared/.../feature/cart/`) and answer kompot's
  `refresh` — but a card's «+» and «Add to cart», `update` (B-63) followed by their message (B-75, B-81). Their paths are the server's strings: each component of the tree carries the URL its
  commands go to (`CartLine.url`, `acknowledgeUrl`, `CartSelection.linesUrl`, `PromoField.url`), so
  the client builds none. **Decided in B-125:** the line `PUT` is an upsert — how a card's «+», «Add to cart» and
  «Buy now» add — but `CartLine.url`, where the cart's own stepper and box send, carries `?existing=true`
  (`CartPaths.existingLine`): a change of the line the page drew, so a line removed meanwhile in another tab is
  `404 line_not_found` («This item is no longer in your cart», B-92's message over the cart drawn again) instead
  of coming back at the stepper's quantity. A flag in the address the tree writes, as `?answer=card` is, was
  taken over a field in `LineChange` (the client would set it at three presses), a route or method of its own (a
  new command in the client) and a compare-and-set on the quantity seen (more than the case needs): the server
  alone changes, and every control drawn on the line — «−», «+», the box, «Select all» — is covered by the one
  address. kompot's own `perform` action was the alternative and was not taken: it is
  sent as a `POST` with a payload of form values, which have no integer and no list and are fixed in
  the tree, while the promo code is what the shopper types; how the client sends a command is B-13's.
- A line remembers the price and the stock the shopper saw (`seen_price_cents`, `seen_in_stock`); it
  is changed when either moved, and acknowledging makes the current ones the seen ones. A refused
  promo code is stored with its reason until the next command, which is what makes `Cart_PromoError`
  a tree the server returns rather than a client-side state.
- Delivery is free from $35 of the selected items at their price, before the promo, and is one fee per
  cart, not per seller group; research D7's «over $35» is read as «$35 or more».

**Decided in B-12, sign-in and the customer.**

- **The browser signs in with kotlin-multiplatform-oidc 0.18.4** (`oidc-appsupport`, wasmJs only),
  the client shildik's app contour was accepted against (`youndie/shildik@18488c4!/docs/api/protocol-oidc-app.md`):
  authorization code with PKCE (S256), the provider's page in a popup. Risk 2's alternative, a
  hand-written flow, was not needed — shildik answers discovery, the key set and the token endpoint
  to any origin (`youndie/shildik@41c2804!/server/src/commonMain/kotlin/io/github/youndie/shildik/server/oidc/PageReadable.kt`),
  so the exchange runs in the page. The popup returns to `signed-in.html`, a static page beside the
  bundle that posts its address to the opener and closes; the realm's public client registers
  `<origin>/signed-in.html`. `offline_access` is asked for, because shildik's access token lives five
  minutes and a refresh token is issued only when asked.
- **The server verifies with shildik's own `oidc-auth-server`** (0.4.1.23): signature against the
  realm's JWKS, lifetime, `iss`; Haul adds `azp` = its client, so a token the realm issued to another
  of its clients is refused. The issuer and the client are `HAUL_OIDC_ISSUER` and
  `HAUL_OIDC_CLIENT_ID`, set together or not at all; neither is a server with sign-in off (every
  bearer `401`, `GET /api/v1/sign-in` `503 unavailable`), one of them refuses the start. Off rather
  than required, so the image's training run and the chart need no realm to start.
- **Tiers at the mount** (`HaulModule.kt`): the public routes take an optional bearer — a token that
  does not verify is `401 unauthenticated`, not quietly a guest — and the customer tier
  (`/api/v1/me/cart/merge`, `/ui/account`) a required one. A token wins over a guest id on the same
  request. Ktor's challenge has no body, so a `401` it sends is rewritten into the `ErrorBody` every
  other refusal has.
- **A customer is created by the first request their token makes** (`customers`, `V8__customers.sql`):
  the `sub` is the id, the token's `name` the name (else the e-mail's local part, else the `sub`),
  no Plus. The seed holds Maya (`maya`, Plus) and Sam (`sam`) and Maya's cart; a stand's realm that
  imports these two people with those ids signs them in as the sample customers.
- **The merge** is an explicit command after sign-in, `POST /api/v1/me/cart/merge` with the guest id:
  the same SKU's quantities summed, capped at ten and at the stock (never below one); the guest's
  other lines appended in their order; the customer's code kept, or the guest's taken when the
  customer had none; the guest's cart deleted, the guest kept (signing out is that guest again, with
  an empty cart). A second merge of the same guest changes nothing.
- **The browser keeps** the guest id in `localStorage` and the tokens in `sessionStorage` (a
  sign-in lasts the tab). A `401` is answered once: a customer's token renewed through the refresh
  token, or the customer signed out — back to the guest they were — when it cannot be; a guest the
  server forgot (`guest_not_found`) replaced. **Decided in B-115:** any other `401` is the answer, not a
  forgotten guest — a guest's `unauthenticated` on a customer's page keeps the guest and its cart, where
  replacing it on every `401` threw the cart away under a prompt that says it comes along. Never
  replacing a guest was rejected: a stand reseeded under a browser would leave it an id nobody knows and
  a cart that cannot be filled. A sign-in whose merge names a guest the server forgot goes through and
  drops the guest.
- **A customer's page refused with `401`** after that — a guest on `/checkout`, a sign-in lapsed past
  renewing — asks for a sign-in instead of drawing an error (B-44): «Sign in to continue», whose
  press starts the sign-in with `next` set to the page, which is loaded again once it has gone
  through; a sign-in that does not go through goes home. A press, not the page's arrival: a browser
  blocks a popup no click asked for, and the sign-in would end before the shopper saw it. A page
  still refused after a sign-in from it is an error page, so the prompt cannot loop
  (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt`).
- **A sign-in ends when its popup does** (B-46). kotlin-multiplatform-oidc 0.18.4 waits for the
  return page's message and nothing else, in a wait that ignores cancellation: walked in Chrome, a
  closed popup left the sign-in pending for good (a blocked one does throw). So the storefront opens
  the popup itself, blank, under a name the library is told to reuse, and watches it
  (`PopupSignInFlow`, `BrowserSignInPopup`): closed without the return page's answer is a sign-in
  that did not go through, in about half a second; blocked is the same, at once; a press while one is
  pending focuses its popup and opens none. Opening the popup before the provider's discovery also
  keeps it inside the click's activation on a slow network — and since B-66 before the server's
  `GET /api/v1/sign-in` too, which the first press of a page load used to wait for (`SignInFlow.signIn`
  asks for the settings rather than being handed them).
- **Every way in reaches the sign-in or says why not** (B-66). `/sign-in` is the client's own page
  (`shell/SignInPage.kt`), not a tree: a reload of `/sign-in?next=…`, a shared link, back or forward onto
  it draw «Sign in to HAUL», whose press opens the popup and, once it has gone through, gives the page's
  history entry to `next` (`Navigator.redirect`), so back does not return to a sign-in that is done; a
  customer who arrives there is sent on at once. Every press — a tree's, the header the shell draws over
  a loading, error or not-found page, a prompt's, the page's own — goes through the same `Signing`
  (`SignInActions` underneath), so no way in follows `/sign-in` as a page. A popup the browser blocked is
  no longer a quiet `cancelled`: the shopper lands on the sign-in page, returning to where they were,
  which says the window was blocked and offers «Sign in on this page» — the same code flow with PKCE in
  the tab itself (`BrowserSignInHere`): the request and `next` wait in the tab's session storage, the
  provider returns to the same `signed-in.html`, which, opened by no storefront window and finding a
  request, keeps the answer beside it and goes on to `/sign-in`, where the exchange and the merge run as
  the popup's do. The browser's back off the provider's page, which never answered, is a sign-in that did
  not go through (B-116): a new load of `/sign-in` finds the request with no answer beside it, and a page
  the browser kept in its back-forward cache — Chrome does, when the back comes quickly — hears the back
  as `pageshow` with `persisted` in the sign-in still waiting to leave; either way the page says it did
  not go through, offers another, and forgets the request. The popup stays the first way: it keeps the page the shopper is on, and the tab's way
  costs a second start of the bundle. A guest's «Orders» and «Saved» carry their `next`
  (`Frame.SIGN_IN_TO_ORDERS`, `SIGN_IN_TO_SAVED`).
- **Signing out** (B-66) is the customer's account slot, a menu on both widths — the account and «Sign
  out» — and the phone header's menu. `/sign-out` is the client's own address, never a page: the tokens
  are forgotten in the browser (the guest id stays, research above), and a customer's page (checkout, the
  account, Saved, an order) leaves for the home page while any other is drawn again for the guest. The
  provider is not asked to end anything: walked against shildik 0.4.1 (the e2e's image), a sign-in after a
  sign-out asks for the password again — the provider kept no session of its own to sign in from. Its
  `end_session_endpoint` is not used.

**Decided in B-162, the demo shopper.** A visitor of the stand had no way into the money half: checkout, the saga,
the live order and returns all need a customer (above), and the realm is closed — nobody can make an account.

- **«Try as a demo shopper» makes a customer of the visitor's own, in one press.** `POST /api/v1/demo-shoppers`
  (public tier, `201` with `DemoShopperTokens`, `Cache-Control: no-store`) imports a person into the realm under
  `demo-` and a random UUID, sets a password of 32 random bytes made on the server — used once, never stored, logged
  or sent anywhere but to the provider — and signs them in exactly as the popup does: code flow with PKCE at the
  storefront's public client, the provider's form posted from the issuer's origin, the code exchanged
  (`ShildikDemoAccounts`). The tokens are therefore the storefront's own kind — `azp` the storefront, a refresh token
  for `offline_access` — and every route, the merge, the renewal in the browser and the live page take them as any
  sign-in's. The browser keeps them and merges the guest cart as after any sign-in (`Identity.tryAsDemoShopper`); no
  popup opens. The customer is made with them, named «Demo Shopper», with the sample address (148 Wythe Avenue,
  Apt 4F) and Haul Pay chosen — the quote falls back to the card on a total Haul Pay is not offered for. D5's rule
  stands: money still needs a customer; the demo only makes one quickly.
- **Where it shows** (`HaulHeader.demo`, the client's word `/demo-shopper`, never a page): at the end of a guest's
  category row, before «HAUL PLUS», at every desktop width — beside «Sign in» it squeezed the search field until its
  placeholder was cut at 1280, and on the row whose words give way it keeps its whole name; under «Sign in» in the
  phone's menu; as the second button of the sign-in page and of a customer's page refused to a guest (B-44), whose
  press returns where the page would. A refusal (`503`) is said over the page with the server's words.
- **How the customer is made — compared.** (a) shildik as it is (0.4.1, the stand's): no token exchange, no
  impersonation, `client_credentials` only for services, but its management API imports a person under a given id,
  sets a password and turns a person off (`enabled: false`, after which shildik refuses their sign-in and the renewal
  of any token they hold); with the browser's own code flow that is enough. (b) A change in shildik — a grant or an
  admin route that issues a person's tokens — would spare scraping its sign-in form, but needs a release and a new
  version on the stand. (c) A token haul issues itself, verified beside shildik's, would be a second issuer on every
  customer route and a renewal path of its own in the browser. **(a) was taken:** it needs nothing new on the
  cluster — the server reaches the release's provider at `http://<release>-shildik:9000` with the bootstrap token from
  the provider's own Secret, and the NetworkPolicy admits the server to that port (`server.demoShoppers`, **off by
  default** — handing the realm's admin token to the server that faces the internet is the owner's decision, not a
  default; on, it needs the release's provider). Its costs: the internet-facing server holds the realm's admin token, which can
  do everything in the realm, not only make people (the realm holds only the stand's sample people and the
  storefront's client); the walk reads shildik's sign-in page as the e2e and the server's suite read it, so a page
  that changes its form breaks it — the server's suite runs it against the provider's image; and shildik 0.4.1 cannot
  delete a person, so a swept one stays in the realm, turned off. A scoped admin role or a delete route in shildik
  would remove the first and the last; neither is needed for the demo.
- **Swept after a day.** Every hour (`DemoOffer.SWEEP_EVERY`) the demo shoppers made more than a day ago
  (`demo_shoppers`, V32) are turned off at the provider first — one the provider does not turn off waits for the next
  sweep, since forgotten here first its token would still renew there — and then forgotten in one transaction
  (`ExposedDemoShoppers.forget`): what their orders reserved goes back on the shelf, less what a return already put
  back, and their windows' places back to the windows; their payments, saga rows and idempotency keys go; their
  reviews, questions and «Helpful» votes go with the counts, the histogram and the average they moved; then the
  customer, whose cascades take the orders, the cart, the Saved list and the rest. One whose order is still being
  placed is left for the next sweep. A token whose `sub` is a demo shopper's with no customer behind it is refused
  (`Callers`) rather than made a new, empty customer.
- **Bounded.** At most `HAUL_DEMO_DAILY_LIMIT` (500 by default) are made in any day; past it `503 unavailable`,
  «Every demo shopper for today is taken — try again later». The three `HAUL_DEMO_*` are set together or not at all,
  and a demo without sign-in refuses the start.
- Rejected, as the item did: one shared demo account with a published password, and a guest checkout.

**Decided in B-14, checkout and the quote.**

- **The quote is the cart's, computed in one place** (`CheckoutCommands.state`, `feature/checkout/`):
  the lines the cart counts — selected, in stock, unchanged — priced by `CartCommands.priced` and
  totalled by the cart's `Totals.of`, with the method, the address or the point, the window and the way
  to pay. Nothing is stored as «the quote»: the tree carries its fingerprint (`CheckoutSummary.quote`, a
  SHA-256 over every value it names, the total included), and placement (B-16) computes the quote again
  through the same call and places only when the fingerprint is the one the shopper saw. A promo code
  applied while valid and expired since is not honoured: the quote leaves it out and the tree says so.
- **What the shopper chose is stored per customer** (`checkouts`, `V9__checkout.sql`), each choice
  optional; a choice not made, or no longer possible, is the quote's default: courier, the newest
  address, the nearest point of the method, the first window with room, the card ···· 4821. Choices are
  `PUT /api/v1/me/checkout` (`CheckoutChoice`), the address form `POST /api/v1/me/addresses`
  (`AddressEntry`); both answer `refresh`, in the customer tier. A point decides the method; a method
  that no longer allows the way to pay chosen falls back to the card.
- **A window's place is taken at placement, not held at quote time** — feature-checkout's «Slot filled
  meanwhile» and `Checkout_PlaceError` only exist if the window can fill between the page and the
  order. Choosing a full window is refused (`409 slot_unavailable`); one that fills after it was chosen
  is cleared from the quote, not swapped for another, and the tree tells the shopper. The place itself
  is `DeliverySlots.reserve(slot, holder)`, one conditional update (`taken = taken + 1 WHERE taken <
  capacity`) per holder, idempotent per holder, with `release` as placement's compensation; built and
  raced here, called by B-16.
- **The numbers** (decisions, not observations — neither the canvas nor the brief names them): windows
  09:00, 12:00, 15:00 and 18:00, three hours each, on five days in the store's time zone, 20 orders a
  window. The first day was tomorrow whatever the items' dispatch days until B-98; it is now the delivery
  calendar's day for the cart's earliest line (D7, «Decided in B-98»), which is still Wed 8 for Maya, whose
  headphones come tomorrow. The delivery fee is the cart's, the same for every method.
- **Every customer pays with the simulator's cards.** v1 has no way to add a card (no artboard; D6 hides
  the account's payment methods), so the payment options are fixed: card ···· 4821 (approves), test
  card ···· 0002 (declines), Haul Pay for totals from $50 to $2,000, pay on delivery except to a locker.
  The canvas lists only the card ···· 4821 (B-15): the test card is chosen by its id
  (`CheckoutChoice.payment`) and drawn only once it is the one chosen — and since B-89 only by a request that
  carries the stand's key, the synthetic shoppers' («Decided in B-89» below).
- **The address form** keeps research §5's fields (street, apt, city, ZIP, door code, courier note; no
  state, so Maya's city is «Brooklyn, NY»). A form at fault is `400 validation_failed` with every field
  at fault in `ErrorBody.fields` (`field_required`, `field_invalid`), and is stored and drawn again with
  an error under each field (`Checkout_Validation`), as B-11 stores a refused promo code.
- **The canvas answered the copy (B-15).** The page is the checkout's header and one `CheckoutBody`:
  the numbered sections beside the order at 1440, under it on a phone. The address is an inline form
  holding the address delivered to, sent when the shopper leaves it; saved addresses are not listed.
  A method says when and where («Tomorrow, Oct 8», «Thu, Oct 9 · 240 m away» — a pickup a day after
  the courier, D7; a cart whose first line comes on Thursday reads «Thu, Oct 9», B-98), the summary names the delivery day, and the button says why it is held («Pick a
  delivery window», «Fill in the street address and ZIP»). The points toggle the canvas draws is the
  server's since B-23 (below).
- **The form edits the address it holds (B-40).** A save rewrites the address delivered to — the chosen one,
  else the newest — in place, keeping its id; the server's state names it and `AddressEntry` carries no id. A
  form equal to an address the customer already has chooses that one and stores nothing. An order copies the
  address it is placed to (`orders.address`, `V14__order_address.sql`) rather than naming the row, which is
  now edited; and the quote's fingerprint names the address's fields as well as its id.
- **The steps say where the shopper is (B-79)**, not the canvas's fixed «Delivery»: the server marks
  «Delivery» until the order can be placed (`CheckoutState.placeable`), then «Review» with the two before it
  ticked. «Payment» is never the step waiting — a way to pay is always chosen, the card ···· 4821 by default —
  so everything that holds an order back is the delivery's. The client's Loading and Error mark none
  (`CHECKOUT_SHELL_HEADER.current = -1`): which step it is is the stored checkout's. The summary's tiles open
  their products, and «Back to cart» beside «Your order» (`CheckoutSummary.back`) is the way back that is not
  the browser's — in the title's row, so the page keeps the canvas's layout.
- **What checkout shows is what it places (B-97).** Any press outside the address form leaves it and sends it — the
  held «Place order» and blank space as much as Tab or Enter — after `awaitTypedInput` (B-76), so the press reads
  what was typed just before it; a drag is a scroll and sends nothing. «Place order» with an address typed and not
  yet in the quote sends the address and places nothing: the next tree quotes it, and the next press places it. A
  day's tile stays the client's own and asks the server nothing: while a day other than the chosen window's is
  shown, the button is held with `DeliverySlots.otherDayHint` («Pick a delivery window») until one of its windows
  is chosen, which is a choice like any other. Asking the server on a tile was the other way; it would need a
  stored day with no window and a round trip per tile, for a state the window's choice settles anyway. The
  summary's delivery day stays the server's throughout.

### D6. Product decisions taken by the owner on the brief (2026-10-08)

The canvas contradicted itself in three places and left one promise unbacked; the owner decided:

- **Haul Pay is 4 interest-free payments two weeks apart** — the plan the canvas shows at checkout
  («4 payments of $128»); the product page's «12 × $29.08 / mo» was dropped.
- **Double points for Plus stay**; the cart promises Maya 1,024 points, not 512.
- **A Plus member never sees the Plus upsell**; members see their delivery savings.
- **Points are redeemable at checkout**, all or nothing (`Checkout_PointsApplied`), because the
  account tile promises «worth $24.80 on your next order».
- Account sections drawn only as menu labels (Returns list, My reviews, Addresses, Payment methods,
  Settings) are hidden in v1; returns themselves are requested from the order page.
- A public demo stand on the owner's domain, with synthetic shoppers walking the path continuously
  (B-31), so the stand has traffic to measure.

**Decided in B-31, the synthetic shoppers.**

- **The e2e module's `main`, one image beside the server's.** The walk (`e2e/src/main/kotlin/.../shoppers/Walk.kt`)
  is the whole path's (`WholePathTest`) on the same storefront client (`Storefront`, moved to `main`), taken for
  its traffic: a random category and buyable product, «Add to cart» on its page, sign-in, courier checkout with a
  random window, placement, delivery, the line returned, the refund, the history. `ghcr.io/youndie/haul-shoppers`
  is published by `stand.yaml` under the server's tag; `scripts/shoppers-check.sh` builds it on every pull request
  and makes it refuse an empty configuration. Not a load test: one walk per `interval`, at most `inFlight` at once.
- **In the chart, off by default** (`shoppers.enabled`, `templates/shoppers.yaml`, chart 0.2.1; the declining walks are 0.3.0). The pod walks the
  server's Service and signs in where the server says, by the storefront's PKCE flow through `haul-web`.
- **Signed in as the stand's demo people, never as a guest.** A guest's «Checkout» goes to sign-in, so a guest walk
  places nothing. The password is the release's shildik Secret's `demo-password`, by reference — never a value, and
  never printed (`Password` prints `<hidden>`, sign-in failures name the status only). The render refuses shoppers
  without `shildik.enabled`, without a demo password, or as anybody not in `shildik.demoPeople`.
- **Sam, not Maya, by default.** Maya's seeded cart would be bought by her first walk and her Plus and points would
  move; Sam's account has nothing seeded to lose. Every walk leaves one returned order in his history; the unit it
  takes at placement comes back with the refund (B-88), so the walks leave the stock as they found it
  (`SyntheticShopperTest` reads it off the database before and after).
- **One walk per person between the sign-in and the placement** (a lock per person): the cart is the customer's.
  Two walks started together without the lock still passed once, so its need is reasoned, not shown.
- **What a walk produces**, at the stand's speed 288: one order saga completed, the fulfilment simulator's moves
  over about ten minutes, a return and its refund over about ten more — so the default `interval: 600` keeps two
  or three walks waiting at once. Until B-89 there was no declined payment among them, since the test card that
  declines is not offered by the checkout; now one walk in ten pays with it («Decided in B-89»).
- **A walk keeps its sign-in alive** (found on the stand after v0.3.0). shildik's access token lives five minutes and
  a walk at speed 288 lives about twenty, so the walk keeps the refresh token the storefront's `offline_access` scope
  gets and, when a request comes back `401`, renews and sends it once more — what the storefront's client does
  (`Identity`). Without it every walk on the stand stopped at its first read of the order page past the token's
  life, about six and a half minutes after signing in, and none reached the return. The fast clock of the e2e and
  of `stand-kind.sh`'s default (a whole walk in under a minute) never showed it; `stand-kind.sh` takes the stand's
  own `SPEED`/`INTERVAL`/`POLL`/`WAIT` for that. The pace itself was not the cause: the fulfilment keys every
  step to placement, not to the window chosen, and a walk's order was delivered ten minutes after it was placed.
  **A shildik token is not unique within its second**: no `jti`, `iat` in whole seconds, RS256 deterministic — a
  refresh in the second of the sign-in returns the same access token. Harmless for a real expiry (five minutes
  later), but a test that marks a token expired by its text must let a second pass first (B-31, iteration 3).

**Decided in B-89, the declining walks.**

- **One walk in ten pays with the test card ···· 0002** (`shoppers.declineEvery: 10`, `HAUL_SHOPPERS_DECLINE_EVERY`;
  `0` for none): walks 10, 20, … — never the first, so a stand just turned on shows the whole path first. Such a walk
  does everything up to placement as the others do, then sees the order refused: cancelled, «Your card ···· 0002 was
  declined», its lines back in the cart. The saga's compensation — the order cancelled, the points given back, the
  window and the stock released — is what it adds to the traffic, and what tracy shows under the order (B-109):
  `order saga step refused` for `authorise-payment` with `payment_declined`, then `order saga step undone` for
  `open-order`, `redeem-points`, `reserve-slot` and `reserve-stock`. Deterministic, not random: one in ten is
  the same share every hour, and a check can tell which walk declined.
- **The card is reached by a key only the shoppers hold, not by a customer of their own.** The server takes
  `card-0002` from `PUT /api/v1/me/checkout` only when the request carries `HAUL_TEST_CARD_KEY` in
  `X-Haul-Test-Card` (`TestCardAccess`, compared in constant time); anyone else naming it by its id is
  `422 payment_method_not_allowed`, as for any way to pay the checkout does not offer, and with no key configured the
  card is nobody's — the default, so a stand without declining shoppers has no way to it at all. The rejected
  alternative, a shopper-only customer whose card declines, would have tied a card's answer to a person (the
  simulator declines a card, not a customer) and would still be open to anybody who signs in as that person — the
  shoppers sign in as the demo people, whose password is the stand's demo password.
- **The key is derived, not configured**: the chart writes `sha256("haul-test-card-0002:" + shildik.masterKeys)` into
  the release's Secret while the shoppers run with `declineEvery` above 0 (`haul.testCardKey`), and hands it to the
  server and to the shoppers by reference. The master keys are already a secret the release holds and keeps across
  upgrades, so the key is stable, never in a values file, and nothing new for the deploy to pass; a one-way hash
  gives the master keys to nobody. Rotating the master keys rotates the key; both pods restart on it (the server by
  its Secret's checksum, the shoppers by the key's). A key under 16 characters refuses the server's start.
- **A declining walk leaves the checkout and the cart as a visitor finds them**, still holding the person's cart
  lock: the listed card chosen again (before the lines go — a checkout with nothing to quote takes no choice) and the
  lines the saga gave back removed. So the next walk buys nothing twice, and somebody who signs in as the shoppers'
  person does not find the declining card chosen. **What is left open**: between the walk's choice and its reset —
  under a second at the stand's pace — that person's checkout shows the card as chosen; and a walk that dies in
  between (the pod killed) leaves it chosen until the next walk of the person chooses the listed card. The server
  does not hide a stored test card from a request without the key; nothing a visitor can do selects it.
- **What the walk sees and what it cannot**: the refusal on the order page and the lines back in the cart. The
  stock is never on the wire (B-88), so «the stock released» is held by `SyntheticShopperTest` against the database:
  the order `cancelled` for `payment_declined`, no `stock_reservations` left for it, every SKU's stock as before.

**Decided in B-19, the account.**

- **Two addresses, one tree.** The overview is `/account` (`StorefrontPage.Account`, tree `GET /ui/account`), the
  orders' history `/account/orders` (`StorefrontPage.Orders`, tree `GET /ui/account/orders`), which the order pages
  sit under. Both are one `AccountBody` built by `feature/account/screen/AccountScreen.kt` from the customer's own
  orders (`OrderRepository.orders`, the customer in the filter) as `OrderTracking` reads them, newest first.
  **Decided in B-159:** only the orders a page draws are tracked — the overview's three cards and four rows, the
  history's page of twenty (`HISTORY_PAGE`); every other order is counted from its own row, shipments and return
  status, read in four queries whatever the count. Tracking reads each order's shipments' history and payments, a
  few queries an order, and the synthetic shoppers' customers on the stand waited 6–8 s for a history of thousands.
  The one state an untracked order cannot tell — a partly refunded return from a whole one — is counted under the
  same chip either way. The history pages as the Saved list does: `?page=N`, page numbers under the rows, each
  loading the account's body in place; a page past the last is the last.
- **The history's filter is the query string**: `?status=active|delivered|returned|cancelled`, none for all; a
  status the history does not have is all of them rather than an error page. The chips count every order and each
  loads the parts of its own address (B-63: before, a `navigate` to it), so a filtered history is a page a reload
  or a link opens. On the history the
  orders on their way come first, then newest first (the canvas's note on `Account_Orders`).
- **Active** is placed, packed, in transit or waiting at a point; such an order is a card on the overview (at most
  three) drawn by the order page's own builder (`OrderScreen.card`): «Arriving *tomorrow*, 15:00 – 18:00» with the
  steps and its lines' tiles, or «Ready for *pickup*» with the point, how long it is kept and the code. The overview's
  history is the last four of the rest, with «All orders» whenever that leaves one out. A row's way on is «Track»
  (on its way), «Details» (waiting, returned, cancelled) or «Reorder» (delivered or picked up) — B-18's reorder.
  Since B-74 the row itself opens the order's page, whatever its way on says: a delivered order is opened from the
  history like any other, and a press on «Reorder» sends only the reorder.
- **What the account cannot store yet is read through ports**: the points and the membership (`Loyalty`), bound
  since B-23 to feature-membership's ledger and membership (`PlusCommands`), and the Saved list's counts
  (`SavedLists`), read from the list itself since B-20 (below). The tree carries the tiles either way, so each item
  changed the source, not the account.
- **A tile is pressed only where it has a page** (B-74): «Price drops» opens the Saved list under «Price dropped»
  (`/account/saved?filter=price-dropped`); the points and a member's «Haul Plus» are explained on no page of the
  store, so they are read, not pressed; the trial's offer is pressed on its button. The Saved list's empty state
  draws a card's saved heart as its illustration, and that tile goes where «Browse deals» does.
- **Returned** is a state of the history (the chip, the filter), derived since B-21 from the order's return: a
  refunded one reads «Returned», one asked for and not refunded yet «Returning» — Blush like it, under the same
  «Returned» filter, with «Details» rather than «Reorder». A refunded return of some of the lines reads «Partly
  returned» at what was paid and kept — the order page's «Paid» (B-101); a whole return keeps the order's total
  beside «Returned», as the canvas's `Account_Orders` draws #HL-44019.
- **The profile line** is «Plus member since <year>» for a member, «Joined <month year>» (the customer row's
  `created_at`) for somebody who has never ordered, «No membership» otherwise; a non-member's avatar is the tile tone
  their reviews are signed with (`ReviewCommands.avatarTone`), a member's Acid.

**Decided in B-20, the Saved list.**

- **One list per customer**, of products (`saved_items`, `V18__saved.sql`: customer, product, the price at saving
  and when): what was hearted on a card or on the product page, and what was saved for later from the cart. A
  product is in it once; a second save — a heart sent twice, or a cart line of a product already saved — changes
  nothing, the first price and day included («Saved twice»). Letting a product go deletes its row, and saving it again
  starts over at that day's price. A guest has no list: their hearts and «Save for later» are the way to sign in
  (`navigate /sign-in`, which draws the same page again once it has gone through, B-41).
- **«Save for later» moves the line**: the product goes into the Saved list and the line leaves the cart
  (`POST /api/v1/cart/lines/{skuId}/save-for-later`), as the phrase means in a shop. The save comes first, so a
  failure between the two leaves the line in the cart with the product saved, and the same press finishes the move;
  a second press after a finished move finds no line (`404 line_not_found`). Since B-100 the press says where the
  line went: «Moved to your Saved list» with «View saved».
- **The price at saving is the product's**, not a SKU's: the cheapest SKU in stock then — the price its card shows —
  or the cheapest at all when none is. A product has **dropped** when its cheapest SKU in stock now is below that
  price; the mark is the difference («Price dropped −$200»), and a product with nothing in stock has not dropped,
  whatever its price. A live deal counts like any price: it is the SKU's price wherever the SKU is read (B-57).
- **The page is an account page**, `/account/saved` (`StorefrontPage.Saved`, tree `GET /ui/account/saved`), the
  account's `AccountBody` with «Saved» and the count in its pill, selected in the menu — the address the screen
  document and the canvas's `Saved_Error` name, beside `/account/orders`. Newest first, 24 to a page; the filter and
  the page are the query string, `?filter=price-dropped` and `?page=2` (both: `?filter=price-dropped&page=2`), each
  chip and page number a `load` of its own address's parts (B-63). A filter the list does not have is all of them, as the
  history's status is; a page past the last is the last (a link left over after letting products go still lands on
  products); a page that is not a number from 1 is `400 validation_failed`, as the deals' is. Only the page numbers
  are drawn, no «Show 24 more».
- **The heart is a command fixed in the tree**, as «+» is (B-37): `ProductCard.heartCommand` and
  `ProductDetails.heartCommand` (`SaveCommand`: the URL and the state the press leaves — `PUT` keeps the product,
  `DELETE` lets it go — answered `refresh`), drawn filled in Hot when the product is in the viewer's list
  (`Viewer.saved`). The header's «Saved» and the account menu's go to the list.
- **Maya's 48 and 6 are seeded rows** (`seed/SampleSaved.kt`): the canvas's Saved pages draw products the seed does
  not sell, so her list is the headphones saved at their old $449 (−$100) and the duvet cover set at its old $179
  (−$40), the Bellwave and the Sonora studio headphones at their price, and generated products picked by a fixed stride —
  four discounted ones saved at their old price. The fixtures keep the canvas's cards (`SavedFixturesTest` holds the
  rest of each body to the server's tree, and each card to the shape of a server card).

**Decided in B-23, Haul Plus and points.**

- **The points ledger is append-only** (`points_entries`, V19): a row per movement, the balance their sum, each
  row's key what makes it happen once — `earned:<shipment>`, `redeemed:<order>`, `returned:<order or return>`,
  `reversed:<return>`, `opening:<customer>`. Points do not expire (no document says they do). A point is a cent.
- **Earning**: an order earns a point per whole dollar *paid* — its total after the points it was paid with — ×2
  when the customer was a member at placement (Maya's $512 cart earns 1,024; with her 2,480 points off, $487.20 earns
  974). The order keeps the number (`orders.points`); each shipment credits its share of it (shared as the capture
  is, `ShipmentShares`) as it is delivered or collected, written before the move so a pass that dies between the two
  credits nothing more.
- **Redemption** is the checkout's toggle (`CheckoutChoice.usePoints`, stored with the checkout): on, the whole
  balance comes off, capped at the items after discounts — points never pay for delivery. The quote's total, the ways
  to pay offered (Haul Pay's range) and the fingerprint follow it; a quote without points keeps the fingerprint it
  had. Placement takes the points in the saga (`redeem-points`, after the window, before the order opens), under a
  lock on the customer, refusing a balance spent meanwhile as the quote having changed (`409 cart_changed`); undone —
  a declined card, a failure further on — they come back as a `returned` row.
- **Haul Plus** is a row per customer (`memberships`) and `customers.plus`, which every price already read; the trial
  writes both in one transaction. 30 days free from the store's date, then «renewing» on the same day each month at
  $4.99 with nothing charged; v1 has no way to end a membership, so `trial_used` cannot happen and is not a code. A
  member asking for a trial — on one or paying — is `409 already_member`.
- **Delivery savings this year** are the fees Plus waived on the member's placed orders since January 1 in the store's
  zone (`orders.delivery_waived_cents`: $5.99 under $35 of items, research D7), plus what a membership carried in from
  before the store kept orders (`memberships.carried_savings_cents`, counted in its year only) — the seed's $186 for
  Maya, whose orders no seed holds, carried into the seeding day's year as the sale is the seeding day's week (B-123:
  under the canvas's 2025 a stand seeded later showed her $0; a database seeded so is moved once, on its next start).
  Her 2,480 points are an `opening` row. Both are seeded once, also into a database seeded before they existed.
- **The offer**: «Try 30 days free» on the home page's Plus block and on the account's tile presents the trial's
  dialog (`PlusTrialDialog`), whose «Start trial» is `POST /api/v1/me/plus/trial` answered `201` with `close`,
  `refresh`; a guest is sent to sign in first. A member's home page draws what Plus saved this year and when it renews.

**Decided in B-25, «Picked for you».**

- **A view is a customer opening a product page**: the product tree's `GET /ui/p/{productId}` records it
  (`feature/recommendations/`, `RecordView`), beside the page's own reads, and a failed write is logged, never
  answered — the page does not wait for it past its own reads and never fails for it. A `GET` with a side effect,
  on purpose: the tree is what the browser fetches when the page opens, and a separate command would be a second
  request on every page and a client change for nothing the server does not already see. A guest's views are not
  kept: a guest gets no block (feature-recommendations), so they would feed nothing.
- **One view per product**, the latest time it was opened (`product_views`, `V20__product_views.sql`, one row per
  customer and product), the newest 20 kept and the rest deleted by the write that pushed them out. A tab, a variant
  or a refresh fetches the tree again and only moves the same view to now; counted as fetches, one product's
  category would outvote three others.
- **The rule** (`PickedForYou`): the viewed products' categories by how many of the views they hold, a tie to the
  category viewed last, then by slug; from each in turn the top-rated products in stock — rating, then reviews, then
  id, so fixtures and tests are stable — that are neither viewed nor in one of the customer's orders that was not
  cancelled, two of a category, six in all. Viewed categories that run out before six are filled from the popular
  row under the same exclusions and the same two-per-category cap, so the six-column row has no holes; the subtitle
  stays «Based on your recent views» while one pick came from the views. «Bought» does not include the cart.
- **A product the page already draws is not picked** (B-96): the home passes the products of the rows above the
  block — the deals of the day, or B-70's «Popular on Haul» with no deal live — and they are left out as a viewed
  product is, the row still filled to six. Without it a customer with fewer than three views saw the same most
  reviewed products in «Popular on Haul» and «Popular right now»: the same `card-<id>` twice on one page, which an
  `update` of the card replaced both of. Whichever row sits above, the block gives way: a product is drawn once.
- **Fewer than 3 views** (or views whose categories have nothing left) is the popular row: the catalog's own
  «Popular» order, most reviews then id, in stock, without what was viewed or bought, two of a category — «Popular
  right now». A customer who has viewed nothing gets it; a guest gets no block.
- **A failure inside the block drops the block**, logged, and the home page answers without it
  (`PickedSection`): the block is the page's extra. A database that cannot be reached still answers `503` through
  the reads every home page makes (B-32).
- **The empty cart's picks stay the day's deals** («From today’s deals», B-13): feature-recommendations names only
  the home page, and the cart is not changed here.
- **Maya's views are seeded** (`seed/SampleViews.kt`): the scenario «From views» as rows — three headphones and her
  cart's duvet cover set and mugs — so the stand draws her home page as `Home_Content` does, «Based on your recent
  views», with two headphones, two duvet covers and two mugs.

**Decided in B-24, Haul Pay's four payments.**

- **The schedule**: the order's total — after the points it was paid with, so points make the plan smaller — in four
  payments of the amount the checkout promised (`HaulPay.paymentCents`, «4 payments of $121.80»), the last taking what
  rounding left, so they add up to the total to the cent (`InstalmentSchedule`). The first is due **when the first
  shipment ships** (feature-checkout's target), the others two weeks apart after it, on the simulator's clock and
  pace (`FulfilmentPace.instalmentInterval`, sped up by `HAUL_FULFILMENT_SPEED` like every other step: 14 s apart on
  the e2e's 86,400).
- **The plan replaces the capture per shipment.** Placement still authorises the whole total; the plan's payments are
  four captures out of that authorisation, each named `instalment:<order>:<n>`, instead of each shipment's share. A
  Haul Pay shipment waits for the first payment as a card shipment waits for its capture; after it, the shipments ship
  with no charge of their own. The simulated merchant side does not change: the authorisation, its limit and its
  ledger are B-17's.
- **The plan is stored when it starts** (`instalment_plans`, `instalments`, V21): until the first shipment ships the
  order's page draws the schedule its total gives, «When it ships», «In 2 weeks»… Each payment is claimed (what it
  charges frozen), charged, then marked paid, each step conditional, so two passes at once take it once and a pass that
  died in between is answered by the processor under the same key. The pass (`HaulPayPlans.advance`) runs after the
  shipments' and the returns' in `FulfilmentRunner`.
- **A declined payment is tried again a day later, once; declined again, the payment is `overdue`** and the plan stops
  there — no later payment is asked for, and nothing chases it (no collections in v1). A first payment that never
  lands holds the shipments `packed`. The simulator itself never declines a payment (the authorisation always covers
  the plan), so overdue is reached through a refusing processor in the tests only.
- **A return comes off what is still owed first**, the last payment first (a payment reduced to nothing is `covered`);
  only what the owed payments cannot absorb is refunded through the processor, out of what was paid. The reduction is
  made once per plan (one return per order) and stored, so a pass that dies before the refund gives back the same rest.
  The points part of a refund goes back as points (B-23), not off the plan.
- **The order's page** draws the schedule under the summary's payment fact (`OrderTotals.plan`): each payment's day,
  where it stands and what it charges; the fact says how much is paid and what comes next. No artboard draws it — it is
  built from the summary's rows and facts, with goldens of its own (`Order_HaulPay*`) and no design reference. The
  account does not show the next payment (no document asks for it).

**Decided in B-52, «bought this month».**

- **The rule** (`feature/catalog/domain/BoughtThisMonth.kt`): the units of the product, over all its SKUs, in the
  orders placed in the last 30 days by the store's clock (`placed_at` after now − 30 days, up to now) whose status is
  `placed` — a `cancelled` order bought nothing, and one still `placing` may yet be declined. A **returned order still
  counts**: it was bought, and the line says no more than that. Below 50 the product page sends no line at all.
- **The abbreviation** (`compactCount`, as the canvas writes «12K» and «600K»): the number itself below 1,000 («840»);
  under ten of a unit one decimal with «.0» dropped («1.2K», «1K»); from ten whole units («12K», «600K»); millions
  alike («1.2M»). Always truncated, never rounded, so 12,999 is «12K» and 999 is «999» — the label never claims a unit
  more than there is.
- **The seed's base is a column, not seeded orders.** `products.bought_base` (V23) is added to the live count: 12,340
  for the headphones («12K», every `Product_*` artboard), 2,180 for the duvet cover set, 840 for the mug, 0 for every
  other product. It stands in for a month of sales the store holds no orders for and does not age out of the window.
  Seeded orders were refused: §6 seeds no orders, an order needs a customer, a saga and shipments, and orders dated at
  the canvas's «now» would leave the window on the stand's wall clock within a month. V23 also writes the three bases
  into a catalogue seeded before it, so the running stand reads like a fresh one (`BoughtBaseMigrationTest` holds the
  migration and `SampleCatalog` together).
- **One aggregate per product page**: a `SUM` over the product's order lines through its SKUs (`skus_product`, then
  V23's `order_lines_sku_id`), joined to their orders by key. Cards do not show the line, so lists cost nothing.

**Decided in B-53, Plus early access to campaign prices.**

- **A SKU names its campaign** (`skus.campaign_slug`, V26): its stored price is the campaign's, its old price the
  regular one, and the database refuses a campaign price that is not a markdown. The seed puts every markdown into the
  Autumn mega sale, whose hero names them («1.2 million items marked down»); a catalogue seeded before V26 has no
  links, as V6 left old rows without headlines.
- **One rule prices every SKU** (`CampaignPricing`, `feature/catalog/domain/CampaignPricing.kt`): until the campaign
  opens for the viewer — at `plus_early_access_at` for a member, a trial included, at `starts_at` for a guest or a
  non-member — the SKU sells at its regular price with nothing struck through; from that instant, at the campaign's.
  The catalog repository applies it to every read that returns SKUs, at the store's clock, and each such read takes
  the `PriceList` it draws for **with no default**, so a screen or a command that forgot whose prices it draws does
  not compile. Cards, the product page, search, the Saved list's drops, the cart, the quote and placement therefore
  read one price; a membership that starts mid-checkout changes the line's price, which the cart marks as changed and
  the quote's fingerprint turns into `409 cart_changed`.
- **Only the opening was read** until B-58, which decided the end (below).
- **Nothing is cached between viewers**: every tree is built per request for its caller, and none is marked for a
  shared cache.

**Decided in B-57, a deal's price is what the cart charges.**

- **A deal is a price of its SKU for a window**: live from `deals.starts_at` up to, not including, `deals.ends_at` on
  the store's clock. V1 gave a deal only its end; `V27__deal_windows.sql` adds the start, and a deal written before it
  is a deal of the day (D7), opening a day before its end. The seed opens the canvas's deals at the canvas's day's
  midnight (`CatalogSeed.DEALS_START`), the instant the Autumn mega sale starts.
- **The same rule prices it** (`CampaignPricing.priced`): a live deal below the price the SKU would otherwise sell at —
  the campaign's when the campaign is open for the viewer, the regular one otherwise — is the SKU's price, with the
  price it beats struck through; a deal at or above it changes nothing, so a deal and a campaign on one SKU sell at
  the lower of the two. The catalog repository reads a SKU's deals with its campaign, so the deal card, every other
  card of the product, the product page, search, the Saved list's drops, the cart, the quote and placement read one
  price. The deal card is the deal's SKU as the catalog prices it; the five generated deals are drawn as before — 70 %
  of the SKU's price under that price — and now charged so; the Sonora deal is the sale's own $349 under $449.
- **An ended deal is gone**: `CatalogRepository.deals()` returns the deals live on the store's clock, so its card
  leaves the home page, the deals page and the empty cart's picks, and its SKU sells at its campaign or regular price
  again; a line put into a cart at the deal's price is then a changed price (B-11's rule).

**Decided in B-58, campaigns and deals end.**

- **A campaign's prices end at its `ends_at`** (`CampaignWindow.liveFor`, `feature/catalog/domain/CampaignPricing.kt`):
  from that instant the SKU is at its regular price with nothing struck through, for everybody — early access opens a
  sale sooner for a member and keeps it no longer. Every read that prices a SKU passes through it, so the card, the
  product page, search, Saved, the cart, the quote and placement all return to the regular price together, and a line
  put into a cart at the sale's price is a changed price (B-11's rule). A deal that outlives its campaign beats the
  regular price. What announces the sale on home and in the empty cart ends with it too (B-59, below).
- **No deals, no section.** «Deals of the day» on home and on the deals page, and the empty cart's «Picked for you»
  (made from the same deals), are drawn only while a deal is live; with none the header, its countdown and the grid
  are all left out, rather than a header over nothing. Home and the empty cart draw «Popular on Haul» in their place
  since B-70 and B-87 (below).
- **The countdown runs to the soonest live deal's `ends_at`**, in the store's zone (`dealsOfTheDay`,
  `feature/catalog/screen/Cards.kt`). A seeded deal of the day ends at the store's midnight (D7), so the countdown is
  the one it was, `DeliveryCalendar.midnight()` is gone, and a deal written with another end is counted down to that
  end instead of to a midnight it outlives or misses.
- **The seed dates its sale from the day it seeds** (`CatalogSeed.generate(day)`): the campaigns' and the deals'
  windows are the canvas's, moved by whole store days to `day` and kept on New York's local midnights across a change
  of clocks; nothing else moves. Tests and fixtures seed `CatalogSeed.CANVAS_DAY` and get the canvas's rows exactly;
  `main` seeds the store's day at start (`CatalogSeed.dayOf`), so a stand seeded today sells what the canvas sells on
  Oct 7.
- **A running stand's sale is moved at start, not by a migration** (`Seeder.redateSale`): a stand keeps its database
  across deploys and `seedIfEmpty` seeds only an empty catalogue, so on every start that seeds (`HAUL_SEED`) the
  sample sale — the seed's campaigns by slug and its deals by id **and** SKU, nothing else — is moved to the store's
  day, to exactly the windows a fresh seed of that day writes, once every sample deal has ended by that day's start.
  Never under a live deal, at most once a day, and never on a database that is not seeded. A migration (V28) was
  refused: it would re-date once, at the version's deploy, and the stand's sale would be over again a week later; and
  it would run on every database, seeded or not. Since B-70 the same re-date also runs at each store midnight while
  the stand runs (below), so a stand never has to restart to keep its deals.
- **The sale's promo code moves with the sale** (B-61): a seeded code that names its campaign
  (`SeedPromoCode.campaignSlug`, `seed/SamplePromoCodes.kt`) — `AUTUMN10`, the Autumn mega sale's — is moved by the
  same whole store days in `generate(day)` and by `redateSale`, which recognises it by its code **and** its terms, so
  a stand takes it while its sale runs and answers `promo_expired` from the sale's last midnight. `SUMMER5` names no
  campaign and keeps the canvas's window, expired on every stand as on the canvas; a store's own code is never read.
  The link is the seed's, not a column: nothing but the seed would read a `promo_codes.campaign_slug`, a code's
  validity is its own window, and a column would not tell the seed's code from a store's own code of the same sale.

**Decided in B-59, home's promo banners lead somewhere.**

- **A campaign's banner opens the deals page** (`HomeScreen.banner`, `PromoBanner.action`, `/deals`). The decision
  was the deals page filtered to the campaign, or the deals page itself when the campaign has no page of its own; a
  campaign has SKUs only through `skus.campaign_slug`, and in the seed only the sale names any, so neither banner's
  campaign (Tech week, the free-delivery weekend) has a page and `/deals` takes no campaign filter. A filter
  (`/deals?campaign=<slug>`) is for the first banner whose campaign sells SKUs of its own. No seeded banner is for
  Haul Plus, so the Plus clause of the decision (the header pill's action, B-49) has nothing to apply to yet.
- **The sale is the first campaign by position** (`liveSale`, `feature/catalog/domain/Catalog.kt`): home's hero
  announces it and the empty cart's line names it. The hero is drawn while the sale is live for the viewer
  (`CampaignWindow.liveFor` — a Plus member's from its early access); the campaign row is the hero's, so without it
  the banners are not drawn either. Each banner is drawn until its campaign ends — not only while it is live, since
  the canvas draws the free-delivery weekend days before it opens.
- **The empty cart's line** («Today's deals end at midnight — up to −70% in the Autumn mega sale.») names today's
  deals and the sale, so it is drawn only while a deal is live and the sale is live for the cart's owner; otherwise
  `EmptyState.text` is left out (it became optional on the wire), and the title and «See today's deals» stay.

**Decided in B-107, the store does not promise what it does not have.** Words a visitor reads are drawn from data or
left out:

- **The sale is named by its campaign row.** `campaigns.name` (V29) is what the hero's eyebrow
  («Autumn mega sale · Oct 7 — 14») and the empty cart's line call it; the line's figure is the campaign's title, its
  first letter lowered (`CartScreen.saleLine`). Both had the autumn sale written into the code. V29 names a campaign
  written before it after its slug, which is the name the seed gives each of its own (`CampaignNameMigrationTest`).
- **The search field reads «Search products».** The canvas's «Search 2.4 million products» counted a catalogue of
  hundreds; the header is drawn before a page has read the catalogue, and the shell's own header has none to count,
  so the number is left out rather than counted (`Frame.SEARCH_PLACEHOLDER`).
- **No «Get the app» block in the footer**, since there is no app: `HaulFooter.appTitle` and `appText` left the wire,
  and the block's place at 1440 stays empty so the footer keeps the canvas's frame.
- **Checkout's note is only «Your card is charged when the order ships.»** — for a card; «By placing the order you
  agree to the Terms of Sale.» named a page the store does not have, and with no card there is no note. Haul Pay's
  reads «The first of 4 payments is charged when the order ships.» (B-152).

**Decided in B-70, a running stand keeps its deals of the day.**

- **The start-up re-date runs at each store midnight too** (`SaleRedater`, `seed/SaleRedater.kt`): a stand started on
  2026-10-09 drew no deals of the day on Oct 10, because `redateSale` ran only at start. Each look is that re-date,
  unchanged, for the store's day at that moment — the seed's own rows only, under the seeding lock, never under a
  live deal, at most once a day — so the sale moves the way a start on that day moves it: its deals open at the
  midnight, its countdown runs to the next one, and the campaigns (the hero's week, the banners) and `AUTUMN10` move
  with it, so the sale never reaches its eighth day either. It looks at each store midnight and at least hourly
  (`SaleRedater.LOOK`), so a process that slept past a midnight, or whose clock jumped, catches up at the next look.
  `main` starts it only when it seeds (`HAUL_SEED`, `haulModule(saleLook = …)`); tests start nothing and call
  `redate()` themselves. No database job was taken: the windows a day's sale has are the seed's (`generate(day)`),
  which only the server computes.
- **No deals, a popular row** (`popular`, `feature/catalog/screen/HomeScreen.kt`): while no deal is live — the seconds between a midnight and the
  look, a store that was never seeded, a store whose own deals ended — home draws «Popular on Haul» in the deals'
  place: the six most reviewed products in stock, drawn as every card is, at the viewer's prices. Without it a
  guest's home had no product at all (hero, banners, tiles, Plus, footer). The measure needs no window, so the row is
  there whatever the sale's calendar; it is never drawn beside live deals, so the canvas's home is unchanged. A
  customer's «Picked for you» under it leaves its products out (B-96).
  `/deals` keeps «On sale» and gains nothing. The empty cart's picks stayed deals-only until B-87 (below).

**Decided in B-87, the empty cart falls back to the same row** (the owner, 2026-10-12). «Popular on Haul» stays on
home, and while no deal is live the empty cart draws it too in the place of «Picked for you»: the same function
(`popular`), so the same six in the same order, at the cart owner's prices (B-53) and with «+» answered by the page
(B-95); with deals live both draw the deals as before. The row is titled «Popular on Haul» in the cart as on home
(`popularTitle`) rather than «Picked for you»: the title names what the row holds, and «Picked for you · From
today's deals» over the most reviewed products would be the same untruth B-107 took out of the copy. Its ids are
home's, `popular-title` and `popular`, for the same reason.

**How the stand is built (B-27).** One image serves the page and the API: the server's distribution
carries the browser bundle and serves it at `/`, so the two cannot be deployed at different versions
and the client needs no base URL. The chart (`charts/haul/`) holds the server, its PostgreSQL as one
pod with one volume, and Traefik IngressRoutes for the host, the shape the sibling reference services
are deployed in; metrik, tracy and katcher are wired in the server and each switched on by an endpoint
and a key.

**Decided in B-27, the public stand (2026-10-09).**

- **The stand is deployed from the owner's infrastructure repository, as the sibling stands are**: it
  holds the stand's values, its secrets and the release tag the stand runs, and deploys when that tag
  changes there. This repository publishes the image — `ghcr.io/youndie/haul-server:<tag>` from a
  pushed tag `v*`, after the image check and the whole e2e path walked through the image pulled back
  from the registry (`.github/workflows/stand.yaml`) — and holds the chart, which the deploy takes at
  the same tag, so the variables the chart renders always meet the binary they were written for.
- **The host is `haul.kotlin.website`, its sign-in `haul-id.kotlin.website`.** One label under the
  domain: the existing wildcard record covers it whatever else is ever recorded under
  `haul.kotlin.website`, which a wildcard stops covering below a name that exists.
- **The stand has a shildik of its own** (`charts/haul/templates/shildik.yaml`, on `shildik.enabled`): the
  provider's published SQLite image at the release the server's suite signs in against (0.4.1), one pod
  and one volume kept on uninstall, written in the chart rather than taken as the provider's
  `shildik-sqlite` subchart — that chart publishes a plain Ingress where the cluster routes by
  IngressRoute, and a subchart would make the deploy fetch a second chart. A public storefront signing
  in through a platform's private provider would tie the two together; its own provider ties nothing.
- **A hook makes the realm** (`charts/haul/files/shildik-bootstrap.py`, a post-install/post-upgrade Job through
  the management API, the one way in — the deploy may not exec into a pod and the provider's image has
  no shell): the realm `haul`, closed to strangers; the public client `haul-web` with the one return
  page `https://<hostname>/signed-in.html`; the sample customers `maya` and `sam`; and a check that
  discovery names the issuer the server is configured with. Every step converges, so each deploy runs
  it again harmlessly, and a failed one fails the release.
- **A visitor browses and shops as a guest; Maya and Sam sign in with a password the owner keeps.**
  shildik's password method has no sign-up, and a sign-in that creates people needs an external
  provider or mail, so the realm holds the seed's two customers (their ids are the seed's, so they sign
  in as the seeded rows — Maya with Plus, her points and her cart) under a password passed as a secret
  and published nowhere. Opening sign-in to visitors is a decision of its own: shared demo credentials
  printed on the stand, or a sign-up the provider does not have yet.
- **The simulated world runs 288 times the store's pace** (`server.fulfilmentSpeed`), a day in five
  minutes: a courier order is packed in under a minute, leaves within five and arrives within fifteen,
  so it moves while a visitor watches its page; the server's suggested 1,440 delivers it before the
  visitor has opened the page.
- **One replica, refused above one at render**: the order page's live updates go through a bus held in
  the process (B-29).
- **NetworkPolicies** (`networkPolicy`, on by default): the database admits only the server, the
  provider's management port only the hook; the provider's public port is open, since Traefik and the
  server's key fetches reach it from outside the namespace.
- **The stand's metrik, tracy and katcher are the cluster's own, by their in-cluster Services**;
  katcher waited for the app to be registered in it, since its key is an app's, issued there — it was
  («Haul», 2026-10-12), and the stand reports to it since (B-122).

**Decided in B-109, an order's saga in tracy (2026-10-11).** Until then tracy held the request spans and nothing
else: no code wrote a record through the agent and no appender was attached, so the saga, the simulators and every
refund were invisible to it — the empty `top_templates` and the zero stored records were the wiring, not a loss.

- **An order writes its own trail** (`ops/OrderTrail.kt`): one line per thing that happened to it, through
  tracy's API with `orderId` as an indexed field — the entity key `get_entity orderId=<id>` reads. Every saga
  member's outcome (`order saga step done`, `… refused` with the reason, `… threw`, `… undone`), each shipment's
  move and the capture that lets it leave, the return asked for, collected and refunded: placement → delivery →
  return in one list. The messages are constants (`OrderLines`), so tracy groups them; what came from the order
  is in the fields.
- **The members are wrapped in haul** (`feature/order/saga/TracedMembers.kt`), **not fed from petich's
  `PetichTracer`**: a tracer's event names the saga, whose id is the customer's and their Idempotency-Key's, and
  only a member holds the payload that names the order. The engine's `LinePetichTracer` stays as it is, at `debug`.
- **Inside `POST /api/v1/orders` each member is a span** (`saga reserve-stock`, `saga open-order undo`) under the
  request's, and its lines join the request's trace and its tail decision. The agent keeps one reference per
  order per trace whatever it decides, so a trace dropped by the sample rate still answers `get_entity` — with
  the bodies gone. Outside a request (the simulators' passes, the sweeper) each line is an entry of its own and
  is always kept.
- **SLF4J still does not reach tracy.** The agent's Logback appender takes MDC as plain fields — never an entity
  key — and attaching it means Logback at compile scope; the saga's own warnings (a rollback given up, a
  shipment held) stay in the pod's log. Recorded in B-109's findings, not done there — done in B-122, below.

**Decided in B-122, what reaches katcher and tracy from the stand (2026-10-12).** Walking TC-STAND-05 found katcher
off on the stand and, reading tracy for it, no line of haul-server at all — not even the `warn` every start logs
saying katcher is off, though tracy keeps every `warn`.

- **katcher is on on the stand**: the owner registered the application («Haul») and the infrastructure release
  carries its key and the address (vedutsya-raboty/infra#104). A `500` is filed with the release the image was
  built from — on a local stack with katcher's own image, and by `ObservabilityTest` against a fake endpoint, through
  a real route whose table is gone. Every failure the catch-all answered was handed to katcher — a `503` and Ktor's own `4xx`
  included (`ErrorAnswers.kt`, as B-32 decided); a client leaving a stream was not (B-110). Narrowed to the `500`s by
  B-148, below.
- **Every SLF4J line from `warn` up is shipped to tracy** (`ops/Observability.kt`, `shipWarnings`): tracy's agent
  captures nothing by itself on the JVM — its Ktor plugin carries spans and its API what is written through it —
  and the Logback appender it ships is the host's to attach; nothing attached it, so no `log.warn` or `log.error`
  ever left the pod. It is attached to the root logger when tracy is installed, before the lines saying what else
  is off, and detached when the application stops.
- **From `warn`, not from the agent's `info`.** A captured line carries no trace — SLF4J has none to give the
  agent — so it skips the tail decision and is sent whole; at `info` that is every refused request's line
  (`ErrorAnswers`) at the shoppers' rate. A `warn` is what tracy keeps from every request anyway.
- **What `main` logs before the application starts is not shipped**: the migrations, the seed, a photo store that
  refused (`Application.kt`) run before tracy is installed, and stay in the pod's log. Shipping them means making
  the agent before the database, outside the module that installs it; not done.

**Decided in B-148, katcher gets what the store got wrong (2026-10-13).** B-32 had the catch-all report everything it
answered — «if it got there, nobody expected it» — and left narrowing it to whoever read katcher. The v0.6.0 run read
it: four groups in an hour on the stand, each a guest's malformed link (`Url decode failed …`), and on a local stack
every page shown during a database outage logged `ERROR … unhandled` with the whole `ExposedSQLException` stack — on
the stand, a katcher report per page.

- **katcher is sent an unexpected `500`, nothing else** (`ErrorAnswers.kt`): a bug, logged at `error` with its stack
  before the report. What the catch-all answers otherwise is no defect of the store and is a line at `info` with no
  stack: Ktor's own `4xx` — a query or a path that does not decode, `NotFoundException`, a body too large or of the
  wrong type — are the sender's; a `503` is an outage.
- **An outage is said once, by the pool that met it**: `FailFastDataSource`'s `warn` when a connection first could
  not be had, now with what the database's address answered («Connection to … refused»), not again until a
  connection is had. tracy ships it (from `warn`); every page the outage fails is
  `GET /ui/home → 503: the database cannot be reached (<cause>)` at `info`. katcher holds none of it: the readiness
  probe, metrik's `503`s and that one line in tracy are where an outage shows. Rejected: one katcher report per
  outage — it needs the catch-all to know when an outage begins and ends, which the pool already does, and files an
  operational event among the bugs. A `503` whose connection died under a statement (SQLSTATE `08`) without the pool
  ever timing out on an ask — a database back before a new connection was needed — would leave the `info` lines
  only; read from the code, not caused.
- **The page at a malformed address ends with the page** (`PageAtAnyQuery`, `WebBundle.kt`). B-117 answered it ahead
  of routing from an `onCall` hook, but Ktor's routing does not ask whether a call was answered before resolving it,
  and resolving reads the parameters: the query was decoded after the page had gone, and its `BadRequestException`
  reached the catch-all — the stand's groups were for addresses answered `200`. The hook is now an interceptor that
  `finish()`es the call. Not changed: `OneCategoryAddress`'s redirect of another form of a category's address has the
  same shape, so by the same reading a query that does not decode behind it leaves an `info` line for a `301` (not
  exercised), and files nothing.

### D7. Numbers the canvas implies, fixed here

Delivery is free over $35 or for Plus, otherwise $5.99; 1 point per whole dollar, ×2 for Plus,
100 points = $1, credited on delivery; courier cut-off 23:30 local (so «Order within 3 h 42 min»
at the canvas's «now»); deals of the day end at local midnight; a pickup is held 5 days; returns
within 30 days of delivery. These are decisions, not observations; the feature documents' scenarios
are written against them and verified against the code before they go `active`.

**Decided in B-98, one delivery calendar, one fee, one place.**

- **Every page reads its days from `DeliveryCalendar`** (`feature/catalog/domain/DeliveryCalendar.kt`): a card,
  the product page, the cart per seller, checkout's windows and the order page per shipment. The rule is the one
  above — the day after, plus the seller's dispatch days, one more after the cut-off, a pickup a day after the
  courier — and the order page applies it to the moment the order was placed (`DeliveryCalendar.courierDay`)
  instead of a copy of it. Checkout used to offer windows from tomorrow whatever the cart held, so a duvet the
  product page promised for Thursday was offered Wednesday, and after the cut-off every window was a day early.
- **Checkout's windows start on the cart's earliest line's day**, not its latest: the courier's method names
  that day («Tomorrow, Oct 8», or «Thu, Oct 9» when no line comes sooner), a pickup the day after it, and a line
  of a slower seller comes on its own day — the day the cart names for that seller and the order page for that
  shipment. Starting from the latest line was the other way: the summary's delivery day would then hold for
  every shipment, but it would move the canvas's checkout (Maya is offered Wed 8 beside a cart that says the
  duvet comes Thu, Oct 9) and every order page placed from it.
- **The product page's delivery price is the cart's** (`Totals.deliveryCents`, for the SKU alone): «Free» from
  $35 or to a Plus member, $5.99 otherwise — for a pickup point as well, since the fee is the cart's and the same
  for every method. It drew $5.99 to a member and «Free» for every pickup.
- **The place is the customer's**: the header's «Delivering to» and the product page's «To …» name the city and
  ZIP of the address a courier delivers to — the one chosen at checkout, else the newest
  (`StoredCheckout.deliveredTo`, read into `Viewer.place`) — and «Brooklyn, NY 11211», the store's default, to a
  guest and to a customer with no address yet. Maya's address is in Brooklyn, so the canvas is unchanged.
- **An order that arrived before its day says why** rather than taking the window's date: the fulfilment
  simulator's pace does not wait for the window (B-17), and on a stand run faster still an order booked for
  Thu Oct 15 was delivered Oct 10. The page keeps the day it arrived — a date the shipment's history holds — and
  the line under the title reads «Deliveries here are simulated: this one came ahead of Thu, Oct 15, the window
  it was booked for.» (or «…, the day it was due.» without a window). Showing the window's date instead would
  put a delivery in the future and start the return window on a day that has not come.

**Decided in B-120, no stock, no day.** A card and the product page promise a day only for a SKU with something in
stock (`DeliveryCalendar.promised`): a card for the SKU it shows — out of stock only when every SKU is — so a product
with nothing to sell has a card that ends at its rating (`ProductCard.delivery` absent); the product page for the SKU
chosen, so out of stock it sends no delivery lines and the stock advice stands in their place, naming the day only of
the colour it turns to. The «Delivery: Tomorrow» facet counts and keeps only products a card promises for tomorrow.
A later date was the alternative and was refused: there is no restock date to promise from. The cart and checkout
are unchanged — a line out of stock there is neither counted nor bought (B-11).

**Decided in B-129, the small lines of the v0.4.0 customer walk.**

- **A promo code is the order's it was typed for.** Placing takes it off the cart with the bought lines
  (`ClearBoughtLines`, the saga's announcement), only while the cart still holds that code — one typed since stays,
  and a declined order, whose saga never reaches the announcement, leaves the cart and its code as they were.
- **A date outside the store's current year carries its year** on the product's reviews and questions — «Sep 28»
  within it, as the canvas writes, «Oct 21, 2023» outside it (`ReviewTabs.day`, against the store's clock): a seeded
  review two years old read as a date to come.
- **No points line where the order earns none** — nothing ticked (`CartScreen.summary`), as a guest has none.
- **Escape closes the return dialog's reasons**, as it closes the header's menus (B-75), and leaves the dialog.
- Two lines the walk noticed are decided otherwise and stay: **«All orders» on the overview counts the active
  cards as left out of the history** (`AccountScreen.recent`, B-19), as `Account_Content` draws it with its two cards
  and four rows; and **questions list the answered first** (B-22, B-99, TC-PDP-09), as the canvas's
  `Product_Questions` does, so a question just asked is listed after them.

### D8. Product photography is out of v1

The canvas marks photos as striped placeholder tiles (tone + label), and v1 ships those tiles.
Images through object storage are B-30.

**Settled in B-30: the mechanism, not the photography.** A product row may carry an `image_key` (V7);
when the server has an S3-compatible bucket (`HAUL_S3_ENDPOINT`, `_BUCKET`, `_ACCESS_KEY`,
`_SECRET_KEY`, optional `_REGION`), the card and the product page carry the photo's address,
`/images/<key>`, which the server serves from its own origin — the bucket stays private and the browser
needs no CORS — with an immutable cache header, because the key holds the content's hash. No endpoint, no
photos: the server starts as before and every tile is the placeholder. The client draws a photo over the
placeholder tile, which stays while the photo is absent, loading or failed (Coil 3 in the bundle). The
seed draws photos for the sample products of §6 in code (since B-149 each is the product's own picture, below);
real product photography is the owner's
question (B-30 findings), and until it is answered the generated catalog keeps its tiles. The fixtures
name no photo, so every artboard's parity is what it was. Since B-108 every tile of a product carries the same
address: a cart line, checkout's «Your order», an order's line and the suggest panel's «Top products»
(`image` on `CartLine`, `SummaryItem`, `OrderItem`, `ProductSuggestion`) — no picture is made up for them, the
stored one is wired; since B-136 so does a line of the return dialog (`ReturnLine.image`), which B-108 had left
out of its list.

**Decided in B-78: the seed's own pictures, drawn and served by the server.** The owner chose (2026-10-12) flat SVG
illustrations in the canvas's style over licensed stock or generated images: no licence, nothing off the store's
origin (B-128). The stand runs without object storage, so nothing is stored either: `SeedArt`
(`server/…/seed/SeedArt.kt`, the objects in `SeedMotifs.kt`, the writer in `Svg.kt`) draws each picture on request
from what names it, and the photo route serves it under `/images/art/<name>.svg` — `image/svg+xml`, a year's
`immutable`, `nosniff` and `Content-Security-Policy: default-src 'none'`.

- **What is drawn.** A product is its category's object — one each for the sample catalog's twelve leaves
  (headphones, speakers, TVs, …, dumbbells) and one for every other top-level category (Shoes shares the running
  shoe; Electronics, Home & Kitchen and Sports draw a leaf of theirs), found by walking up the seed's tree; a category the seed does not know is a parcel — in its SKU's colour (the seed's ten colours have
  swatches; any other draws the object's own), over its tile tone with the canvas's hatching, in one of three
  compositions chosen by its id. So the products of one category share a picture set and differ by colour and
  composition, and the product page's picture changes with the chosen SKU. A category's tile is its object, plain.
  The hero of the Autumn mega sale is falling leaves in Cobalt's shades, white and Hot (the hero's Acid figure runs
  across it); Tech week's banner a laptop. Free delivery weekend's banner has no picture place on the canvas and
  draws none.
- **A stored photo still wins** (`ProductPhotos.url`): the pictures are what a product without one carries, so a
  store with object storage shows its sample products' photos and the rest's pictures on the same page.
- **A name carries the content's hash** (`p_headphones_1_silver_e6e4ff.3f1c9a0e5b.svg`): the route draws the name
  again and serves it only while the hash matches, so a name always means the same bytes, and a name from before a
  change of the drawings is a 404 — the client keeps its tile — instead of another picture.
- **The client draws them with Coil's SVG decoder** (`coil-svg`, over the Skia SVG renderer skiko's wasm already
  carries) in the same loader as the photos, over the same tiles; the tiles lost their captions («CAMPAIGN IMAGE»,
  «TECH», «HEADPHONES», «PRODUCT PHOTO»), which only described the missing picture, and with them the wire's `label`
  fields (`ProductCard`, `CategoryTile`, `CampaignHero`, `PromoBanner` — `image` there now — `CartLine`, `SummaryItem`,
  `ProductSuggestion`, `ProductDetails.photoLabel`). SVG rather than a PNG the server rasterises: measured in D9
  («Measured in B-78»).
- **The goldens draw the pictures.** The screenshot fixtures draw every `/images/art/…` a body names from
  `composeApp/src/desktopTest/resources/art` at once (`ArtLoader`), and `ArtFixturesTest` holds that directory to what
  the server draws under each name, so a golden shows what a shopper gets. The artboards draw a striped tile where
  the pictures stand, so every one of them with a tile left the parity comparison in B-85 (§1.6).

**Decided in B-149: every tile of a product draws its picture, and the sample products their own.** The v0.6.0
run found three gaps in B-78's pictures:

- **The sample products' stored photos** were one tinted box for all fifteen (B-30's still life), so with object
  storage the headphones, the earbuds, the mug and the duvet looked alike. `SeedPhotos` stores each product's own
  picture now — `SeedArt`'s drawing of its category's object in its first SKU's colour — as SVG under
  `products/<id>/<hash>.svg`, which the photo route serves as it serves a picture (`Content-Security-Policy:
  default-src 'none'`). A store seeded before keeps nothing stale: a key the seed stored that is not today's is
  replaced on the next start, and a key that is not the seed's is left alone.
- **The tiles whose wire named no picture** — the review and question dialogs' product (`FormProduct.image`), the
  account's order cards and history rows (`ActiveOrder.images`, `HistoryRow.images`, one per line, as the order's
  page draws them) and a review's photos (`Review.images`, one per tone: the reviewed product's object, plain, over
  the photo's tone, in another of the three compositions than the product's own picture, so each photo differs).
- **Art drawn large was soft**: the client asked Coil for a picture without a size, and Coil's SVG decoder then
  rasterises at the SVG's own size — 240 px — which the product page scaled up to ≈768 px at 800. The loader asks for
  the size of the box the picture is drawn in, in pixels, and again — keeping the picture on screen until the
  sharper one is in — whenever the box outgrows it (a window widened); never for a smaller one. The pictures are
  still asked for after the first frame; their bytes do not change, only the size they are rasterised at, and
  neither the first frame nor the pictures' frame moved (D9, «Measured in B-149»).

### D9. No server rendering, no SEO

A Compose canvas page is not indexable and its first load is dominated by the runtime. Accepted for
a reference; the first-load size is measured (B-28), not assumed. *Confirmed in B-28* (below): skiko's
wasm alone is two thirds of the bytes and, on a throttled link, most of the wait; what Haul itself adds
is about a tenth, and the fonts another tenth. The wait is no longer blank since B-80: the page draws the
header's shape until Compose's first frame replaces it, in the HTML's first round trip (below). Since B-150 that
first frame is drawn in the store's own faces: the page preloads the fonts and the store mounts once it has them.

**Decided in B-161, the title is the page's; the preview is the stand's.** Every tab read «Haul», and a link to the
stand previewed as a bare address.

- *The tab's title.* Each page's tree names it on its header — `HaulHeader.tabTitle`, `CheckoutHeader.tabTitle` on
  checkout — and the client puts it in `document.title` (`shell/TabTitle.kt`, `WindowTitle` in the browser, a
  recording `BrowserTitle` in the tests): «Sony WH-1000XM6 Wireless Noise Cancelling Headphones · Haul» (the listing
  name, B-45), «Headphones · Haul», «Search: running shoes · Haul», «Deals · Haul», «Catalog · Haul», «Cart · Haul»,
  «Checkout · Haul», «Your account · Haul», «Your orders · Haul», «Saved · Haul», «Order #HL-48302 · Haul», and on the
  home «Haul — a demo store built end to end in Kotlin». A field the server writes rather than a title read off the
  tree: the trees name their pages in a dozen different nodes (a product's details, a category's `PageTitle`, the
  cart's title with its count, `CheckoutBody.title`) and the home and the account's overview name none, so the client
  would have kept a table of node types and invented copy for the rest — and the copy is the server's. It is
  `Frame.page`'s required `title`, so a page added without one does not compile. The header alone (`/ui/header`) and
  an `update`'s header name none; the client reads the title from the tree drawn, a refresh's included, never from an
  override. A page the shell draws with no tree (an error, a page not there, a prompt) is «Haul», the sign-in page —
  the client's own — «Sign in · Haul», a placeholder «Haul» once a page has named the tab; a cold load keeps
  `index.html`'s `<title>`, the home's, until the first tree arrives.
- *The preview.* `index.html` — the one page every storefront address answers (B-36) — carries a fixed description
  saying what the demo is, Open Graph and Twitter card tags (`og:type website`, `og:url` the stand's root,
  `summary_large_image`) and one picture, `preview.png`: the top 1440 × 756 of the `Home_Guest` golden scaled to
  1200 × 630 (240 KB), a file of the bundle served beside the page. Every address previews as the store, a product
  link included. `GET /robots.txt` is a route of the server, answered with or without the bundle: `User-agent: *`,
  `Allow: /` — `/ui` stays open, it is what a crawler that renders the page asks for.
- *Rejected for now:* rendering each page's HTML on the server for crawlers (a per-page title, description and
  picture in the HTML). The page's words are on a canvas, not in the HTML, so a crawler would read only what the
  server rendered beside it — a second rendering of every page to keep in step with the trees — and a fixed preview
  already says what a shared link is. Its own item if the numbers ever ask for it.

#### Measured in B-28

**How.** `scripts/measure-first-load.sh` (it builds the arms and calls `scripts/measure-first-load.py`)
on 2026-10-08, on the tree of `cfb9e1f` (before B-27 and B-10 merged; the scripts have not changed
since). The production bundle
(`:composeApp:wasmJsBrowserDistribution`, Compose Multiplatform and Kotlin from the sborka 0.5.0.113
`wip` catalog), served as static files by the script over loopback, each response compressed with
what the browser asked for — it asked for `gzip, deflate, br, zstd` and got brotli (quality 11) on
every file. Two arms:

- **main** — the bundle `main` ships. Its root draws the theme with an empty body and calls no server,
  so no server and no PostgreSQL were needed, and its first frame is the page's background colour.
  Nothing reaches the renderers from `main()`, and dead-code elimination leaves them out.
- **wired** — a throwaway copy of the tree whose root renders the Home body of the screenshot fixtures
  (`home_content.json`) through `haulRegistry()`: every renderer reachable, the Home page drawn. It is
  the bundle the storefront will ship once it draws screens, minus the HTTP client; nothing of it is
  committed. Its skiko `.wasm` is byte-identical to main's (sha256 `089052ba…`). Its patch swapped
  `{ App() }` in `Main.kt` and stopped applying once the root took arguments (B-80); since B-90 it swaps the
  one `App(...)` call (`WIRED_ROOT`), keeps the rest of the entry point, and `WiredArmTest` holds `Main.kt` to it.

Time in headless Chromium (Chrome for Testing 149.0.7827.55, `--headless=new`, WebGL 2 through
SwiftShader, 1440×900 at 1×), a fresh browser and profile per run, cache disabled, three network
profiles through CDP `Network.emulateNetworkConditions` with DevTools' own numbers: **none**
(loopback), **Fast 4G** (165 ms, 1,012,500 B/s down), **Slow 4G** (562.5 ms, 180,000 B/s down). Seven
rounds per profile, profiles interleaved, round 1 discarded; median (min–max) of six. The probe
wraps WebGL's `clear` and `draw*` before any page script runs: **first frame** is the first
`requestAnimationFrame` after Compose's first GL call (the frame that carries it to the screen),
**settled** the same for the last GL frame before the page has been quiet for 3 s. Controls in the same
log: with every `.wasm` blocked the probe saw no GL call and no frame (both arms); the profiles came
out in their known order; Slow 4G's first frame lies above the time its non-font bytes take at its
bandwidth. The stand: the shared Linux build machine (WSL2 on an Intel Core Ultra 7 255HX, 20
threads, 16 GB, kernel 6.6.87.2), **busy with other agents' Gradle builds** — 1-minute load average
3.7–11.0 during the runs (1.4–4.4 in the second, loopback-only campaign), MemAvailable never under
7.6 GB. The throttled profiles are bounded by the
link and barely move with load; the unthrottled one is bounded by the CPU and its spread is the load's.

**Sizes**, KiB, per bucket of the files the page requested (the source map and the licence texts are
in the distribution but never requested):

| Bucket | main raw | gzip -9 | brotli 11 | wired raw | gzip -9 | brotli 11 | share of wired brotli |
|---|---:|---:|---:|---:|---:|---:|---:|
| skiko `.wasm` (Skia) | 8,438 | 3,247 | 2,561 | 8,438 | 3,247 | 2,561 | 65.8 % |
| app `.wasm` (Kotlin: Compose, kotlinx, kompot, Haul) | 1,597 | 529 | 418 | 3,348 | 1,078 | 819 | 21.0 % |
| fonts, four variable TTFs | 1,156 | 522 | 432 | 1,156 | 522 | 432 | 11.1 % |
| `composeApp.js` (the loaders of both) | 513 | 96 | 78 | 515 | 96 | 79 | 2.0 % |
| `index.html` | 0.5 | 0.3 | 0.2 | 0.5 | 0.3 | 0.2 | — |
| **total** | **11,705** | **4,394** | **3,490** | **13,458** | **4,944** | **3,891** | |

The fonts one by one (raw / gzip / brotli): `archivo.ttf` 643 / 248 / 192, `jetbrains_mono.ttf`
183 / 88 / 77, `bodoni_moda_italic.ttf` 172 / 100 / 87, `bodoni_moda.ttf` 158 / 86 / 76. Main's app
`.wasm` is Compose's runtime, UI, foundation and resources with an empty screen — framework, not Haul;
wired minus main (+1,751 KiB raw, +401 KiB brotli) is what drawing the screens brings: kompot,
kotlinx.serialization, Haul's renderers and the parts of Compose they use.

**Time**, ms since navigation start, median (min–max) of six rounds; «skiko» is when its last byte
arrived:

| Arm | Profile | skiko's last byte | First frame | Settled |
|---|---|---:|---:|---:|
| main | none | 269 | 588 (407–764) | 836 (567–1,089) |
| main | Fast 4G | 3,797 | 3,916 (3,806–4,551) | 4,624 (4,474–5,285) |
| main | Slow 4G | 19,298 | 19,506 (19,428–19,690) | 22,625 (22,551–22,811) |
| wired | none | 284 | 989 (663–1,719) | 1,711 (1,084–4,030) |
| wired | Fast 4G | 4,225 | 4,952 (4,575–5,128) | 6,595 (5,888–7,344) |
| wired | Slow 4G | 21,594 | 22,261 (21,968–22,606) | 26,074 (25,302–26,816) |

The raw values, rounds 1–7 in order (round 1 discarded), first frame / settled:

| Arm | Profile | First frame | Settled | 1-min load |
|---|---|---|---|---|
| main | none | 894 629 407 619 764 554 558 | 1448 935 567 875 1089 791 798 | 10.8 9.4 7.7 7.4 7.2 7.2 3.7 |
| main | Fast 4G | 4037 3806 3832 3983 4551 3907 3926 | 4879 4474 4539 4761 5285 4654 4593 | 10.0 8.8 7.0 7.8 7.6 7.2 5.8 |
| main | Slow 4G | 19584 19575 19527 19428 19690 19484 19428 | 22725 22692 22658 22576 22811 22591 22551 | 11.0 10.3 9.1 8.5 8.1 6.7 5.3 |
| wired | none | 954 663 692 927 1051 1309 1719 | 1555 1084 1150 1402 2021 3119 4030 | 7.2 5.1 4.0 4.1 5.7 6.7 9.6 |
| wired | Fast 4G | 4720 4575 4657 5060 5005 4899 5128 | 6402 5888 6289 6459 6992 6731 7344 | 5.3 4.7 5.6 4.5 7.1 6.6 6.7 |
| wired | Slow 4G | 22048 22063 21968 22192 22330 22414 22606 | 25705 25415 25302 25640 26508 26816 26780 | 6.6 7.1 6.4 4.4 4.0 6.3 9.8 |

A second campaign, loopback only, run when the machine's load had dropped (same host, same builds,
same script with `--profiles none`, about 20 minutes after the first):

| Arm | First frame | Settled | Raw first frame | Raw settled | 1-min load |
|---|---:|---:|---|---|---|
| main | 396 (328–566) | 548 (470–801) | 373 341 375 328 417 566 492 | 507 470 528 477 568 801 671 | 1.4–1.6 |
| wired | 1,177 (833–1,268) | 2,302 (1,228–2,972) | 1034 1119 958 833 1243 1235 1268 | 2623 2191 2291 1229 2313 2508 2972 | 2.6–4.4 |

A third campaign served main's bundle **uncompressed** (`--identity`; same bundle — the wasm digests
match — same host, about 25 minutes after the first, 1-minute load 4.4–14.2): 11,707 KiB on the wire.

| Arm | Profile | First frame | Settled | Raw first frame |
|---|---|---:|---:|---|
| main, identity | none | 942 (591–1,276) | 1,346 (868–1,829) | 2029 771 1055 1276 841 1043 591 |
| main, identity | Fast 4G | 11,616 (11,530–11,664) | 13,081 (12,956–13,122) | 11369 11591 11635 11664 11530 11654 11597 |
| main, identity | Slow 4G | 62,136 (62,083–62,342) | 69,405 (69,309–69,622) | 63004 62343 62153 62121 62152 62089 62083 |

**What it says about D9.**

- *Bytes: confirmed.* Skiko's wasm is 65.8 % of the wired first load; with the Compose framework in
  the Kotlin wasm (main's 418 KiB) and the loaders, the runtime is at least 78 % (3,058 of 3,891 KiB
  brotli). Haul's screens add 401 KiB (10 %), its fonts 432 KiB (11 %).
- *Time on a throttled link: confirmed, and it is transfer.* The first frame comes 0.1–0.7 s after
  skiko's last byte, so the wait is the bundle crossing the link: about 5 s on Fast 4G and 22 s on
  Slow 4G, of which skiko's 2,561 KiB alone are 14.6 s at Slow 4G's bandwidth.
- *Time on loopback: the CPU,* compiling and instantiating the wasm and composing the first frame:
  0.4–0.6 s for main and 1.0–1.2 s for wired across the two campaigns, with a spread the shared
  machine's load explains (main's 0.4 s is the one quiet run; wired's second campaign was slower than
  its first). Below a second either way; not a property of the bundle to better than a factor of two.
- *Compression is worth more than anything Haul's own code could save.* The same bundle served
  without it reaches its first frame in 11.6 s instead of 3.9 s on Fast 4G and in 62.1 s instead of
  19.5 s on Slow 4G — a factor of about three, against the 10 % Haul's screens weigh.
- *The first frame is the page, in a fallback face* (until B-150, below). The fonts are fetched only once the wasm runs
  (so they never compete with it on the link), and Compose draws before they arrive: with every font
  blocked, the wired arm still drew the whole Home page in a fallback face carried by the bundle (no
  other font was requested), and the canvas faces swap in when they land — settled minus first
  frame is 0.7 s on loopback, 1.6 s on Fast 4G and 3.8 s on Slow 4G. Main's first frame is only the
  background, the same colour the HTML paints before any script runs.
- *A control that failed as pre-registered.* The item fixed «Slow 4G first frame ≥ all its
  compressed bytes ÷ bandwidth» before the first run; on main it fails (19,506 < 19,862 ms), because
  the first frame does not wait for the fonts — the finding above. The floor over the bytes the frame
  can have waited for (everything but the fonts) passes in both arms (17,404 and 19,686 ms).

#### Measured in B-34

**What changed.** The stand now sends the bundle the way B-28's script served it, from the image
itself: `docker/Dockerfile` writes a `.br` (brotli 1.2.0, quality 11) and a `.gz` (-9) beside each file
of the bundle's directory once, at build time, and the server's `staticFiles` picks one by `Accept-Encoding`
(`server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt`), keeping the original `Content-Type`
and sending `Vary: Accept-Encoding`; the two hash-named `.wasm` are `public, max-age=31536000,
immutable`, everything else `no-cache`; `composeApp.js.map` is gone from the distribution. No
compression per request, no Traefik middleware.

**How.** On 2026-10-08, the image of this item's branch on `4e4d27c`, before its rebase onto B-30
(`haul/server:b34`, `sha256:5b2b3a6a…`, built by
`scripts/image-check.sh`, which also asserts the headers), measured by B-28's script with two new arms:
`ARMS="image image-identity" IMAGE=haul/server:b34 OUT=/tmp/b34-first-load
scripts/measure-first-load.sh 7`. **image** starts the image beside a PostgreSQL and points the
measurement at it (`measure-first-load.py --url`), so every byte is what the server sends;
**image-identity** serves the same bundle directory, copied out of the image, uncompressed through the script's
own server — the stand before B-34. The bundle is `main`'s (its root still draws no screen): skiko's
`.wasm` is byte-identical to B-28's (sha256 `089052ba…`), the app `.wasm` 1,598 KiB against B-28's
1,597. Same Chromium, profiles, probe, rounds and controls as B-28; the same shared machine, load
average 1.6–8.0 during the image arm and 1.1–14.1 during the identity arm.

**What the browser got** (Accept-Encoding `gzip, deflate, br, zstd`): brotli on every file but the
`favicon.ico` the page does not have (404); `application/wasm` on both modules; 3,486 KiB on the wire
against 11,708 KiB uncompressed — the image's `.br` files are within 4 KiB of the q11 sizes B-28
computed.

| Arm | Profile | Transferred KiB | skiko's last byte | First frame | Settled |
|---|---|---:|---:|---:|---:|
| image (brotli) | none | 3,486 | 250 | 414 (367–791) | 589 (512–1,157) |
| image (brotli) | Fast 4G | 3,486 | 3,801 | 3,981 (3,846–4,101) | 4,666 (4,512–4,955) |
| image (brotli) | Slow 4G | 3,486 | 19,267 | 19,429 (19,401–19,480) | 22,546 (22,497–22,601) |
| image-identity (raw) | none | 11,708 | 423 | 975 (613–1,714) | 1,429 (845–2,545) |
| image-identity (raw) | Fast 4G | 11,708 | 11,517 | 11,768 (11,494–12,152) | 13,219 (12,902–13,976) |
| image-identity (raw) | Slow 4G | 11,708 | 61,915 | 62,180 (61,910–62,329) | 69,419 (69,138–69,562) |

The raw values, rounds 1–7 (round 1 discarded), first frame / settled:

| Arm | Profile | First frame | Settled |
|---|---|---|---|
| image | none | 858 791 738 427 372 402 367 | 1196 1157 1096 620 512 559 519 |
| image | Fast 4G | 3910 4101 3944 4090 3885 3846 4019 | 4645 4955 4621 4915 4552 4512 4712 |
| image | Slow 4G | 19413 19468 19444 19480 19401 19413 19412 | 22531 22601 22575 22593 22497 22515 22516 |
| image-identity | none | 841 1079 1714 762 959 992 613 | 1154 1743 2545 1038 1434 1423 845 |
| image-identity | Fast 4G | 11461 11844 12152 11507 11754 11781 11494 | 12859 13296 13976 12939 13218 13220 12902 |
| image-identity | Slow 4G | 62158 62195 62184 62329 62176 62087 61910 | 69422 69513 69424 69562 69414 69336 69138 |

Controls as in B-28: the probe stayed silent with every `.wasm` blocked (both arms), the profiles came
out in order, and Slow 4G's first frame lies above its non-font bytes ÷ bandwidth (17,377 and
60,024 ms); the pre-registered all-bytes floor fails again for the reason B-28 found (the first frame
does not wait for the fonts).

**What it says.** The stand's first frame on Slow 4G drops from 62.2 s to 19.4 s and on Fast 4G from
11.8 s to 4.0 s — the factor of three B-28 predicted, now from the server rather than from the
measurement's own static server, and within 0.1 s of B-28's brotli numbers for the same bundle
(19.5 s and 3.9 s). On loopback the CPU still dominates and the shared machine's load still sets the
spread (0.4 s against 1.0 s here, inside B-28's 0.4–1.2 s). Only the first load was measured; what
`immutable` saves on a second visit (skiko's 2.5 MB) was not.

#### Measured in B-80

**What changed.** The wait itself stays — B-80 shortens none of it — but it is no longer blank:
`composeApp/src/wasmJsMain/resources/index.html` draws the header's shape (the strip, the logo's place and its
dot, «Catalog», the search field, the shortcuts, the cart, the category row) and B-62's sweeping line under it,
in inline HTML and CSS at the header's own measures (185 px at 1440, 206 on a phone), shapes only — the words
need the fonts, which arrive after the bundle. Nothing else is requested; the page is 4,726 bytes, 1,175
brotli (`StaticFrameTest` holds it to the loader as its one request and under a first TCP window). No CSP or
other header restricts inline styles: `WebBundle.kt` sets none and the chart's `IngressRoute` adds only the
HTTPS redirect; the page carried no inline script then. Since B-90 it carries one line: on `/checkout` it marks
the root `checkout`, before the frame is parsed, and the frame draws checkout's own header — the logo, three
steps and «Secure checkout», 89 px at 1440 and 118 on a phone — instead of the storefront's, which a cold
checkout showed first and then swapped. A CSP that forbade inline scripts would leave every address the
storefront's shape.
Compose mounts by replacing the body's children, the frame among them, so the entry point (`Main.kt`) puts
the frame back over the mount in the same task and removes it from a `drawWithContent` around the app: in
the animation frame of Compose's first picture, so the browser shows one or the other, never both and never
neither.

**How.** On 2026-10-10, B-34's `image` arm of B-28's harness, with the frame's probe and `--stop-after 3` added
(the live storefront never goes quiet: the deals count down, so «settled» is not measured), as pre-registered
in the item: two images from one server build of this item's branch on `d07ddf3`, differing only in the bundle
they carry — **before**, `main`'s bundle at `d07ddf3` (`haul/server:b80-before`, `sha256:26e4206c…`), **after**,
this branch's (`haul/server:b80-after`, `sha256:f0b962e8…`); skiko's `.wasm` the same in both (`089052ba…`),
each started beside a seeded PostgreSQL, so the page draws Home from the server. Same Chromium (149.0.7827.55),
profiles and rounds as B-28; the shared machine at a 1-minute load average of 0.7–3.3. «First contentful
paint» is the browser's own (Paint Timing): a page whose only paint is its background colour reports none,
so before it is Compose's first canvas frame and after it is the frame.

| Arm | Profile | HTML end | First contentful paint | First frame (Compose) |
|---|---|---:|---:|---:|
| before | none | 53 | 666 (492–828) | 574 (447–758) |
| before | Fast 4G | 213 | 4,770 (4,696–4,820) | 4,704 (4,641–4,751) |
| before | Slow 4G | 614 | 24,030 (23,920–24,080) | 23,956 (23,864–24,002) |
| after | none | 39 | 328 (268–532) | 533 (462–660) |
| after | Fast 4G | 210 | 292 (272–312) | 4,709 (4,687–4,742) |
| after | Slow 4G | 607 | 668 (656–676) | 23,934 (23,900–23,969) |

The raw first contentful paints, rounds 1–7 (round 1 discarded):

| Arm | Profile | First contentful paint | First frame |
|---|---|---|---|
| before | none | 672 828 540 588 744 780 492 | 583 758 461 510 637 691 447 |
| before | Fast 4G | 4956 4784 4820 4764 4776 4720 4696 | 4814 4719 4751 4704 4705 4659 4641 |
| before | Slow 4G | 24004 24080 24032 24028 24064 23928 23920 | 23933 24002 23965 23948 23983 23870 23864 |
| after | none | 328 304 268 292 352 532 460 | 548 554 491 512 634 462 661 |
| after | Fast 4G | 284 312 292 292 272 300 272 | 4720 4737 4711 4687 4742 4700 4706 |
| after | Slow 4G | 664 668 676 660 668 672 656 | 23953 23969 23900 23931 23938 23949 23917 |

The frame was on screen (first contentful paint to its removal) for 149 ms on loopback, 4.3 s on Fast 4G and
23.2 s on Slow 4G (medians). In every counted run where the probe saw it — 17 of 18 — it was removed in the
animation frame of Compose's first GL call and never hidden before. In the 18th (loopback, round 6) Compose drew
before the browser's second animation frame: the probe, which only starts following the frame once a tick has
seen it, never did, and the first contentful paint (532 ms) was the canvas, after the first frame (462 ms) —
the frame was parsed and taken away without being painted, which is the right behaviour, not a measured one.
Controls: with every `.wasm` blocked the probe saw no GL call in either arm and after's frame stayed on screen;
the profiles came out in order; Slow 4G's first frame lies above the bundle's non-font bytes ÷ bandwidth
(21,893 and 21,895 ms); and the swap check can fail — a copy of the after bundle whose page removes the frame
on a timer, 300 ms after the HTML is parsed, failed it on Fast 4G (removed in animation frame 32–33, Compose's
first GL call in 254).

**What it says.** «Time to first paint» goes from the first frame to the HTML: 4.8 s to 0.3 s on Fast 4G and
24.0 s to 0.7 s on Slow 4G — the page's first round trip plus a frame — and on loopback from 0.67 s to 0.33 s.
The app's first frame does not move (+0.1 % on Fast 4G, −0.1 % on Slow 4G, inside the 5 % the item allowed);
the kilobyte the page grew by is not visible in it. What D9 says about the bytes stands: the shopper still waits
the bundle's whole transfer for the store itself, now looking at its header.

#### Measured in B-78

**What changed.** The storefront draws pictures where it drew captioned tiles (D8, «Decided in B-78»): on the home page
a guest sees sixteen — the hero, Tech week's banner, eight category tiles, six deals — each an `/images/art/…svg` the
server draws, fetched after the page's tree, decoded by Coil's SVG decoder. Two questions, both on the first load:
what the pictures cost, and whether the client should decode SVG or the server send a PNG.

**How.** On 2026-10-13, B-34's `image` arm of B-28's harness with `--stop-after 20` (the storefront never goes quiet)
and two additions (`scripts/measure-first-load.py`): every run reports the pictures the page asked for, the bytes they
took on the link, the last one's last byte and the first GL frame after it («pictures frame»), from Resource Timing and
the probe; and `--art pass|png` puts a relay of the script's own between the browser and the image — `pass` relays as
it is, `png` answers every `/images/art/*.svg` with a PNG of it at its own size (240 × 240, the hero 360 × 560),
rasterised once by a Chromium of its own — the bytes a server that rasterised its pictures would send. Three arms, each
through the relay: **before** — the image of `origin/main` at `2ea55753` (`haul/server:b78-before`, `sha256:edae3bde…`),
captioned tiles; **svg** — this branch (`haul/server:b78`, `sha256:2488b91e…`); **png** — the same image, its pictures
rasterised. Each beside a seeded PostgreSQL, so the page draws Home from the server; a guest, 1440 × 900 at 1×. Same
Chromium (Chrome for Testing 149.0.7827.55), profiles and rounds as B-28 (seven, round 1 discarded, median (min–max) of
six); the shared Linux build machine at a 1-minute load average of 0.4–9.0 (most runs under 5). Skiko's `.wasm` is the
same file in all three (`bfa5198f…`).

**Bytes.** The app's `.wasm` (raw / gzip -9 / brotli q11, bytes): `origin/main` 5,768,950 / 1,759,208 / 1,292,497;
this branch 5,776,230 / 1,761,865 / 1,294,656 — +2.1 KiB brotli. A third build, this branch without `coil-svg` (a
throwaway copy), is 5,767,758 / 1,759,050 / 1,292,312: the decoder alone is +8.3 KiB raw, +2.3 KiB brotli, and the rest
of the change −0.2 KiB. The home page's sixteen pictures are 24 KiB as SVG (sent uncompressed: the server compresses
only the bundle's files) and 143 KiB as PNG at their own size — a lower bound for PNG, whose 2× for a sharp picture on a
2× screen would be larger still, while one SVG is sharp at every size.

| Arm | Profile | Transferred KiB | First frame | Pictures | Pictures frame |
|---|---|---:|---:|---:|---:|
| before | none | 4,344 | 501 (472–602) | — | — |
| before | Fast 4G | 4,344 | 4,784 (4,763–4,842) | — | — |
| before | Slow 4G | 4,344 | 24,266 (24,235–24,333) | — | — |
| svg | none | 4,372 | 529 (464–566) | 16, 24 KiB | 861 (825–1,097) |
| svg | Fast 4G | 4,372 | 4,920 (4,818–5,140) | 16, 24 KiB | 6,035 (5,872–6,319) |
| svg | Slow 4G | 4,372 | 24,387 (24,283–24,435) | 16, 24 KiB | 28,514 (28,419–28,611) |
| png | none | 4,490 | 570 (535–834) | 16, 143 KiB | 832 (817–1,201) |
| png | Fast 4G | 4,490 | 4,903 (4,764–4,944) | 16, 143 KiB | 6,096 (5,951–6,176) |
| png | Slow 4G | 4,490 | 24,380 (24,306–24,474) | 16, 143 KiB | 28,864 (28,788–28,987) |

The raw values, rounds 1–7 (round 1 discarded), first frame | pictures frame:

| Arm | Profile | First frame | Pictures frame |
|---|---|---|---|
| before | none | 480 483 543 490 512 472 602 | — |
| before | Fast 4G | 4821 4786 4773 4763 4842 4788 4783 | — |
| before | Slow 4G | 24283 24333 24245 24285 24288 24247 24235 | — |
| svg | none | 450 528 566 464 514 530 560 | 919 825 893 837 842 1097 881 |
| svg | Fast 4G | 4930 4933 4818 4918 5140 4830 4922 | 6055 6059 5872 6011 6319 5894 6072 |
| svg | Slow 4G | 24554 24423 24324 24435 24283 24350 24427 | 28685 28539 28462 28611 28419 28488 28575 |
| png | none | 636 535 652 588 549 834 551 | 925 817 1023 825 839 1201 819 |
| png | Fast 4G | 4916 4896 4822 4910 4913 4764 4944 | 6145 6130 5976 6124 6067 5951 6176 |
| png | Slow 4G | 24395 24349 24474 24429 24362 24306 24399 | 28883 28818 28987 28886 28788 28842 28922 |

Controls, in every arm's log: with every `.wasm` blocked the probe saw no GL call and the static frame stayed; the
profiles came out in order; Slow 4G's first frame lies above its non-font bytes ÷ bandwidth (22.2 s); the all-bytes
floor fails as B-28 found (the first frame does not wait for the fonts); no void run.

**What it says.**

- *The pictures cost the first frame nothing it can be told from.* They are asked for once the tree arrives, after the
  first frame, so what precedes it is the bundle, which grew 2.1 KiB brotli (≈ 12 ms at Slow 4G's bandwidth). The
  medians moved by +0.5 % on Slow 4G and +2.8 % on Fast 4G, with the arms' ranges overlapping, and the png arm — the
  same bundle — sits within 20 ms of the svg arm in both; not a property of the change to better than the shared
  machine's spread.
- *The pictures are on screen 4.1 s after the first frame on Slow 4G, 1.1 s on Fast 4G, 0.3 s on loopback* (svg):
  they queue behind the fonts, which are fetched at the same moment and take the link until 27.6 s on Slow 4G; the last
  picture lands 0.9 s after them.
- *SVG is the cheaper way to send them.* The PNGs are 119 KiB more for the same sixteen pictures (×5.9) and put them on
  screen 0.35 s later on Slow 4G (28.9 s against 28.5 s) and 0.06 s later on Fast 4G; on loopback, where bytes are free,
  the two are within the spread (0.83 s against 0.86 s). Against that the decoder's 2.3 KiB brotli is paid before the
  first frame — 13 ms at Slow 4G's bandwidth, 2 ms at Fast 4G's — once, and then cached with the bundle (the `.wasm` is
  `immutable`). Decided: the client decodes SVG.
- *Levers, not taken:* the server sends the SVGs uncompressed (`gzip -9` would make the 24 KiB about a third); and a
  repeat visit asks for none of them (`immutable`), which only the first load here does not show.

#### Measured in B-150

**What changed.** The store's first frame was the page in a fallback face, redrawn in the canvas's faces once the
four files landed — «Haul», «Up to −70%», «Laptops from $399» reflowed in front of the shopper, 0.4 s to a few seconds
after the static frame's swap (the v0.6.0 QA run). Now `index.html` preloads the four `.ttf` (`<link rel="preload"
as="fetch" crossorigin>` — the request Compose's resources make, so their read takes the preloaded answer; a `font`
preload or one without `crossorigin` would fetch each file twice), and the entry point reads their bytes
(`loadHaulFonts`, `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/theme/PreloadedFonts.kt`) before it mounts
the store, which is laid out in them from its first composition. A file that cannot be read mounts the store as
before (`rememberHaulFonts`, the fallback until Compose's loading lands) — the fonts-blocked control draws so.

**How.** On 2026-10-13, as pre-registered in the item: B-34's `image` arm of B-28's harness with `STOP_AFTER=8`,
and a new pair of numbers per run (`scripts/measure-first-load.py`) — the fonts' last byte and the first frame after
it, «faces frame». Two images, each beside a seeded PostgreSQL, a guest's Home at 1440 × 900: **before**, `origin/main`
at `0802a3f4` (`haul/server:b150-before`, `sha256:63f3f357…`); **after**, the item's branch (`haul/server:b150`,
`sha256:92c29e6b…`); skiko's `.wasm` the same file in both (`089052ba…`). Same Chromium (Chrome for Testing
149.0.7827.55), rounds and controls as B-28 (seven, round 1 discarded, median (min–max) of six). Two campaigns: the
first before then after, three profiles, the shared machine at a 1-minute load of 3.4–12.4 during before and 1.2–3.5
during after; the second after then before, Fast and Slow 4G only, at 1.0–6.7. The app's `.wasm` grew by 1.2 KiB
brotli (1,295,614 → 1,296,829 bytes) and the page by 178 bytes; 4,372 KiB cross the link in both.

| Campaign | Arm | Profile | Last font byte | First frame | Faces frame |
|---|---|---|---:|---:|---:|
| 1 | before | none | 867 (612–6,109) | 803 (576–5,836) | 1,133 (650–6,283) |
| 1 | before | Fast 4G | 5,825 (5,701–6,827) | 5,179 (5,048–6,126) | 5,841 (5,728–6,835) |
| 1 | before | Slow 4G | 28,036 (27,655–29,217) | 24,780 (24,465–25,946) | 28,096 (27,671–29,223) |
| 1 | after | none | 189 (146–680) | 1,032 (538–2,536) | = first frame |
| 1 | after | Fast 4G | 1,023 (999–1,088) | 5,388 (5,289–5,628) | = first frame |
| 1 | after | Slow 4G | 4,377 (4,347–4,417) | 26,408 (26,328–26,630) | = first frame |
| 2 | before | Fast 4G | 5,473 (5,454–5,763) | 4,837 (4,811–5,115) | 5,532 (5,467–5,785) |
| 2 | before | Slow 4G | 27,539 (27,488–27,712) | 24,341 (24,300–24,526) | 27,563 (27,544–27,807) |
| 2 | after | Fast 4G | 994 (975–1,011) | 5,341 (5,254–5,378) | = first frame |
| 2 | after | Slow 4G | 4,385 (4,356–4,490) | 26,422 (26,346–26,465) | = first frame |

The raw first frames, rounds 1–7 (round 1 discarded), and before's faces frames:

| Campaign | Arm | Profile | First frame | Faces frame (before) |
|---|---|---|---|---|
| 1 | before | none | 4800 954 2387 653 5836 576 639 | 5500 1531 2627 735 6283 650 712 |
| 1 | before | Fast 4G | 5276 5979 6126 5048 5180 5058 5179 | 5967 6686 6835 5728 5845 5746 5836 |
| 1 | before | Slow 4G | 30341 25946 24652 25561 24908 24566 24465 | 35457 29223 27869 28766 28298 27894 27671 |
| 1 | after | none | 827 1002 1019 2536 1044 1054 538 | — |
| 1 | after | Fast 4G | 5285 5499 5310 5441 5289 5335 5628 | — |
| 1 | after | Slow 4G | 27137 26328 26356 26461 26630 26355 26479 | — |
| 2 | before | Fast 4G | 5039 4847 4825 5095 5115 4811 4827 | 5694 5562 5502 5780 5785 5467 5490 |
| 2 | before | Slow 4G | 24363 24300 24356 24326 24357 24526 24315 | 27568 27546 27566 27559 27623 27807 27544 |
| 2 | after | Fast 4G | 5279 5254 5286 5316 5377 5378 5367 | — |
| 2 | after | Slow 4G | 26370 26459 26425 26346 26419 26465 26417 | — |

Controls, in every arm's log: with every `.wasm` blocked the probe saw no GL call and the static frame stayed; the
profiles came out in order; the static frame went in the animation frame of the first GL call in every run the probe
followed; Slow 4G's first frame lies above its non-font bytes ÷ bandwidth (22.2 s), and now above all its bytes ÷
bandwidth too (24.9 s), the floor B-28 pre-registered that failed until the first frame waited for the fonts; no void
run (`/favicon.ico`'s 404 is an answer, not a failed request, and voids nothing). In every before run the first frame
came before the fonts' last byte (6 of 6 per profile and campaign); in no after run.

**The faces at the first frame**, seen: `scripts/first-frame-faces.py` (new), three cold loads per image under Fast 4G,
a fresh profile with the cache off, a screenshot every 0.15 s (the interval stretches to 0.3–0.6 s while the page
compiles and first composes, when no new frame is drawn either), the logo's crop of the first shot taken after the
static frame's removal compared with the last shot's. After: the first store shot in the faces in 3 of 3 loads, no
shot in a fallback (31–32 store shots each). Before, the control: the first store shot in a fallback in 3 of 3, nine
or ten shots in a fallback each, the faces from 1.5–1.8 s after the swap.

**What it says.**

- *The store is in its faces sooner than it was.* The first frame in the faces moves from before's faces frame to
  after's first frame: −0.45 s and −0.19 s on Fast 4G (the two campaigns), −1.7 s and −1.1 s on Slow 4G. The preloaded
  files cross the link beside the bundle instead of after the module has compiled, so the round trip and the redraw
  that followed it are gone.
- *The first frame pays for the fonts' bytes, and no more.* It comes +0.21 s and +0.50 s later on Fast 4G, +1.6 s and
  +2.1 s on Slow 4G — under the fonts' 432 KiB brotli at each link's bandwidth (0.44 s and 2.46 s) plus B-80's 5 %, the
  budget the item fixed. What the shopper looked at in that time was the fallback page; now it is the static frame.
- *The preloads take the link first.* Discovered with the page, the fonts land at 1.0 s on Fast 4G and 4.4 s on Slow
  4G, before skiko's module (26.0 s on Slow 4G against 24.2 s before), so the module still compiles as it streams and
  the first GL call follows its last byte by 0.3 s. Lever, not measured: a lower `fetchpriority` on the preloads might
  let the module through first and compile it while the fonts cross — a few hundred milliseconds at most, since it
  moves the bytes and not their sum.

#### Measured in B-149

**What changed.** The client asks for each picture at the size of the box it is drawn in, and again when the box
outgrows it, instead of with no size — which Coil's SVG decoder rasterised at the SVG's own 240 px (D8, «Decided in
B-149»). Nothing before the first frame changes; the question was only whether the first frame or the pictures moved.

**How.** On 2026-10-13, B-34's `image` arm with B-78's relay (`ART=pass`, `STOP_AFTER=8`), `PROFILES=none,fast4g`,
three rounds per run (the blocked-wasm control first), two passes with the arms interleaved — **before**, the image of
B-150's branch (`haul/server:b150`, B-150's font preload included), **after**, this branch's (`haul/server:b149`,
rebased on B-145 and B-146) — each beside a seeded PostgreSQL, a guest on Home, 1440 × 900 at 1×, Chrome for Testing
149.0.7827.55, on the shared Linux build machine at a 1-minute load average of 2.0–6.5. A first campaign against
B-145's image read +0.35 s on Fast 4G: that image predates B-150, whose preloaded fonts share the link before the
first frame (B-150's own +0.5 s, «Measured in B-150»), so it was no «before» for this item and is not reported.

**Bytes.** The app's `.wasm` brotli 1,266 → 1,271 KiB (B-145 and B-146 in it as well); 4,373 → 4,377 KiB transferred
on Home; the sixteen pictures 24,065 bytes in both — the same SVGs.

| Arm | Profile | First frame | Pictures frame |
|---|---|---:|---:|
| before | none | 625 (558–792) | 999 (939–1,264) |
| after | none | 595 (533–1,028) | 964 (880–1,407) |
| before | Fast 4G | 5,228 (5,182–5,561) | 6,334 (6,267–6,695) |
| after | Fast 4G | 5,198 (5,147–5,273) | 6,270 (6,246–6,427) |

Median (min–max) of six runs each, milliseconds. The raw values, pass 1 rounds 1–3 then pass 2 rounds 1–3, first
frame | pictures frame: before none 792 624 641 558 601 626 | 1264 1051 1023 939 974 972; after none 1028 569 620 640
533 534 | 1407 1010 958 970 880 894; before Fast 4G 5561 5275 5226 5182 5230 5211 | 6695 6353 6315 6267 6313 6368;
after Fast 4G 5273 5213 5175 5147 5235 5182 | 6427 6286 6246 6253 6291 6252.

**What it says.** Neither the first frame nor the pictures' frame moves beyond the machine's spread: the ranges
overlap on both profiles. Rasterising at the box's size — up to ≈768 px on the product page instead of 240 — costs
nothing measurable on Home, whose sixteen pictures are drawn at card and tile sizes.

#### Decided in B-154: words select, and the right button opens the store's menu

A canvas page loses what a browser gives any page for free: no word on it could be selected, and the right button did
nothing (2026-10-30). The page now selects its words — Compose's `SelectionContainer` — and reads the right button
itself, opening the store's menu at the pointer: Copy and Select all over words, «Open in new tab» and «Copy link»
over a link, drawn as the store's other menus. Not Compose's menu: on skiko 1.12 it is the old one, its look and
items internal, and on the web its presses did nothing; nor its copy shortcut, which waits there for a browser
`copy` event that never comes. The product page first (B-154), then every page, the header, the dialogs and the
fields (B-155): a field offers Cut, Copy, Paste and Select all through the store's own field, and a link in a
sentence offers its address. A text drawn past its box over a control takes the control's presses once it can be
selected; the footer's name and the hero's figure are pictures and stay out of the selection. A text set tighter than
its face reaches above its first line box, and takes the pointer from there down only — a category's title had taken
the crumbs above it — while its glyphs draw where they drew (B-157); and a control asks for the hand over its own
words, which ask for the text cursor (B-157).

#### Decided in B-158: the storefront moves, and rests as the canvas draws it

The canvas is still, and so was the store: the owner asked for it to come alive (2026-10-31). Motion is a layer over
the page at rest, never a different page: blocks rise into place as they arrive or are scrolled to, cards and banners
answer the pointer, a pressed control sinks, a saved heart bursts, the cart's badge pops and its figure rolls,
dialogs, menus and messages spring open, placeholders shimmer and photos fade in
(`composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Motion.kt`). All of it plays only under `Motion.Lively`,
ends exactly where the still page is drawn, and under `Motion.Still` adds no node at all. The goldens and every view
drawn outside the storefront are still; the storefront is lively unless the browser asks for reduced motion. A lively
page at rest is the still page to the pixel (`MotionTest`), so the goldens go on describing what a shopper sees once
the page has settled.

### D10. Documentation in English

As the sibling reference projects; this repository is read from outside.

---

## 3. Risks and open questions

**Found in B-04: goldens are recorded on Linux.** The three fonts ship as variable files, and the
same golden recorded on macOS differed on Linux by 0.06–0.10 % of the pixels — single pixels on the
edges of a few glyphs («$» in Bodoni Moda, the comma in JetBrains Mono) — with viddik's vertical
metrics normalised and rasterisation pinned. The likely mechanism, not measured further: CoreText
and FreeType instance a variable font's outlines differently. The tolerance stays viddik's default;
the goldens are recorded where CI verifies them, on Linux (`CLAUDE.md` says how), and a verification
on macOS is expected to fail by that margin.

**The fonts (B-04).** Bodoni Moda (roman and, since B-07, italic), Archivo and JetBrains Mono are the
variable files of `google/fonts@5e8a3ba`, OFL, bundled as Compose resources with their licences in
`composeResources/files/licences`. Bodoni Moda is loaded per optical size (`opsz`), because the canvas
draws it with optical sizing on. *Hypothesis still open:* that wasmJs honours the `opsz` setting as the
desktop target does — checked when a screen is first compared in the browser.

**Found in B-128: a character none of the four faces has is fetched from Google.** In the browser Compose
(1.12) sends every code point a paragraph leaves unresolved to a downloader that picks a Noto face for it and
loads it from `https://fonts.gstatic.com/s/` — `installFallbackFontDownloader`, installed by every
`ComposeViewport`, the base URL a constant. There is no switch: the downloader is `internal`
(`defaultFallbackFontDownloader`, its setter `@TestOnly`), and a CSP that blocks the request leaves the glyph a
box (the item rejected it). So what the code writes stays inside the faces: `BundledGlyphsTest` lays out every
string literal of the client, the contract and the server in a Skia paragraph over the four files alone — the
browser's own test — and a glyph the canvas draws that no face has is traced as a path in `HaulIcons` (the
suggest panel's «↵», U+21B5, which fetched Noto Sans Math). Invisible marks the code uses (a zero-width space, a
word joiner) resolve without a glyph. What a shopper types or a seller names is not held: Archivo and Bodoni
Moda carry Latin only (Latin-1 and Extended-A, four Greek letters), JetBrains Mono adds Greek and Cyrillic, none
has CJK or emoji — such text still fetches a face (`scripts/origin-check.py --control` shows it).

**Decided in B-137: the store writes ’, never `'`, inside a word.** The canvas does («Couldn’t copy the link»), and
four sentences had drifted from it («You’ll earn …», «… isn’t / aren’t available now», «… can’t be sent back»,
«Sellers can’t answer …»). `CurlyApostropheTest` reads the same literals as `BundledGlyphsTest` and fails on `'`
between two letters. Out of it: the seed — shoppers' and sellers' words as typed, the products' names — and text for
the developer, told by what the literal is written into (a suppression's reason, a `require`/`check`/`error` or
exception message, a log line, a date pattern).

**Found in B-106: at the canvas's optical size Bodoni Moda's hairlines are 0.2 px.** The hairline is about
0.2 em ÷ `opsz` (the hyphen: 72/2000 em at 6, 20 at 22, 4 at 96), so drawn at `opsz` = its pixel size it is about
0.2 px at any size: a hyphen and a dash vanish, a 4 reads as a 1. Figures and punctuation are drawn at a smaller
optical size whose hairline is a pixel (`HaulType.legibleFigures`, `legibleOpticalSize`); their advances are the same
along the axis, so no line moves. Letters keep the canvas's cut — theirs grow by up to 2 % towards the small cuts and
re-break titles — and an H's bar still vanishes (B-106's findings).

**Found in B-07: three ways Compose lays text out unlike the browser the references come from.**
Measured on the Home and Catalog artboards (B-07, Iteration 1):

- A line height below the font's own (Bodoni Moda's natural line is 1.53 em; the canvas sets 1, .9,
  .82) does not shrink the paragraph in Compose: a two-line 112 px title at `line-height: .9` measured
  272 px instead of 202, the excess split above and below. CSS keeps the line boxes and lets the glyphs
  overflow. The client's `Text` reports the line boxes as its size, with the leading split evenly
  (`LineHeightStyle.Alignment.Center`, `Trim.None`); this alone took Home_Guest from 23.4 % to 4.7 %.
- viddik's pinned rasterisation places glyphs on whole pixels, which rounds every advance up
  (JetBrains Mono's 6.6 px to 7) and pushes the end of a line several pixels to the right. The
  fixtures keep the pinning (no hinting, anti-aliased) but place glyphs at fractional positions
  (`FixtureFonts`): 0.1–0.7 points off every screen.
- An inline label in a block of another font sits on that block's strut (its own font and line
  height): an 11 px mono eyebrow in a 16 px Archivo block sits on a line about 17 px tall. `InlineLine` adds the strut.

The italic cut of Bodoni Moda was missing in the first iteration and the accents were synthesised
obliques of the roman, the largest difference left on the phone artboards; it is bundled since
(`bodoni_moda_italic.ttf`, weight 500 only, as the canvas uses it).

Also learned on the way: CSS sizes boxes in fractions of a pixel and Compose in whole ones, so every
text's width is rounded up — a row of ten separate category names ends visibly right of the canvas,
and a row laid out as one paragraph with placeholder gaps does not. `flex: 1` grows from a basis of
zero with padding and border on top, which is why two «halves» are not equal on the canvas.

**Found in B-08: Skia rounds a line's top down where the leading is negative.** Probed on the
fixture fonts: a 13 px mono label at `line-height: 1` has its line top at 1.0 where CSS's is 2.08, a
34 px Bodoni title at 8.0 where CSS's is 8.93 — the glyphs sat a pixel lower than the canvas's, and a
pixel's shift of a whole block lights every edge under it (white cards on Paper differ by 17 per
channel, past the ±16). The client's `Text` now places the first line box where CSS does,
`(line height + ascent − descent) / 2` above the baseline, and takes the box's height as lines × line
height. This took the product phone artboards from 5.6–7.5 % to 2.0–4.1 %.

**Found in B-10: `text-wrap: balance` is a width, not a break rule.** The canvas balances its big
titles; Compose breaks greedily, which put «Search didn’t / *respond*» where the canvas has «Search /
didn’t *respond*», and a no-break space that keeps two words together at 1440 splits a word on a
phone where they do not fit one line. `BalancedText` does what Chrome does: it keeps the number of
lines the text takes at the full width and bisects for the narrowest width that still holds it.

**Found in B-76: in the browser a keystroke reaches a field a frame after a press does.** Compose on
wasmJs collects the page's input events and applies them to the focused field at the next animation
frame (`NativeInputEventsProcessor`, Compose UI 1.12), while a pointer press is handled the moment it
arrives. A press that reads a field — the cart's «Apply» — read it without what was typed within that
frame; in a hidden or background tab, where frames are throttled, that is seconds, which is how the stand
review saw «Apply» send nothing. Such a press waits for the input first (`ui/TypedInput.kt`,
`awaitTypedInput`); the desktop target applies input at once, so only a test that holds the clock sees it.

**Found in B-83: every press that reads a field waits, Enter does not, and a focus move keeps the keys.** The
presses that read a field are the cart's «Apply» (B-76), the price range's (B-69), checkout's press outside the
address and «Place order» (B-97), the header's search button and the review and question dialogs' «Post» and
«Send» (B-83); each waits for `awaitTypedInput` before it reads. Enter needs no wait: its `keydown` is applied in
the same frame as the keys before it, in order. A press that moves the focus to another field right after typing
does not lose the keys either: the pending frame applies them through the edit session of the field they were
typed in, which is still the field's (walked in the browser with frames held back four seconds — the price
«from» typed and «to» pressed within 30 ms kept and applied its bound; the search field typed and the price
pressed kept its query). What most likely lost a field's text in B-69 and in the review walk is a press that did
not focus the field at all (B-83's findings).

**Found in B-112: in a page that draws, the first press on a field focuses it.** Ninety fresh loads in headless
Chromium (`scripts/first-press-check.py`) — the search field, the price «from», the promo code; the pane's click,
an unfocused window, frames held to one a second — focused and typed into every field every time. What failed in
the hidden pane was a press on a page it had not drawn yet: a hidden pane runs few animation frames, Compose
composes an arrived screen only in one, and the press lands on the skeleton. Whether anything changes for that
is the owner's question (B-112).

**Found in B-126: Compose 1.12's browser mirror for assistive technology follows only the newest layer.** Each
`Popup` or `Dialog` is a layer of its own in Compose's scene, with a semantics owner of its own; in 1.12 the mirror
(`#cmp_a11y_root` in the viewport's shadow root) takes the newest owner, and when that layer closes it has none —
it keeps the closed layer's nodes until a reload ([CMP-9368](https://youtrack.jetbrains.com/issue/CMP-9368), fixed
upstream in 1.13.0-alpha01). So what opens over the page is drawn in the storefront's own layer
(`OverlayHost`/`OverPage`, `ui/Layers.kt`), not in a `Popup` or a `Dialog`, while the stack holds Compose at 1.12.
And text links: a `LinkAnnotation` is drawn as a press with no name (named upstream in 1.13 too), so the category
row's words and the crumbs are `linkedText` runs, each a named press over its words. `scripts/a11y-check.py` walks
every popup, the main pages and the review dialog in headless Chromium and reads the mirror and Chromium's own tree
for buttons and fields with no name (fields since B-131). Found in B-131: the 1.12 mirror writes a node's `role`, its
`aria-label` and `aria-live` and nothing of its state — a field is a `role="textbox"` named by its content description
alone, and a chosen star, a ticked box or a selected swatch reads as any other button; so a state set in semantics
(`selected`) is there for the desktop tree and the tests, not yet for a screen reader in the browser. (B-151 put the
state of the cart's boxes and checkout's points switch into their names; 1.13's mirror writes `aria-checked`.)

**Found in B-144: an open sheet has to take the key focus back.** A mouse press focuses the control it lands on, and
when that control stops being one — the photos' «›» on the last photo, disabled; a facet of the filter sheet, whose
press the sheet withholds while the load it sent runs — Compose clears the focus to the window, past the sheet: the
arrow keys and Escape stop reaching it (`document.activeElement` stays the canvas, the keys reach the page). So
`SheetOverPage` takes the focus back whenever nothing inside it holds it, unless something opened over it since (a
menu's keys are the menu's until it closes). Kept at the layer rather than at each control: a control re-requesting
the sheet's focus after its press misses a press withdrawn for another reason (the sheet's load), and controls that
cannot be focused would leave nothing for Tab to reach. The other things over the page have nothing to keep: a
menu, and the return dialog's reason list, close on the press of an entry. (Since B-151 the dialogs are sheets too,
and keep the keys the same way.)

**Decided in B-145, a notice the shopper has to act on is drawn over whatever is open.** On a phone a facet is
ticked in the filter sheet, which covers the page; a tick that did not arrive left «The page didn’t update» with
«Retry» under the sheet — nothing seen, and the press meant for Retry landed on the sheet's «Show N items», which
closed it. Two ways were open: the sheet saying itself that its tick did not go through, with a Retry of its own,
or the notice drawn above the sheet. The second was taken because it is what the shell's frame for a notice already
is (B-62, B-75, B-92, B-136): one notice per page, held by the shell, drawn at the bottom of the window over what is
there — it only had to be over the overlays too. So `OverlayHost` has a layer above every overlay
(`NoticeOverPage`, `ui/Layers.kt`), and the not-updated notice and kompot's messages are drawn in it: over the
filter sheet, the phone's menu (whose entries close it before they are followed, so nothing raises a notice while
it is open — now it would be seen) and the dialogs a `present` draws (the Plus trial's «Couldn't reach the store»
was already over them, and stays). A sheet-own notice would have been a second frame for the same failure, and a
second Retry in the tree beside the page's. The cost: while the notice is up it covers the sheet's «Show N items»;
«×» still closes the sheet, and a Retry that goes through takes the notice away. A sheet that holds the keys
(B-144) leaves them with a notice's control Tab reached, so its Retry is reachable from the keyboard.

**Decided in B-151, a box or a switch says its state in its name, and a sheet or a dialog hides the page under it.**
The 1.12 mirror carries a role and a name and no state (B-131), so the cart's line boxes, «Select all» and
checkout's points switch are named for what they do and whether they are on — «Select Sonora SH-718N, ticked»,
«Select all, partly ticked», «Use 2,480 points (−$24.80), off. 100 points = $1 · all or nothing» (`Modifier.toggles`,
`ui/Links.kt`) — and carry the state in semantics as well. Their role there stays a button's: the mirror makes a
`Role.Checkbox` a `role="checkbox"` with no `aria-checked`, and Chromium reads that as not checked (measured: a
`role="checkbox"` named «Select A, ticked» is `checked: false` in `Accessibility.getFullAXTree`), so a ticked box
would have said «ticked» and «not checked» at once. 1.13's mirror writes `aria-checked` (read in 1.13.0-alpha02's
library): on it the role becomes the box's or the switch's, and the word in the name can go. A card's «+» is named
by its product, «Add <product> to cart». What a `present` draws — the review, question, return and Plus trial
dialogs — is a `SheetOverPage` now, and every sheet is modal: while one is open `OverlayHost` clears the semantics
of the page and of whatever opened before it (`clearAndSetSemantics`, which the mirror honours — it walks
`replacedChildren` — where `hideFromAccessibility` it does not read), so the mirror holds the sheet alone, as a
modal `Dialog` would; a menu is not modal and leaves the page as it is, and the notices (B-145) stay over all of it.
A dialog's scrim is a press of the pointer's alone (`pointerPress`): as a press in semantics it was a button with the
whole dialog in it, read as one. Escape reaches a dialog as it reaches the other sheets, after what has the focus
inside it declined the key — the return dialog's reason list closes first — so the Plus trial's dialog, which closed
on no key, and the review dialogs close on Escape.

**Risk 1. The canvas and the code drift apart without anyone seeing it.** A renderer changed for one
screen changes every screen that uses the component. Mitigation: one reference PNG per artboard
(125), exported from the canvas into the client's snapshot directory, and `viddikDesignParity` in
every client item's acceptance. Open: the tolerance, set from the first measured screen.
**Settled in B-85: the code drifts from the canvas on purpose, and parity follows only what still matches.**
The canvas is not caught up (§1.6): the artboards the store has moved past are retired from the comparison, and the
goldens (`viddikVerify`) are what holds every screen between changes. What a renderer changed for one screen does to
another is caught by that screen's golden; parity is left with the 49 artboards that still describe the store.

**Found in B-84: a parity number does not depend on what ran before the fixture.** The whole module rendered in
the registry's order in one JVM, by size in two forks, with every fixture in a scene of its own, and a group
alone gave the same pixels for all 161 fixtures. A number that moves between two runs moved with the code: the
Search_* artboards' 2–3 % → 5–35 % that B-75 and B-72 read as leaking state was B-77's sort control and tips as
text, measured on a branch before and after its merge. Two traps sit beside this: a recording without `--force`
keeps a golden the verification accepts, so a change below the tolerance — a placeholder's words, a glyph's cut —
leaves the old golden in the tree (24 of them on `main` at B-84); and a parity percentage compares the canvas —
since B-85 only its artboards that still describe the store (§1.6), never what the code decided past it.

**Risk 2. The browser cannot sign in with shildik's own client** (§1.4, consequence 5).
Mitigation: B-12 starts by proving the authorisation-code flow with PKCE from wasmJs against a local
shildik before any UI is built on it. **Settled in B-12** (D5): kotlin-multiplatform-oidc in a popup.
What is proven and where: the flow with PKCE against the published shildik image, played over HTTP by
the server's suite (`ShildikHarness`, every identity scenario signs in through it); the library's own
half — the popup, the redirect page, the exchange from a page — by hand on 2026-10-08, in a headless
Chrome for Testing driven over the DevTools protocol against the bundle `installDist` serves, the
same image and a fresh PostgreSQL: the guest's «Sign in» opened shildik's page in a popup, the popup
came back through `signed-in.html` and closed, the page exchanged the code (`200` from the token
endpoint, cross-origin), merged the guest cart, and redrew the header as «Maya» with the merged count.
No automated test covers that half; shildik's acceptance runs this library on the JVM only. The
same stand walked the popup's other endings in B-46 (closed, blocked, pressed twice), before and
after the storefront started watching the popup itself.

**Risk 3. Fixtures and server disagree.** If the parity fixture is a hand-built tree, a screen can
match the canvas while the server builds something else. Mitigation (*hypothesis*): the fixture for
each artboard is the server's recorded body for the sample data (kompot-studio records bodies), so
the screenshot tests the tree and the renderer together. Settled in B-04.

**Found in B-05: a parity fixture cannot be a recorded server body.** The canvas shows a catalogue
at marketplace scale — «12,408 items», «517» pages, «2,341 reviews» on every product — and the seed
holds about two thousand products. A tree the server builds from the seed is therefore not the tree
an artboard draws, and comparing the two would measure the seed. So: the screenshot fixtures are
bodies **in the wire shape**, written with the canvas's copy and decoded through the app's registry
(as B-04 does), and the server's trees are held by the server's own tests, which assert their
structure and the scenario values. The hypothesis above is refuted for screens; it stands for nothing.

**Decided in B-05: facets are counted in memory.** One category's products are read once and
filtered per facet in Kotlin (`Browse`); a facet's count leaves its own filter out, and every value
the category has is listed, with 0 when the other filters rule it out — the facets of an empty
result are what the shopper undoes it with. A leaf of the seed holds tens of products; this stops
being the right shape at thousands per category, and then the counts move into SQL.

**Risk 4. zavarnik's training needs the application to start, and the application needs
PostgreSQL.** An image build without a database trains nothing or fails. Mitigation (*hypothesis*):
train against a throw-away PostgreSQL in the build and verify the cache is accepted on the real
start; settled in B-03.

**Settled in B-03.** `docker/Dockerfile` trains in a stage built from the same JRE image as the final
one, with PostgreSQL installed into that stage alone and started for the training run; the final
stage copies only the application. Measured on the build that closed B-03: the training run was
ready in 2,029 ms with the seed included, 3,851 of 3,852 application classes came from the cache,
and the final image started against a separate PostgreSQL with `-XX:AOTMode=on` (which refuses to
start on a rejected cache) serving all 43 of the server's own classes from it —
`scripts/image-check.sh`, which CI runs. Not measured: the start-up time against a run without the
cache; that is a number for when there is a hot path worth timing.

**Risk 5. The stand measures nothing.** The sibling reference services run without traffic, so no
number about petich, tracy or metrik can be taken on them. Mitigation: synthetic shoppers (B-31), in the chart
since B-31 and on the stand since v0.3.0 (`shoppers.enabled` in the stand's values).

**Open question 1. Search quality on PostgreSQL full text with `pg_trgm`.** *Settled for the seed in
B-09; open beyond it.* Search matches a product when its `to_tsvector('english', listing name, brand,
kind)` — the listing name being the title when the product has none (B-45) — matches every typed word as
a prefix (`running:* & sh:*`), or its lower-cased title or listing name contains the query (`LIKE`, under
trigram indexes); query suggestions are category, brand and kind names that start with the query, then
those with a word that does, then the ones `similarity()` finds (a misspelling — «heaphones» is offered
«headphones»). The indexes are in `V3__search.sql` and `V25__listing_name.sql`
(`server/src/main/kotlin/io/github/youndie/haul/feature/search/data/PostgresSearchRepository.kt`).

Measured with `scripts/suggest-latency.sh` on 2026-10-08: the server from `installDist` on the JVM
(JDK 25.0.4, no AOT cache, CIO) and PostgreSQL 18.6 (`postgres:18-alpine`, default configuration) in
Docker, both on the shared Linux build machine (WSL2, 20 cores, 16 GB; load average 3–3.6 during the
runs, other workloads on it), over loopback; the seed of 1,920 products. One client, sequential, one
kept-alive connection; the URL list is every prefix from two characters of seven queries («running
shoes», «headphones», «sony», «wireless earbuds», «yoga mats», «heaphones», «xqzt»), 58 requests a
pass; one pass discarded as warm-up, 20 kept. Two runs:

| Run | n | cold first request | p50 | p90 | p99 | max |
|---|---|---|---|---|---|---|
| 1 | 1,160 | 109.2 ms | 17.57 ms | 22.00 ms | 25.38 ms | 31.23 ms |
| 2 | 1,160 | 129.3 ms | 17.98 ms | 20.72 ms | 24.43 ms | 29.53 ms |

Two to four characters and five or more did not differ (p50 17.4–17.5 against 17.6–18.2 ms). Inside
that, PostgreSQL spends about 7.4 ms on the matching statement and 4.7 ms on the terms statement, with
2–2.6 ms of planning each (`EXPLAIN ANALYZE`, «running sh»). **The planner uses neither index at this
size**, before and after an explicit `ANALYZE`: it scans 1,920 rows and computes the `tsvector` per
row. So the number says the seed is served comfortably, and nothing about the indexes; at marketplace
scale the next step is a stored `tsvector` column and a prepared statement, measured the same way.

---

## 4. What happens next

The order of work and the acceptance criteria are in the [backlog](../../backlog.md). First: the
scaffold and the CI that builds the server and the bundle (B-02), then the schema, the seed with the
sample data and the image with its cache (B-03), then the theme and the two components every screen
uses (B-04). The feature, screen, endpoint and service documents are drafted in an open pull request
and go `active` one by one as the code behind each lands.

---

## 5. Vocabulary

The names go into the code unchanged.

| Entity | Identified by | Owned by | Notes |
|---|---|---|---|
| `Category` | slug (`headphones`) | — | a tree, three levels; 32 top-level |
| `Seller` | server id | — | seed data; rating, positive share, years on Haul |
| `Product` | server id | `Seller` | title, brand, listing name (what cards, cart lines and order lines write; the title when unset, B-45), category, description, specifications, rating summary |
| `Sku` | server id | `Product` | one combination of options (colour × bundle); price, old price, stock; the campaign whose price it is (B-53) |
| `Campaign` | slug | — | home banners and sale windows; a Plus early-access start |
| `Deal` | server id | `Sku` | a price of its SKU for a window, `starts_at` to `ends_at` (B-57) |
| `Customer` | the shildik `sub` claim | — | name, Plus membership, points balance |
| `Guest` | server-issued id | — | owns a cart until sign-in |
| `Address` | server id | `Customer` | street, apt, city, ZIP, door code, courier note |
| `PaymentMethod` | server id | `Customer` | `card`, `haul_pay`, `pay_on_delivery` |
| `Cart` / `CartLine` | owner; line by `Sku` | `Customer` or `Guest` | quantity 1…10, selected flag |
| `PromoCode` | code | — | one per order |
| `SavedItem` | (`Customer`, `Product`) | `Customer` | the price at saving time |
| `ProductView`, `RecentSearch` | (`Customer`, …) | `Customer` | last 20 views, last 10 searches |
| `Order` | `HL-<5 digits>` | `Customer` | status derived from its shipments; a copy of the address it was placed to; its lines keep line totals (price × quantity), not unit prices (B-91) |
| `Shipment` | server id | `Order` | one per seller: `placed`, `packed`, `in_transit`, `ready_for_pickup`, `delivered`, `picked_up`, `cancelled` |
| `DeliveryMethod` | closed set | — | `courier`, `pickup_point`, `parcel_locker` |
| `PickupPoint`, `DeliverySlot` | server id; (date, window) | — | slots: next 5 days × 4 windows, with capacity |
| `Return` | server id | `Order` | `requested`, `picked_up`, `refunded` |
| `Review`, `Question` | server id | `Product`, author `Customer` | answers are seed data only |
| `Membership` | `Customer` | `Customer` | Haul Plus: `trial`, `active`, `none` |
| `PointsEntry` | server id | `Customer` | earned, redeemed, returned, reversed |
| `InstalmentPlan` | `Order` | `Customer` | Haul Pay: 4 charges two weeks apart |

Tenancy: none. A customer sees only their own cart, orders, saved list, views and searches; «not
yours» answers `404`, the same as «does not exist».

## 6. Sample data

The values every artboard, preview, scenario and screenshot fixture shows. Seeded alongside a larger
generated catalog; the canvas's counts (12,408 items, 14,870 results) are copy for fixtures, not a target
for the database. The canvas's «Search 2.4 million products» is not drawn at all (B-107).

| Entity | Values |
|---|---|
| Now | 2025-10-07 19:47:23 America/New_York (Tuesday) |
| `Customer` | Maya Kowalski — Plus since 2023, renews 2025-11-02, 2,480 points, $186 delivery savings, 48 saved, 6 price drops; Sam Ortiz — no membership, 0 points, one delivered order; Jordan Lee — joined Oct 2025, no orders (the canvas's `Account_NoOrders`; not seeded) |
| `Address` | 148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211 |
| `PaymentMethod` | Card ···· 4821, expires 08/28 (approves); test card ···· 0002 (declines) |
| `PickupPoint` | 214 Bedford Ave, 240 m, open until 21:00; 96 N 6th St, 650 m, open until 22:00; 315 Grand St, 900 m, open until 20:00; lockers «Wythe & N 7th», 180 m, and «Bedford Ave station», 700 m, 24/7 |
| `Seller` | Sonora Audio — 4.9, 98 %, 6 yrs; Brooklyn Home Co. — 4.8, 97 %, 3 yrs |
| `Product` | Sonora SH-718N — listed as «Sonora SH-718N Wireless Noise Cancelling Headphones» on cards, in the cart and on the order, «SH-718N Wireless Noise Cancelling Headphones» under the brand on its page (B-45); $349, was $449, −22 %, 4.8, 2,341 reviews, 86 questions; Midnight Black, Silver (out of stock); bundles Headphones only / + Travel case / + 2-year care; description headline «Silence, tuned to you», accent «to you»; «12K bought this month» from a seeded base of 12,340 (B-52) |
| `Cart` (Maya) | the headphones $349, Linen Duvet Cover Set Queen Oat $139 (was $179), Stoneware Mug 12 oz Sage set of 2 $24; Items $652.00, Discount −$140.00, Delivery Free, Total $512, 1,024 points; with points −$24.80 → $487.20 |
| `PromoCode` | `AUTUMN10` — 10 % off items up to $50, 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211 (Oct 5, $512.00, in transit); #HL-47960 (Oct 3, $58.00, ready for pickup, code 4821, held until Oct 10); #HL-46102 (Sep 24, $103.00), #HL-45277 (Sep 11, $299.00), #HL-42860 (Aug 12, $42.00) delivered; #HL-44019 (Aug 30, $87.50) returned; #HL-48302 placed from the checkout fixture; #HL-48303 cancelled, card ···· 0002; Sam's #HL-45890 (Sep 18, $103.00) delivered. None is seeded: the fixture tests write them (`server/src/test/kotlin/io/github/youndie/haul/testing/SampleOrders.kt`) |

The canvas's *canvas/canvas.json* and the artboards hold the rest of the copy (product lists,
campaigns, reviews); the fixtures take it from there, not from this table.

**The brands and sellers are invented** (B-166). The canvas sold real companies' headphones under their names —
Sony, Bose and Marshall, from «Sony Official Store», «Bose Store» and «Marshall Store» — beside invented reviews and
prices, and its brand facet listed JBL, Apple and Sennheiser; the store now sells Sonora, Bellwave and Mallard
headphones from Sonora Audio, Bellwave Store and Mallard Store, with invented model numbers («Sonora SH-718N»), the
codec LC3 for LDAC, and Juno, Aspen and Halvorsen in the facet. Prices, categories, pictures and reviews stayed: each
renamed product id keeps its old id's `String.hashCode()` modulo 2,991, which is what the seed's picture variant
(3) and generated reviews' copy (997) are chosen by, and sorts where the old one did among every other id — the model
numbers are what make both hold. V31 renames a catalogue seeded before; `InventedBrandsTest` looks for the removed
names in every seeded row, the server's code and the client's bodies and fixtures. The canvas and its references
keep the old names (they are historical, §1.6); the migrations before V31 and the backlog's done items, which record
what was, do too.

The headphones' histogram on Product_Reviews (78 / 14 / 4 / 2 / 2 % of 2,341) averages 4.6, not the 4.8
every artboard writes. The seed kept both as drawn until B-86, which made the rating the histogram's average: the
seed's bars are 84 / 12 / 2 / 1 / 1 % (1,966 / 281 / 47 / 24 / 23), a 4.8, and Product_Reviews is the one artboard
pair whose bars the store does not draw. Since B-99 the seed stores every one of the 2,341 reviews and 86
questions, the histogram is counted from them, and the canvas's two reviews and three questions are the newest of
each.
