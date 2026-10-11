---
id: B-89
title: "e2e: the synthetic shoppers also walk a declined payment"
status: done
priority: P3
size: S
stage: stage-10-review
---

# B-89 — e2e: the synthetic shoppers also walk a declined payment

B-31 found that the declining test card is not offered at checkout, so the stand's traffic never runs the
order saga's compensation path; tracy and metrik show only the happy path.

- AC: a share of walks (configurable, default small) pays with a card that declines and sees the order refused
  and the stock released; the saga's compensation visible in the stand's tracy.
- Anchors: `e2e/src/main/kotlin/io/github/youndie/haul/e2e/shoppers/`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/`.

## Done (2026-10-11)

Decisions are in research D6, «Decided in B-89».

- **One walk in ten declines** (`shoppers.declineEvery: 10` in the chart, `HAUL_SHOPPERS_DECLINE_EVERY` in the
  shoppers' environment, `0` for none): walks 10, 20, … pay with the test card ···· 0002, see the order refused —
  cancelled, «Your card ···· 0002 was declined», its lines back in the cart — then choose the listed card again and
  remove those lines, still holding the person's cart lock, so the next walk buys nothing twice. Their line in the
  log: «walk N as …: «…», order HL-… paid with the declining card and refused after 0.5s, its lines back in the cart
  and removed». Deterministic rather than random, and never the first walk of a fresh pod.
- **How a walk reaches the card without a visitor reaching it: a key only the shoppers hold.** The server takes
  `card-0002` in `PUT /api/v1/me/checkout` only from a request whose `X-Haul-Test-Card` carries `HAUL_TEST_CARD_KEY`
  (`feature/checkout/domain/TestCardAccess.kt`, checked in `CheckoutRouting`); any other request naming it — no
  header, a wrong one, or a server with no key at all, the default — is `422 payment_method_not_allowed` with «This
  way to pay is not offered», and the checkout keeps the card it had. The storefront never lists the card, as
  before. Rejected: a shopper-only customer whose card declines — the simulator declines a card, not a person, and the
  shoppers sign in as the demo people, whom a visitor can sign in as too.
- **The chart derives the key** from `shildik.masterKeys` (`haul.testCardKey`: `sha256("haul-test-card-0002:" + …)`)
  into the release's Secret, only while the shoppers run with `declineEvery` above 0, and hands it to the server and
  the shoppers by reference: stable across upgrades, never in a values file, nothing new for the deploy to pass.
  Chart 0.3.0 (a refusal added: `shoppers.declineEvery` that is not a whole number).
- **Before:** the test card could be chosen by its id by anyone signed in (B-14's «chosen by its id»), though no
  page offered it. Server tests that chose it over HTTP now send the key (`PlacementRoutesTest`); those that choose
  through `CheckoutCommands` are unchanged — the gate is the route's.

**Tests.**

- `CheckoutRoutesTest`: `the test card is chosen only with the stand's key` (no header, an empty one, a wrong one,
  the key less a character — each 422, the checkout still on ···· 4821 and not listing ···· 0002; with the key chosen
  and drawn «Test card: always declined»), `without a key on the server the test card is nobody's`.
  `ServerConfigTest`: the key absent unless set, refused under 16 characters. `KoinGraphTest` binds `TestCardAccess`.
- `SyntheticShopperTest.a walk that pays with the declining card sees the order refused and the stock released`
  (e2e, the image against the composed stack): the person, signed in, names the card without the key and with a wrong
  one — `422 payment_method_not_allowed`; then one walk with `declineEvery = 1`: the history holds one «Cancelled»
  order, its row `cancelled` / `payment_declined`, no `stock_reservations` left for it, every SKU's stock as before,
  and the cart empty («Your cart is empty»). `ShoppersConfigTest`: one in ten by default (walks 10 and 20 of 20), the
  key required while any walk declines and refused by its name, not with `0`, never printed.
- `scripts/chart-check.sh`: seven lines — the shoppers' share and key by reference, the server holding the same key,
  the key stable and the master keys' own, not the master keys themselves, no key without the shoppers or with
  `declineEvery: 0` — and one refusal.
- **Mutations**, each red then restored, `git status` clean: the route's gate removed → both new
  `CheckoutRoutesTest`s; `admits` opening the card when no key is set → `without a key …`; the length check removed
  → `ServerConfigTest`; the walk not removing the lines → the declining e2e test; `declines` off by one → the
  `ShoppersConfigTest` default; chart: the server's env renamed → «the server holds the same key», the helper
  ignoring `declineEvery` → «no declining walks», the key as the master keys verbatim → «not the master keys», the
  shoppers' env renamed and the refusal dropped → those two lines.

**The saga's compensation in tracy.** B-109's `OrderInTracyTest.a declined card's refusal and every member it undid
are filed under the order` is the assertion: `order saga step refused authorise-payment` (`payment_declined`), then
`order saga step undone` for `open-order`, `redeem-points`, `reserve-slot`, `reserve-stock`, all under the order's id.
The e2e shows a walk now reaches that saga outcome on the image (`cancelled`, `payment_declined`, stock released). Not
checked on the stand: no access from here, and nothing was deployed. On the stand, after the next release with the
shoppers on, every tenth walk's order answers `get_entity orderId=<id>` with those lines; the shoppers' log names
the order («paid with the declining card and refused»).

**Open, not done here.** Between a declining walk's choice and its reset — under a second at the stand's pace — the
shoppers' person's checkout shows ···· 0002 as chosen, and a pod killed in between leaves it chosen until that
person's next walk chooses the listed card. Somebody signed in as Sam in that moment could place with it (and be
declined); nobody can select it. The server could hide a stored test card from a request without the key; not done,
as it would thread the request's key through the quote, the tree and placement for a sub-second window.
