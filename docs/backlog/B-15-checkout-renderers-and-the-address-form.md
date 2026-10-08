---
id: B-15
title: "design refs + client: Checkout renderers and the address form"
status: done
priority: P1
size: L
stage: stage-5-order
blocked_by: [B-01, B-13, B-14]
---

# B-15 — design refs + client: Checkout renderers and the address form

The checkout screen and the address form.

Feature: `feature-checkout` — its scenarios are this item's acceptance where it names them.

- Not covered: redeeming points (B-23); `Checkout_PointsApplied` is drawn from the canvas's data.

- AC: parity for every `Checkout_*` artboard; the address form works.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/`, `composeApp/src/desktopTest/snapshots/design`.

## Done (2026-10-08)

`viddikDesignParity` (default tolerance, 5 % of pixels at ±16 per channel, untouched; references not
edited), against references rendered with grayscale text (`--disable-lcd-text`, as B-13's):

| Artboard | Mismatch | | Artboard | Mismatch |
|---|---|---|---|---|
| Checkout_Loading | 0.06 % | | Checkout_Loading_Phone | 0.18 % |
| Checkout_Content | 1.36 % | | Checkout_Content_Phone | 2.47 % |
| Checkout_PointsApplied | 1.41 % | | Checkout_PointsApplied_Phone | 2.55 % |
| Checkout_PickupPoint | 1.32 % | | Checkout_PickupPoint_Phone | 2.32 % |
| Checkout_ParcelLocker | 1.48 % | | Checkout_ParcelLocker_Phone | 2.26 % |
| Checkout_Validation | 1.19 % | | Checkout_Validation_Phone | 2.21 % |
| Checkout_Placing | 1.03 % | | Checkout_Placing_Phone | 1.78 % |
| Checkout_PlaceError | 1.30 % | | Checkout_PlaceError_Phone | 2.52 % |
| Checkout_Error | 0.88 % | | Checkout_Error_Phone | 2.80 % |

All eighteen within tolerance, PointsApplied included (drawn from the canvas's data, below). What is
left is glyph edges, a pixel of column rounding in the three-across cards, and the focus ring Content
draws on the street field (a fixture focuses nothing). Goldens recorded on Linux.

- References: the eighteen `Checkout_*` artboards rendered into `composeApp/src/desktopTest/snapshots/design/`
  (their sizes added to `.canvas/canvas.json`'s `artboards`); `manifest.json` lists all 83.
- Contract (`shared/.../ui/CheckoutComponents.kt`): the page is `CheckoutHeader` + `CheckoutBody`
  (`haul_checkout_body`: the title, the notices, the sections in order, the summary — the canvas puts the
  sections beside the order at 1440, which a page's column cannot say, as `CartBody` for the cart).
  `MethodOption.price`; `CheckoutAddress` is the form alone (`form`, `url`: no saved-address list, no
  open/add/save labels — the canvas draws an inline form); `FormField.placeholder`; `SlotDay.weekday`,
  `.date`, `.selected`; `DeliverySlots.notice`; `PickupPointOption.detail` in place of `hours`;
  `PaymentMethods.points` (the toggle sits under the ways to pay), `PointsToggle.detail` and a nullable
  `url`; `CheckoutSummary.title`, `.placeHint`, `.placingLabel`, `points` and `redeem` gone.
- Server (`server/.../feature/checkout/screen/CheckoutScreen.kt`, `domain/`): the canvas's copy — «How to
  receive» with «Tomorrow, Oct 8» / «Thu, Oct 9 · 240 m away» / «Thu, Oct 9 · 24/7 access» and «Free»
  (a pickup is a day after the courier's first window, research D7), «Address» with «Street address»,
  «Apt / suite», «City», «ZIP», «Door code» («Optional»), «Note for courier» («e.g. leave with the
  doorman») holding the address delivered to, «Delivery time» with day tiles and windows «15:00 – 18:00»
  («· Full» when full), «Pickup point» / «Parcel locker» rows «Pickup point · open until 21:00 · Thu, Oct
  9», «Payment» with «Card or cash» for pay on delivery, «Your order» with «Delivery · Wed, Oct 8», the
  terms under the button, and why it is held. The button is held while the address form is at fault
  (`CheckoutState.placeable`). Address errors «Enter the street address», «Enter a 5-digit ZIP». The test
  card ···· 0002 is not listed (still chosen by id, B-16's declined card). Seed: 315 Grand St (900 m),
  the lockers' distances (180 m) and «Bedford Ave station» (700 m); hours written «open until 21:00».
  `CheckoutRoutesTest` asserts the new copy (the B-14 expectations that encoded its guesses);
  `CheckoutFixturesTest` holds the client's five checkout bodies equal, as JSON, to the trees the server
  builds for each artboard's checkout, and PointsApplied to Content with the canvas's changes (below).
- Client: `feature/checkout/CheckoutViews.kt` (header, body at both widths, the sections, the address form,
  the summary, Placing), `CheckoutCommandsClient.kt` (the seam), `CheckoutHeaderRenderer` and
  `CheckoutBodyRenderer` in `registry/HaulRenderers.kt`, `CheckoutLoading` and `CheckoutError` in
  `shell/Shell.kt`; `/checkout` is `PageKind.Checkout` (its placeholders and its failure), and
  `Storefront`/`App` take the `CheckoutCommands` that `Main` builds.
- **The command seam**: a press is a `CheckoutCommand` — `Choose` (`PUT` a `CheckoutChoice`), `SaveAddress`
  (`POST` an `AddressEntry`), `Place` — sent by `CheckoutCommands.send`; the browser's is
  `ktorCheckoutCommands(http, origin, identity::send)`. The answer — `refresh` — goes to the screen's
  handler, which B-35's shell answers by fetching the tree again; a refusal (a form at fault, a window
  that filled) is a `refresh` too, no answer leaves the page. The address form is sent when the shopper
  leaves it (or presses Enter in a field), only when something changed.
- **Placement** (B-16, merged while this was built): «Place order» sends `CheckoutCommand.Place` —
  `PlaceOrderRequest(quote)` to `CheckoutSummary.placeUrl` under `IDEMPOTENCY_KEY_HEADER`, one key per
  quote (`newIdempotencyKey`, remembered per fingerprint, so a retry is the same order) — and draws
  Placing until the answer: a `navigate` to the order is followed, a refusal redraws the checkout.
- Fixtures: `composeApp/src/desktopTest/.../CheckoutFixtures.kt`, one per artboard; Placing is Content's
  body drawn with the order on its way.
- Tests: `CheckoutWiringTest` (each choice's command and the redraw; the chosen method, a full window and
  a held button send nothing; another day's tile shows its windows; the address sent once the shopper is
  done and not while moving between fields, nothing for an unchanged form; Validation's errors; the points
  toggle inert; Placing drawn while placement answers and nothing sent meanwhile; a retry keeps its key;
  in the storefront, `/checkout` loads `/ui/checkout`, a command's answer fetches it again, a failed
  checkout says the cart is unchanged), `CheckoutCommandsTest` (method, URL, body, bearer token and key
  over a mock engine; a refusal's fields), `AddressTest` (`/checkout`). Mutations seen failing: a key per
  press (the retry test), the form sent on every focus change (the address test), the button not held by
  a form at fault (`CheckoutFixturesTest`), the test card listed again (`CheckoutRoutesTest` ×2).

## Findings (2026-10-08)

- **The canvas answered B-14's copy questions** and the server follows it (Done). Two of B-14's choices
  were rules, not guesses, and stay: the default window is the first with room (Content's 15:00–18:00 is
  the fixture's choice), and the windows start tomorrow whatever the items' dispatch days.
- **Points are drawn from the canvas's data.** No points balance is stored (B-23), so the server sends no
  toggle. Every artboard draws one, off: the five bodies carry the canvas's toggle — «Use 2,480 points
  (−$24.80)», «100 points = $1 · all or nothing», no `url`, so pressing does nothing — and
  `CheckoutFixturesTest` checks it is exactly that one before comparing the rest with the server. The
  PointsApplied body is Content's tree with the canvas's notes applied (toggle on, «Points −$24.80»,
  «$487.20», «Place order · $487.20», Haul Pay «4 payments of $121.80», the same quote fingerprint), and
  the test holds it to exactly that. B-23 replaces both with the server's own.
- **Chrome's `line-height: normal` is a pixel taller than Compose's** for Archivo at most sizes (ascent
  and descent rounded apiece: 19 px at 17, 15 at 14). On a phone the checkout stacks enough lines for that
  to move the page by tens of pixels (Content_Phone 10.95 %); `CheckoutViews` sets the browser's line
  explicitly and the phones came to 2.2–2.6 %. The other screens still use Compose's line.
- **The address form has no button on the canvas**, so it is sent when the shopper leaves it; the
  canvas's note says the client checks the server's rules (kompot-forms) — here the server's refusal draws
  them, one round trip, one copy of the rules. Every save is a new address row (B-14's `addAddress`):
  editing «4F» to «5B» leaves the old one stored, unlisted.
- **Placement and the held button disagree in one case**: the button is held while a refused form is
  shown, but `Placement` checks only `quote.complete`, which the previous address still satisfies — a
  client that ignores the button could place to the old address. One line in B-16's `Placement`.
- **An existing database keeps B-14's points**: the seed runs only into an empty catalog, so a stand
  seeded before this has two points, one locker without a distance and the old hours wording.
- **Content draws the street field focused** (ink border, a caret): a real state of the form, not one a
  fixture reaches; it is part of Content's residual.
- **Two files named `CheckoutCommands.kt` in one package** (the contract's and the client's) compiled to
  one JVM facade class twice; the client's is `CheckoutCommandsClient.kt`. The cart's pair escapes only
  because the contract's has no top-level declarations.
- Not built here: points (B-23), editing or listing saved addresses (no artboard), a card form; the step
  indicator stays on «Delivery» (the canvas's note: the steps are labels in v1).
- PR #2's drafts this changes: screen-checkout (states ticked; Loading and Error are the client's
  `CheckoutLoading` / `CheckoutError`; the copy above), feature-checkout (the client half; the held button),
  endpoint-checkout (the commands as `CheckoutCommand`s; refusals redraw), haul-shared (`CheckoutBody` and
  the field changes above).
