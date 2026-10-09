---
id: B-50
title: "server + client: the return dialog names the points share of a refund"
status: done
priority: P3
size: S
stage: stage-6-account
blocked_by: [B-21, B-23]
---

# B-50 — server + client: the return dialog names the points share of a refund

B-21's return dialog shows the refund as the ticked lines' full value. Since B-23 an order paid partly with
points refunds the card that value minus the points' share, and gives the share back as points — the dialog
does not say so, so the card is refunded less than the dialog promised. Found by B-23.

Decided as product owner: for an order paid partly with points the dialog's refund box reads the card amount
and, beneath it, «+ N points back»; the order page's «Refunded» row and the account's numbers already follow
the ledger. An order paid without points reads as today.

- AC: the dialog's total for a points-paid order equals what the card is refunded, with the points line; a
  route test over an order paid with points; the `Order_ReturnDialog` goldens unchanged (Maya's sample order
  is paid without points).
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/returns/`.

## Done (2026-10-09)

- **Where the numbers come from.** Each `ReturnLine` carries `refundCents` and, new, `pointsBack` — its share of
  the points the order was paid with (`ReturnRefunds.pointsBack(order)`, B-23's ledger rule made per line); the
  form carries `pointsBack` = «+ {points} points back» only when the order redeemed points. The dialog adds up
  the ticked lines: the card amount is Σ(`refundCents − pointsBack`), the points Σ`pointsBack`
  (`groupedCount`, shared, writes «1,926»). It computes nothing else, and an order paid without points has
  `pointsBack = 0` on every line and no template, so it reads as before and its wire body is the same bytes.
- **Rounding — the share is per line now, not per return.** B-23 took one `floor(redeemed × refund ÷ paid)`
  over the whole return, which no per-line numbers can add up to (the duvet cover and the mugs: 673.28 + 116.25
  floor to 789 together, 673 + 116 apart). So the rule moved to the lines, as the discount already was: each
  share rounded down, the few points left over given one each to the lines whose share was cut, from the last
  line back. The shares add up to every point redeemed, each is its exact part rounded down or up, and none
  passes its line's refund (the redemption is capped at the items as paid) — «last takes the rest» could, and
  would make a line's card amount negative. `ReturnSimulator` sums the same shares for the returned positions,
  so the dialog is exact for any set. Single-line returns of every line but the one that took a leftover
  point are what B-23 gave (Sony's 1,690 of Maya's 2,480 included); a multi-line partial return can differ
  from B-23's number by fewer points than lines (the duvet cover and the mugs: 790, was 789). No migration:
  shares are computed from the order at refund time.
- **Haul Pay: the same box.** «Refund {amount} to your Haul Pay plan» with the money part and «+ N points back»
  under it. The money is what the plan and the processor give back together (`HaulPayPlans.refund` takes it
  off what is still owed first, the rest goes back to the card); the split depends on how many payments are
  taken when the parcel is back, which the dialog cannot know, so it names the plan and the one amount. Pay on
  delivery reads the money part too («in cash, from the courier»).
- **Tests**: `ReturnRefundsTest` — the shares of Maya's order (1,690 / 673 / 117), a set's points as the sum of
  its shares, each share within its exact part and its line's refund over seven orders (the 299-point case
  where «last takes the rest» would give a one-cent line two points), `groupedCount` against the server's
  `count`. `ReturnRoutesTest.a points-paid order's dialog says what the card and the points get back for any
  lines ticked` — Maya's order paid with 2,480 points over HTTP: the form's per-line shares, every one of the
  seven tickable sets summing to the refund rule's points, then the duvet cover and the mugs returned and
  refunded: the card gets $155.10, the dialog's sum, and 790 points come back; the plain order's dialog test
  now asserts no points line. `HaulPayPlanTest.a points-paid return gives back through the plan what the
  dialog said` — $332.10 = the fourth payment covered ($121.80) + $210.30 refunded. Client:
  `ReturnWiringTest.ticking lines updates the card amount and the points back` over a server-built body
  (`order_delivered_with_points.json`, #HL-46102 as if paid with 2,480 points, held to the tree by
  `OrderFixturesTest`): $60.74 / 1,926, $78.20 / 2,480, $17.46 / 554, then nothing; the plain dialog test
  asserts no points line.
- **Mutations**, each restored: the refund back on B-23's per-return floor → only the new route test fails
  (that rule refunds $155.11 and gives back 789 points); no leftover distribution → the two share tests and
  the route test; the form's lines without their shares → the route test, the Haul Pay test and
  `OrderFixturesTest`; the template on every order → `OrderFixturesTest` and the plain dialog route test; the
  client summing `refundCents` only, or not drawing the points line → the new wiring test alone.
- **Goldens and parity**: the `Order_ReturnDialog*` bodies and goldens did not move; `viddikVerify` green;
  `viddikDesignParity --component "Order - Return*"` 2.51 / 3.23 / 1.84 / 3.18 % on the branch and on the
  base dialog alike.
- **Where it ran**: the Linux build machine (WSL), on `9915261` (rebased afterwards onto `73655ac`, a backlog
  document only): `:composeApp:wasmJsBrowserDistribution`; `check :server:installDist` — `:server:test` 316
  tests, `:composeApp:desktopTest` 163, `viddikVerify`, ktlint; `scripts/e2e.sh` green.

## Findings (2026-10-09)

- **The return's heading still names the whole value.** «$349.00 goes back to your card ···· 4821 once the
  seller has it» (`OrderPage.returnHeading`, «Return requested» / «picked up» / «refunded») and the paid fact
  «$x refunded» use `OrderReturn.refundCents`, the lines' value with the points' share in it. Out of this
  item's scope (the dialog); for a points-paid order the page then promises more money than the card gets,
  as the dialog did.
- **A failing `http {}` in `ReturnRoutesTest` takes the next test down with it** (`HikariDataSource … has been
  closed`), seen only while a mutation was making the first one fail; on green runs the class is green.
