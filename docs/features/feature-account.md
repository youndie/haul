---
id: feature-account
title: Account overview and the Saved list
type: feature
status: active
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-account
  - screen-saved
api:
  - endpoint-account
  - endpoint-saved
  - endpoint-orders
tags: []
---

# Account overview and the Saved list

## 1. Overview

One page answers «what is coming, what did I save, what do I have»: points, Plus savings, price drops, active orders, history. The Saved list holds hearted products and lines saved for later, and marks the ones that got cheaper.
> Described as built: the overview and the orders' history (B-19), the Saved list, save for later and
> price drops (B-20), the points and Haul Plus tiles read from the ledger and the membership (B-23,
> [feature-membership](feature-membership.md)), and a guest at these addresses asked to sign in (B-44).

## 2. Business rules

* the account's addresses are `/account` (the overview), `/account/orders` (the history) and
  `/account/saved` (the Saved list), each a `StorefrontPage` that reloads and links like any page; the
  order pages sit under the history at `/account/orders/{id}` ([feature-orders](feature-orders.md));
  all three are the customer's alone — a guest's request is `401 unauthenticated`, which the storefront
  draws as the sign-in prompt and returns to after sign-in (B-44);
* the menu shows Overview, Orders (with the count of active orders) and Saved (with the count saved);
  the other sections are hidden in v1 (research D6); the header's «Orders» and «Saved», the order
  page's «Orders» crumb and an order not found's «Go to your orders» lead to the history and the list;
* **active** = placed, packed, in transit or waiting at a pickup point; the overview shows up to 3 active
  orders as cards (the order page's own card: the steps and the lines' tiles, or the pickup code) and
  the last 4 of the rest, with «All orders» whenever that leaves one out;
* the history puts the orders on their way first, then newest first, and filters by its query string
  `?status=active|delivered|returned|cancelled` — none is all, an unknown status is read as all; each
  chip is a `navigate` to its own address; a return in flight reads «Returning» and a refunded one
  «Returned», both counted under `returned`;
* a row says «Track» on an order on its way, «Details» on one waiting for pickup, returning, returned or
  cancelled, and «Reorder» on one delivered or picked up ([feature-orders](feature-orders.md)'s reorder);
  a customer with no orders is sent to the deals («See today's deals») and greeted «Joined <month>»
  while a non-member has never ordered;
* the overview's tiles: Points (the ledger's balance and what it is worth), Haul Plus (the member's
  savings this year and renewal) or the trial's offer for a non-member («Try 30 days free» presents the
  trial dialog), and Price drops ([feature-membership](feature-membership.md));
* **the Saved list** is one list per customer of products — hearted ones and cart lines saved for later
  (research D6, «Decided in B-20»); a product is in it once, at the price and day of its **first** save:
  saving again changes nothing, letting go deletes the row (so a later save starts at that day's
  price), and letting go twice changes nothing; a guest has no list — the heart and «Save for later»
  lead to `/sign-in`, which draws the same page again after sign-in;
* the saved price is the cheapest in-stock SKU when saved (the card's price; the cheapest SKU when none
  is in stock); a product has **dropped** when its cheapest in-stock SKU is now below that price, and is
  marked with the difference («Price dropped −$200»); a product with nothing in stock has not dropped;
  deals are ignored; «Price drops N» on the overview counts them;
* **«Save for later» moves the line**: the product goes into the list and the line leaves the cart; the
  save comes first, so pressing again finishes an interrupted move; a press after a finished move is
  `404 line_not_found`;
* the heart is a command fixed in the tree, like the card's «+»: `PUT` keeps, `DELETE` lets go, both
  answer `refresh`; a saved heart is drawn filled in Hot on every card and on the product page;
* the Saved list is newest first, 24 to a page, filtered «All» / «Price dropped»
  (`?filter=price-dropped`) and paged (`?page=2`), each chip and page number its own address; an unknown
  filter shows everything, a page past the last shows the last, and a page that is not a number ≥ 1 is
  `400 validation_failed`.

Numbers in these rules (3 and 4 on the overview, 24 to a page) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7, and checked against the code and
the tests below.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/AccountComponents.kt` — the account's body; `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SavedComponents.kt` — the list; `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SaveCommand.kt` — the heart |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/account/` — the overview and the history (`screen/AccountScreen.kt`); `server/src/main/kotlin/io/github/youndie/haul/feature/saved/` — the list, its commands and storage; `server/src/main/resources/db/migration/V18__saved.sql`; Maya's seeded list `server/src/main/kotlin/io/github/youndie/haul/seed/SampleSaved.kt` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/` |

## 5. Scenarios (BDD / test cases)

### Scenario: Price drop counted
* **Given:** Maya saved the Robot Vacuum S8 at $499 and it now costs $299
* **When:** she opens the account
* **Then:** «Price drops» counts it (6 → 7) and the Saved list marks it «Price dropped −$200» under «All» and «Price dropped»; once it is out of stock it is no longer a drop.
* **Automated:** `SavedRoutesTest.a product cheaper than on the day it was saved is counted and marked` (`server/src/test/kotlin/io/github/youndie/haul/feature/saved/SavedRoutesTest.kt`)

### Scenario: Saved twice
* **When:** the client saves the same product twice
* **Then:** the second call returns `200` with `refresh` and the list holds it once, at the first save's price.
* **Automated:** `SavedRoutesTest.a product saved twice is in the list once`

### Scenario: Save for later moves the line
* **Given:** a line in Maya's cart
* **When:** she presses «Save for later»
* **Then:** the product is in her list and the line is no longer in the cart.
* **Automated:** `SavedRoutesTest.save for later moves a line from the cart to the list`

### Scenario: Only one's own account
* **Given:** a database holding Maya's orders and list
* **When:** Sam opens his overview, history and Saved list
* **Then:** none of Maya's orders or saved products are in them.
* **Automated:** `AccountRoutesTest.the overview draws the customer's own orders and nobody else's` (`server/src/test/kotlin/io/github/youndie/haul/feature/account/AccountRoutesTest.kt`); `SavedRoutesTest.a customer sees only their own list`

### Scenario: The history filtered by its address
* **Given:** Maya's delivered, in-transit, returned and cancelled orders
* **When:** she opens `/account/orders?status=delivered`, then an unknown status
* **Then:** the first lists the delivered orders only, each with «Reorder»; the second lists them all.
* **Automated:** `AccountRoutesTest.the history filters by the status in its address`; a returned order, `AccountRoutesTest.a returned order is returned in the history and under its filter`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1 —
  the hidden menu sections, a returns list in the account, price-drop notifications outside the app.

## 7. Quirks

* A non-member's avatar is the tone their reviews are signed with (`ReviewCommands.avatarTone`), not the
  canvas's (Sam's `#E9F0D2` for `#E0EEF7`); no rule over the canvas's people gives both.
* The canvas has no artboard for an overview with no past orders, a history filter that matches nothing
  («No cancelled orders.») or an active order on its way to a pickup point; each is drawn from the
  nearest artboard's pieces.
* The orders of research §6 are not seeded (B-18's reason, [feature-orders](feature-orders.md)); the
  account's artboards are drawn from fixtures. Maya's 48 saved and 6 price drops are seeded rows; the
  canvas's Saved cards are products the seed does not sell, so the page the stand shows her is her
  seeded list.
* Two saves in one store second tie and fall back to product order.
* A guest's heart is not remembered across sign-in: after it the page is drawn again and the heart has
  to be pressed again.
