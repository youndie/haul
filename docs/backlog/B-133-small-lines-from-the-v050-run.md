---
id: B-133
title: "fix: the small lines the v0.5.0 run noticed"
status: done
priority: P3
size: S
stage: stage-12-qa-run
---

# B-133 — fix: the small lines the v0.5.0 run noticed

Found outside the catalogue by its v0.5.0 run ([run](../qa/runs/2026-10-12-v0.5.0.md)), each small:

- «1 items»: a category or filter result with one product titles «1 items», and the sheet's button would read «Show
  1 items» (`CatalogScreen.kt`); the cart already writes «1 item».
- A press on the words «from» / «to» inside a price box does not focus its field (the field is only the box's right
  two thirds); the keys typed after it are lost.
- At machine speed, leaving «from» for «to» and pressing blank space within one frame wait still sends the same
  price load twice, the first aborted (B-118 meant one).
- An order's shipment line reads «TOMORROW, OCT 13 · 12:00 – 15:00» while packed and «TOMORROW · 12:00 – 15:00» in
  transit — the date drops mid-order.
- At 390 a returned line's «Midnight Black · Headphones only · 1 × $349» breaks between «1» and «× $349».
- The cold `/sign-in` page's phone menu lists the ten row categories, not the 32 (its picker presses nothing — as
  decided in B-66; the menu's list is worth the same answer as the other pages').

- AC: each behaves as its line says, or the line is recorded as decided otherwise with its reason; a test per line
  where it is code's logic.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FacetPanelView.kt`.

## Findings

- **«1 items»** — `CatalogScreen` wrote `"${count(n)} items"` on the title and on the sheet's button. A shared
  `items(n)` (`catalog/domain/Formats.kt`) says «1 item» / «Show 1 item», as the cart does; `CatalogRoutesTest`
  («a result of one product is one item») finds a brand the facet counts one product of and reads both. The deals'
  «N items on sale» (`DealsScreen`) has the same form and is left as it is: the line names the catalog's result.
- **The words in a price box** — the field was only the `BasicTextField` right of «from» / «to», so a press on the
  word reached nothing (and, with the other field focused, counted as a blank press). The whole box now takes a tap
  and focuses its field (`PriceInput`, a `FocusRequester` and `detectTapGestures`, no semantics added — B-131 names
  the fields). `PriceRangeTest` («a press on a price box's word focuses its field»): «from» pressed is focused and
  takes the bound typed next, «to» pressed takes the focus and applies it once.
- **A double load at machine speed** — B-118's one-confirm rule holds within the fields, but a blank press waits
  two frames of its own (B-76) before it takes the focus off «to», so «to»'s confirm started after «from»'s had
  sent its load and, the answer not back yet, read the same two bounds and sent them again. Not seen with the
  test's instant transport, whose answer redrew the range (and dropped the second confirm) first: the new test
  («leaving both price fields at once sends one load») holds the part's answer back 200 ms, as a network does, and
  saw the two loads. `PriceRange` now keeps the bounds it sent until its answer redraws the range; a confirm of the
  same bounds with nothing edited sends nothing. An edit (a digit typed, a thumb taken) lets the same bounds go
  again, so a load that failed is retried by typing the bound once more.
- **The shipment date** — not a decision on record: B-18 followed the canvas, whose Order_Placed draws «TOMORROW,
  OCT 8 · …» and Order_InTransit «TOMORROW · …». `OrderScreen.eta` now says tomorrow with its date in both; the
  fixtures `order_in_transit.json` and `order_haul_pay.json` (whose two shipments, one packed and one in transit,
  read the two forms side by side) follow, `OrderFixturesTest` holds it. Recorded in research (B-18's decisions)
  and added to B-85's list of places the client moved past the canvas.
- **«1 × $349» at 390** — the details joined the quantity, «×» and the price with plain spaces. They are held by
  no-break spaces now (`OrderScreen.details`), so a line breaks only between its « · » parts; the nine order
  fixtures and `LineOfSeveralTest` carry them. The bundled Archivo has the glyph (the shell's «didn’t load» already
  used one), and no golden moved anywhere but where a line broke differently.
- **The cold `/sign-in` page's menu — decided otherwise, left.** The 32 top-level categories and their addresses
  are the server's: they reach the client only in a tree's header or in the header alone (`GET /ui/header`,
  B-105). The page landed on cold asks for none (B-66, B-105: a way through, a customer sent on at once), so it
  draws the client's own `SHELL_HEADER`, whose ten words each lead to the catalog's root, where all 32 are.
  Baking the 32 into the client would copy the seed's categories into the bundle (research D2: the server draws).
  Recorded in research beside B-105's header alone. If the owner wants the 32 there, the narrow way is a request
  on the menu's press, not on the page's load — not done here.
- **Goldens** (`viddikRecord --force` in a copy outside the replica on the Linux box; six moved, each looked at):
  Order_InTransit, Order_InTransit_Phone, Order_HaulPay, Order_HaulPay_Phone — the date back on the in-transit
  line; Order_Placed_Phone, Order_Cancelled_Phone, Order_InTransit_Phone, Order_HaulPay_Phone — the Sony line
  breaks before «1 × $349» instead of inside it. Parity (`viddikDesignParity --component "Order*"`, against the
  same run with main's fixtures): InTransit 2.21 → 2.23 %, InTransit_Phone 4.35 → 4.46 %, Placed_Phone 5.27 →
  5.30 %, Cancelled_Phone 8.50 → 8.54 % (both already over before this item, B-85); tolerance untouched.
- **Mutations**, each red then restored: `items` always plural (the one-item test), the in-transit «Tomorrow»
  back (`OrderFixturesTest`: in_transit and haul_pay), the no-break space a plain one (`OrderFixturesTest`, both
  `LineOfSeveralTest` cases), the box's tap removed (the word test, not focused) and the sent-bounds check removed
  (the double-load test, two loads).
- **Verified** on the Linux box: `:composeApp:wasmJsBrowserDistribution`, `check :server:installDist` (server and
  client tests, `viddikVerify`, ktlint) and `scripts/e2e.sh` against the image built from this branch, all green.
  Not walked in a browser: the price box's presses and the frame race are held by the desktop tests over the
  storefront, the server's strings by the route and fixture tests.
