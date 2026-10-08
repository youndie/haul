---
id: B-19
title: "server + client: Account overview and orders history"
status: done
priority: P1
size: M
stage: stage-6-account
blocked_by: [B-01, B-18]
---

# B-19 — server + client: Account overview and orders history

The account overview answers «what is coming, what did I save, what do I have».

Feature: `feature-account` — its scenarios are this item's acceptance where it names them.

- Not covered: menu sections hidden in v1 (research D6).

- AC: parity for every `Account_*` artboard.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/account/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/`.

## Done (2026-10-08)

- **The addresses.** The overview is `/account` (`StorefrontPage.Account`, tree `GET /ui/account`), the history
  `/account/orders` (`StorefrontPage.Orders`, tree `GET /ui/account/orders`), which B-18's order pages sit under; the
  header's «Orders», the order page's «Orders» crumb and an order not found's «Go to your orders» go there
  (`Frame.ORDERS`). The history's filter is its query string, `?status=active|delivered|returned|cancelled`, none
  for all and an unknown one read as all: each chip is a `navigate` to its own address, so a filtered history reloads
  and links like any page. Recorded in research D6, «Decided in B-19».
- **The contract** (`shared/.../ui/AccountComponents.kt`): one `AccountBody` — the profile, the menu (Overview,
  Orders with the active orders' count, Saved with the saved count; the other sections hidden, research D6), the
  title, the overview's tiles (`AccountTileKind`: Points, Plus, PlusOffer, PriceDrops), `ActiveOrders` and the
  `OrderHistory` (filters, rows, the empty state, the sentence for a filter that matches nothing).
- **The server** (`feature/account/`): `AccountScreen` builds both pages from the customer's own orders —
  `OrderRepository.orders`, the customer in the query's filter, newest first — as `OrderTracking.of` reads them.
  Active (placed, packed, in transit, waiting at a point) orders are cards drawn by the order page's builder
  (`OrderScreen.card`): the meta line, «Arriving *tomorrow*, 15:00 – 18:00» with the steps and the lines' tiles, or
  «Ready for *pickup*» with the point, «kept until» and the code; at most three. The overview's history is the last
  four of the rest, «All orders» whenever that leaves one out; the history page puts the orders on their way first,
  then newest first. A row says «Track» (on its way), «Details» (waiting, returned, cancelled) or «Reorder»
  (delivered, picked up: B-18's `POST /api/v1/me/orders/{id}/reorder`). A guest is `401 unauthenticated` on both, as
  before (B-44's client now starts sign-in on it). `Customer` carries `joined` (the row's `created_at`).
- **Placeholders for B-23 and B-20.** Points, the membership and the Saved list's counts have no store: the account
  reads them through `Loyalty` and `SavedLists` (`feature/account/domain/Account.kt`), bound to
  `seed/SampleLoyalty.kt` — research §6's numbers for Maya (2,480 points «Worth $24.80 on your next order», Plus
  since 2023, «$186 Saved on delivery this year · renews Nov 2», 48 saved, 6 price drops), none for anybody else. The
  tree carries the tiles either way, so those items bind their own source (the notes in B-20 and B-23). «Try 30 days
  free» has no action, as the home page's offer has none, until B-23's trial; «Saved» has none until `/saved` is a page.
- **Returned** is a state of the history (chip, filter, «Details») that nothing derives: no order can be returned
  until B-21. The `Account_Orders` and `Account_Content` bodies draw #HL-44019 returned from the fixture.
- **The orders are not seeded.** Research §6's history (#HL-48211, #HL-47960, #HL-46102, #HL-45277, #HL-44019,
  #HL-42860 for Maya; #HL-45890 for Sam) stays fixture-only, as B-18 left it: seeding it would add products and
  sellers the seed does not sell (the lines' foreign keys), and the fulfilment simulator would move a seeded order
  in transit along on the stand. B-18's in-memory orders moved to `server/src/test/.../testing/SampleOrders.kt`,
  shared by `OrderFixturesTest` and `AccountFixturesTest`; B-22's own #HL-46102 is inserted by its test into a
  database of its own, so nothing collides. Jordan Lee is not seeded either: any new customer is Jordan.
- **The client** (`composeApp/.../feature/account/AccountViews.kt`): `AccountBodyView` and its renderer (a row's
  «Reorder» is the order page's `CartCommand.Reorder`, its `navigate` followed, a refusal drawn again);
  `PageKind.Account` for both addresses, the shell's `AccountLoading` and «Your account didn’t *load*» with Retry.
  The page lays out on fractional line boxes: B-18's `CssColumn` moved to `ui/CssLayout.kt`, and a nested block whose
  `CssBox` opts in (`carry`) now starts from the fraction of its own start, so a tile at 446.4 + 372.4 is drawn at 819
  as Chrome draws it rather than at 446 + 372 (measured in Chrome on the artboard: the tiles at 446.391, 596.188,
  818.781). The order page's boxes do not opt in; its goldens are unchanged.
- **Parity** (`viddikDesignParity --component "Account*"`, references rendered on Linux with grayscale text,
  tolerance untouched), all twelve within 5 %: Loading 0.60 / 0.40, Content 1.38 / 2.99, NotMember 1.42 / 2.69,
  Orders 1.37 / 2.87, NoOrders 1.93 / 3.60, Error 1.46 / 2.86 (wide / phone). Four rounds: the tabs and chips sized
  as CSS's content box (an outlined tab is 46, its row too; the selected chip stretches to its outlined neighbours'
  40), the tile row and the cards reporting their CSS heights, then the carry. Goldens recorded for `Account*` only.

## Findings (2026-10-08)

- **The avatar of a non-member** is the tile tone their reviews are signed with (`ReviewCommands.avatarTone`): Sam's
  `#E9F0D2` and Jordan's `#F1EBDD` where the canvas draws `#E0EEF7` and `#FFE5DD`. No rule over the canvas's
  people gives both; about 0.3 % of each phone artboard.
- **The header's Orders, Saved and name labels** are drawn 7 px lower than the canvas draws them on every screen with
  the full header — the same in B-18's `Order_Placed` golden; the shared `HaulHeaderView`, not this item.
- **The canvas has no artboard** for an overview with no past orders (Jordan's overview: the active section's
  sentence and no history), for a filter that matches nothing («No cancelled orders.» on a white card), or for an
  active order on its way to a point (the card's lead is «Pickup point · <point> · <hours>»); each is drawn from the
  nearest artboard's pieces.
- **Profile line**: the canvas writes «No membership» for Sam (one order) and «Joined Oct 2025» for Jordan (none);
  the rule taken is «Joined» while a non-member has never ordered.
- **For B-21**: `OrderState.Returned` (`feature/account/screen/AccountScreen.kt`) is where a returned order's state
  goes; nothing derives it today, and the fixture test checks #HL-44019 is delivered until then.
- **Three hand-kept module lists** (`HaulModule.kt`, `testing/FulfilmentWorld.kt`, `di/KoinGraphTest.kt`) each needed
  the account's module; a module missing from the test world fails only the test that resolves it.
