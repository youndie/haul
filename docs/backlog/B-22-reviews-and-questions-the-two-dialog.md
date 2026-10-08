---
id: B-22
title: "server + client: reviews and questions, the two dialog routes and forms"
status: done
priority: P2
size: L
stage: stage-7-reviews
blocked_by: [B-08, B-17]
---

# B-22 — server + client: reviews and questions, the two dialog routes and forms

Reviews and questions complete the product page; answers are seed data only, as there is no seller side.

Feature: `feature-reviews` — its scenarios are this item's acceptance where it names them.

- Not covered: moderation.

- AC: feature-reviews scenarios pass; parity for the remaining `Product_*`.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/`.

## Done (2026-10-08)

`viddikDesignParity` (default tolerance, 5 % of pixels at ±16 per channel, untouched; references not
edited — B-06's, rendered with grayscale text on the Mac), first round, all twenty `Product_*` within
tolerance:

| Artboard | Mismatch | | Artboard | Mismatch |
|---|---|---|---|---|
| Product_Reviews | 2.43 % | | Product_Reviews_Phone | 3.69 % |
| Product_Questions | 2.80 % | | Product_Questions_Phone | 3.52 % |
| Product_ReviewDialog | 2.03 % | | Product_ReviewDialog_Phone | 3.57 % |
| Product_QuestionDialog | 2.28 % | | Product_QuestionDialog_Phone | 3.34 % |

The other twelve did not move (Loading 0.68 / 0.29, Description 2.78 / 4.05, Specifications 2.92 /
1.98, OutOfStock 2.72 / 4.02, NotFound 1.43 / 4.53, Error 1.40 / 2.67 %). What is left in the dialogs is
glyph edges, the product line (the server's name, below), a 1 px move of the review's text (the canvas
draws the field focused, with a caret) and a crescent of the card's shadow under it. Goldens recorded on
Linux for the four dialogs; the other sixteen are byte-identical to the committed ones.

- Server (`server/.../feature/reviews/`): `V12__reviews.sql` — `reviews` (one per customer per product,
  a unique index under the check), `rating_counts` (the histogram, per product and stars) and `questions`
  (an answer only with its date); `ReviewCommands` (post, ask), `ExposedReviews`, `ReviewTabs` (the two
  tabs and the two forms), `ReviewsRouting` in the customer tier. The product page's tab row lists four
  tabs with the reviews' and questions' counts, and `?tab=reviews` / `?tab=questions` are answered.
  Writing a review takes the product's row (`FOR UPDATE`) and moves its count, its average and its
  histogram in the same transaction; asking moves the questions' count.
- **Who may review, as built**: any customer (the customer tier; a guest gets `401`, and the tab's button
  is the way to sign in), once per product (`409 review_exists`). «Verified purchase» is decided when
  the review is written: the author has a shipment of one of their orders holding the product that is
  `delivered` or `picked_up`. Without one the review is posted, unmarked — feature-reviews makes the
  delivered shipment the mark, not the right to review.
- Wire (`shared`): `ReviewEntry` (rating, title, body), `QuestionEntry` (text), `ReviewRules` and
  `reviewProblems` / `questionProblems` — the one copy of the rules, read by the server to refuse and by
  the client to check before sending; `ErrorCode.ReviewExists` (`409`); the components `ReviewForm`
  (`haul_review_form`) and `QuestionForm` (`haul_question_form`) with `FormProduct`. `ProductReviews.action`
  and `ProductQuestions.action` are kompot's `present` of the form for a customer, `navigate` to
  `/sign-in` for a guest.
- Routes and errors: `POST /api/v1/products/{id}/reviews` and `POST /api/v1/products/{id}/questions`,
  customer tier, `201` with kompot's `sequence` of `close` and `refresh`; `400 validation_failed` with every
  field at fault in `ErrorBody.fields` (`rating`, `title`, `body`; `text`; `field_required` when empty,
  `field_invalid` otherwise), `field: request` for a body that is not JSON; `401 unauthenticated`;
  `404 product_not_found`; `409 review_exists`. The dialogs have no address: `StorefrontPage` is unchanged
  (research, «Decided in B-22»).
- Seed (`seed/SampleReviews.kt`): the canvas's two reviews (Daniel R., Aisha K., 48 helpful), the
  histogram 1,826 / 328 / 94 / 47 / 46 = 2,341, the two answered questions and «Is the headband adjustable
  enough for a small head?» unanswered; `SeedDigest` covers the three tables.
- Client (`feature/product/`): `ReviewDialogs.kt` (the scrim and card, both forms at both widths, the
  stateful dialogs), `ReviewCommandsClient.kt` (the seam: `ReviewCommand.Post` / `Ask`, `ReviewCommands`,
  `ktorReviewCommands`, `LocalReviewCommands`), `ReviewFormRenderer` and `QuestionFormRenderer`; «Write a
  review» and «Ask a question» follow the tab's action. The shell draws a `present` over the page and
  follows `close` and `sequence` (`presenting` in `Storefront.kt`); `App` and `Main` pass the review
  commands. `normal()` (Chrome's `line-height: normal`) moved from `CheckoutViews` to `ui/Basics.kt`.
- Fixtures: `ProductFixtures.kt` gained the four dialogs — the tab's page with the form its `present`
  carries drawn over it, holding what the canvas typed. `product_reviews.json` and `product_questions.json`
  now carry the server's tab row and tab, the form included; `ReviewFixturesTest` holds those sections
  equal, as JSON, to what the server builds for Maya.
- Scenarios (feature-reviews): «A verified review» — Maya with delivered #HL-46102 holding the headphones
  posts five stars: `201`, «Oct 7 · Verified purchase» first, «2,342 reviews» on the tab and its caption;
  «A second review» — `409 review_exists`, the count unmoved; «Too short» — a body of five characters is
  `400 validation_failed` naming `body` (`ReviewRoutesTest`, all three).
- Tests: `ReviewRoutesTest` (12: the two tabs as the canvas draws them, the guest's sign-in, the
  customer's dialogs and the chosen SKU in them, the three scenarios, an in-transit order posting
  unmarked, every field named at once, a malformed body, `401`, `404`, a question asked and counted, a
  short and a long question), `ReviewFixturesTest`, `ReviewCountingTest` (the average, the author's name),
  `SchemaTest` (V12), `KoinGraphTest`; client `ReviewWiringTest` (8: a review posted where the form says
  and the page fetched again, the rules checked before sending, a refusal and a refused field drawn in the
  dialog, no answer keeps the draft, Cancel and «×», a question sent, a guest sent to sign in) and
  `ReviewCommandsTest` (method, URL, body and bearer token over a mock engine; a refusal's fields).
- Mutations seen failing, each in a private copy on the Linux build machine: an in-transit shipment
  counted as received (the unmarked review), the one-per-product check and the index's answer ignored («A
  second review»), the body's minimum dropped from the rules («Too short» and the client's check; the
  review that then got in moved the shared seed's count under the tab test too), the count not moved
  («A verified review», «A second review»), the tab's `present` dropped (`ReviewFixturesTest` and two
  route tests), `close` ignored by the shell (three wiring tests), the client posting without checking
  the rules (the wiring test).
- Where it ran: `check :server:installDist :composeApp:wasmJsBrowserDistribution` on the Linux build
  machine (PostgreSQL and shildik in Testcontainers, `viddikVerify` green), parity and recording there in
  a private copy; `make check` on the Mac.

## Findings (2026-10-08)

- **The canvas's histogram does not give its average.** 78 / 14 / 4 / 2 / 2 % averages 4.6; every artboard
  writes 4.8. Both are kept as drawn (research §6): the histogram as counts, the average on the product,
  and a review moves the average from the stored one by its share (`ExposedReviews.average`), not by
  recounting the histogram — which feature-reviews' «recomputed when a review is written» would otherwise
  mean, and which would turn the page to 4.6 at the first review.
- **The order of the reviews, for a person.** feature-reviews says «most helpful» by default; the canvas
  (and its note, «sort most helpful») draws Daniel R. — nobody found it helpful — above Aisha K. with 48.
  The server lists the newest first, the only rule the canvas's data agrees with; no sort control is
  drawn. Choices: keep newest-first and correct the feature; or sort by helpful and re-draw the artboard.
- **«Helpful» carries nothing.** The vote (`PUT /api/v1/reviews/{id}/helpful`, `409 own_review`,
  endpoint-reviews) is outside this item's AC and no item holds it; the button is drawn and inert, as B-37
  allows only for a control a later item gives something. Whether it becomes an item is the owner's call.
- **#HL-46102.** feature-reviews' «A verified review» names Maya's delivered #HL-46102 holding the product;
  `canvas.json` (Order_Delivered) gives #HL-46102 a Merino sweater and a serum, $103, which cannot hold the
  $349 headphones, and asks for it to be reconciled. No orders are seeded yet, so the test writes its own
  #HL-46102 with the headphones; the order fixtures will have to pick one of the two.
- **The product line in the dialogs** is the server's — brand and title, «Sony WH-1000XM6 Wireless Noise
  Cancelling Headphones», cut with an ellipsis on a phone — where the canvas writes «Sony WH-1000XM6». The
  seed has no shorter name (the same drift B-13 found on cards and cart lines); part of the dialogs'
  residual. The canvas's counter reads «122 / 5,000» for a review of 120 characters; the client counts.
- **The SKU's options come out of `jsonb` sorted**, `bundle` before `colour`: the dialog orders the colour
  first on purpose. `ProductScreen.variants` reads the same keys unsorted, so the server's details list
  Bundle above Colour, where the canvas (and B-08's hand-written bodies) draw Color first — B-08's tree,
  not this item's; worth a look when the details are compared with the server.
- **A list without paging.** Ten reviews and ten questions are listed (feature-reviews: 10 per page) and
  no artboard draws a second page. Questions are listed answered first, so on a product with more than
  ten answered a new question would not be on the tab. Only the sample product has rows; a generated
  product keeps its random counts with no reviews, no questions and an empty histogram (0 % bars).
- **An existing database has none of it**: the seed runs only into an empty catalog, so a stand seeded
  before V12 has the tables empty — tabs that answer with counts and no rows — until it is seeded again.
- **The card's shadow** (`0 40px 100px -20px`) leaves a thin crescent under the desktop card in the diff:
  `dropShadow` with the same numbers is a little darker at its bottom edge than Chrome's. Inside tolerance
  on the first round; not chased.
- PR #2's drafts this changes: feature-reviews (the verified rule as built — a mark, not a gate; the
  scenarios' `**Automated:**` lines, `ReviewRoutesTest`; the sort; helpful not built), endpoint-reviews
  (`201` with `close` + `refresh`, the fields, `request` for a malformed body; the helpful route not
  built), screen-product (Reviews and Questions answered by the server, ReviewDialog and QuestionDialog
  ticked: kompot's `present` over the tab, no address), haul-shared (`ReviewForm`, `QuestionForm`,
  `FormProduct`, `ReviewEntry`, `QuestionEntry`, `ReviewRules`, `review_exists`).
