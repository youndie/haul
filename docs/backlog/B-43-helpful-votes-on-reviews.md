---
id: B-43
title: "server + client: «Helpful» votes on reviews"
status: done
priority: P3
size: S
stage: stage-7-reviews
blocked_by: [B-22]
---

# B-43 — server + client: «Helpful» votes on reviews

B-22 serves reviews with their «Helpful (48)» button drawn and inert: the vote route the feature
document names (`PUT /api/v1/reviews/{id}/helpful`, refused on one's own review with `own_review`) was
outside its acceptance. Found by B-22.

Decided as product owner: one vote per customer per review, and a second press takes it back; a guest's
press is `navigate /sign-in`; the count on the button is the stored count.

- AC: a vote moves the review's count by one and a second press moves it back; voting on one's own review
  answers `409 own_review`; two votes by one customer never count twice (a route test and a unique index).
  The button carries the action and the client follows its `refresh`.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/`, `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/`.

## Done (2026-10-08)

- **Wire** (`shared`): `HelpfulVote` (`helpful`: the vote the press leaves) and `HelpfulCommand` (`url`,
  `vote`) in `feature/reviews/ReviewCommands.kt`; `Review.helpfulCommand` beside B-08's `helpfulAction`;
  `ErrorCode.OwnReview` (`own_review`, `409`) and `ErrorCode.ReviewNotFound` (`review_not_found`, `404`).
- **The vote is a state, not a toggle.** The decision «a second press takes it back» is kept by the tree,
  not by the route: a customer's review carries `HelpfulCommand(PUT /api/v1/reviews/{id}/helpful,
  HelpfulVote(helpful = !voted))`, so the first press votes, the page is drawn again with the opposite
  vote on the button, and the second press takes it back. A press sent twice — a retry, a double click —
  counts once, as a card's «+» carries the next quantity (research, «Decided in B-37»). A toggle on `PUT`
  would have counted a retried press as taking the vote back.
- **Server** (`server/.../feature/reviews/`): `V15__helpful_votes.sql` — `helpful_votes`, one row per
  customer per review, its primary key `(review_id, customer_id)` the unique index under «one vote each»;
  `ExposedReviews.vote` writes the row with `ON CONFLICT DO NOTHING` (or deletes it) and moves
  `reviews.helpful` by an SQL increment only when the row went in or out, both in one transaction; no
  lock, because the index decides and the increment is atomic. `ReviewCommands.helpful`, the route
  `PUT /api/v1/reviews/{reviewId}/helpful` in the customer tier, answered `200` with `refresh`.
  `ReviewTabs` asks the repository which of the listed reviews the viewer voted on (`votedHelpful`).
- **Who may vote, as built**: any customer on anybody's review but their own. The own review carries
  neither a command nor an action (the button is drawn, and pressing it does nothing); the route refuses
  it all the same (`409 own_review`). A guest's «Helpful» is `navigate /sign-in`. The count on the
  button is the stored count: the seed's 48 for Aisha K. stand for votes `helpful_votes` does not hold one
  by one, and a vote moves them to 49.
- **Client** (`feature/product/`): `ReviewCommand.Vote`, sent with `PUT` by `ktorReviewCommands` (the
  dialogs' stay `POST`); `ReviewCommands.vote` follows the answer, follows `refresh` on a refusal too (the
  page then shows what the server has), and nothing when there is no answer. «Helpful» in `ReviewCard`
  (`HELPFUL_TAG`) sends the command through `LocalReviewCommands` and hands the answer to
  `LocalHaulActions`; a guest's press follows its action. No pressed state: the canvas has none.
- Goldens: the drawn button did not change (the press adds no indication); `product_reviews.json` gained
  the two reviews' `helpfulCommand` for Maya, which `ReviewFixturesTest` holds equal to the server's tree.
- Tests: `ReviewRoutesTest` (+5: helpful is a customer's vote and a guest's way to sign in; a vote moves the
  count by one and the second press moves it back; one customer's vote sent twice or at once counts once,
  the rows read past the repository; a vote on one's own review is refused with own_review; a vote is a
  customer's for a review that exists — `401`, `404 review_not_found`, `400` naming `request`), `SchemaTest`
  (V15), `ReviewFixturesTest`; client `ReviewWiringTest` (+3: helpful sends the review's vote and the answer
  redraws the page; a refused vote redraws the page and one with no answer does not; a guest's helpful signs
  in and sends no vote) and `ReviewCommandsTest` (+1: method, URL, body, bearer; refusal and no answer).
- Mutations seen failing: the count moved whether or not the row went in («sent twice or at once»); the
  primary key dropped from V15 and the table together (the same test; `SchemaTest` stays green, as it
  compares the two sides); the button's vote not inverted («the second press moves it back»); the own-review
  refusal dropped from the repository, and separately the own review carrying a vote in the tree (both
  «own_review»); the client following the guest's action instead of the command (two wiring tests); a
  refusal followed by nothing (`ReviewCommandsTest` and the wiring test).

## Findings (2026-10-08)

- **No «voted» look.** The canvas draws «Helpful» one way; after a vote only the count says so (and on a
  review nobody else found helpful, «1 person found this helpful» appears). A pressed state would be a
  design decision — an artboard — not built here.
- **The order of the reviews matters now.** B-22's finding stands: feature-reviews says «most helpful»
  first, the canvas and the server list the newest first. With votes counted, sorting by `helpful` is one
  `orderBy` away; it changes which review the artboard draws first, so it is the owner's call.
- **V15 is this item's; V13 is still reserved.** Flyway runs with `validateOnMigrate` and without
  `outOfOrder`, so a database migrated past V13 — by V14, on `main` since B-40, or by this V15 — refuses to
  start once a V13 arrives. Whatever holds V13 merges before any stand migrates further, or takes a number
  above the highest one merged.
- The draft endpoint-reviews says «request: —» for the vote; it carries `HelpfulVote` (above), answers `200`
  with `refresh`, and refuses with `400 validation_failed` (`request`), `401 unauthenticated`,
  `404 review_not_found` and `409 own_review` — the draft's `404` had no code.
