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
doubling to thirty) rather than reloading the page. The bus is in memory: one process serves the
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
  directly). At the line's limit «Buy now» only selects the line; out of stock neither is offered.
- A list of choices («Catalog»'s categories, the sort's orders) travels as `Link`s and opens as a
  menu in the client. No artboard draws a menu open; the menu is drawn from the theme's tokens.
- «Show 24 more» opens the next page, the same address as the page number. Since B-62 the shell keeps
  the page and its scroll across a new address of the same path, and since B-63 the press is a `load` whose
  answer replaces the results where the shopper is; appending to the grid instead is still not done — the
  server sends each page of results whole.
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
trial's dialog for a customer who is not a member, `/account` for a member, sign-in for a guest.

- **The one control wired in the client alone is the filter sheet's.** The sheet is the facets already
  in the tree, drawn over the page on a phone; «Filters» opens it and «×» closes it without asking the
  server, because there is nothing for the server to decide and no address to change. «Show N items»
  closes it too (B-54): each press inside the sheet already opened the page those results are on. The
  sheet is held by the shell above the page
  (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt`), so
  it stays open over the pages its own presses open and is drawn from each one's tree; any other new
  page closes it. It stays there after B-62 kept the page across a filter: back and forward keep the
  page too, and only the shell sees whether an arrival came from the sheet. Since B-63 the sheet's presses
  are `load`s, so it stays open by itself and is drawn from the updated results; what the shell still
  decides is that a page *visited* — a link, back, forward — closes it, and that its facets follow nothing
  while a `load` of the screen is on its way.
- **A control with no page behind it is plain text**, not a link that opens nothing: the strip's «Sell
  on HAUL», «Help» and the language, and the footer's links, until a page exists for them.

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
changed nodes since B-63.

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
| `GET /ui/parts/c/{categoryPath}?…` | public | a kind, a facet, «Show N more», an applied chip, «Clear all», a sort, a page | `title`, `kinds` (when the category has kinds), `results` |
| `GET /ui/parts/search?q=…` | public | a category chip, a page | `categories`, `grid`, `pagination` |
| `GET /ui/parts/deals?page=…` | public | a page between pages past the first; to or from the first, whose «Deals of the day» the others lack, the press is still `navigate` | `grid`, `pagination` |
| `GET /ui/parts/account/orders?status=…` | customer (`401`) | a chip of the history | `account` |
| `GET /ui/parts/account/saved?…` | customer (`401`) | a filter or a page of the Saved list | `account` |

A part is found by its id, so an id names one node of a page (kompot SPEC §4.2) — the order's live frame and the
override store rely on it too. `server/.../shell/UniqueIdsTest.kt` holds it over every page a guest and a customer
open and every `update` above, the nodes sent and the page after them; a page whose section already carries the
page's name has the root `<name>-page` (B-65: until then the account's parts were its whole page, root included).

The query is the page's own, refused the same way (`400 validation_failed`); the search's parts do not record
the search again — a `load` changes nothing. In the client (`shell/Storefront.kt`) an `update` goes into the
screen's override store and its address into the history without a load (`Navigator.record`); back and forward
to such an address are visits like any other and load its page, which replaces whatever the updates drew. The
line under the header is on while a `load` is on its way, and one whose answer does not arrive leaves the page
under B-62's notice, whose Retry presses it again; of two `load`s the last press wins (kompot's `withLoad`).

**A card's «+» and the product page's «Add to cart» answer an `update` too**: the header (its count) and the
control pressed (its next quantity), instead of `refresh`. Which node is in the command's own address, written
by the tree (`?answer=card`, `?answer=details`, and the search the header shows, `&q=`;
`feature/catalog/screen/LineAnswers.kt`); a card is built the same from its product and SKU on every page, and
the client draws a grid's cards as nodes of the tree (`LocalCardNodes`), so the `update` of one card reaches it.
The cart's own lines, «Buy now», the Saved list's cards (whose «Price dropped» mark only that page draws) and every
other command still answer `refresh`.

**Decided in B-22, a route over the screen.** A dialog the canvas draws over a page («Write a review»,
«Ask a question») is kompot's `present` of a component the server built — the form, its labels and the
URL it posts to — carried by the control that opens it, so it has no address of its own and
`StorefrontPage` lists nothing for it; a reload shows the page without it. The shell draws the presented
component over the page with the scrim, and the same handler follows its actions: `close` takes it away,
and a `sequence` is each of its actions in turn — a dialog's command answers `close`, then `refresh`.
What is typed is the client's until it is sent; the client checks the rules the server will (they are
the contract's, `shared/.../feature/reviews/`), and a refusal is drawn in the dialog. For a guest the
same control is the way to sign in instead of a `present`.

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
  to collect a day after that, collected two days later — inside the five days a point holds it. So Sony
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
  the day it was chosen for. Another customer's order and a missing one are `404 order_not_found`, which
  the client draws as Order_NotFound under the header it last drew.
- **Reorder** (`POST /api/v1/me/orders/{id}/reorder`, `feature/order/domain/Reorder.kt`) puts each SKU
  of the order into the cart through the cart's own `changeLine`, selected, at the order's quantity — at
  most ten and the stock; a line already holding as many is only selected, so a second press adds
  nothing. A SKU gone or out of stock is left out. It answers `navigate` to the cart.
- **«Orders» in the header** goes to the orders' history, `/account/orders` (B-19; until then `/account`),
  and a guest's to sign-in, as the account shortcut does. The page's «Orders» crumb goes there too.
- **The address drawn is the order's** (`NewOrder.address`, B-40's copy), never the saved address it came from,
  which the checkout edits in place.
- **«Write a review»** on a delivered line is the product page's own: B-22's review dialog, presented over the
  order (`ReviewTabs.writeReview`). «Return items» is B-21's return dialog (below).

**Decided in B-21, returns and refunds.**

- **One return per order, of whole lines**, as the dialog's checkboxes take them: `POST
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
- **What a line gives back** is its share of the items as paid — the order's items are at list price and its
  discount holds the sale and the code, so the code is shared over the lines in proportion to their price paid;
  the delivery is kept. The points taken back are the order's in proportion to the refund (the canvas's 87 × 2 =
  174); there is no points ledger yet (B-23), so they are the return's own number, drawn once refunded.
- **The order's progress** is `returning` while its return is requested or picked up and `returned` once it is
  refunded (`OrderProgress.of`, reading `Order.returnStatus`): the order page draws the return's three steps,
  the lines kept in their shipments and the returned ones in a card of their own, «Paid» net of the refund.

### D5. Sign-in through shildik; guests can browse and fill a cart

Decision: a guest has a server-issued id (`X-Haul-Guest`) and a cart; checkout, saving, reviews and
the account need a shildik token; signing in merges the guest cart.

**Decided in B-11, the guest and the cart.**

- A guest id is `g-` and a random UUID from `POST /api/v1/guests` (`201`), stored in `guests`; it is
  the guest's only credential, so it is unguessable. A cart route with no `X-Haul-Guest`, or one the
  server never issued, answers `401 unauthenticated`: the client creates a new guest rather than the
  server inventing one.
- The cart's commands keep the methods and JSON bodies of endpoint-cart (`PUT`/`DELETE` with
  `LineChange`, `LinesRemoval`, `PromoEntry` from `shared/.../feature/cart/`) and answer kompot's
  `refresh` — but a card's «+» and «Add to cart», `update` (B-63). Their paths are the server's strings: each component of the tree carries the URL its
  commands go to (`CartLine.url`, `acknowledgeUrl`, `CartSelection.linesUrl`, `PromoField.url`), so
  the client builds none. kompot's own `perform` action was the alternative and was not taken: it is
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
  token, or the customer signed out when it cannot be; a guest the server forgot replaced.
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
  keeps it inside the click's activation on a slow network.

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
  09:00, 12:00, 15:00 and 18:00, three hours each, on the five days from tomorrow in the store's time
  zone whatever the items' dispatch days (the canvas offers Maya Wed 8 for a duvet and mugs that dispatch
  in a day), 20 orders a window. The delivery fee is the cart's, the same for every method.
- **Every customer pays with the simulator's cards.** v1 has no way to add a card (no artboard; D6 hides
  the account's payment methods), so the payment options are fixed: card ···· 4821 (approves), test
  card ···· 0002 (declines), Haul Pay for totals from $50 to $2,000, pay on delivery except to a locker.
  The canvas lists only the card ···· 4821 (B-15): the test card is chosen by its id
  (`CheckoutChoice.payment`) and drawn only once it is the one chosen.
- **The address form** keeps research §5's fields (street, apt, city, ZIP, door code, courier note; no
  state, so Maya's city is «Brooklyn, NY»). A form at fault is `400 validation_failed` with every field
  at fault in `ErrorBody.fields` (`field_required`, `field_invalid`), and is stored and drawn again with
  an error under each field (`Checkout_Validation`), as B-11 stores a refused promo code.
- **The canvas answered the copy (B-15).** The page is the checkout's header and one `CheckoutBody`:
  the numbered sections beside the order at 1440, under it on a phone. The address is an inline form
  holding the address delivered to, sent when the shopper leaves it; saved addresses are not listed.
  A method says when and where («Tomorrow, Oct 8», «Thu, Oct 9 · 240 m away» — a pickup a day after
  the courier, D7), the summary names the delivery day, and the button says why it is held («Pick a
  delivery window», «Fill in the street address and ZIP»). The points toggle the canvas draws is the
  server's since B-23 (below).
- **The form edits the address it holds (B-40).** A save rewrites the address delivered to — the chosen one,
  else the newest — in place, keeping its id; the server's state names it and `AddressEntry` carries no id. A
  form equal to an address the customer already has chooses that one and stores nothing. An order copies the
  address it is placed to (`orders.address`, `V14__order_address.sql`) rather than naming the row, which is
  now edited; and the quote's fingerprint names the address's fields as well as its id.

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

**Decided in B-19, the account.**

- **Two addresses, one tree.** The overview is `/account` (`StorefrontPage.Account`, tree `GET /ui/account`), the
  orders' history `/account/orders` (`StorefrontPage.Orders`, tree `GET /ui/account/orders`), which the order pages
  sit under. Both are one `AccountBody` built by `feature/account/screen/AccountScreen.kt` from the customer's own
  orders (`OrderRepository.orders`, the customer in the filter) as `OrderTracking` reads them, newest first.
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
- **What the account cannot store yet is read through ports**: the points and the membership (`Loyalty`), bound
  since B-23 to feature-membership's ledger and membership (`PlusCommands`), and the Saved list's counts
  (`SavedLists`), read from the list itself since B-20 (below). The tree carries the tiles either way, so each item
  changed the source, not the account.
- **Returned** is a state of the history (the chip, the filter), derived since B-21 from the order's return: a
  refunded one reads «Returned», one asked for and not refunded yet «Returning» — Blush like it, under the same
  «Returned» filter, with «Details» rather than «Reorder».
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
  a second press after a finished move finds no line (`404 line_not_found`).
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
  (−$40), the Bose and the Sony studio headphones at their price, and generated products picked by a fixed stride —
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
  Maya, whose orders no seed holds. Her 2,480 points are an `opening` row. Both are seeded once, also into a database
  seeded before they existed.
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
  of the SKU's price under that price — and now charged so; the Sony deal is the sale's own $349 under $449.
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
  are all left out, rather than a header over nothing.
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
  it would run on every database, seeded or not. A stand that runs for days without a restart still sees its deals
  end at midnight and the sale end on its eighth day — the next start brings them back.
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
- **The empty cart's line** («Today's deals end at midnight — up to −70 % in the Autumn mega sale.») names today's
  deals and the sale, so it is drawn only while a deal is live and the sale is live for the cart's owner; otherwise
  `EmptyState.text` is left out (it became optional on the wire), and the title and «See today's deals» stay.

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
  katcher waits for the app to be registered in it, since its key is an app's, issued there.

### D7. Numbers the canvas implies, fixed here

Delivery is free over $35 or for Plus, otherwise $5.99; 1 point per whole dollar, ×2 for Plus,
100 points = $1, credited on delivery; courier cut-off 23:30 local (so «Order within 3 h 42 min»
at the canvas's «now»); deals of the day end at local midnight; a pickup is held 5 days; returns
within 30 days of delivery. These are decisions, not observations; the feature documents' scenarios
are written against them and verified against the code before they go `active`.

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
seed draws photos for the sample products of §6 in code; real product photography is the owner's
question (B-30 findings), and until it is answered the generated catalog keeps its tiles. The fixtures
name no photo, so every artboard's parity is what it was.

### D9. No server rendering, no SEO

A Compose canvas page is not indexable and its first load is dominated by the runtime. Accepted for
a reference; the first-load size is measured (B-28), not assumed. *Confirmed in B-28* (below): skiko's
wasm alone is two thirds of the bytes and, on a throttled link, most of the wait; what Haul itself adds
is about a tenth, and the fonts another tenth.

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
  committed. Its skiko `.wasm` is byte-identical to main's (sha256 `089052ba…`).

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
- *The first frame is the page, in a fallback face.* The fonts are fetched only once the wasm runs
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

**Risk 1. The canvas and the code drift apart without anyone seeing it.** A renderer changed for one
screen changes every screen that uses the component. Mitigation: one reference PNG per artboard
(125), exported from the canvas into the client's snapshot directory, and `viddikDesignParity` in
every client item's acceptance. Open: the tolerance, set from the first measured screen.

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
number about petich, tracy or metrik can be taken on them. Mitigation: synthetic shoppers (B-31).

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
| `Order` | `HL-<5 digits>` | `Customer` | status derived from its shipments; a copy of the address it was placed to |
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
generated catalog; the canvas's counts (12,408 items, 14,870 results, 2.4 million products) are copy
for fixtures, not a target for the database.

| Entity | Values |
|---|---|
| Now | 2025-10-07 19:47:23 America/New_York (Tuesday) |
| `Customer` | Maya Kowalski — Plus since 2023, renews 2025-11-02, 2,480 points, $186 delivery savings, 48 saved, 6 price drops; Sam Ortiz — no membership, 0 points, one delivered order; Jordan Lee — joined Oct 2025, no orders (the canvas's `Account_NoOrders`; not seeded) |
| `Address` | 148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211 |
| `PaymentMethod` | Card ···· 4821, expires 08/28 (approves); test card ···· 0002 (declines) |
| `PickupPoint` | 214 Bedford Ave, 240 m, open until 21:00; 96 N 6th St, 650 m, open until 22:00; 315 Grand St, 900 m, open until 20:00; lockers «Wythe & N 7th», 180 m, and «Bedford Ave station», 700 m, 24/7 |
| `Seller` | Sony Official Store — 4.9, 98 %, 6 yrs; Brooklyn Home Co. — 4.8, 97 %, 3 yrs |
| `Product` | Sony WH-1000XM6 — listed as «Sony WH-1000XM6 Wireless Noise Cancelling Headphones» on cards, in the cart and on the order, «WH-1000XM6 Wireless Noise Cancelling Headphones» under the brand on its page (B-45); $349, was $449, −22 %, 4.8, 2,341 reviews, 86 questions; Midnight Black, Silver (out of stock); bundles Headphones only / + Travel case / + 2-year care; description headline «Silence, tuned to you», accent «to you»; «12K bought this month» from a seeded base of 12,340 (B-52) |
| `Cart` (Maya) | the headphones $349, Linen Duvet Cover Set Queen Oat $139 (was $179), Stoneware Mug 12 oz Sage set of 2 $24; Items $652.00, Discount −$140.00, Delivery Free, Total $512, 1,024 points; with points −$24.80 → $487.20 |
| `PromoCode` | `AUTUMN10` — 10 % off items up to $50, 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211 (Oct 5, $512.00, in transit); #HL-47960 (Oct 3, $58.00, ready for pickup, code 4821, held until Oct 10); #HL-46102 (Sep 24, $103.00), #HL-45277 (Sep 11, $299.00), #HL-42860 (Aug 12, $42.00) delivered; #HL-44019 (Aug 30, $87.50) returned; #HL-48302 placed from the checkout fixture; #HL-48303 cancelled, card ···· 0002; Sam's #HL-45890 (Sep 18, $103.00) delivered. None is seeded: the fixture tests write them (`server/src/test/kotlin/io/github/youndie/haul/testing/SampleOrders.kt`) |

The canvas's *canvas/canvas.json* and the artboards hold the rest of the copy (product lists,
campaigns, reviews); the fixtures take it from there, not from this table.

The headphones' histogram on Product_Reviews (78 / 14 / 4 / 2 / 2 % of 2,341) averages 4.6, not the 4.8
every artboard writes. The seed keeps both as drawn — the histogram as counts (`rating_counts`), the
average on the product — and a new review moves the average from the stored one rather than recounting
it from the histogram (B-22).
