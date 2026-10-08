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

**Decided in B-11, the guest and the cart.**

- A guest id is `g-` and a random UUID from `POST /api/v1/guests` (`201`), stored in `guests`; it is
  the guest's only credential, so it is unguessable. A cart route with no `X-Haul-Guest`, or one the
  server never issued, answers `401 unauthenticated`: the client creates a new guest rather than the
  server inventing one.
- The cart's commands keep the methods and JSON bodies of endpoint-cart (`PUT`/`DELETE` with
  `LineChange`, `LinesRemoval`, `PromoEntry` from `shared/.../feature/cart/`) and answer kompot's
  `refresh`. Their paths are the server's strings: each component of the tree carries the URL its
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

**How the stand is built (B-27).** One image serves the page and the API: the server's distribution
carries the browser bundle and serves it at `/`, so the two cannot be deployed at different versions
and the client needs no base URL. The chart (`charts/haul/`) holds the server, its PostgreSQL as one
pod with one volume, and Traefik IngressRoutes for the host, the shape the sibling reference services
are deployed in; metrik, tracy and katcher are wired in the server and each switched on by an endpoint
and a key. The host, `haul.kotlin.website`, is the siblings' convention and an assumption until the
first deploy.

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
shildik before any UI is built on it. Open: which client library, or a hand-written flow.

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
B-09; open beyond it.* Search matches a product when its `to_tsvector('english', title, brand, kind)`
matches every typed word as a prefix (`running:* & sh:*`) or its lower-cased title contains the query
(`LIKE`, under a trigram index); query suggestions are category, brand and kind names that start with
the query, then those with a word that does, then the ones `similarity()` finds (a misspelling —
«heaphones» is offered «headphones»). Both indexes are in `V3__search.sql`.

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
| `Product` | Sony WH-1000XM6 — $349, was $449, −22 %, 4.8, 2,341 reviews, 86 questions; Midnight Black, Silver (out of stock); bundles Headphones only / + Travel case / + 2-year care; description headline «Silence, tuned to you», accent «to you» |
| `Cart` (Maya) | the headphones $349, Linen Duvet Cover Set Queen Oat $139 (was $179), Stoneware Mug 12 oz Sage set of 2 $24; Items $652.00, Discount −$140.00, Delivery Free, Total $512, 1,024 points; with points −$24.80 → $487.20 |
| `PromoCode` | `AUTUMN10` — 10 % off items up to $50, 2025-10-07…14; `SUMMER5` — expired 2025-08-31 |
| `Order` | #HL-48211 (Oct 5, $512.00, in transit); #HL-47960 (Oct 3, $58.00, ready for pickup, code 4821, held until Oct 10); #HL-46102, #HL-45277, #HL-42860 delivered; #HL-44019 returned; #HL-48302 placed from the checkout fixture; #HL-48303 cancelled, card ···· 0002 |

The canvas's *canvas/canvas.json* and the artboards hold the rest of the copy (product lists,
campaigns, reviews); the fixtures take it from there, not from this table.
