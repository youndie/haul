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

**Consequence 2.** A server built by kompot has to run on the JVM (D1). The browser client and
the screenshot tests share one renderer: `kompot-client` on wasmJs ships, the same on
`jvm("desktop")` draws the references the parity check compares against.

**Consequence 3.** Live order tracking through `kompot-realtime-server` is available on the JVM;
it is still a later item (B-29), because v1 refreshes the order screen on open.

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
`/api/v1/...` answer a kompot action (refresh, navigate, show a route over the screen) or an error.
The client renders the tree through a registry of Haul components (`HaulHeader`, `ProductCard`,
`CartLine`, `OrderProgress`, …) declared once in `shared`, and draws `Loading` and `Error` itself.

Why:

- it is the stack's showcase: the storefront is the case backend-driven UI is built for
  (campaigns, deals, the Plus block change without a client release);
- one renderer per component gives the canvas one drawing per piece, so parity is checked per
  component as well as per screen;
- rejected: JSON endpoints per screen and Compose layouts in the client — a second contract for the
  same screens, and the server would no longer own what the shopper sees.

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

### D5. Sign-in through shildik; guests can browse and fill a cart

Decision: a guest has a server-issued id (`X-Haul-Guest`) and a cart; checkout, saving, reviews and
the account need a shildik token; signing in merges the guest cart.

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

### D7. Numbers the canvas implies, fixed here

Delivery is free over $35 or for Plus, otherwise $5.99; 1 point per whole dollar, ×2 for Plus,
100 points = $1, credited on delivery; courier cut-off 23:30 local (so «Order within 3 h 42 min»
at the canvas's «now»); deals of the day end at local midnight; a pickup is held 5 days; returns
within 30 days of delivery. These are decisions, not observations; the feature documents' scenarios
are written against them and verified against the code before they go `active`.

### D8. Product photography is out of v1

The canvas marks photos as striped placeholder tiles (tone + label), and v1 ships those tiles.
Images through object storage are B-30.

### D9. No server rendering, no SEO

A Compose canvas page is not indexable and its first load is dominated by the runtime. Accepted for
a reference; the first-load size is measured (B-28), not assumed.

### D10. Documentation in English

As the sibling reference projects; this repository is read from outside.

---

## 3. Risks and open questions

**Risk 1. The canvas and the code drift apart without anyone seeing it.** A renderer changed for one
screen changes every screen that uses the component. Mitigation: one reference PNG per artboard
(125), exported from the canvas into the client's snapshot directory, and `viddikDesignParity` in
every client item's acceptance. Open: the tolerance, set from the first measured screen.

**Risk 2. The browser cannot sign in with shildik's own client** (§1.4, consequence 5).
Mitigation: B-12 starts by proving the authorisation-code flow with PKCE from wasmJs against a local
shildik before any UI is built on it. Open: which client library, or a hand-written flow.

**Risk 3. Fixtures and server disagree.** If the parity fixture is a hand-built tree, a screen can
match the canvas while the server builds something else. Mitigation (*hypothesis*): the fixture for
each artboard is the server's recorded body for the sample data (kompot-studio records bodies), so
the screenshot tests the tree and the renderer together. Settled in B-04.

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

**Open question 1. Search quality on PostgreSQL full text with `pg_trgm`.** *Hypothesis:* enough for
a seed of ≈ 2,000 products; the suggest latency is measured in B-09.

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
| `Product` | server id | `Seller` | title, brand, category, description, specifications, rating summary |
| `Sku` | server id | `Product` | one combination of options (colour × bundle); price, old price, stock |
| `Campaign` | slug | — | home banners and sale windows; a Plus early-access start |
| `Deal` | server id | `Sku` | a price that ends at a fixed instant |
| `Customer` | the shildik `sub` claim | — | name, Plus membership, points balance |
| `Guest` | server-issued id | — | owns a cart until sign-in |
| `Address` | server id | `Customer` | street, apt, city, ZIP, door code, courier note |
| `PaymentMethod` | server id | `Customer` | `card`, `haul_pay`, `pay_on_delivery` |
| `Cart` / `CartLine` | owner; line by `Sku` | `Customer` or `Guest` | quantity 1…10, selected flag |
| `PromoCode` | code | — | one per order |
| `SavedItem` | (`Customer`, `Product`) | `Customer` | the price at saving time |
| `ProductView`, `RecentSearch` | (`Customer`, …) | `Customer` | last 20 views, last 10 searches |
| `Order` | `HL-<5 digits>` | `Customer` | status derived from its shipments |
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
| `Customer` | Maya Kowalski — Plus since 2023, renews 2025-11-02, 2,480 points, $186 delivery savings, 48 saved, 6 price drops; Sam Ortiz — no membership, 0 points, one delivered order |
| `Address` | 148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211 |
| `PaymentMethod` | Card ···· 4821, expires 08/28 (approves); test card ···· 0002 (declines) |
| `PickupPoint` | 214 Bedford Ave, 240 m, until 21:00; 96 N 6th St, 650 m, until 22:00; locker «Wythe & N 7th», 24/7 |
| `Seller` | Sony Official Store — 4.9, 98 %, 6 yrs; Brooklyn Home Co. — 4.8, 97 %, 3 yrs |
| `Product` | Sony WH-1000XM6 — $349, was $449, −22 %, 4.8, 2,341 reviews, 86 questions; Midnight Black, Silver (out of stock); bundles Headphones only / + Travel case / + 2-year care |
| `Cart` (Maya) | the headphones $349, Linen Duvet Cover Set Queen Oat $139 (was $179), Stoneware Mug 12 oz Sage set of 2 $24; Items $652.00, Discount −$140.00, Delivery Free, Total $512, 1,024 points; with points −$24.80 → $487.20 |
| `PromoCode` | `AUTUMN10` — 10 % off items up to $50, 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211 (Oct 5, $512.00, in transit); #HL-47960 (Oct 3, $58.00, ready for pickup, code 4821, held until Oct 10); #HL-46102, #HL-45277, #HL-42860 delivered; #HL-44019 returned; #HL-48302 placed from the checkout fixture; #HL-48303 cancelled, card ···· 0002 |

The canvas's *canvas/canvas.json* and the artboards hold the rest of the copy (product lists,
campaigns, reviews); the fixtures take it from there, not from this table.
