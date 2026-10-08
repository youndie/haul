---
id: B-48
title: "server + client: the product page adds to cart and buys now"
status: done
priority: P1
size: S
stage: stage-4-cart
blocked_by: [B-37]
---

# B-48 — server + client: the product page adds to cart and buys now

The product page draws «Add to cart» and «Buy now» and neither carries a command (B-37 left them «owned by
nobody»). The storefront's main button does nothing: B-26's end-to-end run had to add through a category
card's «+» instead. Found by B-37, met again by B-26.

Decided as product owner: «Add to cart» puts the chosen SKU (the variant the page shows) in the cart — one
more of it, up to the line's limit, through the cart's existing command — and the page redraws with the
header's count; «Buy now» does the same and navigates to `/checkout` (a guest goes through sign-in with
`next=/checkout`, B-41/B-44). Out of stock, neither is offered (the page's own out-of-stock state).

- AC: both buttons carry commands fixed in the tree for the SKU shown; route tests for each (count, the
  line's SKU, the navigate, the limit, out of stock), client wiring tests; B-26's run adds from the product
  page instead of the card; product goldens unchanged.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/LinePress.kt`.

## Findings (2026-10-08)

- **Built.** The contract: `ProductDetails.add` and `ProductDetails.buy`, both a `LineCommand`
  (`shared/.../ui/ProductComponents.kt`), and `LineCommand.next` (`shared/.../feature/cart/CartCommands.kt`) — what
  the client follows once the server accepted the change, in place of its answer. The server:
  `ProductScreen.details` draws them for the viewer's cart — `add` is `addToCart`, the card's «+» for the SKU shown;
  `buy` is the same line with `selected = true` and `next`. The client: `linePress` (`composeApp/.../feature/cart/
  LinePress.kt`) is the one press of a fixed line change, the card's «+» and both buttons alike; `CartCommands.run`
  takes the `next` and returns it only when every command was answered, so a refusal is the usual `refresh`.
- **A guest's «Buy now» goes to `/sign-in?next=%2Fcheckout`, not to `/checkout`.** It is the address the cart's
  «Sign in to check out» uses (`CartScreen.SIGN_IN`), so both ways to checkout run one sign-in and one merge, the
  line just added comes along, and a sign-in that does not go through draws the product page again with the header's
  new count (B-41). Navigating to `/checkout` would also have worked — the guest is refused with `401` and B-44 asks
  for a sign-in — but that prompt is the shell's answer to an address opened directly, drawn under a failed page
  load; a button the server draws should not lead through a refusal it can foresee.
- **«Buy now» selects the line.** Checkout takes the selected lines only (B-14), so a line the shopper had unticked
  would have been added to and then left behind. «Add to cart» keeps the line's selection as the card's «+» does.
- **At the line's limit «Buy now» only selects the line** (`LineChange(selected = true)`) and still goes to checkout:
  the SKU is in the cart as many times as it can be, and pressing «Buy now» means buy it, not a button that does
  nothing. «Add to cart» is absent there, as the card's «+» is. Out of stock both are absent and the buttons stay
  drawn greyed (`Product_OutOfStock`), so no golden changed.
- **The client bodies were left as they were.** `product_description.json` and `product_out_of_stock.json` carry no
  `add` or `buy`: the commands draw nothing, and the bodies are compared with the server only in their tabs and
  reviews (`ReviewFixturesTest`). `BuyBoxWiringTest` adds the commands to the description body itself.

## Verification (2026-10-09)

- **Tests.** Server, `ProductButtonsTest` against the routes and PostgreSQL: «Add to cart» puts one more of the SKU
  shown (the travel-case bundle, not the cheapest) and the redrawn page counts it and offers one more; a guest's
  «Buy now» adds, selects an unticked line again and names `/sign-in?next=%2Fcheckout`; a customer's (shildik)
  names `/checkout`, and checkout opens on the line; at ten mugs, and at a SKU's whole stock, «Add to cart» is gone
  and «Buy now» only selects; out of stock neither is offered. Client, `BuyBoxWiringTest` in the storefront over
  the server's Product_Description body: «Add to cart» sends its change and fetches the page again; «Buy now» opens
  `/checkout`, or signs a guest in first; a refused «Buy now» stays and redraws; out of stock nothing is sent.
  B-37's card «+» tests (both `DrawnActionsTest`s) hold the shared press.
- **Mutations**, one batch, each seen failing its own test with its own message, then restored: `add` dropped,
  the guest and customer addresses swapped, the out-of-stock guard removed, a quantity added at the limit (all five
  server tests); «Add to cart» unwired, `next` ignored, `next` followed on a refusal (four client tests). The
  client's out-of-stock test guards an absence and held, as it would on the code before the change.
- **The gate** on the Linux build machine, after the rebase over B-20 (the Saved list, which changed the same card
  and buy box: the card's heart keeps its own press, «+» takes the shared one):
  `:composeApp:wasmJsBrowserDistribution`; `check :server:installDist` — 252 server tests, 146 desktop tests,
  `viddikVerify` with every golden unchanged; `scripts/e2e.sh` on the image built from the branch —
  `WholePathTest` passed, adding from the product page.
