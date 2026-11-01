---
id: B-164
title: "feat: a public feed of live orders shows the saga's steps and its compensations"
status: wip
priority: P1
size: M
stage: stage-15-showcase
---

# B-164 — feat: a public feed of live orders shows the saga's steps and its compensations

The synthetic shoppers (B-31) place, pay, ship, receive and return orders on the stand all day, some of them
failing on purpose so the petich saga compensates — and nobody can see it except in tracy. That is the most
convincing thing the stand does, and it is invisible.

- **«Live orders» page, linked from the demo strip and the About page:** the last orders across the store, each a
  row of saga steps (reserve, pay, ship per seller, …) lighting up as they happen over the existing SSE bus, failed
  steps red and their compensations shown undoing them; a row opens a read-only view of that order's trail
  (`OrderTrail`). Anonymised: no names, no addresses — order number, sellers, totals, steps, times.
- Rejected: a public link into tracy (another product's UI, and a log is not a story); showing only the visitor's
  own orders (the demo shopper of B-162 has one order; the feed has hundreds).

- AC: with the shoppers running on a local stand, the page shows orders moving step by step without a reload; a
  compensated order shows the failed step and the undoing ones; no personal data in the tree (a test over it); the
  page's goldens recorded.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ops/OrderTrail.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/saga/OrderSaga.kt`.

## Findings

- **What was built.** `/live` (`StorefrontPage.Live`, tree `GET /ui/live`, `feature/live/`) in the frame, like
  `/about`: the last 20 orders across the store, newest first; each row its number, time, sellers, «N items · $X» and
  where it is, then its lanes — «The saga» (the six members of `OrderSaga.kt`, now named once in `OrderSagaMembers`),
  one per seller's shipment, «The return» — and, opened in place, its trail with every step's time and the saga's
  members by their keys. The intro says the rows are the petich saga of every order and links to `OrderSaga.kt` on
  GitHub. Linked from the About page («Watch the live orders») and the demo strip («Live orders»; it fits the strip's
  line at 1440 and its links' line at 390). The contract gains `LiveOrdersBody` (`haul_live_orders`) and its rows.
- **The rows are read off the order's own rows, not off `OrderTrail`.** The trail goes to tracy only (B-109); reading
  tracy from a public page puts another product's API in the request, and writing the trail a second time into the
  database puts a write in every saga member's path, where B-139/B-140 bound a member's time. A placed order ran every
  member, a declined card refused at `authorise-payment` and its compensation undid the members before it that held
  something; a member with nothing to take is drawn as such. The one thing a row cannot say is which member after the
  order opened failed in an order cancelled `failed` — the row says the saga failed there and tracy has the member.
  The saga's steps carry the placement's moment: they run inside the request, in milliseconds. Research «Decided in
  B-164».
- **Expanded in place, not a page of its own:** a trail is a dozen lines, the rows stay live while one is open, and
  a page per order would be a second order page with the customer's rules to keep apart, plus a second channel.
- **Anonymised by construction:** `ExposedFeed` selects no customer, address, window, pickup point or code;
  `LiveRoutesTest` reads every such value the orders hold off the database and finds none in the body.
- **Load.** Five statements whatever the store holds, through a new index `orders_placed_at (placed_at, id)` (V32);
  held by counting statements over 2,001 orders (≤ 10 for the whole page, header included) and a loose 2 s. The live
  channel reuses the order page's transport — the same broadcaster and `GET /ui/updates`, topic `live:orders` — and
  draws the body once for every listener, at most once a second, never for nobody; a connecting listener gets the last
  drawing while nothing moved; at most 200 listen, past that `503 unavailable` and the client's own backoff
  (`LiveFeedTest`).
- **`GET /ui/updates` moved from the customer tier to the public one**, the topic deciding the tier: an order's topic
  is still `401` without a sign-in and `404` for another's (`LiveOrderTest`, `LiveUpdatesTest`).
- **The migration is V32**: V31 is taken by B-166's branch (and by B-162's). Whichever of them lands second
  renumbers; Flyway refuses a lower version applied after a higher one on the stand.
- **Not drawn:** Haul Pay's four payments (the plan is the order's, not a shipment's) and the points an order earns
  or a return takes back; the clear-cart announcement. The `failed` cancellation's member, above.
- **Two goldens drift on main, not here**, as B-160 found: a forced recording changes `Search_Results` and
  `Search_Autocomplete` byte-wise on the WSL box; not brought back. The goldens brought back are the new
  `Live_Content`, `Live_Content_Phone` and the four the new links moved: `Demo_Strip`, `Demo_Strip_Phone`,
  `About_Content`, `About_Content_Phone` — each looked at.
