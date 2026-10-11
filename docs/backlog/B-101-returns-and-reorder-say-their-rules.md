---
id: B-101
title: "server + client: returns and reorder say what they did and what is left"
status: done
priority: P2
size: S
stage: stage-11-qa
---

# B-101 — server + client: returns and reorder say what they did and what is left

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk:
- after one partial return, «Reorder» and «Return items» vanish for good although the page says «Returns are open until
  Nov 9»; «one return per order» is in a KDoc, never shown;
- the history row then says «Returned» at the original total although one line went back;
- «Reorder» drops the SKUs it could not add (`server/src/main/kotlin/io/github/youndie/haul/feature/order/OrderRouting.kt:101`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt:13, 41-46`);
  an all-unavailable reorder lands on an empty cart with no word.

- AC: the rule is shown where the button was (or a second return is allowed — the owner's call, recorded); the row says
  «Partly returned» with what was paid; reorder names what it could not add; tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Reorder.kt`.

## Done (2026-10-11)

- **Decision: show the rule, keep one return per order.** The owner's call is not recorded in the item, and the code
  supports exactly one return everywhere: `returns` has the order id as its primary key, the refund, the points and
  the restock are keyed by the order (`refund:<order>`, `return:<order>`), and the order's progress reads one
  return. A second return would change all of them, so the rule is shown instead: once a return of some of the
  lines is in, the summary reads «One return per order: what you kept can't be sent back.» where «Return items»
  was (`OrderTotals.returnNote`, `OrderScreen.returnRule`). A whole return leaves nothing to ask about and says
  nothing. Recorded in research D4, B-21's bullets.
- **«Reorder» stays after a return.** It was offered only while the order's progress was Delivered or Picked up,
  and a return moves the progress to Returning/Returned — so a return took it away for good. It is now offered on
  every order that arrived, returned or not: a return unbuys nothing. The history's row of a returned order keeps
  «Details» (the canvas's `Account_Orders`, and the row opens the page anyway, B-74).
- **«Partly returned» with what was paid.** A refunded return of some of the lines reads «Partly returned» in the
  history at what was paid and kept — the order's total less the money refunded, the order page's «Paid»
  (`AccountScreen.view`, `OrderState.PartlyReturned`, under the «Returned» chip). Choice: a whole return keeps the
  order's total beside «Returned», as the canvas draws #HL-44019 ($87.50), rather than $0.00; while the courier
  still has the parcel the row reads «Returning» at the order's total — nothing is back yet.
- **Reorder names what it could not add.** `Reorder` now answers what it did (`Reordered`: the SKUs put in and each
  `Shortfall` — left out, or put in with fewer units than the order had), and `ReorderAnswer` words it in B-75's
  message at the `error` level: «Added what was in stock — Stoneware Mug, 12 oz isn't available now; Sony …: only
  1 of 2 went in.» after the `navigate` to the cart; when nothing went in, «Nothing was added: … aren't available
  now.» is the whole answer and the shopper stays on the order rather than landing on an empty cart. A `200` with a
  message rather than a refusal (no new error code): the press did what it could. The client needed no new code
  for it — B-92's `presenting` already runs a `sequence` and holds the message above the pages.
- **Tests.** Server: `OrderRoutesTest.a reorder names what it could not put back` (Maya's order with two pairs of
  headphones; all three SKUs at stock 0 → the message alone and an empty cart; then one pair and no mugs → the cart
  with a pair and the duvet cover and a message naming both); `ReturnRoutesTest.a returned line is refunded…` (the
  rule and «Reorder» while requested and once refunded); `AccountRoutesTest.a returned order is returned in the
  history…` («Returning» at $512.00, then «Partly returned» at $163.00); `OrderFixturesTest` with a new body,
  `order_partly_returned.json` (#HL-44019 with only the blocks sent back, `SampleOrders.partlyReturned`). Client:
  `OrderWiringTest` — the rule drawn where the button was and «Reorder» pressed on the partly returned body, no rule
  and «Reorder» on the whole return, and in the storefront a reorder answered with the cart and a message lands on
  the cart under it while one answered with the message alone stays on the order under it.
- **Mutations** (each red, restored, `git status` clean): no rule (`ReturnRoutesTest`, `OrderFixturesTest`); no
  «Partly returned» (`AccountRoutesTest`); the answer always a bare `navigate` (`OrderRoutesTest`); «Reorder» back to
  arrived orders only (`ReturnRoutesTest`, `OrderFixturesTest`); a short line not counted (`OrderRoutesTest`); the
  client not drawing the rule (`OrderWiringTest`). Under a server mutation `OrderRoutesTest.reorder puts a delivered
  order back…` also fails, with «HikariDataSource has been closed» — it runs after the failing test in the same
  class; it is green whenever the class is.
- **Goldens.** `Order_Returned` and `Order_Returned_Phone` moved: «Reorder» is drawn under the facts. The phone
  artboard is 1,446 px tall and now clips the button's lower half — the canvas draws no «Reorder» on a returned
  order (B-85 redraws the canvas). New, no artboard: `Order_PartlyReturned` (1440 × 1200) and
  `Order_PartlyReturned_Phone` (390 × 1700), the kept lines in their shipment, the returned one in its card,
  «Reorder» and the rule under it. Recorded on the Linux build machine in a copy outside the mutagen replica; a
  full `viddikRecord` there changed no other golden. The account's goldens did not move (#HL-44019 is a whole
  return).
- **The synthetic shoppers' walk** checked the history row's label, «Returned»; it now checks the row's kind
  (`HistoryStatusKind.Returned`), so a walk whose merged cart carried a second line reads «Partly returned» without
  failing.
- **Not found**: where the QA pass saw «Returns are open until Nov 9» after the return. In the code that line is
  only the heading of a Delivered or Picked-up order; once a return is requested the heading is the return's. It
  may have been the order seen before the return was asked; nothing here changes it.
- **Where the checks ran**: on the Linux build machine on `a6c26b2`'s tree: `:composeApp:wasmJsBrowserDistribution`;
  `check :server:installDist` (439 server and 330 client tests, 0 failed; `viddikVerify`), then `ktlintCheck` after
  wrapping one test line it had flagged; `scripts/e2e.sh` against an image built from the branch (the whole path,
  the shoppers' config and both synthetic-shopper walks). On the Mac: `make check`, `make docs-against
  BASE=origin/main`. After rebasing onto B-99 (#120): `:composeApp:wasmJsBrowserDistribution` and `check
  :server:installDist` again on the Linux build machine (445 server and 334 client tests, 0 failed; `viddikVerify`,
  ktlint), `make check` and `make docs-against BASE=origin/main` on the Mac.
